package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.CustoMedioPonderado;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.entity.ListaCompra;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraConfirmacaoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraContagensResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ImpactoCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.SimulacaoCancelamentoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #541/RN-NOVA-4 (V0.15.0, DT-NOVA-3) — registro de compra RASCUNHO → CONFIRMADA → CANCELADA.
 *
 * <p>Rascunho não mexe em estoque nem custo. Confirmar é síncrono e tudo ou nada (uma transação):
 * para cada linha, na ordem, recalcula o custo médio ponderado (INS-004, {@link CustoMedioPonderado}),
 * soma o estoque, registra ENTRADA/COMPRA com referência COMPRA, grava preço pago e custo
 * anterior/posterior e cria/atualiza o vínculo Fornecedor↔Insumo (RN-NOVA-6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompraService {

    // Expressões JPQL sobre o alias da query de listagem (c = compra, f = fornecedor do cabeçalho, em LEFT
    // JOIN). Ajuste do teste manual (#541): a listagem ordena por qualquer coluna. Fornecedor = o do
    // cabeçalho (compra com vários fornecedores fica sem, no fim); total e itens por subquery.
    private static final Map<String, String> CAMPOS_ORDENACAO = Map.of(
            "dataCompra", "c.dataCompra",
            "numero", "c.numero",
            "createdAt", "c.createdAt",
            "fornecedor", "f.nome",
            "status", "c.status",
            "itens", "(SELECT COUNT(ci) FROM CompraItem ci WHERE ci.compra = c)",
            "total", "(SELECT COALESCE(SUM(ci.precoTotal), 0) FROM CompraItem ci WHERE ci.compra = c)");

    static final String MSG_INSUMO_INATIVO = "Este insumo está inativo e não pode ser adicionado. Reative-o para continuar.";
    static final String MSG_SEM_METODO = "Escolha como a compra foi paga.";
    static final int PARCELAS_MAX_PADRAO = 12;

    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;
    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteService clienteService;
    private final FornecedorInsumoService fornecedorInsumoService;
    private final StatusListaCompraService statusListaCompraService;
    private final CompraMapper compraMapper;
    private final ImpactoCompraService impactoCompraService;

    // --------------------------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public Page<CompraResumoResponse> listar(StatusCompra status, UUID fornecedorId, LocalDate de, LocalDate ate,
                                             Pageable pageable) {
        return listar(status, fornecedorId, de, ate, null, null, pageable);
    }

    /**
     * DT-NOVA-19 (V0.15.0) — {@code insumoId}: compras com o insumo em alguma linha; {@code busca}: número
     * (COM-12 ou 12), nome de insumo ou de fornecedor, sem diferenciar maiúscula (drill-down do dashboard).
     */
    @Transactional(readOnly = true)
    public Page<CompraResumoResponse> listar(StatusCompra status, UUID fornecedorId, LocalDate de, LocalDate ate,
                                             UUID insumoId, String busca, Pageable pageable) {
        return listar(status == null ? List.of() : List.of(status), fornecedorId, de, ate, insumoId, busca, null, false, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CompraResumoResponse> listar(Collection<StatusCompra> statuses, UUID fornecedorId, LocalDate de, LocalDate ate,
                                             UUID insumoId, String busca, Boolean pago, boolean comDesconto,
                                             Pageable pageable) {
        return listarComFiltros(statuses, fornecedorId == null ? List.of() : List.of(fornecedorId), de, ate,
                insumoId == null ? List.of() : List.of(insumoId), busca, pago, comDesconto, pageable);
    }

    /**
     * #585/#601 (adendo 2, DT-NOVA-27) — {@code statuses} vazio = todos; valores do mesmo filtro somam
     * como OU e filtros diferentes como E (RN-NOVA-35). {@code pago} nulo = tanto faz;
     * {@code comDesconto} = desconto de linha ou de nota. Vários fornecedores/insumos somam como OU
     * (campo de filtros da modal de listagem).
     */
    @Transactional(readOnly = true)
    public Page<CompraResumoResponse> listarComFiltros(Collection<StatusCompra> statuses, Collection<UUID> fornecedorIds, LocalDate de,
                                             LocalDate ate, Collection<UUID> insumoIds, String busca, Boolean pago,
                                             boolean comDesconto, Pageable pageable) {
        boolean filtrarFornecedor = fornecedorIds != null && !fornecedorIds.isEmpty();
        boolean filtrarInsumo = insumoIds != null && !insumoIds.isEmpty();
        UUID usuarioId = getUsuarioAutenticado().getId();
        Pageable ordenado = PageableOrdenacaoResolver.resolverExpressaoJpql(comDesempate(pageable), CAMPOS_ORDENACAO,
                "dataCompra, numero, createdAt, fornecedor, status, itens, total");
        String termo = busca != null ? busca.trim().toLowerCase(java.util.Locale.ROOT) : "";
        String digitos = termo.replaceAll("\\D", "");
        Integer numeroBusca = !digitos.isEmpty() && digitos.length() <= 9 && termo.matches("(com-?\\s*)?\\d+")
                ? Integer.valueOf(digitos) : -1;
        boolean filtrarStatus = statuses != null && !statuses.isEmpty();
        Page<Compra> pagina = compraRepository.buscarComFiltros(usuarioId, filtrarStatus,
                filtrarStatus ? statuses : List.of(StatusCompra.values()), pago != null, Boolean.TRUE.equals(pago),
                comDesconto, filtrarFornecedor,
                filtrarFornecedor ? fornecedorIds : List.of(new UUID(0, 0)), de, ate,
                filtrarInsumo, filtrarInsumo ? insumoIds : List.of(new UUID(0, 0)),
                !termo.isEmpty(), numeroBusca, "%" + termo + "%", ordenado);

        Map<UUID, List<CompraItem>> itensPorCompra = pagina.isEmpty() ? Map.of()
                : compraItemRepository.findByCompraIdIn(pagina.map(Compra::getId).getContent()).stream()
                        .collect(Collectors.groupingBy(i -> i.getCompra().getId()));
        List<CompraResumoResponse> conteudo = pagina.getContent().stream()
                .map(c -> compraMapper.toResumo(c, itensPorCompra.getOrDefault(c.getId(), List.of())))
                .toList();
        return new PageImpl<>(conteudo, pageable, pagina.getTotalElements());
    }

    /** Empate numa coluna (mesmo total, mesmo fornecedor…) cai na compra mais recente, sempre estável. */
    private static Pageable comDesempate(Pageable pageable) {
        if (pageable.getSort().isUnsorted() || pageable.getSort().getOrderFor("numero") != null) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                pageable.getSort().and(Sort.by(Sort.Direction.DESC, "numero")));
    }

    @Transactional(readOnly = true)
    public CompraResponse buscar(UUID id) {
        Compra compra = buscarEntidade(id, getUsuarioAutenticado().getId());
        return compraMapper.toResponse(compra, compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
    }

    // --------------------------------------------------------------------------------- rascunho

    /** "Salvar rascunho" de uma compra nova: o COM-N nasce aqui (RN-NOVA-4). */
    public CompraResponse criarRascunho(CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = novaCompra(usuario);
        aplicarRequest(compra, request, usuario.getId(), List.of());
        return compraMapper.toResponse(compra, compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
    }

    /** DT-NOVA-3 — o PUT geral só vale para RASCUNHO; pagamento de confirmada é PATCH próprio. */
    public CompraResponse atualizarRascunho(UUID id, CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = buscarEntidade(id, usuario.getId());
        exigirRascunho(compra, "Só é possível editar uma compra em Rascunho.");
        aplicarRequest(compra, request, usuario.getId(), compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
        return compraMapper.toResponse(compra, compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
    }

    /** Soft delete: o COM-N do rascunho excluído nunca volta à sequência (RN-053). */
    public void excluirRascunho(UUID id) {
        Compra compra = buscarEntidade(id, getUsuarioAutenticado().getId());
        exigirRascunho(compra, "Só é possível excluir uma compra em Rascunho.");
        compra.setDeletedAt(LocalDateTime.now());
        compraRepository.save(compra);
    }

    // --------------------------------------------------------------------------------- confirmar

    /** Compra nova confirmada direto ("Confirmar" sem salvar rascunho antes). Tudo ou nada. */
    public CompraConfirmacaoResponse confirmarNova(CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = novaCompra(usuario);
        aplicarRequest(compra, request, usuario.getId(), List.of());
        return confirmarEntidade(compra, usuario);
    }

    /** Confirma um rascunho, gravando antes as alterações do request (se enviado). Tudo ou nada. */
    public CompraConfirmacaoResponse confirmar(UUID id, CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = buscarEntidade(id, usuario.getId());
        exigirRascunho(compra, "Só é possível confirmar uma compra em Rascunho.");
        if (request != null) {
            aplicarRequest(compra, request, usuario.getId(), compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
        }
        return confirmarEntidade(compra, usuario);
    }

    private CompraConfirmacaoResponse confirmarEntidade(Compra compra, Usuario usuario) {
        List<CompraItem> itens = compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId());
        validarParaConfirmar(compra, itens);
        // #543 — custo de cada insumo antes da compra (primeira linha dele) → comparado ao final.
        Map<UUID, BigDecimal> custoAntes = new LinkedHashMap<>();
        itens.forEach(i -> custoAntes.putIfAbsent(i.getInsumo().getId(), i.getInsumo().getCustoUnitario()));

        for (CompraItem item : itens) {
            Insumo insumo = item.getInsumo();
            BigDecimal custoAnterior = insumo.getCustoUnitario();
            BigDecimal novoCusto = CustoMedioPonderado.calcular(insumo.getEstoqueAtual(), custoAnterior,
                    item.getQuantidade(), item.getPrecoTotal());
            BigDecimal precoPago = CompraMapper.precoUnitario(item.getPrecoTotal(), item.getQuantidade());

            insumo.setCustoUnitario(novoCusto);
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(item.getQuantidade()));
            insumoRepository.save(insumo);

            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.ENTRADA)
                    .motivo(MotivoMovimentacaoInsumo.COMPRA)
                    .quantidade(item.getQuantidade())
                    .custoUnitario(novoCusto)
                    .referenciaId(compra.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.COMPRA)
                    .estornada(false)
                    .build());

            item.setPrecoUnitarioPago(precoPago);
            item.setCustoUnitarioAnterior(custoAnterior.setScale(CustoMedioPonderado.ESCALA_CUSTO, java.math.RoundingMode.HALF_UP));
            item.setCustoUnitarioPosterior(novoCusto);
            compraItemRepository.save(item);
        }

        compra.setStatus(StatusCompra.CONFIRMADA);
        compra.setConfirmadaEm(LocalDateTime.now());
        compraRepository.saveAndFlush(compra);

        statusListaCompraService.recalcular(compra.getListaCompra()); // #596/RN-NOVA-41

        // #590/RN-NOVA-39 — depois de CONFIRMADA, para a própria compra entrar na média/menor valor.
        for (CompraItem item : itens) {
            if (item.getFornecedor() != null) {
                fornecedorInsumoService.registrarPrecoDaCompra(usuario, item.getFornecedor(), item.getInsumo(),
                        item.getPrecoUnitarioPago());
            }
        }

        ImpactoCompraResponse impacto = impactoCompraService.aplicar(usuario.getId(), mudancas(itens, custoAntes));
        return new CompraConfirmacaoResponse(compraMapper.toResponse(compra, itens), impacto);
    }

    private static List<ImpactoCompraService.MudancaCusto> mudancas(List<CompraItem> itens, Map<UUID, BigDecimal> custoAntes) {
        Map<UUID, Insumo> insumos = new LinkedHashMap<>();
        itens.forEach(i -> insumos.putIfAbsent(i.getInsumo().getId(), i.getInsumo()));
        return insumos.values().stream()
                .map(i -> new ImpactoCompraService.MudancaCusto(i, custoAntes.get(i.getId()), i.getCustoUnitario()))
                .toList();
    }

    /**
     * RN-NOVA-4 — tudo ou nada: qualquer linha inválida impede a confirmação inteira, e a mensagem
     * aponta cada linha com problema. Fornecedor inativado desde o rascunho não impede (RN-NOVA-2).
     */
    private void validarParaConfirmar(Compra compra, List<CompraItem> itens) {
        if (itens.isEmpty()) {
            throw BusinessException.explicado("Compra sem insumos", "Adicione pelo menos um insumo para confirmar a compra.",
                    "Uma compra confirmada dá entrada no estoque e muda o custo dos insumos; sem insumos não há o que registrar.",
                    "Busque o insumo em \"Adicionar insumo à compra…\", informe quantidade e preço e confirme de novo.");
        }
        validarData(compra.getDataCompra());
        List<String> problemas = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            CompraItem item = itens.get(i);
            List<String> daLinha = new ArrayList<>();
            if (item.getQuantidade() == null) {
                daLinha.add("informe a quantidade");
            }
            if (item.getPrecoCheio() == null) {
                daLinha.add("informe o preço");
            }
            if (!Boolean.TRUE.equals(item.getInsumo().getAtivo()) || item.getInsumo().getDeletedAt() != null) {
                daLinha.add("o insumo está inativo");
            }
            if (!daLinha.isEmpty()) {
                problemas.add("Linha " + (i + 1) + " (" + item.getInsumo().getNome() + "): " + String.join(", ", daLinha));
            }
        }
        if (!problemas.isEmpty()) {
            throw BusinessException.explicado("Compra incompleta", "Não foi possível confirmar a compra. " + String.join("; ", problemas) + ".",
                    "Para confirmar, cada linha precisa de quantidade e preço e de um insumo ativo, porque é com esses dados que o estoque e o custo são atualizados.",
                    "Complete as linhas apontadas (ex.: quantidade 10 e preço cheio 30,00) ou remova a linha, e confirme de novo.")
                    .comItens(problemas);
        }
    }

    // --------------------------------------------------------------------------------- cancelar

    /** #544/RN-NOVA-9 — prévia do cancelamento (padrão simular-*): nada é gravado. */
    @Transactional(readOnly = true)
    public SimulacaoCancelamentoResponse simularCancelamento(UUID id) {
        Compra compra = buscarEntidade(id, getUsuarioAutenticado().getId());
        exigirConfirmada(compra);
        return simular(compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
    }

    /**
     * #544/RN-NOVA-9 — só CONFIRMADA. Estoque negativo proibido em qualquer linha bloqueia tudo.
     * Custo: volta ao anterior só se nada mudou o custo do insumo desde esta compra
     * (custo atual == custo posterior da linha, DT-NOVA-4); senão mantém, com AVISO confirmado antes.
     * Linhas processadas de trás para frente, para o mesmo insumo em duas linhas voltar em cadeia.
     * Movimentações originais ficam estornadas; nasce uma SAIDA/ESTORNO_COMPRA por linha. Preço de
     * referência dos vínculos não é revertido.
     */
    public CompraConfirmacaoResponse cancelar(UUID id, CancelarCompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = buscarEntidade(id, usuario.getId());
        exigirConfirmada(compra);
        List<CompraItem> itens = compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId());

        SimulacaoCancelamentoResponse simulacao = simular(itens);
        if (!simulacao.bloqueios().isEmpty()) {
            throw BusinessException.explicado("Cancelamento bloqueado pelo estoque",
                    "Não é possível cancelar: o estoque ficaria negativo em "
                    + simulacao.bloqueios().stream().map(SimulacaoCancelamentoResponse.EstoqueNegativo::nome)
                            .collect(Collectors.joining(", "))
                    + ", que não permite estoque negativo.",
                    "Cancelar a compra tira do estoque a quantidade que ela deu entrada. Parte disso já foi usada, e o insumo está marcado para não aceitar estoque negativo.",
                    "Ajuste o estoque do insumo (edição manual) ou marque no insumo que ele aceita estoque negativo, e tente cancelar de novo.")
                    .comItens(simulacao.bloqueios().stream()
                            .map(b -> b.nome() + ": estoque " + b.estoqueAtual().stripTrailingZeros().toPlainString()
                                    + " − " + b.quantidadeEstornada().stripTrailingZeros().toPlainString()
                                    + " = " + b.estoqueResultante().stripTrailingZeros().toPlainString()
                                    + (b.unidade() != null ? " " + b.unidade() : ""))
                            .toList());
        }
        if (!simulacao.avisos().isEmpty() && !Boolean.TRUE.equals(request.confirmarManterCusto())) {
            throw new BusinessException("Confirme o cancelamento: "
                    + simulacao.avisos().stream().map(SimulacaoCancelamentoResponse.CustoMantido::nome)
                            .collect(Collectors.joining(", "))
                    + " manterá o custo atual, porque o custo mudou depois desta compra.");
        }

        Map<UUID, BigDecimal> custoAntes = new LinkedHashMap<>();
        itens.forEach(i -> custoAntes.putIfAbsent(i.getInsumo().getId(), i.getInsumo().getCustoUnitario()));

        List<CompraItem> reverso = new ArrayList<>(itens);
        java.util.Collections.reverse(reverso);
        for (CompraItem item : reverso) {
            Insumo insumo = item.getInsumo();
            if (item.getCustoUnitarioPosterior() != null
                    && insumo.getCustoUnitario().compareTo(item.getCustoUnitarioPosterior()) == 0) {
                insumo.setCustoUnitario(item.getCustoUnitarioAnterior());
            }
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().subtract(item.getQuantidade()));
            insumoRepository.save(insumo);

            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.SAIDA)
                    .motivo(MotivoMovimentacaoInsumo.ESTORNO_COMPRA)
                    .quantidade(item.getQuantidade())
                    .custoUnitario(insumo.getCustoUnitario())
                    .observacao(request.observacao())
                    .referenciaId(compra.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.COMPRA)
                    .estornada(false)
                    .build());
        }
        movimentacaoInsumoRepository.findByReferenciaIdAndReferenciaTipo(compra.getId(), ReferenciaMovimentacaoTipo.COMPRA)
                .stream().filter(m -> m.getMotivo() == MotivoMovimentacaoInsumo.COMPRA)
                .forEach(m -> {
                    m.setEstornada(true);
                    movimentacaoInsumoRepository.save(m);
                });

        compra.setStatus(StatusCompra.CANCELADA);
        compra.setCanceladaEm(LocalDateTime.now());
        compra.setObservacaoCancelamento(request.observacao());
        compraRepository.saveAndFlush(compra);

        statusListaCompraService.recalcular(compra.getListaCompra()); // #596/RN-NOVA-41

        // #590/RN-NOVA-39 — o preço de referência (Média/Menor valor) passa a ignorar a compra cancelada.
        itens.stream().filter(i -> i.getFornecedor() != null)
                .forEach(i -> fornecedorInsumoService.recalcular(i.getFornecedor(), i.getInsumo()));

        ImpactoCompraResponse impacto = impactoCompraService.aplicar(usuario.getId(), mudancas(itens, custoAntes));
        return new CompraConfirmacaoResponse(compraMapper.toResponse(compra, itens), impacto);
    }

    private SimulacaoCancelamentoResponse simular(List<CompraItem> itens) {
        // estoque: soma por insumo (o mesmo insumo pode estar em duas linhas)
        Map<UUID, BigDecimal> estornoPorInsumo = new LinkedHashMap<>();
        Map<UUID, Insumo> insumos = new LinkedHashMap<>();
        itens.forEach(i -> {
            insumos.putIfAbsent(i.getInsumo().getId(), i.getInsumo());
            estornoPorInsumo.merge(i.getInsumo().getId(), i.getQuantidade(), BigDecimal::add);
        });
        List<SimulacaoCancelamentoResponse.EstoqueNegativo> bloqueios = new ArrayList<>();
        estornoPorInsumo.forEach((insumoId, qtd) -> {
            Insumo insumo = insumos.get(insumoId);
            BigDecimal resultante = insumo.getEstoqueAtual().subtract(qtd);
            if (resultante.signum() < 0 && !Boolean.TRUE.equals(insumo.getPermitirEstoqueNegativo())) {
                bloqueios.add(new SimulacaoCancelamentoResponse.EstoqueNegativo(insumoId, insumo.getNome(),
                        insumo.getUnidadeMedida() != null ? insumo.getUnidadeMedida().getSigla() : null,
                        insumo.getEstoqueAtual(), qtd, resultante));
            }
        });

        // custo: simula a reversão de trás para frente, como o cancelamento faz
        Map<UUID, BigDecimal> custoSimulado = new LinkedHashMap<>();
        insumos.forEach((insumoId, insumo) -> custoSimulado.put(insumoId, insumo.getCustoUnitario()));
        Map<UUID, BigDecimal> mantido = new LinkedHashMap<>();
        for (int k = itens.size() - 1; k >= 0; k--) {
            CompraItem item = itens.get(k);
            UUID insumoId = item.getInsumo().getId();
            if (item.getCustoUnitarioPosterior() != null
                    && custoSimulado.get(insumoId).compareTo(item.getCustoUnitarioPosterior()) == 0) {
                custoSimulado.put(insumoId, item.getCustoUnitarioAnterior());
            } else {
                mantido.put(insumoId, item.getCustoUnitarioAnterior());
            }
        }
        List<SimulacaoCancelamentoResponse.CustoMantido> avisos = mantido.entrySet().stream()
                .map(e -> new SimulacaoCancelamentoResponse.CustoMantido(e.getKey(), insumos.get(e.getKey()).getNome(),
                        insumos.get(e.getKey()).getCustoUnitario(), e.getValue()))
                .toList();
        return new SimulacaoCancelamentoResponse(bloqueios.isEmpty(), bloqueios, avisos);
    }

    /** #591/RN-NOVA-44 — contagens dos filtros de Minhas compras (total da conta). */
    @Transactional(readOnly = true)
    public CompraContagensResponse contagens() {
        UUID usuarioId = getUsuarioAutenticado().getId();
        long rascunhos = compraRepository.countByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, StatusCompra.RASCUNHO);
        long confirmadas = compraRepository.countByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, StatusCompra.CONFIRMADA);
        long canceladas = compraRepository.countByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, StatusCompra.CANCELADA);
        return new CompraContagensResponse(rascunhos + confirmadas + canceladas, rascunhos, confirmadas, canceladas);
    }

    // --------------------------------------------------------------------------------- duplicar

    /**
     * #544/RN-NOVA-10 — CONFIRMADA ou CANCELADA vira RASCUNHO novo (COM-N novo, data de hoje, Não pago),
     * com cabeçalho, fornecedores, insumos, quantidades e preços copiados. Linhas com insumo hoje
     * inativo são copiadas assim mesmo; a validação acontece ao confirmar.
     */
    public CompraResponse duplicar(UUID id) {
        return duplicar(id, true);
    }

    /**
     * #593/RN-NOVA-43 — {@code manterDescontos=false}: copia preço cheio e quantidades, sem desconto de
     * linha nem de nota.
     */
    public CompraResponse duplicar(UUID id, boolean manterDescontos) {
        Usuario usuario = getUsuarioAutenticado();
        Compra origem = buscarEntidade(id, usuario.getId());
        if (origem.getStatus() == StatusCompra.RASCUNHO) {
            throw BusinessException.explicado("Rascunho não é duplicado", "Só é possível duplicar uma compra confirmada ou cancelada.",
                    "O rascunho ainda pode ser editado, então não precisa de cópia.",
                    "Abra o rascunho e edite, ou confirme a compra antes de duplicar.");
        }
        Compra copia = novaCompra(usuario);
        copia.setDataCompra(LocalDate.now());
        copia.setMultiplosFornecedores(origem.getMultiplosFornecedores());
        copia.setFornecedor(origem.getFornecedor());
        copia.setObservacoes(origem.getObservacoes());
        copia.setDescontoNotaTipo(manterDescontos ? origem.getDescontoNotaTipo() : null);
        copia.setDescontoNotaInformado(manterDescontos ? origem.getDescontoNotaInformado() : null);
        copia.setPago(false);
        copia.setMetodoPagamento(null);
        compraRepository.save(copia);

        List<CompraItem> itens = compraItemRepository.findByCompraIdOrderByOrdemAsc(origem.getId()).stream()
                .map(i -> CompraItem.builder().compra(copia).insumo(i.getInsumo()).fornecedor(i.getFornecedor())
                        .quantidade(i.getQuantidade()).precoCheio(i.getPrecoCheio())
                        .descontoTipo(manterDescontos ? i.getDescontoTipo() : null)
                        .descontoInformado(manterDescontos ? i.getDescontoInformado() : null)
                        .ordem(i.getOrdem()).build())
                .toList();
        DescontoCompra.aplicar(copia, itens); // #576 — duplicar copia os descontos (#593: se pedido)
        compraRepository.save(copia);
        compraItemRepository.saveAll(itens);
        return compraMapper.toResponse(copia, compraItemRepository.findByCompraIdOrderByOrdemAsc(copia.getId()));
    }

    private static void exigirConfirmada(Compra compra) {
        if (compra.getStatus() != StatusCompra.CONFIRMADA) {
            throw BusinessException.explicado("Esta compra não pode ser cancelada", "Só é possível cancelar uma compra confirmada.",
                    "Só a compra confirmada mexeu no estoque e no custo; rascunho é excluído e compra cancelada já foi desfeita.",
                    "Para um rascunho, use Excluir.");
        }
    }

    // --------------------------------------------------------------------------------- pagamento

    /** #550 + S2 (Decisão 14) — em CONFIRMADA, Pago/Não pago (e o método) é o único campo editável. */
    public CompraResponse atualizarPagamento(UUID id, PagamentoCompraRequest request) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        Compra compra = buscarEntidade(id, usuarioId);
        if (compra.getStatus() != StatusCompra.CONFIRMADA) {
            throw compra.getStatus() == StatusCompra.RASCUNHO
                    ? BusinessException.explicado("Pagamento do rascunho", "Em rascunho, o pagamento é alterado junto com a compra.",
                            "Enquanto a compra é rascunho, todos os campos são editados no formulário.",
                            "Abra o rascunho em Editar e altere o pagamento lá.")
                    : BusinessException.explicado("Compra cancelada", "Compra cancelada não pode ter o pagamento alterado.",
                            "A compra cancelada foi desfeita e fica só como histórico.",
                            "Se a compra aconteceu de novo, use Duplicar e registre o pagamento na compra nova.");
        }
        aplicarPagamento(compra, request.pago(), request.metodoPagamentoId(), request.parcelas(), usuarioId);
        compraRepository.save(compra);
        return compraMapper.toResponse(compra, compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
    }

    // --------------------------------------------------------------------------------- internos

    private Compra novaCompra(Usuario usuario) {
        // #161 — lock por usuário antes do MAX(numero)+1 (RN-053, mesmo padrão dos outros XXX-N).
        usuarioRepository.lockPorId(usuario.getId());
        return Compra.builder()
                .usuario(usuario)
                .numero(NumeroSequencialUtil.proximoNumero(
                        compraRepository.findTopByUsuarioIdOrderByNumeroDesc(usuario.getId()).map(Compra::getNumero)))
                .status(StatusCompra.RASCUNHO)
                .build();
    }

    /**
     * Grava cabeçalho e linhas do request na compra (rascunho). {@code itensSalvos}: linhas atuais,
     * para a regra de vínculo existente — insumo inativo ou fornecedor inativo/sem papel que já estavam
     * na compra são aceitos; só um vínculo novo passa pela trava (RN-NOVA-2/3, INS-011).
     */
    private void aplicarRequest(Compra compra, CompraRequest request, UUID usuarioId, List<CompraItem> itensSalvos) {
        validarData(request.dataCompra());

        Set<UUID> fornecedoresSalvos = new HashSet<>();
        if (compra.getFornecedor() != null) {
            fornecedoresSalvos.add(compra.getFornecedor().getId());
        }
        itensSalvos.stream().map(CompraItem::getFornecedor).filter(Objects::nonNull)
                .forEach(f -> fornecedoresSalvos.add(f.getId()));
        Set<UUID> insumosSalvos = itensSalvos.stream().map(i -> i.getInsumo().getId()).collect(Collectors.toSet());

        boolean multiplos = Boolean.TRUE.equals(request.multiplosFornecedores());
        Cliente fornecedorCabecalho = resolverFornecedor(request.fornecedorId(), usuarioId, fornecedoresSalvos);

        compra.setDataCompra(request.dataCompra());
        compra.setMultiplosFornecedores(multiplos);
        compra.setFornecedor(fornecedorCabecalho);
        compra.setObservacoes(request.observacoes());
        compra.setDescontoNotaTipo(request.descontoNotaValor() != null ? request.descontoNotaTipo() : null);
        compra.setDescontoNotaInformado(request.descontoNotaTipo() != null ? request.descontoNotaValor() : null);
        aplicarPagamento(compra, Boolean.TRUE.equals(request.pago()), request.metodoPagamentoId(), request.parcelas(), usuarioId);

        List<CompraItem> novos = new ArrayList<>();
        Set<String> pares = new HashSet<>();
        int ordem = 1;
        for (CompraItemRequest linha : request.itensOuVazio()) {
            Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(linha.insumoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + linha.insumoId()));
            if (!Boolean.TRUE.equals(insumo.getAtivo()) && !insumosSalvos.contains(insumo.getId())) {
                throw BusinessException.explicado("Insumo inativo", MSG_INSUMO_INATIVO,
                        "Insumo inativo não recebe compras novas, para não voltar a entrar no custo sem você perceber.",
                        "Reative o insumo na tela de Insumos e adicione de novo.");
            }
            Cliente fornecedor = multiplos
                    ? resolverFornecedor(linha.fornecedorId(), usuarioId, fornecedoresSalvos)
                    : fornecedorCabecalho;
            String par = insumo.getId() + "|" + (fornecedor != null ? fornecedor.getId() : "-");
            if (!pares.add(par)) {
                throw BusinessException.explicado("Insumo repetido", "O insumo " + insumo.getNome() + " está repetido com o mesmo fornecedor nesta compra.",
                        "Cada insumo entra uma vez por fornecedor na mesma compra, para o custo e o estoque não serem somados em dobro.",
                        "Some as quantidades numa linha só (ex.: 10 + 5 = 15) e remova a outra.");
            }
            boolean temDesconto = linha.descontoTipo() != null && linha.descontoValor() != null;
            novos.add(CompraItem.builder().compra(compra).insumo(insumo).fornecedor(fornecedor)
                    .quantidade(linha.quantidade()).precoCheio(linha.precoCheioEfetivo())
                    .descontoTipo(temDesconto ? linha.descontoTipo() : null)
                    .descontoInformado(temDesconto ? linha.descontoValor() : null)
                    .ordem(ordem++).build());
        }
        // #576/RN-NOVA-28 — preço pago de cada linha = preço cheio − desconto da linha − parte da nota.
        DescontoCompra.aplicar(compra, novos);

        compraRepository.save(compra);
        if (!itensSalvos.isEmpty()) {
            compraItemRepository.deleteByCompraId(compra.getId());
        }
        compraItemRepository.saveAll(novos);
    }

    private Cliente resolverFornecedor(UUID fornecedorId, UUID usuarioId, Set<UUID> fornecedoresSalvos) {
        if (fornecedorId == null) {
            return null;
        }
        return clienteService.resolverParaVinculo(fornecedorId, usuarioId, PapelCadastro.FORNECEDOR,
                fornecedoresSalvos.contains(fornecedorId) ? fornecedorId : null);
    }

    /**
     * RN-NOVA-23 — Pago exige método (BLOQUEIO); Não pago limpa o método. Método novo precisa estar
     * ativo; manter o método já salvo é aceito mesmo se ele foi inativado depois.
     */
    private void aplicarPagamento(Compra compra, boolean pago, UUID metodoPagamentoId, Integer parcelas, UUID usuarioId) {
        if (!pago) {
            compra.setPago(false);
            compra.setMetodoPagamento(null);
            compra.setParcelas(null);
            return;
        }
        if (metodoPagamentoId == null) {
            throw BusinessException.explicado("Forma de pagamento", MSG_SEM_METODO,
                    "Compra marcada como paga precisa do método, para o histórico e o PDF mostrarem como foi paga.",
                    "Escolha o método em \"Como foi paga?\" ou marque \"Não pago\".");
        }
        MetodoPagamentoConfiguravel metodo = metodoPagamentoRepository.findByIdAndUsuarioId(metodoPagamentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não encontrado: " + metodoPagamentoId));
        boolean jaSalvo = compra.getMetodoPagamento() != null && compra.getMetodoPagamento().getId().equals(metodoPagamentoId);
        if (!jaSalvo && !Boolean.TRUE.equals(metodo.getAtivo())) {
            throw BusinessException.explicado("Método inativo", "Este método de pagamento está inativo.",
                    "Métodos inativos não são usados em compras novas.",
                    "Escolha outro método ou reative este em Configurações › Métodos de pagamento.");
        }
        compra.setPago(true);
        compra.setMetodoPagamento(metodo);
        compra.setParcelas(parcelasValidas(metodo, parcelas));
    }

    /**
     * #597/RN-NOVA-42 — Cartão de crédito exige parcelas de 1 até a parcela máxima do método (sem
     * máximo cadastrado: 12); nulo = 1. Outros métodos não guardam parcelas.
     */
    static Integer parcelasValidas(MetodoPagamentoConfiguravel metodo, Integer parcelas) {
        if (metodo.getTipo() != TipoMetodoPagamento.CARTAO_CREDITO) {
            return null;
        }
        int maximo = metodo.getMaxParcelas() != null && metodo.getMaxParcelas() > 0 ? metodo.getMaxParcelas() : PARCELAS_MAX_PADRAO;
        int valor = parcelas == null ? 1 : parcelas;
        if (valor < 1 || valor > maximo) {
            throw BusinessException.explicado("Parcelas fora do limite",
                    "Escolha de 1 a " + maximo + " parcelas para " + metodo.getNome() + ".",
                    "O método " + metodo.getNome() + " aceita no máximo " + maximo + " parcelas.",
                    "Escolha um número de parcelas entre 1 e " + maximo + ", ou aumente a parcela máxima do método em Configurações.");
        }
        return valor;
    }

    /**
     * #596/RN-NOVA-41 — liga o rascunho criado a partir da lista (RN-NOVA-13) à lista de origem, para o
     * status da lista acompanhar as compras.
     */
    public void vincularLista(UUID compraId, ListaCompra lista) {
        Compra compra = buscarEntidade(compraId, getUsuarioAutenticado().getId());
        compra.setListaCompra(lista);
        compraRepository.save(compra);
    }

    private static void validarData(LocalDate dataCompra) {
        if (dataCompra == null) {
            throw BusinessException.explicado("Data da compra", "Informe a data da compra",
                    "A data define em que mês a compra entra nos números e no custo.",
                    "Preencha a data em que a compra foi feita (ex.: a data da nota).");
        }
        // Compra agendada é #549 (fora desta versão).
        if (dataCompra.isAfter(LocalDate.now())) {
            throw BusinessException.explicado("Data no futuro", "A data da compra não pode ser futura",
                    "A compra registra algo que já aconteceu: dá entrada no estoque e muda o custo hoje.",
                    "Use a data de hoje ou a data em que a compra foi feita.");
        }
    }

    private static void exigirRascunho(Compra compra, String mensagem) {
        if (compra.getStatus() != StatusCompra.RASCUNHO) {
            throw BusinessException.explicado("Compra fora de rascunho", mensagem,
                    "Só o rascunho é editado, excluído ou confirmado: a compra confirmada já mexeu no estoque e no custo, e a cancelada já foi desfeita.",
                    "Para corrigir uma compra confirmada, cancele e use Duplicar para registrar de novo.");
        }
    }

    Compra buscarEntidade(UUID id, UUID usuarioId) {
        return compraRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Compra não encontrada: " + id));
    }

    static String identificador(Compra compra) {
        return IdentificadorFormatter.formatar("COM", compra.getNumero());
    }

    Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
