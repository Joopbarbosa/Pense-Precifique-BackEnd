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
