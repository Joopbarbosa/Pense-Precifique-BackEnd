package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.Cliente;
import com.penseprecifique.api.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.domain.entity.Orcamento;
import com.penseprecifique.api.domain.entity.OrcamentoItem;
import com.penseprecifique.api.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.ReciboPagamento;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MetodoPagamento;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.StatusOrcamento;
import com.penseprecifique.api.domain.enums.TipoDesconto;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.TipoProduto;
import com.penseprecifique.api.dto.request.AvancaStatusRequest;
import com.penseprecifique.api.dto.request.OrcamentoItemCustomizacaoRequest;
import com.penseprecifique.api.dto.request.OrcamentoItemRequest;
import com.penseprecifique.api.dto.request.OrcamentoRequest;
import com.penseprecifique.api.dto.response.OrcamentoDetalheResponse;
import com.penseprecifique.api.dto.response.OrcamentoItemResponse;
import com.penseprecifique.api.dto.response.OrcamentoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.OrcamentoMapper;
import com.penseprecifique.api.repository.ClienteRepository;
import com.penseprecifique.api.repository.MovimentacaoProdutoRepository;
import com.penseprecifique.api.repository.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.repository.OrcamentoItemRepository;
import com.penseprecifique.api.repository.OrcamentoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.ReciboPagamentoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrcamentoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int MIN_OBS_OUTRO = 50;

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrcamentoMapper orcamentoMapper;

    @Transactional(readOnly = true)
    public Page<OrcamentoResponse> listar(StatusOrcamento status, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Page<Orcamento> page = (status != null)
                ? orcamentoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, status, pageable)
                : orcamentoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable);
        return page.map(orcamentoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrcamentoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
        return montarDetalhe(orcamento);
    }

    public OrcamentoDetalheResponse criar(OrcamentoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        Cliente cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(request.getClienteId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));

        validarRegras(request);

        TipoDesconto tipoDesconto = parseTipoDesconto(request.getTipoDesconto());

        Orcamento orcamento = Orcamento.builder()
                .usuario(usuario)
                .cliente(cliente)
                .numero(proximoNumero(usuarioId))
                .status(StatusOrcamento.RASCUNHO)
                .metodoPagamento(request.getMetodoPagamento())
                .metodoPagamentoObs(request.getMetodoPagamentoObs())
                .prazoProducaoDias(request.getPrazoProducaoDias())
                .inicioAssimQueAprovado(request.isInicioAssimQueAprovado())
                .dataInicioEstimada(request.getDataInicioEstimada())
                .sinalAtivo(request.isSinalAtivo())
                .percentualSinal(request.getPercentualSinal())
                .descontoTipo(tipoDesconto)
                .descontoValor(request.getDescontoValor() != null ? request.getDescontoValor() : BigDecimal.ZERO)
                .observacoes(request.getObservacoes())
                .dataValidade(request.getDataValidade())
                .build();
        orcamento = orcamentoRepository.save(orcamento);

        BigDecimal subtotalOrcamento = BigDecimal.ZERO;

        for (OrcamentoItemRequest itemReq : request.getItens()) {
            Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(itemReq.getProdutoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
            if (produto.getTipo() != TipoProduto.PRODUTO) {
                throw new BusinessException("O item '" + produto.getNome() + "' não é um produto válido para o orçamento");
            }

            BigDecimal precoUnitario = produto.getPrecoVenda();
            BigDecimal subtotalItem = precoUnitario.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            OrcamentoItem item = OrcamentoItem.builder()
                    .orcamento(orcamento)
                    .produto(produto)
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(precoUnitario)
                    .subtotal(subtotalItem)
                    .build();
            item = orcamentoItemRepository.save(item);

            for (OrcamentoItemCustomizacaoRequest custReq : itemReq.getCustomizacoes()) {
                Produto custProduto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(custReq.getProdutoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customização não encontrada"));
                if (custProduto.getTipo() != TipoProduto.CUSTOMIZACAO) {
                    throw new BusinessException("O item '" + custProduto.getNome() + "' não é uma customização válida");
                }

                BigDecimal custPrecoUnitario = custProduto.getPrecoVenda();
                BigDecimal custSubtotal = custPrecoUnitario.multiply(BigDecimal.valueOf(custReq.getQuantidade()));

                OrcamentoItemCustomizacao customizacao = OrcamentoItemCustomizacao.builder()
                        .orcamentoItem(item)
                        .produto(custProduto)
                        .quantidade(custReq.getQuantidade())
                        .precoUnitario(custPrecoUnitario)
                        .subtotal(custSubtotal)
                        .build();
                orcamentoItemCustomizacaoRepository.save(customizacao);

                subtotalItem = subtotalItem.add(custSubtotal);
            }

            // Atualiza o subtotal do item para incluir customizações
            item.setSubtotal(subtotalItem);
            orcamentoItemRepository.save(item);

            subtotalOrcamento = subtotalOrcamento.add(subtotalItem);
        }

        BigDecimal total = calcularTotal(subtotalOrcamento, tipoDesconto, orcamento.getDescontoValor());

        orcamento.setSubtotal(subtotalOrcamento);
        orcamento.setTotal(total);

        if (orcamento.getSinalAtivo()) {
            orcamento.setValorSinal(calcularValorSinal(total, request));
        }

        orcamento = orcamentoRepository.save(orcamento);

        return montarDetalhe(orcamento);
    }

    public OrcamentoDetalheResponse avancarStatus(UUID id, AvancaStatusRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        StatusOrcamento atual = orcamento.getStatus();

        switch (atual) {
            case RASCUNHO:
                orcamento.setStatus(StatusOrcamento.ENVIADO);
                break;

            case ENVIADO:
                orcamento.setStatus(StatusOrcamento.APROVADO);
                orcamento.setDataAprovacao(LocalDateTime.now());
                break;

            case APROVADO:
                if (Boolean.TRUE.equals(orcamento.getSinalAtivo())) {
                    orcamento.setStatus(StatusOrcamento.AGUARDANDO_SINAL);
                } else {
                    orcamento.setStatus(StatusOrcamento.EM_PRODUCAO);
                }
                break;

            case AGUARDANDO_SINAL:
                if (request.getMetodoSinalRecebido() == null) {
                    throw new BusinessException("O método de recebimento do sinal é obrigatório");
                }
                if (request.getMetodoSinalRecebido() == MetodoPagamento.OUTRO) {
                    String obs = request.getMetodoSinalRecebidoObs();
                    if (obs == null || obs.trim().length() < MIN_OBS_OUTRO) {
                        throw new BusinessException(
                                "Para o método OUTRO, a observação é obrigatória (mín. " + MIN_OBS_OUTRO + " caracteres)");
                    }
                }
                orcamento.setStatus(StatusOrcamento.SINAL_PAGO);
                orcamento.setDataSinalPago(LocalDateTime.now());
                orcamento.setMetodoSinalRecebido(request.getMetodoSinalRecebido());
                orcamento.setMetodoSinalRecebidoObs(request.getMetodoSinalRecebidoObs());
                break;

            case SINAL_PAGO:
                orcamento.setStatus(StatusOrcamento.EM_PRODUCAO);
                break;

            case EM_PRODUCAO:
                List<OrcamentoItem> itensParaBaixa = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
                for (OrcamentoItem item : itensParaBaixa) {
                    Produto produto = item.getProduto();
                    produto.setEstoqueAtual(produto.getEstoqueAtual()
                            .subtract(BigDecimal.valueOf(item.getQuantidade())));
                    produtoRepository.save(produto);

                    movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                            .produto(produto)
                            .tipo(TipoMovimentacaoProduto.SAIDA)
                            .motivo(MotivoMovimentacaoProduto.ORCAMENTO)
                            .quantidade(BigDecimal.valueOf(item.getQuantidade()))
                            .referenciaId(orcamento.getId())
                            .referenciaTipo(ReferenciaMovimentacaoTipo.ORCAMENTO.name())
                            .estornada(false)
                            .build());
                }
                orcamento.setStatus(StatusOrcamento.FINALIZADO);
                break;

            case FINALIZADO:
                orcamento.setStatus(StatusOrcamento.ENTREGUE);
                break;

            case ENTREGUE:
                BigDecimal valorSinalPago = Boolean.TRUE.equals(orcamento.getSinalAtivo()) && orcamento.getValorSinal() != null
                        ? orcamento.getValorSinal()
                        : BigDecimal.ZERO;
                BigDecimal valorRestantePago = orcamento.getTotal().subtract(valorSinalPago);

                reciboPagamentoRepository.save(ReciboPagamento.builder()
                        .orcamento(orcamento)
                        .valorTotal(orcamento.getTotal())
                        .valorSinalPago(valorSinalPago)
                        .valorRestantePago(valorRestantePago)
                        .totalQuitado(orcamento.getTotal())
                        .build());

                orcamento.setStatus(StatusOrcamento.PAGO);
                break;

            default:
                throw new BusinessException("Transição de status inválida.");
        }

        orcamento = orcamentoRepository.save(orcamento);
        return montarDetalhe(orcamento);
    }

    private void validarRegras(OrcamentoRequest request) {
        if (request.getMetodoPagamento() == MetodoPagamento.OUTRO) {
            String obs = request.getMetodoPagamentoObs();
            if (obs == null || obs.trim().length() < MIN_OBS_OUTRO) {
                throw new BusinessException(
                        "Para o método de pagamento OUTRO, a observação é obrigatória (mín. " + MIN_OBS_OUTRO + " caracteres)");
            }
        }

        if (!request.isInicioAssimQueAprovado() && request.getDataInicioEstimada() == null) {
            throw new BusinessException("A data de início estimada é obrigatória quando o início não é assim que aprovado");
        }

        if (request.isSinalAtivo()
                && request.getPercentualSinal() == null
                && request.getValorSinal() == null) {
            throw new BusinessException("Quando o sinal está ativo, o percentual ou o valor do sinal deve ser informado");
        }
    }

    private TipoDesconto parseTipoDesconto(String tipoDesconto) {
        if (tipoDesconto == null || tipoDesconto.isBlank()) {
            return TipoDesconto.PERCENTUAL;
        }
        try {
            return TipoDesconto.valueOf(tipoDesconto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Tipo de desconto inválido: " + tipoDesconto);
        }
    }

    private BigDecimal calcularTotal(BigDecimal subtotal, TipoDesconto tipoDesconto, BigDecimal descontoValor) {
        BigDecimal desconto = descontoValor != null ? descontoValor : BigDecimal.ZERO;
        BigDecimal total;
        if (tipoDesconto == TipoDesconto.PERCENTUAL) {
            BigDecimal fator = BigDecimal.ONE.subtract(desconto.divide(CEM, 6, RoundingMode.HALF_UP));
            total = subtotal.multiply(fator);
        } else {
            total = subtotal.subtract(desconto);
        }
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularValorSinal(BigDecimal total, OrcamentoRequest request) {
        if (request.getPercentualSinal() != null) {
            return total.multiply(request.getPercentualSinal())
                    .divide(CEM, 2, RoundingMode.HALF_UP);
        }
        return request.getValorSinal();
    }

    private OrcamentoDetalheResponse montarDetalhe(Orcamento orcamento) {
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        List<OrcamentoItemResponse> itensResponse = new ArrayList<>();
        for (OrcamentoItem item : itens) {
            List<OrcamentoItemCustomizacao> customizacoes =
                    orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId());
            itensResponse.add(orcamentoMapper.toItemResponse(item, customizacoes));
        }
        OrcamentoDetalheResponse response = orcamentoMapper.toDetalheResponse(orcamento, itens);
        response.setItens(itensResponse);
        return response;
    }

    private Integer proximoNumero(UUID usuarioId) {
        return orcamentoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId)
                .map(o -> o.getNumero() != null ? o.getNumero() + 1 : 1)
                .orElse(1);
    }

    private UUID getUsuarioIdAutenticado() {
        return getUsuarioAutenticado().getId();
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
