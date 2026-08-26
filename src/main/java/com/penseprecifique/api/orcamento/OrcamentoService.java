package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Producao;
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
import com.penseprecifique.api.shared.dto.request.orcamento.VincularProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.AvisoEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.ItemSemEstoqueResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoEstoqueProdutoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.SimulacaoAvancoStatusResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.OrcamentoMapper;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoCustomizacaoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.producao.ProducaoService;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    // #354 — allowlist explícita dos campos de ordenação aceitos em GET /orcamentos. Campo fora
    // desta lista é rejeitado com BusinessException (400) por PageableOrdenacaoResolver, nunca mais
    // repassado cru pro Hibernate (UnknownPathException → 500).
    private static final Map<String, String> CAMPOS_ORDENACAO_ORCAMENTO = Map.of(
            "total", "total",
            "createdAt", "createdAt",
            "status", "status",
            "cliente.nome", "cliente.nome",
            "numero", "numero"
    );

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
    private final OrcamentoProducaoRepository orcamentoProducaoRepository;
    private final ProducaoRepository producaoRepository;
    private final ProducaoService producaoService;
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

        Pageable pageableOrdenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO_ORCAMENTO,
                "total, createdAt, status, cliente.nome, numero");
        Page<Orcamento> page = orcamentoRepository.buscar(
                usuarioId, status, buscaNormalizada, dataCriacaoDeInicio, dataCriacaoAteFim, pageableOrdenado);
        Page<OrcamentoResponse> mapeado = page.map(orcamentoMapper::toResponse);
        return new PageImpl<>(mapeado.getContent(), pageable, mapeado.getTotalElements());
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
            subtotalOrcamento = subtotalOrcamento.add(criarItem(orcamento, itemReq, usuarioId));
        }

        validarDesconto(subtotalOrcamento, tipoDesconto, orcamento.getDescontoValor());
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
     * RN-NOVA-4 (V0.8.2) — edição de orçamento em status RASCUNHO. Reaproveita os validadores de
     * {@link #criar()} (XOR de origem — ORC-020, desconto — ORC-002/ORC-017) e a criação de item
     * ({@link #criarItem}). A lista de itens enviada é comparada (diff) contra a persistida — nunca
     * apaga e recria tudo: item que casa por origem+quantidade+customizações ad-hoc mantém o
     * snapshot de preço original, sem recálculo (ORC-001); item sem par no request é removido; item
     * do request sem par na lista persistida é criado como item novo, com snapshot atual (mesma
     * lógica de item recém-adicionado — troca de produto é modelada como remover+adicionar, nunca
     * mutação in-place do mesmo item, para não colidir com ORC-001).
     */
    public OrcamentoDetalheResponse editar(UUID id, OrcamentoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        // Guard de status primeiro (fail fast) — ORC-004: fora de RASCUNHO, edição é bloqueada.
        if (orcamento.getStatus() != StatusOrcamento.RASCUNHO) {
            throw new BusinessException("Só é possível editar um orçamento em Rascunho.");
        }

        Cliente cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(request.getClienteId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));

        validarRegras(request);
        for (OrcamentoItemRequest itemReq : request.getItens()) {
            validarOrigemItem(itemReq);
        }

        TipoDesconto tipoDesconto = parseTipoDesconto(request.getTipoDesconto());
        BigDecimal descontoValor = request.getDescontoValor() != null ? request.getDescontoValor() : BigDecimal.ZERO;

        // Diff item a item: casamento guloso 1:1 entre itens persistidos e itens do request.
        List<OrcamentoItem> disponiveis = new ArrayList<>(orcamentoItemRepository.findByOrcamentoId(orcamento.getId()));
        record Pareamento(OrcamentoItemRequest request, OrcamentoItem casado) {
        }
        List<Pareamento> pareamentos = new ArrayList<>();
        for (OrcamentoItemRequest itemReq : request.getItens()) {
            OrcamentoItem casado = disponiveis.stream().filter(p -> mesmoItem(p, itemReq)).findFirst().orElse(null);
            if (casado != null) {
                disponiveis.remove(casado);
            }
            pareamentos.add(new Pareamento(itemReq, casado));
        }

        // Sobrou em "disponiveis" = não bateu com nenhum item do request → removido.
        for (OrcamentoItem removido : disponiveis) {
            orcamentoItemCustomizacaoRepository.deleteByOrcamentoItemId(removido.getId());
            orcamentoItemRepository.delete(removido);
        }

        BigDecimal subtotalOrcamento = BigDecimal.ZERO;
        for (Pareamento par : pareamentos) {
            if (par.casado() != null) {
                // Item mantido — snapshot intocado, sem recálculo (ORC-001).
                subtotalOrcamento = subtotalOrcamento.add(par.casado().getSubtotal());
            } else {
                // Item novo (adição real ou resultado de troca de produto) — snapshot atual.
                subtotalOrcamento = subtotalOrcamento.add(criarItem(orcamento, par.request(), usuarioId));
            }
        }

        validarDesconto(subtotalOrcamento, tipoDesconto, descontoValor);
        BigDecimal total = calcularTotal(subtotalOrcamento, tipoDesconto, descontoValor);

        orcamento.setCliente(cliente);
        orcamento.setMetodoPagamento(request.getMetodoPagamento());
        orcamento.setMetodoPagamentoObs(request.getMetodoPagamentoObs());
        orcamento.setPrazoProducaoDias(request.getPrazoProducaoDias());
        orcamento.setInicioAssimQueAprovado(request.isInicioAssimQueAprovado());
        orcamento.setDataInicioEstimada(request.getDataInicioEstimada());
        orcamento.setSinalAtivo(request.isSinalAtivo());
        orcamento.setPercentualSinal(request.getPercentualSinal());
        orcamento.setDescontoTipo(tipoDesconto);
        orcamento.setDescontoValor(descontoValor);
        orcamento.setObservacoes(request.getObservacoes());
        orcamento.setDataValidade(request.getDataValidade());
        orcamento.setSubtotal(subtotalOrcamento);
        orcamento.setTotal(total);
        orcamento.setValorSinal(orcamento.getSinalAtivo() ? calcularValorSinal(total, request) : null);

        orcamento = orcamentoRepository.save(orcamento);

        OrcamentoDetalheResponse response = montarDetalhe(orcamento);
        List<OrcamentoItem> itensGravados = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        response.setAvisosEstoque(calcularAvisosEstoque(itensGravados));
        return response;
    }

    /**
     * RN-NOVA-5 (V0.8.2) — duplica um orçamento existente (qualquer status de origem) para um
     * RASCUNHO novo e independente. Monta um {@link OrcamentoRequest} sintético a partir da entidade
     * original e delega para {@link #criar}, reaproveitando toda a validação/cálculo de lá sem
     * duplicar lógica — inclusive o preço dos itens, sempre recalculado pelo valor atual (nunca o
     * snapshot antigo), tanto para item de Catálogo quanto avulso.
     */
    public OrcamentoDetalheResponse duplicar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento original = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        return criar(montarRequestDuplicacao(original));
    }

    /**
     * RN-NOVA-5 — campos copiados diretamente: cliente, pagamento (+obs), prazo, sinal
     * (percentualSinal — valorSinal é sempre recalculado por {@link #calcularValorSinal} dentro de
     * {@link #criar}, nunca copiado do original), desconto, observações. Campos resetados (deixados
     * de fora do request, {@code criar()} já os trata como novo): usuário, número, status, dados de
     * aprovação/sinal-pago/cancelamento. Datas ({@code dataInicioEstimada}/{@code dataValidade})
     * sempre limpas — por isso {@code inicioAssimQueAprovado} é forçado para {@code true} na
     * duplicata (mesmo quando o original é {@code false}), já que copiar {@code false} sem uma data
     * de início específica violaria {@link #validarRegras}. {@code temPrazoProducao} (RN-NOVA-3,
     * V0.8.2) é derivado de {@code prazoProducaoDias != null} do original — não existe como campo
     * próprio na entidade, só no request.
     */
    private OrcamentoRequest montarRequestDuplicacao(Orcamento original) {
        OrcamentoRequest request = new OrcamentoRequest();
        request.setClienteId(original.getCliente().getId());
        request.setMetodoPagamento(original.getMetodoPagamento());
        request.setMetodoPagamentoObs(original.getMetodoPagamentoObs());
        request.setTemPrazoProducao(original.getPrazoProducaoDias() != null);
        request.setPrazoProducaoDias(original.getPrazoProducaoDias());
        request.setInicioAssimQueAprovado(true);
        request.setDataInicioEstimada(null);
        request.setSinalAtivo(Boolean.TRUE.equals(original.getSinalAtivo()));
        request.setPercentualSinal(original.getPercentualSinal());
        request.setTipoDesconto(original.getDescontoTipo() != null ? original.getDescontoTipo().name() : null);
        request.setDescontoValor(original.getDescontoValor());
        request.setObservacoes(original.getObservacoes());
        request.setDataValidade(null);

        List<OrcamentoItemRequest> itensRequest = new ArrayList<>();
        for (OrcamentoItem itemOriginal : orcamentoItemRepository.findByOrcamentoId(original.getId())) {
            OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
            itemReq.setQuantidade(itemOriginal.getQuantidade());
            itemReq.setCustomizacoes(customizacoesAdHocRequest(itemOriginal));
            if (itemOriginal.getItemCatalogo() != null) {
                itemReq.setItemCatalogoId(itemOriginal.getItemCatalogo().getId());
            } else {
                itemReq.setProdutoId(itemOriginal.getProduto().getId());
                itemReq.setMargemAplicada(itemOriginal.getMargemAplicada());
                // RN-NOVA-5 — preço recalculado: preco_venda ATUAL do cadastro do produto, não o
                // snapshot antigo do item (criarItem() não lê isso sozinho para origem avulsa).
                itemReq.setPrecoUnitario(itemOriginal.getProduto().getPrecoVenda());
            }
            itensRequest.add(itemReq);
        }
        request.setItens(itensRequest);
        return request;
    }

    /**
     * RN-NOVA-4 — critério de "mesmo item" no diff de edição: mesma origem (itemCatalogoId ou
     * produtoId), mesma quantidade e mesmas customizações ad-hoc (ver {@link #mesmasCustomizacoesAdHoc}).
     * Preço/margem informados no request são ignorados para efeito de casamento — só decidem o
     * snapshot de um item novo, nunca "desempatam" um item já existente.
     */
    private boolean mesmoItem(OrcamentoItem persistido, OrcamentoItemRequest req) {
        boolean mesmaOrigemCatalogo = persistido.getItemCatalogo() != null && req.getItemCatalogoId() != null
                && persistido.getItemCatalogo().getId().equals(req.getItemCatalogoId());
        boolean mesmaOrigemAvulsa = persistido.getItemCatalogo() == null && req.getItemCatalogoId() == null
                && persistido.getProduto() != null && req.getProdutoId() != null
                && persistido.getProduto().getId().equals(req.getProdutoId());
        if (!mesmaOrigemCatalogo && !mesmaOrigemAvulsa) {
            return false;
        }
        if (!persistido.getQuantidade().equals(req.getQuantidade())) {
            return false;
        }
        return mesmasCustomizacoesAdHoc(persistido, req.getCustomizacoes());
    }

    /**
     * Compara as customizações ad-hoc (RN-030) do item persistido contra as do request. O request só
     * carrega customizações ad-hoc (as fixas do catálogo — RN-048 — nunca aparecem nele, são geradas
     * automaticamente); para isolar a parcela ad-hoc do item persistido, subtrai do total persistido
     * o conjunto fixo recalculado ao vivo a partir do ItemCatalogo (mesma fórmula de arredondamento
     * de {@link #criarItem}). Item avulso não tem customização fixa — a subtração não roda, o total
     * persistido já é 100% ad-hoc.
     */
    private boolean mesmasCustomizacoesAdHoc(OrcamentoItem persistido, List<OrcamentoItemCustomizacaoRequest> customizacoesRequest) {
        Map<UUID, Integer> adHocPersistido = customizacoesAdHocPersistidas(persistido);
        adHocPersistido.values().removeIf(q -> q == 0);
        // Conjunto fixo mudou desde a criação do item (catálogo editado depois) — não dá pra confirmar
        // igualdade com segurança; trata como item diferente (vira remover+adicionar).
        if (adHocPersistido.values().stream().anyMatch(q -> q < 0)) {
            return false;
        }

        Map<UUID, Integer> adHocRequest = new LinkedHashMap<>();
        for (OrcamentoItemCustomizacaoRequest c : customizacoesRequest) {
            adHocRequest.merge(c.getProdutoId(), c.getQuantidade(), Integer::sum);
        }

        return adHocPersistido.equals(adHocRequest);
    }

    /**
     * Multiset (produtoId -> quantidade) das customizações ad-hoc de um item persistido — total de
     * customizações gravadas menos o conjunto fixo do catálogo recalculado ao vivo (quando a origem é
     * Catálogo). Compartilhado por {@link #mesmasCustomizacoesAdHoc} (diff de edição, RN-NOVA-4) e
     * {@link #customizacoesAdHocRequest} (reconstrução para duplicação, RN-NOVA-5) — mesma extração,
     * dois usos diferentes do resultado.
     */
    private Map<UUID, Integer> customizacoesAdHocPersistidas(OrcamentoItem persistido) {
        Map<UUID, Integer> adHoc = new LinkedHashMap<>();
        for (OrcamentoItemCustomizacao c : orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(persistido.getId())) {
            adHoc.merge(c.getProduto().getId(), c.getQuantidade(), Integer::sum);
        }
        if (persistido.getItemCatalogo() != null) {
            for (ItemCatalogoCustomizacao fixa : itemCatalogoCustomizacaoRepository.findByItemCatalogoId(persistido.getItemCatalogo().getId())) {
                int quantidade = Math.max(1, fixa.getQuantidade().setScale(0, RoundingMode.HALF_UP).intValue());
                adHoc.merge(fixa.getProduto().getId(), -quantidade, Integer::sum);
            }
        }
        return adHoc;
    }

    /**
     * RN-NOVA-5 — reconstrói a lista de customizações ad-hoc de um item persistido no formato de
     * request, para alimentar {@link #criarItem} na duplicação (que readiciona as fixas do catálogo
     * automaticamente — só a parcela ad-hoc precisa vir explícita). Entradas com quantidade <= 0
     * (customização fixa cobre tudo, ou catálogo mudou desde a criação do item) são descartadas.
     */
    private List<OrcamentoItemCustomizacaoRequest> customizacoesAdHocRequest(OrcamentoItem persistido) {
        Map<UUID, Integer> adHoc = customizacoesAdHocPersistidas(persistido);
        List<OrcamentoItemCustomizacaoRequest> resultado = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : adHoc.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            OrcamentoItemCustomizacaoRequest custReq = new OrcamentoItemCustomizacaoRequest();
            custReq.setProdutoId(entry.getKey());
            custReq.setQuantidade(entry.getValue());
            resultado.add(custReq);
        }
        return resultado;
    }

    /**
     * Cria um {@link OrcamentoItem} novo (origem Catálogo ou avulsa, RN-054/ORC-020) com snapshot de
     * preço no momento da criação (ORC-001/ORC-024) e suas customizações (fixas do pacote + ad-hoc,
     * RN-048/RN-030). Extraído de {@link #criar()} para reaproveitar em {@link #editar()} — item novo
     * na edição (adição real ou resultado de troca de produto) segue exatamente a mesma lógica de um
     * item recém-adicionado na criação.
     */
    private BigDecimal criarItem(Orcamento orcamento, OrcamentoItemRequest itemReq, UUID usuarioId) {
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

        return subtotalItem;
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
                // RN-NOVA-2 (V0.8.2) — atalho: sinal inativo + sem prazo de produção + estoque
                // suficiente pula APROVADO/AGUARDANDO_SINAL/SINAL_PAGO/EM_PRODUCAO e vai direto pra
                // FINALIZADO, reaproveitando a mesma checagem/baixa de estoque de EM_PRODUCAO→FINALIZADO.
                // Exceção deliberada ao invariante de ORC-005 (fluxo unidirecional, um passo por vez).
                // RN-NOVA-2 (revisada, P-B012) — ignorarAtalhoAprovacaoDireta=true força o fluxo normal
                // mesmo elegível: usuário recusou o atalho na modal de confirmação do frontend.
                if (!request.isIgnorarAtalhoAprovacaoDireta() && elegivelParaAtalhoAprovacaoDireta(orcamento)) {
                    List<OrcamentoItem> itensAtalho = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
                    List<UUID> confirmadosAtalho = request.getConfirmarEstoqueNegativoProdutoIds() != null
                            ? request.getConfirmarEstoqueNegativoProdutoIds() : List.of();
                    ResultadoValidacaoEstoque resultadoAtalho = validarEstoqueParaFinalizar(itensAtalho, confirmadosAtalho);
                    if (resultadoAtalho.bloqueados().isEmpty()) {
                        if (!resultadoAtalho.avisosPendentes().isEmpty()) {
                            ConfirmacaoEstoqueNegativoResponse respostaAtalho = new ConfirmacaoEstoqueNegativoResponse();
                            respostaAtalho.setAvisos(resultadoAtalho.avisosPendentes());
                            return respostaAtalho;
                        }
                        // RN-033/ORC-019 — data de aprovação é registrada mesmo pulando o status
                        // APROVADO persistido: o orçamento passou pela aprovação conceitualmente, e
                        // essa data aparece nos PDFs de recibo/multa gerados depois de FINALIZADO.
                        orcamento.setDataAprovacao(LocalDateTime.now());
                        baixarEstoque(orcamento, itensAtalho);
                        orcamento.setStatus(StatusOrcamento.FINALIZADO);
                        break;
                    }
                    // bloqueio duro (produto sem permitirEstoqueNegativo e insuficiente) — atalho não
                    // se aplica, sem erro (RN-NOVA-2), segue para a transição normal abaixo.
                }
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
                ResultadoValidacaoEstoque resultado = validarEstoqueParaFinalizar(itensParaBaixa, confirmados);
                if (!resultado.bloqueados().isEmpty()) {
                    throw new BusinessException(
                            "Estoque insuficiente para " + String.join(", ", resultado.bloqueados())
                                    + ". Este(s) produto(s) não permite(m) estoque negativo.");
                }
                // RN-052 — algum produto ficaria negativo e ainda não foi confirmado: nada foi baixado,
                // devolve o aviso para o usuário confirmar antes de reenviar.
                if (!resultado.avisosPendentes().isEmpty()) {
                    ConfirmacaoEstoqueNegativoResponse resposta = new ConfirmacaoEstoqueNegativoResponse();
                    resposta.setAvisos(resultado.avisosPendentes());
                    return resposta;
                }
                baixarEstoque(orcamento, itensParaBaixa);
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

    /**
     * RN-NOVA-2 (revisada, V0.8.2, P-B012) — simula o resultado de {@link #avancarStatus} para o
     * caso {@code ENVIADO} sem persistir nada, para o frontend decidir se mostra a modal de
     * confirmação do atalho antes de aplicar de verdade. Reaproveita
     * {@link #elegivelParaAtalhoAprovacaoDireta} e {@link #validarEstoqueParaFinalizar} por chamada
     * direta — os dois já são puros (só leem a entidade já carregada, nenhum {@code save()}) — e
     * nunca chama {@link #baixarEstoque}. Só suporta status {@code ENVIADO}, que é o único onde
     * RN-NOVA-2 se aplica; outros status não têm o que simular para este fim.
     */
    @Transactional(readOnly = true)
    public SimulacaoAvancoStatusResponse simularAvancarStatus(UUID id, AvancaStatusRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus() != StatusOrcamento.ENVIADO) {
            throw new BusinessException("Simulação de atalho só é aplicável a orçamentos em ENVIADO.");
        }

        SimulacaoAvancoStatusResponse resposta = new SimulacaoAvancoStatusResponse();
        resposta.setStatusAtual(StatusOrcamento.ENVIADO);

        boolean elegivel = !request.isIgnorarAtalhoAprovacaoDireta() && elegivelParaAtalhoAprovacaoDireta(orcamento);
        if (!elegivel) {
            resposta.setAtalhoAplicavel(false);
            resposta.setStatusResultante(StatusOrcamento.APROVADO);
            resposta.setAvisosEstoque(List.of());
            return resposta;
        }

        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        List<UUID> confirmados = request.getConfirmarEstoqueNegativoProdutoIds() != null
                ? request.getConfirmarEstoqueNegativoProdutoIds() : List.of();
        ResultadoValidacaoEstoque resultado = validarEstoqueParaFinalizar(itens, confirmados);

        if (!resultado.bloqueados().isEmpty()) {
            // bloqueio duro — mesmo critério do avancarStatus real: atalho não se aplica, sem erro.
            resposta.setAtalhoAplicavel(false);
            resposta.setStatusResultante(StatusOrcamento.APROVADO);
            resposta.setAvisosEstoque(List.of());
            return resposta;
        }

        resposta.setAtalhoAplicavel(true);
        resposta.setStatusResultante(StatusOrcamento.FINALIZADO);
        resposta.setAvisosEstoque(resultado.avisosPendentes());
        return resposta;
    }

    /**
     * RN-NOVA-6/RN-PROD-VINC-01/02/RN-ORC-VINC-03 (V0.8.2) — vincula uma produção existente do
     * usuário a um orçamento (N:N, tabela de junção {@code orcamento_producoes}) e sincroniza de
     * verdade os produtos do orçamento com a produção via {@link ProducaoService#adicionarProdutosDeOrcamento}
     * — que também aplica a restrição RN-PROD-VINC-02 (só {@code AGUARDANDO_INICIO} aceita item novo;
     * produção em outro estado lança BusinessException antes de qualquer gravação). Não exige nenhum
     * status específico do orçamento — o vínculo pode ser estabelecido a qualquer momento, mesmo sem
     * nunca chegar a bloquear nenhuma transição (RN-ORC-VINC-01).
     *
     * <p><b>Re-sincronização (P-B017, achado de P-B015):</b> vincular a mesma produção mais de uma vez
     * não é mais um no-op puro depois da primeira chamada — {@link #produtosPendentesDeSincronizacao}
     * compara os itens atuais do orçamento contra o que já foi registrado em histórico
     * ({@code ITEM_ADICIONADO}) para aquele par orçamento+produção e só passa adiante a diferença
     * (delta positivo) de cada produto. Item novo desde o último vínculo entra pela primeira vez; item
     * cuja quantidade aumentou entra só pela diferença; item inalterado não gera chamada nenhuma a
     * {@code adicionarProdutosDeOrcamento} (nunca re-soma o que já foi sincronizado). Quantidade que
     * <em>diminuiu</em> no orçamento não decrementa nada aqui — vincular só adiciona, nunca remove;
     * remover é responsabilidade explícita de {@link #desvincularProducao}.
     */
    public List<OrcamentoProducaoResponse> vincularProducao(UUID id, VincularProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
        Producao producao = producaoRepository.findByIdAndUsuarioId(request.getProducaoId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        List<ProducaoProdutoRequest> pendentes = produtosPendentesDeSincronizacao(orcamento.getId(), producao.getId());
        if (!pendentes.isEmpty()) {
            producaoService.adicionarProdutosDeOrcamento(producao.getId(), pendentes, usuarioId, orcamento);
        }

        if (orcamentoProducaoRepository.findByOrcamentoIdAndProducaoId(orcamento.getId(), producao.getId()).isEmpty()) {
            orcamentoProducaoRepository.save(OrcamentoProducao.builder()
                    .orcamento(orcamento)
                    .producao(producao)
                    .build());
        }

        return orcamentoProducaoRepository.findByOrcamentoId(orcamento.getId()).stream()
                .map(orcamentoMapper::toOrcamentoProducaoResponse)
                .toList();
    }

    /**
     * P-B017 (#320) — diferença entre os itens atuais do orçamento e o que já foi registrado como
     * {@code ITEM_ADICIONADO} para o par orçamento+produção (via {@link ProducaoService#produtosJaAdicionadosPeloOrcamento}).
     * Mescla duplicatas do orçamento por {@code produtoId} antes de comparar (mesmo produto pode
     * vir de mais de um {@code OrcamentoItem}). Delta zero ou negativo (quantidade não mudou ou
     * diminuiu) não entra na lista — só delta positivo é considerado "pendente".
     */
    private List<ProducaoProdutoRequest> produtosPendentesDeSincronizacao(UUID orcamentoId, UUID producaoId) {
        Map<UUID, BigDecimal> jaSincronizado = producaoService.produtosJaAdicionadosPeloOrcamento(producaoId, orcamentoId);

        Map<UUID, BigDecimal> atual = new LinkedHashMap<>();
        for (ProducaoProdutoRequest item : produtosDoOrcamentoComoRequest(orcamentoId)) {
            atual.merge(item.getProdutoId(), item.getQuantidade(), BigDecimal::add);
        }

        List<ProducaoProdutoRequest> pendentes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : atual.entrySet()) {
            BigDecimal sincronizado = jaSincronizado.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal delta = entry.getValue().subtract(sincronizado);
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                ProducaoProdutoRequest pendente = new ProducaoProdutoRequest();
                pendente.setProdutoId(entry.getKey());
                pendente.setQuantidade(delta);
                pendentes.add(pendente);
            }
        }
        return pendentes;
    }

    /**
     * RN-PROD-VINC-03 (V0.8.2, #320) — preview do alerta combinado de insumo/rendimento antes de
     * confirmar {@link #vincularProducao}: soma dos produtos já persistidos na produção com os
     * produtos do orçamento, não cada um isoladamente. Não persiste nada — delega o cálculo a
     * {@link ProducaoService#calcularAlertasComAdicao}, que também aplica a mesma restrição de estado
     * (RN-PROD-VINC-02) do vínculo real, para o preview nunca prometer um vínculo que a confirmação
     * não vai conseguir efetivar.
     */
    @Transactional(readOnly = true)
    public List<AlertaInsumoResponse> simularVincularProducao(UUID id, VincularProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        return producaoService.calcularAlertasComAdicao(
                request.getProducaoId(), produtosDoOrcamentoComoRequest(orcamento.getId()));
    }

    private List<ProducaoProdutoRequest> produtosDoOrcamentoComoRequest(UUID orcamentoId) {
        return orcamentoItemRepository.findByOrcamentoId(orcamentoId)
                .stream()
                .map(item -> {
                    ProducaoProdutoRequest produtoRequest = new ProducaoProdutoRequest();
                    produtoRequest.setProdutoId(item.getProdutoVendido().getId());
                    produtoRequest.setQuantidade(BigDecimal.valueOf(item.getQuantidade()));
                    return produtoRequest;
                })
                .toList();
    }

    /**
     * RN-NOVA-2 (V0.8.2) — checagem barata (sem consulta ao banco além do orçamento já carregado) das
     * duas primeiras condições de habilitação do atalho ENVIADO→FINALIZADO: sinal inativo e nenhum
     * prazo de produção informado. {@code prazoProducaoDias == null} é o único sinal confiável de "sem
     * prazo" garantido por {@link #validarRegras} no momento da escrita (RN-NOVA-3). A terceira
     * condição (estoque suficiente para todos os produtos) é mais cara — só verificada em
     * {@link #avancarStatus} quando estas duas primeiras já passaram. Não checa
     * {@link #validarVinculoProducao} de propósito: RN-NOVA-6 se aplica só às duas transições que
     * persistem {@code status=EM_PRODUCAO}, e o atalho nunca passa por esse status — o atalho existe
     * justamente para quando a produção não é necessária.
     */
    private boolean elegivelParaAtalhoAprovacaoDireta(Orcamento orcamento) {
        return !Boolean.TRUE.equals(orcamento.getSinalAtivo()) && orcamento.getPrazoProducaoDias() == null;
    }

    /**
     * RN-049/RN-050 — baixa de estoque + registro de movimentação para os itens de um orçamento.
     * Reaproveitada tanto pela transição normal EM_PRODUCAO→FINALIZADO quanto pelo atalho de
     * RN-NOVA-2 (ENVIADO direto pra FINALIZADO) — assume que o chamador já obteve um
     * {@link ResultadoValidacaoEstoque} sem bloqueios nem avisos pendentes antes de chamar este método.
     */
    private void baixarEstoque(Orcamento orcamento, List<OrcamentoItem> itens) {
        for (OrcamentoItem item : itens) {
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
                    if (request.getDataEstornoSinal() == null) {
                        throw new BusinessException("A data do estorno é obrigatória.");
                    }
                    reciboEstornoRepository.save(ReciboEstorno.builder()
                            .orcamento(orcamento)
                            .valorEstornado(orcamento.getValorSinal())
                            .dataEstorno(request.getDataEstornoSinal())
                            .build());
                    orcamento.setEstornoSinal(true);
                    orcamento.setDataEstornoSinal(request.getDataEstornoSinal());
                }
                break;

            case EM_PRODUCAO:
            case FINALIZADO:
                orcamento.setCancelamentoTipo(TipoCancelamento.MULTA);
                orcamento.setPercentualMulta(request.getPercentualMulta());
                orcamento.setCancelamentoMotivo(request.getMotivoCancelamento());
                ResultadoMulta resultadoMulta = calcularResultadoMulta(orcamento);
                orcamento.setValorMulta(resultadoMulta.valorMulta());
                orcamento.setValorDevolvidoMulta(resultadoMulta.valorDevolvido());
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
     * RN-059 — produto com permitirEstoqueNegativo=false e estoque insuficiente vira bloqueio duro
     * (tudo ou nada, antes de qualquer baixa), sem opção de forçar. Quantidade é acumulada por produto
     * caso o mesmo produto apareça em mais de um item do orçamento, para refletir o efeito real da
     * baixa sequencial. RN-052 — produto com permitirEstoqueNegativo=true cujo resultado ficaria
     * negativo e cujo id não está em idsConfirmados vira aviso pendente, sem bloquear.
     *
     * Devolve um {@link ResultadoValidacaoEstoque} em vez de lançar exceção no bloqueio duro — os dois
     * chamadores (V0.8.2) dão tratamento diferente ao mesmo bloqueio: em EM_PRODUCAO→FINALIZADO é erro
     * real (o chamador lança {@link BusinessException}, preservando o comportamento anterior deste
     * método); no atalho de RN-NOVA-2 (ENVIADO→FINALIZADO) é só "atalho não se aplica", sem erro
     * (Caso 6) — usar exceção para os dois misturaria essas duas semânticas na mesma chamada.
     */
    private ResultadoValidacaoEstoque validarEstoqueParaFinalizar(List<OrcamentoItem> itens, List<UUID> idsConfirmados) {
        Map<UUID, BigDecimal> quantidadeAcumulada = new LinkedHashMap<>();
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        for (OrcamentoItem item : itens) {
            Produto produto = item.getProdutoVendido();
            quantidadeAcumulada.merge(produto.getId(), calcularQuantidadeMovimentacao(item), BigDecimal::add);
            produtosPorId.putIfAbsent(produto.getId(), produto);
        }

        List<String> bloqueados = new ArrayList<>();
        List<AvisoEstoqueNegativoResponse> avisosPendentes = new ArrayList<>();
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
                avisosPendentes.add(aviso);
            }
        }
        return new ResultadoValidacaoEstoque(bloqueados, avisosPendentes);
    }

    /**
     * Resultado de {@link #validarEstoqueParaFinalizar}. {@code bloqueados} são nomes de produtos com
     * {@code permitirEstoqueNegativo=false} e estoque insuficiente; {@code avisosPendentes} são
     * produtos que ficariam negativos mas permitem, ainda sem confirmação do chamador.
     */
    private record ResultadoValidacaoEstoque(List<String> bloqueados, List<AvisoEstoqueNegativoResponse> avisosPendentes) {
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

    /**
     * RN-NOVA-3 (V0.8.2) — substitui ORC-018: prazo de produção deixa de ser obrigatório
     * incondicionalmente, passa a depender da resposta a "Vai ter prazo de produção?"
     * ({@code temPrazoProducao}, obrigatório via {@code @NotNull} no DTO). "Sim" preserva o
     * comportamento antigo de ORC-018 (mínimo 1 dia, obrigatório); "Não" exige
     * {@code prazoProducaoDias} ausente — rejeitado como inconsistência se vier preenchido mesmo
     * assim, em vez de ignorado silenciosamente (evita persistir um valor que o front nunca deveria
     * ter mandado). {@code prazoProducaoDias == null} na entidade é, a partir daqui, o único sinal
     * confiável de "sem prazo" em todo o resto do código (ver {@link #avancarStatus}), garantido por
     * esta validação no momento da escrita.
     */
    private void validarRegras(OrcamentoRequest request) {
        if (Boolean.TRUE.equals(request.getTemPrazoProducao())) {
            if (request.getPrazoProducaoDias() == null) {
                throw new BusinessException("O prazo de produção é obrigatório quando há prazo de produção");
            }
        } else if (request.getPrazoProducaoDias() != null) {
            throw new BusinessException(
                    "O prazo de produção deve ficar vazio quando a resposta for 'Não terá prazo de produção'");
        }

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

        if (request.isSinalAtivo()) {
            boolean percentualValido = request.getPercentualSinal() != null
                    && request.getPercentualSinal().compareTo(BigDecimal.ZERO) > 0;
            boolean valorValido = request.getValorSinal() != null
                    && request.getValorSinal().compareTo(BigDecimal.ZERO) > 0;
            if (!percentualValido && !valorValido) {
                throw new BusinessException(
                        "Quando o sinal está ativo, o percentual ou o valor do sinal deve ser maior que zero");
            }
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

    /**
     * RN-NOVA-8 (V0.8.2) — bloqueia desconto negativo e desconto (percentual ou em valor) que
     * excede o subtotal do orçamento, em vez de aplicar piso zero silencioso em {@link #calcularTotal}.
     */
    private void validarDesconto(BigDecimal subtotal, TipoDesconto tipoDesconto, BigDecimal descontoValor) {
        BigDecimal desconto = descontoValor != null ? descontoValor : BigDecimal.ZERO;
        if (desconto.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("O desconto não pode ser negativo");
        }
        BigDecimal descontoEmValor = tipoDesconto == TipoDesconto.PERCENTUAL
                ? subtotal.multiply(desconto).divide(CEM, 6, RoundingMode.HALF_UP)
                : desconto;
        if (descontoEmValor.compareTo(subtotal) > 0) {
            throw new BusinessException("O desconto não pode ser maior que o subtotal do orçamento");
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
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularValorSinal(BigDecimal total, OrcamentoRequest request) {
        if (request.getPercentualSinal() != null) {
            return total.multiply(request.getPercentualSinal())
                    .divide(CEM, 2, RoundingMode.HALF_UP);
        }
        return request.getValorSinal();
    }

    /** RN-NOVA-1 (V0.8.2) — resultado do cálculo de multa: valor final cobrado + valor devolvido (mini-estorno). */
    private record ResultadoMulta(BigDecimal valorMulta, BigDecimal valorDevolvido) {
    }

    /**
     * RN-NOVA-1 (V0.8.1, estendida em V0.8.2) — valor final de multa desconta o sinal já pago,
     * piso zero: nunca cobra valor negativo. Desde V0.8.2, quando o sinal pago excede o valor
     * bruto da multa, a diferença é devolvida ao cliente ("mini-estorno") em vez de simplesmente
     * zerar a cobrança sem devolução — {@code valorDevolvido} vem preenchido só nesse caso,
     * {@code null} nos demais (sinal <= multa bruta, ou sem sinal pago — comportamento inalterado).
     */
    private ResultadoMulta calcularResultadoMulta(Orcamento orcamento) {
        if (orcamento.getPercentualMulta() == null || orcamento.getTotal() == null) {
            return new ResultadoMulta(null, null);
        }
        BigDecimal valorMultaBruto = orcamento.getTotal()
                .multiply(orcamento.getPercentualMulta())
                .divide(CEM, 2, RoundingMode.HALF_UP);
        BigDecimal sinalPago = Boolean.TRUE.equals(orcamento.getSinalAtivo()) && orcamento.getValorSinal() != null
                ? orcamento.getValorSinal()
                : BigDecimal.ZERO;
        BigDecimal diferenca = sinalPago.subtract(valorMultaBruto).setScale(2, RoundingMode.HALF_UP);
        if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
            return new ResultadoMulta(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), diferenca);
        }
        BigDecimal valorMultaFinal = valorMultaBruto.subtract(sinalPago).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new ResultadoMulta(valorMultaFinal, null);
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
        response.setProducoesVinculadas(orcamentoProducaoRepository.findByOrcamentoId(orcamento.getId()).stream()
                .map(orcamentoMapper::toOrcamentoProducaoResponse)
                .toList());
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
