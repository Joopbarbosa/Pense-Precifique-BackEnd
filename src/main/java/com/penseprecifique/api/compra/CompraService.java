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
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.PagamentoCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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

    private static final Map<String, String> CAMPOS_ORDENACAO = Map.of(
            "dataCompra", "dataCompra",
            "numero", "numero",
            "createdAt", "createdAt");

    static final String MSG_INSUMO_INATIVO = "Este insumo está inativo e não pode ser adicionado. Reative-o para continuar.";
    static final String MSG_SEM_METODO = "Escolha como a compra foi paga.";

    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;
    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteService clienteService;
    private final FornecedorInsumoService fornecedorInsumoService;
    private final CompraMapper compraMapper;

    // --------------------------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public Page<CompraResumoResponse> listar(StatusCompra status, UUID fornecedorId, LocalDate de, LocalDate ate,
                                             Pageable pageable) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        Pageable ordenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO, "dataCompra, numero, createdAt");
        Page<Compra> pagina = compraRepository.buscarComFiltros(usuarioId, status, fornecedorId != null,
                fornecedorId != null ? fornecedorId : new UUID(0, 0), de, ate, ordenado);

        Map<UUID, List<CompraItem>> itensPorCompra = pagina.isEmpty() ? Map.of()
                : compraItemRepository.findByCompraIdIn(pagina.map(Compra::getId).getContent()).stream()
                        .collect(Collectors.groupingBy(i -> i.getCompra().getId()));
        List<CompraResumoResponse> conteudo = pagina.getContent().stream()
                .map(c -> compraMapper.toResumo(c, itensPorCompra.getOrDefault(c.getId(), List.of())))
                .toList();
        return new PageImpl<>(conteudo, pageable, pagina.getTotalElements());
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
    public CompraResponse confirmarNova(CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = novaCompra(usuario);
        aplicarRequest(compra, request, usuario.getId(), List.of());
        return confirmarEntidade(compra, usuario);
    }

    /** Confirma um rascunho, gravando antes as alterações do request (se enviado). Tudo ou nada. */
    public CompraResponse confirmar(UUID id, CompraRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = buscarEntidade(id, usuario.getId());
        exigirRascunho(compra, "Só é possível confirmar uma compra em Rascunho.");
        if (request != null) {
            aplicarRequest(compra, request, usuario.getId(), compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()));
        }
        return confirmarEntidade(compra, usuario);
    }

    private CompraResponse confirmarEntidade(Compra compra, Usuario usuario) {
        List<CompraItem> itens = compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId());
        validarParaConfirmar(compra, itens);

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

            if (item.getFornecedor() != null) {
                fornecedorInsumoService.registrarPrecoDaCompra(usuario, item.getFornecedor(), insumo, precoPago);
            }
        }

        compra.setStatus(StatusCompra.CONFIRMADA);
        compra.setConfirmadaEm(LocalDateTime.now());
        compraRepository.save(compra);
        return compraMapper.toResponse(compra, itens);
    }

    /**
     * RN-NOVA-4 — tudo ou nada: qualquer linha inválida impede a confirmação inteira, e a mensagem
     * aponta cada linha com problema. Fornecedor inativado desde o rascunho não impede (RN-NOVA-2).
     */
    private void validarParaConfirmar(Compra compra, List<CompraItem> itens) {
        if (itens.isEmpty()) {
            throw new BusinessException("Adicione pelo menos um insumo para confirmar a compra.");
        }
        validarData(compra.getDataCompra());
        List<String> problemas = new ArrayList<>();
        for (int i = 0; i < itens.size(); i++) {
            CompraItem item = itens.get(i);
            List<String> daLinha = new ArrayList<>();
            if (item.getQuantidade() == null) {
                daLinha.add("informe a quantidade");
            }
            if (item.getPrecoTotal() == null) {
                daLinha.add("informe o preço total pago");
            }
            if (!Boolean.TRUE.equals(item.getInsumo().getAtivo()) || item.getInsumo().getDeletedAt() != null) {
                daLinha.add("o insumo está inativo");
            }
            if (!daLinha.isEmpty()) {
                problemas.add("Linha " + (i + 1) + " (" + item.getInsumo().getNome() + "): " + String.join(", ", daLinha));
            }
        }
        if (!problemas.isEmpty()) {
            throw new BusinessException("Não foi possível confirmar a compra. " + String.join("; ", problemas) + ".");
        }
    }

    // --------------------------------------------------------------------------------- pagamento

    /** #550 + S2 (Decisão 14) — em CONFIRMADA, Pago/Não pago (e o método) é o único campo editável. */
    public CompraResponse atualizarPagamento(UUID id, PagamentoCompraRequest request) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        Compra compra = buscarEntidade(id, usuarioId);
        if (compra.getStatus() != StatusCompra.CONFIRMADA) {
            throw new BusinessException(compra.getStatus() == StatusCompra.RASCUNHO
                    ? "Em rascunho, o pagamento é alterado junto com a compra."
                    : "Compra cancelada não pode ter o pagamento alterado.");
        }
        aplicarPagamento(compra, request.pago(), request.metodoPagamentoId(), usuarioId);
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
        aplicarPagamento(compra, Boolean.TRUE.equals(request.pago()), request.metodoPagamentoId(), usuarioId);

        List<CompraItem> novos = new ArrayList<>();
        Set<String> pares = new HashSet<>();
        int ordem = 1;
        for (CompraItemRequest linha : request.itensOuVazio()) {
            Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(linha.insumoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + linha.insumoId()));
            if (!Boolean.TRUE.equals(insumo.getAtivo()) && !insumosSalvos.contains(insumo.getId())) {
                throw new BusinessException(MSG_INSUMO_INATIVO);
            }
            Cliente fornecedor = multiplos
                    ? resolverFornecedor(linha.fornecedorId(), usuarioId, fornecedoresSalvos)
                    : fornecedorCabecalho;
            String par = insumo.getId() + "|" + (fornecedor != null ? fornecedor.getId() : "-");
            if (!pares.add(par)) {
                throw new BusinessException("O insumo " + insumo.getNome() + " está repetido com o mesmo fornecedor nesta compra.");
            }
            novos.add(CompraItem.builder().compra(compra).insumo(insumo).fornecedor(fornecedor)
                    .quantidade(linha.quantidade()).precoTotal(linha.precoTotal()).ordem(ordem++).build());
        }

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
    private void aplicarPagamento(Compra compra, boolean pago, UUID metodoPagamentoId, UUID usuarioId) {
        if (!pago) {
            compra.setPago(false);
            compra.setMetodoPagamento(null);
            return;
        }
        if (metodoPagamentoId == null) {
            throw new BusinessException(MSG_SEM_METODO);
        }
        MetodoPagamentoConfiguravel metodo = metodoPagamentoRepository.findByIdAndUsuarioId(metodoPagamentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não encontrado: " + metodoPagamentoId));
        boolean jaSalvo = compra.getMetodoPagamento() != null && compra.getMetodoPagamento().getId().equals(metodoPagamentoId);
        if (!jaSalvo && !Boolean.TRUE.equals(metodo.getAtivo())) {
            throw new BusinessException("Este método de pagamento está inativo.");
        }
        compra.setPago(true);
        compra.setMetodoPagamento(metodo);
    }

    private static void validarData(LocalDate dataCompra) {
        if (dataCompra == null) {
            throw new BusinessException("Informe a data da compra");
        }
        // Compra agendada é #549 (fora desta versão).
        if (dataCompra.isAfter(LocalDate.now())) {
            throw new BusinessException("A data da compra não pode ser futura");
        }
    }

    private static void exigirRascunho(Compra compra, String mensagem) {
        if (compra.getStatus() != StatusCompra.RASCUNHO) {
            throw new BusinessException(mensagem);
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
