package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * V0.19.0 (#1172, DT-NOVA-1, RN-NOVA-1 a 6) — único ponto que cria, baixa, desfaz a baixa e cancela lançamentos e parcelas.
 * Compra, Orçamento e a conta avulsa chamam este serviço; nenhum outro código escreve na parcela. O total do lançamento é
 * sempre recalculado como a soma das parcelas não canceladas, na mesma transação. "Vencida" nunca é gravada (DT-NOVA-2).
 */
@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final UsuarioRepository usuarioRepository;
    private final LancamentoRepository lancamentoRepository;
    private final ParcelaRepository parcelaRepository;
    private final CompraRepository compraRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final MetodoPagamentoConfiguravelRepository metodoRepository;
    private final CategoriaFinanceiraService categoriaService;
    private final Clock relogioBrasilia;

    // ------------------------------------------------------------------------------------------ criar

    /** Cria o lançamento da usuária autenticada com as parcelas informadas (1 a 60). Todo o pedido é validado antes de gravar. */
    @Transactional
    public Lancamento criar(NovoLancamento pedido) {
        Usuario usuario = usuarioAutenticado();
        if (pedido == null || pedido.tipo() == null || pedido.origem() == null) {
            throw new BusinessException("Informe o tipo e a origem do lançamento.");
        }
        String descricao = ValidacaoFinanceira.exigirDescricao(pedido.descricao());
        List<NovaParcela> novas = pedido.parcelas();
        if (novas == null || novas.isEmpty() || novas.size() > DivisaoParcelas.MAXIMO_PARCELAS) {
            throw BusinessException.noCampo("quantidadeParcelas",
                    "O número de parcelas deve ser de 1 a " + DivisaoParcelas.MAXIMO_PARCELAS + ".");
        }
        LocalDate hoje = hoje();
        BigDecimal total = BigDecimal.ZERO;
        for (NovaParcela n : novas) {
            ValidacaoFinanceira.exigirValor(n.valor(), "valor");
            ValidacaoFinanceira.exigirVencimento(n.vencimento(), "vencimento");
            total = total.add(n.valor());
        }
        if (total.compareTo(ValidacaoFinanceira.VALOR_MAXIMO) > 0) {
            throw BusinessException.noCampo("valor", ValidacaoFinanceira.MENSAGEM_VALOR);
        }
        CategoriaFinanceira categoria = categoriaService.resolverParaLancamento(usuario.getId(), pedido.tipo(), pedido.origem(),
                pedido.categoriaId());
        Lancamento lancamento = Lancamento.builder()
                .usuario(usuario).categoria(categoria).tipo(pedido.tipo()).descricao(descricao).origem(pedido.origem()).valorTotal(total.setScale(2))
                .compra(pedido.origem() == OrigemLancamento.COMPRA ? compraRepository
                        .findByIdAndUsuarioIdAndDeletedAtIsNull(exigirId(pedido.compraId(), "Compra"), usuario.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Compra não encontrada.")) : null)
                .orcamento(pedido.origem() == OrigemLancamento.ORCAMENTO ? orcamentoRepository
                        .findByIdAndUsuarioIdAndDeletedAtIsNull(exigirId(pedido.orcamentoId(), "Orçamento"), usuario.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado.")) : null)
                .build();
        if (pedido.origem() == OrigemLancamento.AVULSO && (pedido.compraId() != null || pedido.orcamentoId() != null)) {
            throw new BusinessException("Lançamento avulso não tem compra nem orçamento de origem.");
        }
        lancamento = lancamentoRepository.save(lancamento);
        List<Parcela> parcelas = new ArrayList<>(novas.size());
        int numero = 1;
        for (NovaParcela n : novas) {
            Parcela p = Parcela.builder().lancamento(lancamento).usuario(usuario).numero(numero++)
                    .valor(n.valor().setScale(2)).vencimento(n.vencimento()).build();
            if (n.baixaInicial() != null) {
                aplicarBaixa(p, n.baixaInicial().data(), n.baixaInicial().metodoPagamentoId(), usuario.getId(), hoje, OrigemBaixa.AUTOMATICA);
            }
            parcelas.add(p);
        }
        parcelaRepository.saveAll(parcelas);
        return lancamento;
    }

    // ------------------------------------------------------------------------------------------ baixa

    /** RN-NOVA-3 — "Paguei"/"Recebi": baixa a parcela inteira. Data padrão hoje (não pode ser futura); método ativo da usuária. */
    @Transactional
    public Parcela baixar(UUID parcelaId, LocalDate dataBaixa, UUID metodoPagamentoId) {
        Usuario usuario = usuarioAutenticado();
        Parcela p = parcelaDaUsuariaParaAlterar(parcelaId, usuario.getId());
        exigirLancamentoAtivo(p);
        if (p.getSituacao() != SituacaoParcela.EM_ABERTO) {
            throw new BusinessException(p.getSituacao() == SituacaoParcela.BAIXADA
                    ? "Esta parcela já foi baixada." : "Esta parcela está cancelada e não pode ser baixada.");
        }
        aplicarBaixa(p, dataBaixa, metodoPagamentoId, usuario.getId(), hoje(), OrigemBaixa.USUARIA);
        Parcela salva = parcelaRepository.save(p);
        sincronizarPagoDaCompra(salva.getLancamento());
        return salva;
    }

    /** RN-NOVA-3 — desfazer a baixa: volta a Em aberto e apaga data, método e origem da baixa. */
    @Transactional
    public Parcela desfazerBaixa(UUID parcelaId) {
        Usuario usuario = usuarioAutenticado();
        Parcela p = parcelaDaUsuariaParaAlterar(parcelaId, usuario.getId());
        exigirLancamentoAtivo(p);
        if (p.getSituacao() != SituacaoParcela.BAIXADA) {
            throw new BusinessException("Só é possível desfazer a baixa de uma parcela baixada.");
        }
        p.setSituacao(SituacaoParcela.EM_ABERTO);
        p.setDataBaixa(null);
        p.setMetodoPagamento(null);
        p.setOrigemBaixa(null);
        Parcela salva = parcelaRepository.save(p);
        sincronizarPagoDaCompra(salva.getLancamento());
        return salva;
    }

    // ---------------------------------------------------------------------------------------- cancelar

    /**
     * RN-NOVA-1 — cancela a origem: parcelas Em aberto viram Canceladas (histórico) e o lançamento passa a Cancelado, com
     * total igual ao que já foi pago/recebido (R$ 0,00 se nada). Cancelar de novo é idempotente.
     */
    @Transactional
    public Lancamento cancelar(UUID lancamentoId) {
        Usuario usuario = usuarioAutenticado();
        Lancamento l = lancamentoRepository.buscarParaAlterar(lancamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
        List<Parcela> parcelas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        for (Parcela p : parcelas) {
            if (p.getSituacao() == SituacaoParcela.EM_ABERTO) {
                p.setSituacao(SituacaoParcela.CANCELADA);
            }
        }
        parcelaRepository.saveAll(parcelas);
        l.setSituacao(SituacaoLancamento.CANCELADO);
        l.setValorTotal(somaAtivas(parcelas));
        return lancamentoRepository.save(l);
    }

    // ------------------------------------------------------------------------------- ganchos da compra (#1173)

    /**
     * RN-NOVA-7/8 — gera ou substitui, na transação da compra, as parcelas do lançamento A pagar dela. Sem lançamento
     * (compra nova, ou antiga anterior à entrega, RN-NOVA-6) cria o primeiro. Com lançamento: uma parcela baixada pela usuária
     * bloqueia; as Em aberto e as que nasceram Pagas (baixa automática) viram Canceladas (histórico, sem estorno) e as novas
     * nascem numeradas de 1 a N. O teto de 60 conta só as parcelas ativas. Em seguida grava Pago/Não pago da compra.
     */
    @Transactional
    public Lancamento substituirParcelasDaCompra(Compra compra, String descricao, List<NovaParcela> novas) {
        Usuario usuario = usuarioAutenticado();
        Optional<Lancamento> existente = lancamentoRepository.buscarDaCompraParaAlterar(compra.getId(), usuario.getId());
        if (existente.isEmpty()) {
            Lancamento criado = criar(new NovoLancamento(TipoLancamento.A_PAGAR, descricao, OrigemLancamento.COMPRA,
                    compra.getId(), null, novas));
            sincronizarPagoDaCompra(criado);
            return criado;
        }
        Lancamento l = existente.get();
        List<Parcela> antigas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        exigirSemBaixaDaUsuaria(antigas, "alterar a forma de pagar");
        if (novas == null || novas.isEmpty() || novas.size() > DivisaoParcelas.MAXIMO_PARCELAS) {
            throw BusinessException.noCampo("quantidadeParcelas",
                    "O número de parcelas deve ser de 1 a " + DivisaoParcelas.MAXIMO_PARCELAS + ".");
        }
        LocalDate hoje = hoje();
        BigDecimal total = BigDecimal.ZERO;
        for (NovaParcela n : novas) {
            ValidacaoFinanceira.exigirValor(n.valor(), "valor");
            ValidacaoFinanceira.exigirVencimento(n.vencimento(), "vencimento");
            total = total.add(n.valor());
        }
        if (total.compareTo(ValidacaoFinanceira.VALOR_MAXIMO) > 0) {
            throw BusinessException.noCampo("valor", ValidacaoFinanceira.MENSAGEM_VALOR);
        }
        cancelarParcelasDaCompra(antigas);
        List<Parcela> criadas = new ArrayList<>(novas.size());
        int numero = 1;
        for (NovaParcela n : novas) {
            Parcela p = Parcela.builder().lancamento(l).usuario(usuario).numero(numero++)
                    .valor(n.valor().setScale(2)).vencimento(n.vencimento()).build();
            if (n.baixaInicial() != null) {
                aplicarBaixa(p, n.baixaInicial().data(), n.baixaInicial().metodoPagamentoId(), usuario.getId(), hoje, OrigemBaixa.AUTOMATICA);
            }
            criadas.add(p);
        }
        parcelaRepository.saveAll(criadas);
        l.setSituacao(SituacaoLancamento.ATIVO);
        l.setDescricao(ValidacaoFinanceira.exigirDescricao(descricao));
        l.setValorTotal(total.setScale(2));
        lancamentoRepository.save(l);
        sincronizarPagoDaCompra(l);
        return l;
    }

    /**
     * RN-NOVA-8 (b) — cancelar a compra: bloqueia se houver baixa da usuária; senão as parcelas Em aberto e as que nasceram
     * Pagas viram Canceladas e o lançamento passa a Cancelado com total R$ 0,00. Compra sem lançamento (RN-NOVA-6): nada a fazer.
     */
    @Transactional
    public void cancelarLancamentoDaCompra(Compra compra) {
        Usuario usuario = usuarioAutenticado();
        Optional<Lancamento> existente = lancamentoRepository.buscarDaCompraParaAlterar(compra.getId(), usuario.getId());
        if (existente.isEmpty()) {
            return;
        }
        Lancamento l = existente.get();
        List<Parcela> parcelas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        exigirSemBaixaDaUsuaria(parcelas, "cancelar a compra");
        cancelarParcelasDaCompra(parcelas);
        l.setSituacao(SituacaoLancamento.CANCELADO);
        l.setValorTotal(somaAtivas(parcelas));
        lancamentoRepository.save(l);
    }

    /** RN-NOVA-6 — a compra já tem lançamento? (Compra anterior à entrega não tem.) */
    @Transactional(readOnly = true)
    public boolean compraTemLancamento(UUID compraId) {
        return lancamentoRepository.existsByCompraIdAndUsuarioId(compraId, usuarioAutenticado().getId());
    }

    /** RN-NOVA-8 — critério único: só a baixa feita pela usuária bloqueia; basta uma parcela. */
    private static void exigirSemBaixaDaUsuaria(List<Parcela> parcelas, String acao) {
        boolean baixadaPelaUsuaria = parcelas.stream().anyMatch(p -> p.getSituacao() == SituacaoParcela.BAIXADA
                && p.getOrigemBaixa() == OrigemBaixa.USUARIA);
        if (baixadaPelaUsuaria) {
            throw new BusinessException("Desfaça os pagamentos já marcados antes de " + acao + ".");
        }
    }

    /** Em aberto e baixa automática viram Canceladas (a baixa some junto, sem estorno); as já canceladas ficam como estão. */
    private void cancelarParcelasDaCompra(List<Parcela> parcelas) {
        for (Parcela p : parcelas) {
            if (p.getSituacao() != SituacaoParcela.CANCELADA) {
                p.setSituacao(SituacaoParcela.CANCELADA);
                p.setDataBaixa(null);
                p.setMetodoPagamento(null);
                p.setOrigemBaixa(null);
            }
        }
        parcelaRepository.saveAllAndFlush(parcelas);
    }

    /**
     * RN-NOVA-7/9 — Pago/Não pago da compra é derivado das parcelas: Paga quando não resta parcela Em aberto (e há alguma
     * parcela baixada). Compra cancelada ou sem lançamento da compra não é alterada. Pago exige método (CHECK): se a compra
     * não tinha, usa o da última parcela baixada; Não paga só guarda método de cartão de crédito parcelado.
     */
    private void sincronizarPagoDaCompra(Lancamento l) {
        if (l.getOrigem() != OrigemLancamento.COMPRA || l.getCompra() == null || l.getSituacao() != SituacaoLancamento.ATIVO) {
            return;
        }
        Compra compra = l.getCompra();
        if (compra.getStatus() == StatusCompra.CANCELADA) {
            return;
        }
        List<Parcela> parcelas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId()).stream()
                .filter(p -> p.getSituacao() != SituacaoParcela.CANCELADA).toList();
        boolean paga = !parcelas.isEmpty() && parcelas.stream().noneMatch(p -> p.getSituacao() == SituacaoParcela.EM_ABERTO);
        if (paga) {
            if (compra.getMetodoPagamento() == null) {
                parcelas.stream().filter(p -> p.getMetodoPagamento() != null)
                        .max(java.util.Comparator.comparing(Parcela::getDataBaixa)).map(Parcela::getMetodoPagamento)
                        .ifPresent(compra::setMetodoPagamento);
            }
            compra.setPago(true);
        } else {
            compra.setPago(false);
            if (compra.getMetodoPagamento() != null && compra.getMetodoPagamento().getTipo() != TipoMetodoPagamento.CARTAO_CREDITO) {
                compra.setMetodoPagamento(null);
                compra.setParcelas(null);
            }
        }
        compraRepository.save(compra);
    }

    // ------------------------------------------------------------------------------------------ internos

    private void aplicarBaixa(Parcela p, LocalDate dataBaixa, UUID metodoId, UUID usuarioId, LocalDate hoje, OrigemBaixa origem) {
        LocalDate data = dataBaixa == null ? hoje : dataBaixa;
        if (!FaixaDataPermitida.contem(data)) {
            throw BusinessException.noCampo("dataBaixa", FaixaDataPermitida.MENSAGEM);
        }
        if (data.isAfter(hoje)) {
            throw BusinessException.noCampo("dataBaixa", "A data da baixa não pode ser futura.");
        }
        if (metodoId == null) {
            throw BusinessException.noCampo("metodoPagamentoId", "Informe a forma de pagamento.");
        }
        MetodoPagamentoConfiguravel metodo = metodoRepository.findByIdAndUsuarioId(metodoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Forma de pagamento não encontrada."));
        if (!Boolean.TRUE.equals(metodo.getAtivo())) {
            throw BusinessException.noCampo("metodoPagamentoId", "Esta forma de pagamento está inativa. Escolha uma forma ativa.");
        }
        p.setSituacao(SituacaoParcela.BAIXADA);
        p.setDataBaixa(data);
        p.setMetodoPagamento(metodo);
        p.setOrigemBaixa(origem);
    }

    private Parcela parcelaDaUsuariaParaAlterar(UUID id, UUID usuarioId) {
        if (id == null) {
            throw new ResourceNotFoundException("Parcela não encontrada.");
        }
        return parcelaRepository.buscarParaAlterar(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Parcela não encontrada."));
    }

    private static void exigirLancamentoAtivo(Parcela p) {
        if (p.getLancamento().getSituacao() == SituacaoLancamento.CANCELADO) {
            throw new BusinessException("Este lançamento está cancelado e não aceita alterações.");
        }
    }

    private static BigDecimal somaAtivas(List<Parcela> parcelas) {
        return parcelas.stream().filter(p -> p.getSituacao() != SituacaoParcela.CANCELADA)
                .map(Parcela::getValor).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    private static UUID exigirId(UUID id, String origem) {
        if (id == null) {
            throw new BusinessException("Informe a " + origem.toLowerCase() + " de origem do lançamento.");
        }
        return id;
    }

    private LocalDate hoje() {
        return LocalDate.now(relogioBrasilia);
    }

    private Usuario usuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
