package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.DuplicarCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.CatalogoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CatalogoMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CatalogoService {

    private final CatalogoRepository catalogoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoCustomizacaoRepository customizacaoRepository;
    private final CatalogoMapper catalogoMapper;
    private final ItemCatalogoService itemCatalogoService;
    private final UsuarioRepository usuarioRepository;

    // ---------------------------------------------------------------
    // Consultas
    // ---------------------------------------------------------------

    /**
     * RN-057 — busca por nome (case-insensitive, vazio/nulo retorna tudo — RN-055) e ordenação
     * clicável por coluna. `quantidadeItens` não é coluna persistida (calculado em toResponse via
     * count), então a ordenação é feita em memória sobre a lista já mapeada, uniforme pros 4 campos.
     */
    @Transactional(readOnly = true)
    public List<CatalogoResponse> listar(String busca, String ordenarPor, String direcao) {
        UUID usuarioId = getUsuarioIdAutenticado();

        List<Catalogo> catalogos = (busca != null && !busca.isBlank())
                ? catalogoRepository.findByUsuarioIdAndNomeContainingIgnoreCase(usuarioId, busca.trim())
                : catalogoRepository.findByUsuarioId(usuarioId);

        List<CatalogoResponse> resultado = new ArrayList<>(catalogos.stream()
                .map(this::toResponse)
                .toList());

        Comparator<CatalogoResponse> comparador = resolverComparador(ordenarPor);
        if (comparador != null) {
            if ("DESC".equalsIgnoreCase(direcao)) {
                comparador = comparador.reversed();
            }
            resultado.sort(comparador);
        }

        return resultado;
    }

    private Comparator<CatalogoResponse> resolverComparador(String ordenarPor) {
        if (ordenarPor == null) {
            return null;
        }
        return switch (ordenarPor) {
            case "numero" -> Comparator.comparing(CatalogoResponse::getNumero, Comparator.nullsLast(Integer::compareTo));
            case "nome" -> Comparator.comparing(CatalogoResponse::getNome, String.CASE_INSENSITIVE_ORDER);
            case "margem" -> Comparator.comparing(CatalogoResponse::getMargem, Comparator.nullsLast(BigDecimal::compareTo));
            case "quantidadeItens" -> Comparator.comparing(CatalogoResponse::getQuantidadeItens, Comparator.nullsLast(Integer::compareTo));
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public CatalogoResponse buscarPorId(UUID id) {
        return toResponse(buscarCatalogo(id, getUsuarioIdAutenticado()));
    }

    // ---------------------------------------------------------------
    // Escrita
    // ---------------------------------------------------------------

    public CatalogoResponse cadastrar(CatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        validarMargem(request.getMargem()); // RN-041
        validarNomeUnico(usuarioId, request.getNome(), null); // RN-040

        Catalogo catalogo = catalogoMapper.toEntity(request, getUsuarioAutenticado());
        catalogo.setNumero(proximoNumero(usuarioId)); // RN-053
        catalogo.setAtivo(true);
        catalogo = catalogoRepository.save(catalogo);

        return catalogoMapper.toResponse(catalogo, 0);
    }

    public CatalogoResponse editar(UUID id, CatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Catalogo catalogo = buscarCatalogo(id, usuarioId);

        validarMargem(request.getMargem()); // RN-041
        validarNomeUnico(usuarioId, request.getNome(), catalogo); // RN-040

        BigDecimal margemAntiga = catalogo.getMargem();
        catalogoMapper.updateEntity(request, catalogo);
        catalogo = catalogoRepository.save(catalogo);

        // RN-042 — mudar a margem recalcula preco_venda de todo item sem override
        if (margemAntiga.compareTo(catalogo.getMargem()) != 0) {
            for (ItemCatalogo item : itemCatalogoRepository.findByCatalogoIdAndDeletedAtIsNull(catalogo.getId())) {
                if (!Boolean.TRUE.equals(item.getOverride())) {
                    itemCatalogoService.recalcularPrecoVendaPorMargem(item, catalogo.getMargem());
                }
            }
        }

        return toResponse(catalogo);
    }

    /** RN-046 — desativar bloqueia a venda dos itens; não altera os itens em si. */
    public CatalogoResponse desativar(UUID id) {
        return alterarAtivo(id, false);
    }

    public CatalogoResponse reativar(UUID id) {
        return alterarAtivo(id, true);
    }

    /** RN-047 — duplica nome, margem e todos os itens preservando overrides e preços exatos; gera número novo (RN-053). */
    public CatalogoResponse duplicar(UUID id, DuplicarCatalogoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Catalogo original = buscarCatalogo(id, usuarioId);

        String novoNome = (request != null && request.getNovoNome() != null && !request.getNovoNome().isBlank())
                ? request.getNovoNome().trim()
                : original.getNome() + " (cópia)";
        validarNomeUnico(usuarioId, novoNome, null);

        Catalogo copia = Catalogo.builder()
                .usuario(original.getUsuario())
                .numero(proximoNumero(usuarioId))
                .nome(novoNome)
                .margem(original.getMargem())
                .ativo(true)
                .build();
        copia = catalogoRepository.save(copia);

        List<ItemCatalogo> itensOriginais = itemCatalogoRepository.findByCatalogoIdAndDeletedAtIsNull(original.getId());
        for (ItemCatalogo itemOriginal : itensOriginais) {
            ItemCatalogo novoItem = ItemCatalogo.builder()
                    .catalogo(copia)
                    .produto(itemOriginal.getProduto())
                    .quantidadePacote(itemOriginal.getQuantidadePacote())
                    .precoVenda(itemOriginal.getPrecoVenda())   // preço exato, não recalcula
                    .override(itemOriginal.getOverride())        // preserva override (RN-047)
                    .build();
            novoItem = itemCatalogoRepository.save(novoItem);

            for (ItemCatalogoCustomizacao custom : customizacaoRepository.findByItemCatalogoId(itemOriginal.getId())) {
                customizacaoRepository.save(ItemCatalogoCustomizacao.builder()
                        .itemCatalogo(novoItem)
                        .produto(custom.getProduto())
                        .quantidade(custom.getQuantidade())
                        .build());
            }
        }

        return catalogoMapper.toResponse(copia, itensOriginais.size());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private CatalogoResponse alterarAtivo(UUID id, boolean ativo) {
        Catalogo catalogo = buscarCatalogo(id, getUsuarioIdAutenticado());
        catalogo.setAtivo(ativo);
        return toResponse(catalogoRepository.save(catalogo));
    }

    private CatalogoResponse toResponse(Catalogo catalogo) {
        long quantidadeItens = itemCatalogoRepository.countByCatalogoIdAndDeletedAtIsNull(catalogo.getId());
        return catalogoMapper.toResponse(catalogo, (int) quantidadeItens);
    }

    private void validarMargem(BigDecimal margem) {
        if (margem == null || margem.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("A margem deve ser maior que zero.");
        }
    }

    private void validarNomeUnico(UUID usuarioId, String nome, Catalogo atual) {
        boolean nomeMudou = atual == null || !atual.getNome().equalsIgnoreCase(nome);
        if (nomeMudou && catalogoRepository.existsByUsuarioIdAndNomeIgnoreCase(usuarioId, nome)) {
            throw new BusinessException("Já existe um catálogo com esse nome.");
        }
    }

    private Catalogo buscarCatalogo(UUID id, UUID usuarioId) {
        return catalogoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));
    }

    /** #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition. */
    private Integer proximoNumero(UUID usuarioId) {
        usuarioRepository.lockPorId(usuarioId);
        return catalogoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId)
                .map(c -> c.getNumero() != null ? c.getNumero() + 1 : 1)
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
