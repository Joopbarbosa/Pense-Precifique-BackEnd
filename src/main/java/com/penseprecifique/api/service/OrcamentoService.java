package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.Cliente;
import com.penseprecifique.api.domain.entity.ItemCatalogo;
import com.penseprecifique.api.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.domain.entity.Orcamento;
import com.penseprecifique.api.domain.entity.OrcamentoItem;
import com.penseprecifique.api.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.ReciboEstorno;
import com.penseprecifique.api.domain.entity.ReciboPagamento;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MetodoPagamento;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.StatusOrcamento;
import com.penseprecifique.api.domain.enums.TipoCancelamento;
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
import com.penseprecifique.api.repository.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.repository.ItemCatalogoRepository;
import com.penseprecifique.api.repository.MovimentacaoProdutoRepository;
import com.penseprecifique.api.repository.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.repository.OrcamentoItemRepository;
import com.penseprecifique.api.repository.OrcamentoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.ReciboEstornoRepository;
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
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository itemCatalogoCustomizacaoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final ReciboEstornoRepository reciboEstornoRepository;
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
            // RN-045 + RN-046 — item só entra no orçamento se produto e catálogo estiverem ativos.
            ItemCatalogo itemCatalogo = buscarItemCatalogoParaVenda(itemReq.getItemCatalogoId(), usuarioId);

            // RN-048 — snapshot do preço de venda no momento exato da adição. Por ser uma cópia
            // (não referência viva), nenhuma edição futura no catálogo/produto/margem altera este valor.
            BigDecimal precoUnitario = itemCatalogo.getPrecoVenda();
            BigDecimal subtotalItem = precoUnitario.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

            OrcamentoItem item = OrcamentoItem.builder()
                    .orcamento(orcamento)
                    .itemCatalogo(itemCatalogo)
                    .quantidade(itemReq.getQuantidade())
                    .precoUnitario(precoUnitario)
                    .subtotal(subtotalItem)
                    .build();
            item = orcamentoItemRepository.save(item);

            // RN-048 — customizações fixas do pacote entram automaticamente (sem ação da artesã),
            // cada uma com snapshot do preço de venda do produto CUSTOMIZACAO correspondente.
            for (ItemCatalogoCustomizacao fixa : itemCatalogoCustomizacaoRepository.findByItemCatalogoId(itemCatalogo.getId())) {
                int quantidade = Math.max(1, fixa.getQuantidade().setScale(0, RoundingMode.HALF_UP).intValue());
                subtotalItem = subtotalItem.add(salvarCustomizacao(item, fixa.getProduto(), quantidade));
            }

            // RN-030 (inalterado) — customizações ad-hoc adicionadas manualmente coexistem com as fixas.
            for (OrcamentoItemCustomizacaoRequest custReq : itemReq.getCustomizacoes()) {
                Produto custProduto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(custReq.getProdutoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customização não encontrada"));
                if (custProduto.getTipo() != TipoProduto.CUSTOMIZACAO) {
                    throw new BusinessException("O item '" + custProduto.getNome() + "' não é uma customização válida");
                }
                subtotalItem = subtotalItem.add(salvarCustomizacao(item, custProduto, custReq.getQuantidade()));
            }

            // Atualiza o subtotal do item para incluir customizações (fixas + ad-hoc)
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

    /**
     * RN-045 + RN-046 — busca o item de catálogo do usuário e valida se está liberado para venda.
     * O bloqueio pode vir do produto inativado/excluído (RN-045) ou do catálogo desativado (RN-046);
     * a mensagem diferencia a causa para orientar a artesã no próximo passo.
     */
    private ItemCatalogo buscarItemCatalogoParaVenda(UUID itemCatalogoId, UUID usuarioId) {
        ItemCatalogo item = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemCatalogoId)
                .filter(i -> i.getCatalogo().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new BusinessException("Item de catálogo não encontrado"));

        // RN-046 — catálogo desativado bloqueia a venda de todos os seus itens.
        if (!Boolean.TRUE.equals(item.getCatalogo().getAtivo())) {
            throw new BusinessException("O catálogo '" + item.getCatalogo().getNome()
                    + "' está desativado. Reative o catálogo antes de adicionar seus itens ao orçamento.");
        }

        // RN-045 — produto do item inativado/excluído bloqueia a venda do item.
        Produto produto = item.getProduto();
        if (!Boolean.TRUE.equals(produto.getAtivo()) || produto.getDeletedAt() != null) {
            throw new BusinessException("O produto '" + produto.getNome()
                    + "' deste item de catálogo foi inativado. Reative o produto ou troque o produto do item"
                    + " antes de adicioná-lo ao orçamento.");
        }
        return item;
    }

    /**
     * RN-048 — cria uma linha de customização do orçamento com snapshot do preço de venda do
     * produto CUSTOMIZACAO. Usado tanto pelas customizações fixas do pacote quanto pelas ad-hoc
     * (RN-030). Retorna o subtotal da customização para compor o subtotal do item.
     */
    private BigDecimal salvarCustomizacao(OrcamentoItem item, Produto custProduto, int quantidade) {
        BigDecimal precoUnitario = custProduto.getPrecoVenda();
        BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidade));
        orcamentoItemCustomizacaoRepository.save(OrcamentoItemCustomizacao.builder()
                .orcamentoItem(item)
                .produto(custProduto)
                .quantidade(quantidade)
                .precoUnitario(precoUnitario)
                .subtotal(subtotal)
                .build());
        return subtotal;
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
                    Produto produto = item.getItemCatalogo().getProduto();
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

    public OrcamentoDetalheResponse cancelar(UUID id, AvancaStatusRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        StatusOrcamento atual = orcamento.getStatus();
        LocalDateTime now = LocalDateTime.now();

        switch (atual) {
            case RASCUNHO:
            case ENVIADO:
            case APROVADO:
            case AGUARDANDO_SINAL:
                orcamento.setCancelamentoTipo(TipoCancelamento.SIMPLES);
                orcamento.setCancelamentoMotivo(request.getMotivoCancelamento());
                break;

            case SINAL_PAGO:
                orcamento.setCancelamentoTipo(TipoCancelamento.ESTORNO);
                orcamento.setCancelamentoMotivo(request.getMotivoCancelamento());
                if (request.isEstornarSinal()) {
                    reciboEstornoRepository.save(ReciboEstorno.builder()
                            .orcamento(orcamento)
                            .valorEstornado(orcamento.getValorSinal())
                            .dataEstorno(now)
                            .build());
                    orcamento.setEstornoSinal(true);
                    orcamento.setDataEstornoSinal(now);
                }
                break;

            case EM_PRODUCAO:
            case FINALIZADO:
                orcamento.setCancelamentoTipo(TipoCancelamento.MULTA);
                orcamento.setPercentualMulta(request.getPercentualMulta());
                orcamento.setCancelamentoMotivo(request.getMotivoCancelamento());
                if (atual == StatusOrcamento.FINALIZADO) {
                    reverterEstoque(orcamento, request.getMotivoCancelamento());
                }
                break;

            case ENTREGUE:
            case PAGO:
                String justificativa = request.getJustificativa();
                if (justificativa == null || justificativa.trim().length() < MIN_OBS_OUTRO) {
                    throw new BusinessException(
                            "A justificativa é obrigatória (mín. " + MIN_OBS_OUTRO + " caracteres)");
                }
                orcamento.setCancelamentoTipo(TipoCancelamento.JUSTIFICATIVA);
                orcamento.setCancelamentoMotivo(justificativa);
                break;

            case CANCELADO:
                throw new BusinessException("Este orçamento já foi cancelado.");

            default:
                throw new BusinessException("Cancelamento inválido para o status atual.");
        }

        orcamento.setStatus(StatusOrcamento.CANCELADO);
        orcamento = orcamentoRepository.save(orcamento);
        return montarDetalhe(orcamento);
    }

    private void reverterEstoque(Orcamento orcamento, String motivo) {
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        for (OrcamentoItem item : itens) {
            Produto produto = item.getItemCatalogo().getProduto();
            produto.setEstoqueAtual(produto.getEstoqueAtual()
                    .add(BigDecimal.valueOf(item.getQuantidade())));
            produtoRepository.save(produto);

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.ORCAMENTO)
                    .quantidade(BigDecimal.valueOf(item.getQuantidade()))
                    .observacao(motivo)
                    .referenciaId(orcamento.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.ORCAMENTO.name())
                    .estornada(false)
                    .build());
        }
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
