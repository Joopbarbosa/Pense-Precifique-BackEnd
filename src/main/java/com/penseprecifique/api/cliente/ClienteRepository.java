package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * #538/DT-NOVA-2 (V0.15.0) — sem deleted_at, duas famílias de consulta:
 * <ul>
 *   <li>"para selecionar" (listagem/seletores): filtra ativo e papel — {@link #buscarComFiltros};</li>
 *   <li>"para exibir vínculo existente" (detalhe, carregar orçamento/venda/compra): sem filtro de
 *   ativo nem de papel — {@link #findByIdAndUsuarioId}. Corrige o 404 ao editar orçamento de
 *   cliente inativado.</li>
 * </ul>
 */
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    // CAST(:busca AS string) obrigatório — mesmo motivo de InsumoRepository#buscarComFiltros
    // (parâmetro nulo inferido como bytea). Busca por nome ou por documento (sem máscara).
    @Query("SELECT c FROM Cliente c WHERE c.usuario.id = :usuarioId " +
            "AND c.ativa = :ativo " +
            "AND (:somenteClientes = false OR c.ehCliente = true) " +
            "AND (:somenteFornecedores = false OR c.ehFornecedor = true) " +
            "AND (:busca IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')) " +
            "     OR (:buscaDocumento IS NOT NULL AND c.documento LIKE CONCAT('%', CAST(:buscaDocumento AS string), '%')))")
    Page<Cliente> buscarComFiltros(@Param("usuarioId") UUID usuarioId,
                                   @Param("busca") String busca,
                                   @Param("buscaDocumento") String buscaDocumento,
                                   @Param("ativo") boolean ativo,
                                   @Param("somenteClientes") boolean somenteClientes,
                                   @Param("somenteFornecedores") boolean somenteFornecedores,
                                   Pageable pageable);

    Optional<Cliente> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<Cliente> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    Optional<Cliente> findByUsuarioIdAndDocumento(UUID usuarioId, String documento);

    long countByUsuarioIdAndAtiva(UUID usuarioId, Boolean ativa);

    long countByUsuarioIdAndAtivaTrueAndEhClienteTrue(UUID usuarioId);

    long countByUsuarioIdAndAtivaTrueAndEhFornecedorTrue(UUID usuarioId);
}
