package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.FornecedorInsumo;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ListaCompra;
import com.penseprecifique.api.shared.domain.entity.ListaCompraItem;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.GerarListaCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.PreviaListaCompraResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #546/RN-NOVA-12/13 (V0.15.0, DT-NOVA-8) — Lista de Compras. A prévia é calculada na hora e não é
 * salva; "Gerar" grava a LST-N como retrato imutável (valores copiados); a lista pode virar um ou
 * mais rascunhos de compra pelo mesmo caminho do POST /compras.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ListaCompraService {

    private final ListaCompraRepository listaCompraRepository;
    private final ListaCompraItemRepository listaCompraItemRepository;
    private final InsumoRepository insumoRepository;
    private final FornecedorInsumoRepository fornecedorInsumoRepository;
    private final CompraItemRepository compraItemRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final CompraService compraService;
    private final UsuarioRepository usuarioRepository;
    private final CompraMapper compraMapper;

    /**
     * RN-NOVA-12 — só insumos ativos. "Abaixo do mínimo" (estoque &lt; mínimo, insumo sem mínimo nunca
     * entra) e "negativo" (estoque &lt; 0) somam; "de um fornecedor" restringe aos insumos vinculados a
     * ele (sem filtro de estoque marcado: todos os vinculados); a seleção manual sempre acrescenta.
     */
    @Transactional(readOnly = true)
    public PreviaListaCompraResponse previa(boolean abaixoMinimo, boolean estoqueNegativo, UUID fornecedorId,
                                            Collection<UUID> insumoIds) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        List<Insumo> ativos = insumoRepository.findByUsuarioIdAndAtivoTrueAndDeletedAtIsNull(usuarioId);
        Map<UUID, Insumo> porId = ativos.stream().collect(Collectors.toMap(Insumo::getId, i -> i));

        Set<UUID> vinculadosAoFiltro = null;
        if (fornecedorId != null) {
            clienteRepository.findByIdAndUsuarioId(fornecedorId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cadastro não encontrado: " + fornecedorId));
            vinculadosAoFiltro = fornecedorInsumoRepository.findByUsuarioIdAndFornecedorId(usuarioId, fornecedorId)
                    .stream().map(v -> v.getInsumo().getId()).collect(Collectors.toSet());
        }

        Set<UUID> selecionados = new LinkedHashSet<>();
        boolean filtroEstoque = abaixoMinimo || estoqueNegativo;
        for (Insumo i : ativos) {
            boolean entraPorEstoque = (abaixoMinimo && abaixoDoMinimo(i)) || (estoqueNegativo && i.getEstoqueAtual().signum() < 0);
            boolean entra = filtroEstoque ? entraPorEstoque : vinculadosAoFiltro != null;
            if (entra && (vinculadosAoFiltro == null || vinculadosAoFiltro.contains(i.getId()))) {
                selecionados.add(i.getId());
            }
        }
        if (insumoIds != null) {
            insumoIds.stream().filter(porId::containsKey).forEach(selecionados::add);
        }
        if (selecionados.isEmpty()) {
            return new PreviaListaCompraResponse(List.of());
        }

        Map<UUID, List<FornecedorInsumo>> vinculosValidos = fornecedorInsumoRepository
                .findByUsuarioIdAndInsumoIdIn(usuarioId, selecionados).stream()
                .filter(v -> fornecedorValido(v.getFornecedor()))
                .collect(Collectors.groupingBy(v -> v.getInsumo().getId()));
        Map<UUID, Cliente> fornecedorUltimaCompra = fornecedorDaUltimaCompra(usuarioId, selecionados);

        List<PreviaListaCompraResponse.Linha> linhas = new ArrayList<>();
        for (UUID insumoId : selecionados) {
            Insumo insumo = porId.get(insumoId);
            List<FornecedorInsumo> vinculos = vinculosValidos.getOrDefault(insumoId, List.of());
            FornecedorInsumo sugerido = sugerir(vinculos, fornecedorId);
            Cliente fornecedorSugerido = sugerido != null ? sugerido.getFornecedor()
                    : fornecedorId == null ? fornecedorUltimaCompra.get(insumoId) : null;
            BigDecimal preco = sugerido != null ? sugerido.getPrecoReferencia() : null;

            List<PreviaListaCompraResponse.OpcaoFornecedor> opcoes = vinculos.stream()
                    .sorted(Comparator.comparing((FornecedorInsumo v) -> v.getPrecoReferencia(),
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(v -> v.getFornecedor().getNome(), String.CASE_INSENSITIVE_ORDER))
                    .map(v -> new PreviaListaCompraResponse.OpcaoFornecedor(compraMapper.toRef(v.getFornecedor()), v.getPrecoReferencia()))
                    .toList();
            linhas.add(new PreviaListaCompraResponse.Linha(compraMapper.toRef(insumo), insumo.getEstoqueAtual(),
                    insumo.getEstoqueMinimo(), quantidadeSugerida(insumo), compraMapper.toRef(fornecedorSugerido), preco, opcoes));
        }
        linhas.sort(Comparator.comparing(l -> l.insumo().nome(), String.CASE_INSENSITIVE_ORDER));
        return new PreviaListaCompraResponse(linhas);
    }

    /** RN-NOVA-12 — grava a LST-N com o retrato das linhas. Lista vazia ou quantidade ≤ 0 é BLOQUEIO. */
    public ListaCompraResponse gerar(GerarListaCompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        List<ListaCompraItem> itens = montarItens(usuario, request.itensOuVazio(), true);
        ListaCompra lista = novaLista(usuario, StatusListaCompra.GERADA);
        itens.forEach(i -> i.setLista(lista));
        listaCompraItemRepository.saveAll(itens);
        return toResponse(lista, itens);
    }

    /** #596/RN-NOVA-41 — "Salvar rascunho": ganha LST-N, status RASCUNHO; quantidade pode ficar vazia. */
    public ListaCompraResponse salvarRascunho(GerarListaCompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        List<ListaCompraItem> itens = montarItens(usuario, request.itensOuVazio(), false);
        ListaCompra lista = novaLista(usuario, StatusListaCompra.RASCUNHO);
        itens.forEach(i -> i.setLista(lista));
        listaCompraItemRepository.saveAll(itens);
        return toResponse(lista, itens);
    }

    /** #596 — edita as linhas de um RASCUNHO (substitui todas). */
    public ListaCompraResponse atualizarRascunho(UUID id, GerarListaCompraRequest request) {
        ListaCompra lista = buscarEntidade(id);
        exigirRascunho(lista);
        List<ListaCompraItem> itens = montarItens(lista.getUsuario(), request.itensOuVazio(), false);
        substituirItens(lista, itens);
        return toResponse(lista, itens);
    }

    /**
     * #596 — "Gerar lista" a partir do rascunho: mesmas validações do gerar direto e o retrato é tirado
     * AGORA (estoques, nome, unidade e preço de referência atuais). Depois disso não muda mais.
     */
    public ListaCompraResponse gerarRascunho(UUID id) {
        ListaCompra lista = buscarEntidade(id);
        exigirRascunho(lista);
        List<GerarListaCompraRequest.Linha> linhas = listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()).stream()
                .map(i -> new GerarListaCompraRequest.Linha(i.getInsumo().getId(), i.getQuantidade(),
                        i.getFornecedor() != null ? i.getFornecedor().getId() : null))
                .toList();
        List<ListaCompraItem> itens = montarItens(lista.getUsuario(), linhas, true);
        substituirItens(lista, itens);
        lista.setStatus(StatusListaCompra.GERADA);
        lista.setGeradaEm(LocalDateTime.now());
        listaCompraRepository.save(lista);
        return toResponse(lista, itens);
    }

    /**
     * #596/RN-NOVA-41 — troca manual, livre entre GERADA, PARCIALMENTE_COMPRADA, COMPRADA e CANCELADA.
     * Nenhuma lista volta para RASCUNHO; o RASCUNHO só sai para GERADA (pelo "Gerar lista") ou CANCELADA.
     */
    public ListaCompraResponse alterarStatus(UUID id, StatusListaCompra novo) {
        ListaCompra lista = buscarEntidade(id);
        if (novo == StatusListaCompra.RASCUNHO) {
            throw BusinessException.explicado("Status não permitido", "Uma lista não volta para Rascunho.",
                    "A lista gerada é um retrato do que comprar e não é mais editada.",
                    "Para montar outra lista com os mesmos insumos, crie uma Nova lista.");
        }
        if (lista.getStatus() == StatusListaCompra.RASCUNHO && novo != StatusListaCompra.CANCELADA) {
            throw BusinessException.explicado("Lista em rascunho", "O rascunho só pode ser gerado ou cancelado.",
                    "Enquanto é rascunho, a lista ainda não foi gerada; comprada ou parcialmente comprada só depois de gerada.",
                    "Abra o rascunho e use \"Gerar lista\", ou cancele.");
        }
        lista.setStatus(novo);
        listaCompraRepository.save(lista);
        return toResponse(lista, listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()));
    }

    private List<ListaCompraItem> montarItens(Usuario usuario, List<GerarListaCompraRequest.Linha> linhas, boolean exigirQuantidade) {
        if (linhas.isEmpty()) {
            throw BusinessException.explicado("Lista vazia", "Escolha pelo menos um insumo para gerar a lista.",
                    "A lista gerada é o retrato do que comprar; sem insumos não há o que listar.",
                    "Marque um filtro em \"O que entra na lista\" ou use \"Adicionar insumos\".");
        }
        List<ListaCompraItem> itens = new ArrayList<>();
        List<String> problemas = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        int ordem = 1;
        for (GerarListaCompraRequest.Linha linha : linhas) {
            Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(linha.insumoId(), usuario.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + linha.insumoId()));
            if (!Boolean.TRUE.equals(insumo.getAtivo())) {
                throw BusinessException.explicado("Insumo inativo", "O insumo " + insumo.getNome() + " está inativo e não pode entrar na lista.",
                        "Insumo inativo não é mais comprado.",
                        "Reative o insumo na tela de Insumos ou tire-o da lista.");
            }
            if (!vistos.add(insumo.getId())) {
                throw BusinessException.explicado("Insumo repetido", "O insumo " + insumo.getNome() + " está repetido na lista.",
                        "Cada insumo aparece uma vez na lista, com a quantidade total a comprar.",
                        "Some as quantidades numa linha só e remova a outra.");
            }
            boolean semQuantidade = linha.quantidade() == null || linha.quantidade().signum() <= 0;
            if (semQuantidade && (exigirQuantidade || linha.quantidade() != null)) {
                problemas.add("Linha " + ordem + " (" + insumo.getNome() + "): informe uma quantidade maior que zero");
            }
            Cliente fornecedor = linha.fornecedorId() == null ? null
                    : clienteService.resolverParaVinculo(linha.fornecedorId(), usuario.getId(), PapelCadastro.FORNECEDOR, null);
            BigDecimal preco = fornecedor == null ? null
                    : fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId())
                            .map(FornecedorInsumo::getPrecoReferencia).orElse(null);
            itens.add(ListaCompraItem.builder().insumo(insumo).insumoNome(insumo.getNome())
                    .unidade(insumo.getUnidadeMedida() != null ? insumo.getUnidadeMedida().getSigla() : null)
                    .estoqueAtual(insumo.getEstoqueAtual()).estoqueMinimo(insumo.getEstoqueMinimo())
                    .quantidade(linha.quantidade()).fornecedor(fornecedor)
                    .fornecedorNome(fornecedor != null ? fornecedor.getNome() : null)
                    .precoReferencia(preco).ordem(ordem++).build());
        }
        if (!problemas.isEmpty()) {
            throw BusinessException.explicado("Lista incompleta", "Não foi possível gerar a lista. " + String.join("; ", problemas) + ".",
                    "Cada linha precisa de uma quantidade para a lista dizer quanto comprar.",
                    "Preencha a quantidade das linhas apontadas (ex.: 12) ou remova a linha.")
                    .comItens(problemas);
        }
        return itens;
    }

    private ListaCompra novaLista(Usuario usuario, StatusListaCompra status) {
        usuarioRepository.lockPorId(usuario.getId());
        return listaCompraRepository.save(ListaCompra.builder().usuario(usuario).status(status)
                .numero(NumeroSequencialUtil.proximoNumero(
                        listaCompraRepository.findTopByUsuarioIdOrderByNumeroDesc(usuario.getId()).map(ListaCompra::getNumero)))
                .build());
    }

    private void substituirItens(ListaCompra lista, List<ListaCompraItem> itens) {
        listaCompraItemRepository.deleteAll(listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()));
        listaCompraItemRepository.flush();
        itens.forEach(i -> i.setLista(lista));
        listaCompraItemRepository.saveAll(itens);
    }

    private static void exigirRascunho(ListaCompra lista) {
        if (lista.getStatus() != StatusListaCompra.RASCUNHO) {
            throw BusinessException.explicado("Lista já gerada", "Só a lista em rascunho pode ser editada.",
                    "A lista gerada é um retrato do que comprar e não muda mais.",
                    "Crie uma Nova lista se precisar de outra seleção.");
        }
    }

    // #595/RN-NOVA-41 — allowlist de ordenação do histórico (padrão: mais recente primeiro).
    private static final Map<String, String> CAMPOS_ORDENACAO_LISTA = Map.of(
            "numero", "l.numero",
            "geradaEm", "l.geradaEm",
            "status", "l.status",
            "quantidadeItens", "(SELECT COUNT(i) FROM ListaCompraItem i WHERE i.lista = l)");

    @Transactional(readOnly = true)
    public Page<ListaCompraResumoResponse> historico(Pageable pageable) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        Pageable base = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "numero"));
        Sort comDesempate = base.getSort().getOrderFor("numero") != null ? base.getSort()
                : base.getSort().and(Sort.by(Sort.Direction.DESC, "numero"));
        Pageable ordenado = PageableOrdenacaoResolver.resolverExpressaoJpql(
                PageRequest.of(base.getPageNumber(), base.getPageSize(), comDesempate),
                CAMPOS_ORDENACAO_LISTA, "numero, geradaEm, status, quantidadeItens");
        Page<ListaCompra> pagina = listaCompraRepository.buscarDoUsuario(usuarioId, ordenado);
        Map<UUID, Long> contagem = pagina.isEmpty() ? Map.of()
                : listaCompraItemRepository.contarPorLista(pagina.map(ListaCompra::getId).getContent()).stream()
                        .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));
        return pagina.map(l -> new ListaCompraResumoResponse(l.getId(), l.getNumero(),
                IdentificadorFormatter.formatar("LST", l.getNumero()), l.getGeradaEm(), contagem.getOrDefault(l.getId(), 0L),
                l.getStatus(), l.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public ListaCompraResponse buscar(UUID id) {
        ListaCompra lista = buscarEntidade(id);
        return toResponse(lista, listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()));
    }

    /**
     * RN-NOVA-13 — rascunho novo (COM-N) com os insumos, quantidades e fornecedores sugeridos da lista,
     * preço vazio. Mais de um fornecedor entre as linhas (contando "sem fornecedor") → nasce em
     * "Múltiplos fornecedores". Pelo mesmo caminho do POST /compras (DT-NOVA-8): insumo ou fornecedor
     * inativado depois da geração bloqueia com a mensagem da compra. A lista não muda.
     */
    public CompraResponse criarCompra(UUID id) {
        ListaCompra lista = buscarEntidade(id);
        // #596/RN-NOVA-41 — só de lista gerada ainda não comprada por inteiro.
        if (lista.getStatus() != StatusListaCompra.GERADA && lista.getStatus() != StatusListaCompra.PARCIALMENTE_COMPRADA) {
            throw BusinessException.explicado("Lista sem compra pendente",
                    "Só é possível criar compra de uma lista Gerada ou Parcialmente comprada.",
                    "Rascunho ainda não foi gerado; lista Comprada ou Cancelada não tem mais o que comprar.",
                    "Gere o rascunho primeiro, ou mude o status da lista se ainda falta comprar algo.");
        }
        List<ListaCompraItem> itens = listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId());
        Set<UUID> fornecedores = itens.stream()
                .map(i -> i.getFornecedor() != null ? i.getFornecedor().getId() : null)
                .collect(Collectors.toCollection(HashSet::new));
        boolean multiplos = fornecedores.size() > 1;
        UUID cabecalho = multiplos ? null : fornecedores.iterator().next();

        List<CompraItemRequest> linhas = itens.stream()
                .map(i -> new CompraItemRequest(i.getInsumo().getId(),
                        i.getFornecedor() != null ? i.getFornecedor().getId() : null, i.getQuantidade(), null))
                .toList();
        CompraResponse rascunho = compraService.criarRascunho(new CompraRequest(LocalDate.now(), multiplos, cabecalho, false, null, null, linhas));
        compraService.vincularLista(rascunho.id(), lista);
        return compraService.buscar(rascunho.id());
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean abaixoDoMinimo(Insumo i) {
        return i.getEstoqueMinimo() != null && i.getEstoqueAtual().compareTo(i.getEstoqueMinimo()) < 0;
    }

    /**
     * mínimo − atual quando positivo; sem mínimo e estoque negativo = o déficit; senão vazio.
     */
    static BigDecimal quantidadeSugerida(Insumo i) {
        if (i.getEstoqueMinimo() != null) {
            BigDecimal falta = i.getEstoqueMinimo().subtract(i.getEstoqueAtual());
            return falta.signum() > 0 ? falta : null;
        }
        return i.getEstoqueAtual().signum() < 0 ? i.getEstoqueAtual().negate() : null;
    }

    private static boolean fornecedorValido(Cliente f) {
        return Boolean.TRUE.equals(f.getAtiva()) && Boolean.TRUE.equals(f.getEhFornecedor());
    }

    /**
     * Com filtro de fornecedor: o próprio fornecedor filtrado. Sem filtro: o de menor preço de
     * referência entre os vínculos válidos (empate: nome); nenhum com preço → null (cai no da compra
     * confirmada mais recente).
     */
    private static FornecedorInsumo sugerir(List<FornecedorInsumo> vinculos, UUID fornecedorFiltro) {
        if (fornecedorFiltro != null) {
            return vinculos.stream().filter(v -> v.getFornecedor().getId().equals(fornecedorFiltro)).findFirst().orElse(null);
        }
        return vinculos.stream().filter(v -> v.getPrecoReferencia() != null)
                .min(Comparator.comparing(FornecedorInsumo::getPrecoReferencia)
                        .thenComparing(v -> v.getFornecedor().getNome(), String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    /** Fornecedor (válido) da linha de compra CONFIRMADA mais recente de cada insumo. */
    private Map<UUID, Cliente> fornecedorDaUltimaCompra(UUID usuarioId, Collection<UUID> insumoIds) {
        Map<UUID, Cliente> resultado = new LinkedHashMap<>();
        compraItemRepository.findPorInsumosEStatus(usuarioId, insumoIds, StatusCompra.CONFIRMADA).stream()
                .filter(i -> i.getFornecedor() != null && fornecedorValido(i.getFornecedor()))
                .sorted(Comparator.comparing((CompraItem i) -> i.getCompra().getDataCompra())
                        .thenComparing(i -> i.getCompra().getNumero()).reversed())
                .forEach(i -> resultado.putIfAbsent(i.getInsumo().getId(), i.getFornecedor()));
        return resultado;
    }

    private ListaCompraResponse toResponse(ListaCompra lista, List<ListaCompraItem> itens) {
        return new ListaCompraResponse(lista.getId(), lista.getNumero(),
                IdentificadorFormatter.formatar("LST", lista.getNumero()), lista.getGeradaEm(), lista.getStatus(),
                itens.stream().map(i -> new ListaCompraResponse.Item(i.getOrdem(), i.getInsumo().getId(), i.getInsumoNome(),
                        i.getUnidade(), i.getEstoqueAtual(), i.getEstoqueMinimo(), i.getQuantidade(),
                        i.getFornecedor() != null ? i.getFornecedor().getId() : null, i.getFornecedorNome(),
                        i.getPrecoReferencia())).toList());
    }

    ListaCompra buscarEntidade(UUID id) {
        return listaCompraRepository.findByIdAndUsuarioId(id, getUsuarioAutenticado().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lista de compras não encontrada: " + id));
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
