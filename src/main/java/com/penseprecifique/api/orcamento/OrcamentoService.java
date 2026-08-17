package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.ReciboEstorno;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemCustomizacaoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.SimularAlertasOrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.AvisoEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoEstoqueProdutoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.OrcamentoMapper;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class OrcamentoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int MIN_OBS_OUTRO = 30;

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository itemCatalogoCustomizacaoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final ReciboEstornoRepository reciboEstornoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrcamentoMapper orcamentoMapper;

    /**
     * Frente 3/P-BE-CONSOLIDADO-001 — filtro opcional de intervalo de data de criação
     * (dataCriacaoDe/dataCriacaoAte), combinável com status/busca já existentes (critério AND).
     * createdAt é LocalDateTime; dataCriacaoDe/dataCriacaoAte chegam como LocalDate (yyyy-MM-dd, mesmo
     * formato de dataInicioDe/dataInicioAte em GET /producoes) e são convertidos aqui pro início/fim
     * do dia antes de ir pro repositório, pra intervalo inclusive nos dois extremos.
     */
    @Transactional(readOnly = true)
    public Page<OrcamentoResponse> listar(StatusOrcamento status, String busca,
                                           LocalDate dataCriacaoDe, LocalDate dataCriacaoAte, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        String buscaNormalizada = busca != null && !busca.isBlank() ? busca : null;
        LocalDateTime dataCriacaoDeInicio = dataCriacaoDe != null ? dataCriacaoDe.atStartOfDay() : null;
        LocalDateTime dataCriacaoAteFim = dataCriacaoAte != null ? dataCriacaoAte.atTime(LocalTime.MAX) : null;

        Page<Orcamento> page = orcamentoRepository.buscar(
                usuarioId, status, buscaNormalizada, dataCriacaoDeInicio, dataCriacaoAteFim, pageable);
        return page.map(orcamentoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrcamentoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
        return montarDetalhe(orcamento);
    }

    /**
     * #194/RN-NOVA-5 — itens do orçamento cujo produto não tem estoque suficiente pra cobrir a
     * quantidade solicitada. Somente leitura: alimenta a condição de exibir o botão "Criar produção"
     * no Detalhe do Orçamento (UC-NOVA-4) — a criação da produção em si passa pelos endpoints já
     * existentes de Produção, não por aqui. Reaproveita OrcamentoItem.getProdutoVendido() (já resolve
     * a origem Catálogo/avulso — RN-054) em vez de duplicar essa lógica.
     */
    @Transactional(readOnly = true)
    public List<ItemSemEstoqueResponse> itensSemEstoque(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        List<ItemSemEstoqueResponse> semEstoque = new ArrayList<>();
        for (OrcamentoItem item : itens) {
            Produto produto = item.getProdutoVendido();
            if (produto == null) {
                continue;
            }
            BigDecimal solicitada = BigDecimal.valueOf(item.getQuantidade());
            BigDecimal estoqueAtual = produto.getEstoqueAtual();
            if (estoqueAtual.compareTo(solicitada) >= 0) {
                continue;
            }

            ItemSemEstoqueResponse resposta = new ItemSemEstoqueResponse();
            resposta.setProdutoId(produto.getId());
            resposta.setIdentificador(IdentificadorFormatter.formatar("PRO", produto.getNumero()));
            resposta.setNomeProduto(produto.getNome());
            resposta.setQuantidadeSolicitada(solicitada);
            resposta.setEstoqueAtual(estoqueAtual);
            resposta.setQuantidadeFaltante(solicitada.subtract(estoqueAtual));
            semEstoque.add(resposta);
        }
        return semEstoque;
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
            validarOrigemItem(itemReq);

            OrcamentoItem item;
            BigDecimal subtotalItem;

            if (itemReq.getItemCatalogoId() != null) {
                // RN-045 + RN-046 — item só entra no orçamento se produto e catálogo estiverem ativos.
                ItemCatalogo itemCatalogo = buscarItemCatalogoParaVenda(itemReq.getItemCatalogoId(), usuarioId);

                // RN-048 — snapshot do preço de venda no momento exato da adição. Por ser uma cópia
                // (não referência viva), nenhuma edição futura no catálogo/produto/margem altera este valor.
                BigDecimal precoUnitario = itemCatalogo.getPrecoVenda();
                subtotalItem = precoUnitario.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

                item = OrcamentoItem.builder()
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
            } else {
                // RN-054 — produto avulso (sem Catálogo): preco_unitario é o snapshot definitivo informado
                // na hora, nunca recalculado depois mesmo que margem_padrao das Configurações mude.
                Produto produtoAvulso = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(itemReq.getProdutoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

                BigDecimal precoUnitario = itemReq.getPrecoUnitario();
                subtotalItem = precoUnitario.multiply(BigDecimal.valueOf(itemReq.getQuantidade()));

                item = OrcamentoItem.builder()
                        .orcamento(orcamento)
                        .produto(produtoAvulso)
                        .margemAplicada(itemReq.getMargemAplicada())
                        .quantidade(itemReq.getQuantidade())
                        .precoUnitario(precoUnitario)
                        .subtotal(subtotalItem)
                        .build();
                item = orcamentoItemRepository.save(item);
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

        OrcamentoDetalheResponse response = montarDetalhe(orcamento);
        List<OrcamentoItem> itensGravados = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        response.setAvisosEstoque(calcularAvisosEstoque(itensGravados));
        return response;
    }

    /**
     * Frente 2/P-BE-CONSOLIDADO-001 (Cenário 207) — simulação de alertas de estoque ao montar o
     * orçamento, sem persistir nada, mesmo espírito de {@code ProducaoService.simularAlertas()}
     * (RN-NOVA-7). Diferença de modelo: Orçamento vende Produto já pronto (estoque do próprio
     * Produto), não consumo de insumo via ficha técnica — por isso a resolução usa
     * {@code Produto.estoqueAtual}/{@code permitirEstoqueNegativo} (mesmo critério de
     * {@link #calcularAvisosEstoque}/{@link #validarEstoqueParaFinalizar}/{@link #decidirSituacaoEstoque}).
     * #218 (RN-NOVA-8/9) — resposta dedicada {@link SimulacaoEstoqueProdutoResponse}, com
     * {@code permitirEstoqueNegativo} explícito (gap do antigo reaproveitamento de
     * {@code AlertaInsumoResponse}, DTO de Produção). SUFICIENTE não é filtrado aqui, mesmo
     * comportamento não-filtrado do simularAlertas de Produção — quem decide omitir da exibição é
     * o front.
     */
    @Transactional(readOnly = true)
    public List<SimulacaoEstoqueProdutoResponse> simularAlertas(List<SimularAlertasOrcamentoItemRequest> itens) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Map<UUID, BigDecimal> necessidadePorProduto = new LinkedHashMap<>();
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();

        for (SimularAlertasOrcamentoItemRequest item : itens) {
            ProdutoNecessario resolvido = resolverProdutoNecessario(
                    item.getItemCatalogoId(), item.getProdutoId(), item.getQuantidade(), usuarioId);
            necessidadePorProduto.merge(resolvido.produto().getId(), resolvido.necessaria(), BigDecimal::add);
            produtosPorId.putIfAbsent(resolvido.produto().getId(), resolvido.produto());
        }

        List<SimulacaoEstoqueProdutoResponse> alertas = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorProduto.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            BigDecimal necessaria = entry.getValue();
            boolean permitirEstoqueNegativo = Boolean.TRUE.equals(produto.getPermitirEstoqueNegativo());

            SimulacaoEstoqueProdutoResponse alerta = new SimulacaoEstoqueProdutoResponse();
            alerta.setProdutoId(produto.getId());
            alerta.setNomeProduto(produto.getNome());
            alerta.setEstoqueAtual(produto.getEstoqueAtual());
            alerta.setQuantidadeNecessaria(necessaria);
            alerta.setPermitirEstoqueNegativo(permitirEstoqueNegativo);
            alerta.setSituacao(decidirSituacaoEstoque(produto.getEstoqueAtual(), necessaria, permitirEstoqueNegativo));
            alertas.add(alerta);
        }
        return alertas;
    }

    /**
     * RN-NOVA-8/9/RN-081/RN-NOVA-11 (revisada) — critério único de "situação de estoque" para um
     * Produto vendido em Orçamento, usado por {@link #simularAlertas} (aviso ao adicionar/checkpoint
     * ao criar, nunca bloqueante). RN-NOVA-10 (bloqueio pré-save em {@code criar()}) foi removida —
     * o orçamento nunca é bloqueado por estoque insuficiente; a única trava real é
     * {@link #validarEstoqueParaFinalizar}, no avanço para FINALIZADO.
     */
    private SituacaoAlertaInsumo decidirSituacaoEstoque(BigDecimal estoqueAtual, BigDecimal necessaria,
                                                          boolean permitirEstoqueNegativo) {
        if (estoqueAtual.compareTo(necessaria) >= 0) {
            return SituacaoAlertaInsumo.SUFICIENTE;
        }
        return permitirEstoqueNegativo ? SituacaoAlertaInsumo.AVISO : SituacaoAlertaInsumo.BLOQUEIO_FUTURO;
    }

    /** Resolução de item em construção (Catálogo ou avulso) para o Produto vendido + quantidade necessária. */
    private record ProdutoNecessario(Produto produto, BigDecimal necessaria) {
    }

    /**
     * Resolve a origem XOR de um item em construção (itemCatalogoId ou produtoId) para o Produto
     * vendido e a quantidade necessária dele — mesma resolução usada por {@link #simularAlertas} e
     * pelo bloqueio pré-save de RN-NOVA-10, para não divergir em qual produto/quantidade cada
     * caminho está avaliando.
     */
    private ProdutoNecessario resolverProdutoNecessario(UUID itemCatalogoId, UUID produtoId, int quantidade,
                                                          UUID usuarioId) {
        boolean temCatalogo = itemCatalogoId != null;
        boolean temProduto = produtoId != null;
        if (temCatalogo == temProduto) {
            throw new BusinessException(
                    "Cada item deve ter exatamente uma origem: itemCatalogoId (Catálogo) ou produtoId (avulso).");
        }

        if (temCatalogo) {
            ItemCatalogo itemCatalogo = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemCatalogoId)
                    .filter(i -> i.getCatalogo().getUsuario().getId().equals(usuarioId))
                    .orElseThrow(() -> new BusinessException("Item de catálogo não encontrado"));
            BigDecimal necessaria = BigDecimal.valueOf((long) quantidade * itemCatalogo.getQuantidadePacote());
            return new ProdutoNecessario(itemCatalogo.getProduto(), necessaria);
        }
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return new ProdutoNecessario(produto, BigDecimal.valueOf(quantidade));
    }

    /**
     * UC-037/#126 — aviso informativo (nunca bloqueia) de estoque insuficiente para os produtos
     * vendidos no orçamento, calculado só na criação. Quantidade é acumulada por produto quando o
     * mesmo produto aparece em mais de um item (mesmo critério de RN-059/validarEstoqueParaFinalizar),
     * para refletir a necessidade real somada, não item a item isoladamente.
     */
    private List<AvisoEstoqueResponse> calcularAvisosEstoque(List<OrcamentoItem> itens) {
        Map<UUID, BigDecimal> quantidadeAcumulada = new LinkedHashMap<>();
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        for (OrcamentoItem item : itens) {
            Produto produto = item.getProdutoVendido();
            quantidadeAcumulada.merge(produto.getId(), calcularQuantidadeMovimentacao(item), BigDecimal::add);
            produtosPorId.putIfAbsent(produto.getId(), produto);
        }

        List<AvisoEstoqueResponse> avisos = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumulada.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            BigDecimal necessaria = entry.getValue();
            if (produto.getEstoqueAtual().compareTo(necessaria) < 0) {
                AvisoEstoqueResponse aviso = new AvisoEstoqueResponse();
                aviso.setProdutoId(produto.getId());
                aviso.setNomeProduto(produto.getNome());
                aviso.setEstoqueAtual(produto.getEstoqueAtual());
                aviso.setQuantidadeNecessaria(necessaria);
                aviso.setMensagem("Estoque insuficiente para " + necessaria.stripTrailingZeros().toPlainString()
                        + " unidades de " + produto.getNome() + ". Estoque atual: "
                        + produto.getEstoqueAtual().stripTrailingZeros().toPlainString());
                avisos.add(aviso);
            }
        }
        return avisos;
    }

    /**
     * RN-054 — a origem do item é XOR: itemCatalogoId (Catálogo) ou produtoId (avulso), nunca os dois nem nenhum.
     * Item avulso exige precoUnitario informado (snapshot definitivo, não recalculado depois).
     */
    private void validarOrigemItem(OrcamentoItemRequest itemReq) {
        boolean temCatalogo = itemReq.getItemCatalogoId() != null;
        boolean temProduto = itemReq.getProdutoId() != null;
        if (temCatalogo == temProduto) {
            throw new BusinessException(
                    "Cada item do orçamento deve ter exatamente uma origem: itemCatalogoId (Catálogo) ou produtoId (avulso).");
        }
        if (temProduto && itemReq.getPrecoUnitario() == null) {
            throw new BusinessException("O preço unitário é obrigatório para item avulso (sem Catálogo).");
        }
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

    public Object avancarStatus(UUID id, AvancaStatusRequest request) {
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
                List<UUID> confirmados = request.getConfirmarEstoqueNegativoProdutoIds() != null
                        ? request.getConfirmarEstoqueNegativoProdutoIds() : List.of();
                List<AvisoEstoqueNegativoResponse> avisos = validarEstoqueParaFinalizar(itensParaBaixa, confirmados);
                // RN-052 — algum produto ficaria negativo e ainda não foi confirmado: nada foi baixado,
                // devolve o aviso para o usuário confirmar antes de reenviar.
                if (!avisos.isEmpty()) {
                    ConfirmacaoEstoqueNegativoResponse resposta = new ConfirmacaoEstoqueNegativoResponse();
                    resposta.setAvisos(avisos);
                    return resposta;
                }
                for (OrcamentoItem item : itensParaBaixa) {
                    Produto produto = item.getProdutoVendido();
                    BigDecimal quantidadeBaixa = calcularQuantidadeMovimentacao(item);
                    produto.setEstoqueAtual(produto.getEstoqueAtual()
                            .subtract(quantidadeBaixa));
                    produtoRepository.save(produto);

                    movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                            .produto(produto)
                            .tipo(TipoMovimentacaoProduto.SAIDA)
                            .motivo(MotivoMovimentacaoProduto.ORCAMENTO)
                            .quantidade(quantidadeBaixa)
                            // RN-050: snapshot do catálogo (ou "venda sem catálogo") e do preço vendido no
                            // orçamento (nunca o valor atual)
                            .catalogoReferencia(catalogoReferenciaMovimentacao(item))
                            .precoVendido(item.getPrecoUnitario())
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
        orcamento.setDataCancelamento(now);
        orcamento = orcamentoRepository.save(orcamento);
        return montarDetalhe(orcamento);
    }

    private void reverterEstoque(Orcamento orcamento, String motivo) {
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        for (OrcamentoItem item : itens) {
            Produto produto = item.getProdutoVendido();
            // RN-049: reversão espelha a baixa (quantidade × quantidade_pacote, quando aplicável)
            BigDecimal quantidadeReversao = calcularQuantidadeMovimentacao(item);
            produto.setEstoqueAtual(produto.getEstoqueAtual()
                    .add(quantidadeReversao));
            produtoRepository.save(produto);

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.ORCAMENTO)
                    .quantidade(quantidadeReversao)
                    .observacao(motivo)
                    .referenciaId(orcamento.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.ORCAMENTO.name())
                    .estornada(false)
                    .build());
        }
    }

    /**
     * RN-059 — produto com permitirEstoqueNegativo=false bloqueia o avanço para FINALIZADO
     * incondicionalmente (tudo ou nada, antes de qualquer baixa), sem opção de forçar. Quantidade é
     * acumulada por produto caso o mesmo produto apareça em mais de um item do orçamento, para refletir
     * o efeito real da baixa sequencial. RN-052 — produto com permitirEstoqueNegativo=true cujo resultado
     * ficaria negativo e cujo id não está em idsConfirmados vira aviso pendente na lista retornada, sem
     * bloquear (chamador decide: se a lista vier não-vazia, nada foi baixado ainda).
     */
    private List<AvisoEstoqueNegativoResponse> validarEstoqueParaFinalizar(List<OrcamentoItem> itens, List<UUID> idsConfirmados) {
        Map<UUID, BigDecimal> quantidadeAcumulada = new LinkedHashMap<>();
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        for (OrcamentoItem item : itens) {
            Produto produto = item.getProdutoVendido();
            quantidadeAcumulada.merge(produto.getId(), calcularQuantidadeMovimentacao(item), BigDecimal::add);
            produtosPorId.putIfAbsent(produto.getId(), produto);
        }

        List<String> bloqueados = new ArrayList<>();
        List<AvisoEstoqueNegativoResponse> avisos = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumulada.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            BigDecimal necessaria = entry.getValue();
            BigDecimal resultante = produto.getEstoqueAtual().subtract(necessaria);
            if (resultante.compareTo(BigDecimal.ZERO) >= 0) {
                continue;
            }
            if (!produto.getPermitirEstoqueNegativo()) {
                bloqueados.add(produto.getNome());
            } else if (!idsConfirmados.contains(produto.getId())) {
                AvisoEstoqueNegativoResponse aviso = new AvisoEstoqueNegativoResponse();
                aviso.setComponenteId(produto.getId());
                aviso.setNome(produto.getNome());
                aviso.setEstoqueAtual(produto.getEstoqueAtual());
                aviso.setQuantidadeNecessaria(necessaria);
                aviso.setMensagem("A baixa de " + necessaria.stripTrailingZeros().toPlainString()
                        + " de " + produto.getNome() + " deixará o estoque negativo (atual: "
                        + produto.getEstoqueAtual().stripTrailingZeros().toPlainString() + "). Confirme para prosseguir.");
                avisos.add(aviso);
            }
        }
        if (!bloqueados.isEmpty()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + String.join(", ", bloqueados)
                            + ". Este(s) produto(s) não permite(m) estoque negativo.");
        }
        return avisos;
    }

    /**
     * RN-049 — baixa/reversão de estoque: quantidade do orçamento × quantidade_pacote quando o item vem
     * de um ItemCatalogo. RN-054 — item avulso (sem Catálogo) não tem quantidade_pacote: baixa é direto
     * pela quantidade do orçamento.
     */
    private BigDecimal calcularQuantidadeMovimentacao(OrcamentoItem item) {
        if (item.getItemCatalogo() != null) {
            return BigDecimal.valueOf((long) item.getQuantidade() * item.getItemCatalogo().getQuantidadePacote());
        }
        return BigDecimal.valueOf(item.getQuantidade());
    }

    /**
     * RN-050 + RN-054 — snapshot da origem do item na movimentação: "{CTG-N}" para Catálogo,
     * "{PRO-N} - Venda sem catálogo" para produto avulso.
     */
    private String catalogoReferenciaMovimentacao(OrcamentoItem item) {
        if (item.getItemCatalogo() != null) {
            return IdentificadorFormatter.formatar("CTG", item.getItemCatalogo().getCatalogo().getNumero());
        }
        return IdentificadorFormatter.formatar("PRO", item.getProduto().getNumero()) + " - Venda sem catálogo";
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
            List<FichaTecnicaItem> fichaTecnicaProduto =
                    fichaTecnicaItemRepository.findByProdutoId(item.getProdutoVendido().getId());
            itensResponse.add(orcamentoMapper.toItemResponse(item, customizacoes, fichaTecnicaProduto));
        }
        OrcamentoDetalheResponse response = orcamentoMapper.toDetalheResponse(orcamento, itens);
        response.setItens(itensResponse);
        return response;
    }

    /** #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition. */
    private Integer proximoNumero(UUID usuarioId) {
        usuarioRepository.lockPorId(usuarioId);
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
