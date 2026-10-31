package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
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

    // Métodos F4 (#1175): adicionados sem alterar a API compartilhada por Compra e Orçamento.
    @Transactional
    public Lancamento criarContaAvulsa(ContaAvulsaRequest pedido) {
        if (pedido == null) throw new BusinessException("Informe os dados da conta avulsa.");
        String descricao = pedido.descricao() == null ? "" : pedido.descricao().strip();
        if (descricao.length() < 2 || descricao.length() > 80 || descricao.indexOf('\u0000') >= 0)
            throw BusinessException.noCampo("descricao", "A descrição deve ter de 2 a 80 caracteres.");
        ValidacaoFinanceira.exigirValor(pedido.valor(), "valor");
        ValidacaoFinanceira.exigirVencimento(pedido.vencimento(), "vencimento");
        int meses = pedido.meses() == null ? 1 : pedido.meses();
        if (pedido.meses() != null && (meses < 2 || meses > 24))
            throw BusinessException.noCampo("meses", "Informe de 2 a 24 meses.");
        BigDecimal total = pedido.valor().multiply(BigDecimal.valueOf(meses));
        if (total.compareTo(ValidacaoFinanceira.VALOR_MAXIMO) > 0)
            throw new BusinessException("A soma das ocorrências passa de R$ 99.999.999,99.");
        List<NovaParcela> parcelas = new ArrayList<>();
        for (int i = 0; i < meses; i++) {
            var vencimento = RecorrenciaMensalFinanceira.vencimento(pedido.vencimento(), i);
            ValidacaoFinanceira.exigirVencimento(vencimento, "vencimento");
            NovaParcela.BaixaInicial baixa = i == 0 && Boolean.TRUE.equals(pedido.jaPaga())
                    ? new NovaParcela.BaixaInicial(pedido.dataBaixa(), pedido.metodoPagamentoId()) : null;
            parcelas.add(new NovaParcela(pedido.valor().setScale(2), vencimento, baixa));
        }
        if (Boolean.TRUE.equals(pedido.jaPaga())) {
            if (pedido.dataBaixa() == null) throw BusinessException.noCampo("dataBaixa", "Informe a data da baixa.");
            if (pedido.metodoPagamentoId() == null) throw BusinessException.noCampo("metodoPagamentoId", "Informe a forma de pagamento.");
        }
        Lancamento l = criar(new NovoLancamento(pedido.tipo(), descricao, OrigemLancamento.AVULSO,
                null, null, parcelas, pedido.categoriaId()));
        l.setForaProjecao(Boolean.TRUE.equals(pedido.foraProjecao()));
        return lancamentoRepository.save(l);
    }

    @Transactional
    public Parcela editarOcorrencia(UUID parcelaId, EditarOcorrenciaRequest pedido, boolean seguintes) {
        Usuario usuario = usuarioAutenticado();
        Parcela alvo = parcelaDaUsuariaParaAlterar(parcelaId, usuario.getId());
        Lancamento l = lancamentoRepository.buscarParaAlterar(alvo.getLancamento().getId(), usuario.getId()).orElseThrow();
        exigirAvulsoEditavel(l, alvo, false);
        if (pedido == null) throw new BusinessException("Informe os dados da edição.");
        if (pedido.descricao() == null && pedido.valor() == null && pedido.vencimento() == null
                && pedido.categoriaId() == null && pedido.foraProjecao() == null && !Boolean.TRUE.equals(pedido.atualizarCategoria()))
            throw new BusinessException("Informe ao menos um campo para alterar.");
        if (pedido.valor() != null) ValidacaoFinanceira.exigirValor(pedido.valor(), "valor");
        if (pedido.vencimento() != null) ValidacaoFinanceira.exigirVencimento(pedido.vencimento(), "vencimento");
        List<Parcela> todas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        List<Parcela> afetadas = todas.stream().filter(p -> p.getNumero() >= alvo.getNumero()
                && p.getSituacao() == SituacaoParcela.EM_ABERTO && (seguintes || p.getId().equals(alvo.getId()))).toList();
        if (pedido.descricao() != null) {
            String d = pedido.descricao().strip();
            if (d.length() < 2 || d.length() > 80) throw BusinessException.noCampo("descricao", "A descrição deve ter de 2 a 80 caracteres.");
            l.setDescricao(d);
        }
        if (pedido.foraProjecao() != null) l.setForaProjecao(pedido.foraProjecao());
        if (Boolean.TRUE.equals(pedido.atualizarCategoria())
                && (l.getCategoria() == null || pedido.categoriaId() == null || !l.getCategoria().getId().equals(pedido.categoriaId())))
            l.setCategoria(categoriaService.resolverParaLancamento(usuario.getId(), l.getTipo(), OrigemLancamento.AVULSO, pedido.categoriaId()));
        LocalDate base = pedido.vencimento() == null ? alvo.getVencimento() : pedido.vencimento();
        for (Parcela p : afetadas) {
            if (pedido.valor() != null) p.setValor(pedido.valor().setScale(2));
            if (pedido.vencimento() != null) {
                LocalDate vencimento = RecorrenciaMensalFinanceira.vencimento(base, p.getNumero() - alvo.getNumero());
                ValidacaoFinanceira.exigirVencimento(vencimento, "vencimento");
                p.setVencimento(vencimento);
            }
        }
        BigDecimal soma = somaAtivas(todas);
        if (soma.compareTo(ValidacaoFinanceira.VALOR_MAXIMO) > 0)
            throw new BusinessException("A soma das ocorrências passa de R$ 99.999.999,99.");
        l.setValorTotal(soma);
        parcelaRepository.saveAll(todas);
        lancamentoRepository.save(l);
        return alvo;
    }

    @Transactional
    public Lancamento excluirOcorrencia(UUID parcelaId, boolean seguintes) {
        Usuario usuario = usuarioAutenticado();
        Parcela alvo = parcelaDaUsuariaParaAlterar(parcelaId, usuario.getId());
        Lancamento l = lancamentoRepository.buscarParaAlterar(alvo.getLancamento().getId(), usuario.getId()).orElseThrow();
        exigirAvulsoEditavel(l, alvo, true);
        List<Parcela> parcelas = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId());
        parcelas.stream().filter(p -> p.getNumero() >= alvo.getNumero() && p.getSituacao() == SituacaoParcela.EM_ABERTO
                && (seguintes || p.getId().equals(alvo.getId()))).forEach(p -> p.setSituacao(SituacaoParcela.CANCELADA));
        BigDecimal total = somaAtivas(parcelas);
        parcelaRepository.saveAll(parcelas);
        if (total.signum() == 0) l.setSituacao(SituacaoLancamento.CANCELADO);
        l.setValorTotal(total);
        return lancamentoRepository.save(l);
    }

    @Transactional
    public ImpactoExclusaoAvulsaResponse preverExclusaoOcorrencia(UUID parcelaId, boolean seguintes) {
        Usuario usuario = usuarioAutenticado();
        Parcela alvo = parcelaDaUsuariaParaAlterar(parcelaId, usuario.getId());
        Lancamento l = alvo.getLancamento();
        exigirAvulsoEditavel(l, alvo, true);
        boolean restaParcela = parcelaRepository.findByLancamentoIdOrderByNumeroAsc(l.getId()).stream()
                .anyMatch(p -> p.getSituacao() != SituacaoParcela.CANCELADA
                        && !(p.getSituacao() == SituacaoParcela.EM_ABERTO && p.getNumero() >= alvo.getNumero()
                        && (seguintes || p.getId().equals(alvo.getId()))));
        return new ImpactoExclusaoAvulsaResponse(!restaParcela);
    }

    @Transactional
    public Lancamento marcarForaProjecao(UUID lancamentoId, boolean foraProjecao) {
        Usuario usuario = usuarioAutenticado();
        Lancamento l = lancamentoRepository.buscarParaAlterar(lancamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
        if (l.getOrigem() != OrigemLancamento.AVULSO)
            throw new BusinessException("Este valor vem de uma " + (l.getOrigem() == OrigemLancamento.COMPRA ? "compra" : "orçamento") + "; altere por lá.");
        l.setForaProjecao(foraProjecao);
        return lancamentoRepository.save(l);
    }

    private static void exigirAvulsoEditavel(Lancamento l, Parcela p, boolean exclusao) {
        if (l.getOrigem() != OrigemLancamento.AVULSO)
            throw new BusinessException("Este valor vem de uma " + (l.getOrigem() == OrigemLancamento.COMPRA ? "compra" : "orçamento") + "; altere por lá.");
        if (p.getSituacao() != SituacaoParcela.EM_ABERTO)
            throw new BusinessException(exclusao ? "Desfaça o pagamento antes de excluir." : "Desfaça o pagamento antes de alterar.");
        if (l.getSituacao() == SituacaoLancamento.CANCELADO) throw new BusinessException("Este lançamento está cancelado.");
    }

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
        return parcelaRepository.save(p);
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
        return parcelaRepository.save(p);
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
