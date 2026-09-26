package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompraRepository extends JpaRepository<Compra, UUID> {

    /** RN-053 — inclui excluídas (soft delete): o COM-N de um rascunho excluído nunca volta. */
    Optional<Compra> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    @EntityGraph(attributePaths = {"fornecedor", "metodoPagamento"})
    Optional<Compra> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    /**
     * Listagem de Minhas compras. O fornecedor casa o do cabeçalho ou o de qualquer linha (modo
     * múltiplos fornecedores). Datas com CAST (mesmo padrão de OrcamentoRepository#buscar); o filtro
     * de fornecedor usa flag em vez de UUID nulo, porque o Postgres não infere o tipo de um parâmetro
     * nulo usado só em "IS NULL".
     */
    @EntityGraph(attributePaths = {"fornecedor", "metodoPagamento"})
    @Query(value = "SELECT c FROM Compra c LEFT JOIN c.fornecedor f WHERE c.usuario.id = :usuarioId AND c.deletedAt IS NULL " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (CAST(:de AS date) IS NULL OR c.dataCompra >= :de) " +
            "AND (CAST(:ate AS date) IS NULL OR c.dataCompra <= :ate) " +
            "AND (:filtrarFornecedor = false OR f.id = :fornecedorId " +
            "     OR EXISTS (SELECT 1 FROM CompraItem ci WHERE ci.compra = c AND ci.fornecedor.id = :fornecedorId))",
            countQuery = "SELECT COUNT(c) FROM Compra c LEFT JOIN c.fornecedor f WHERE c.usuario.id = :usuarioId AND c.deletedAt IS NULL " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (CAST(:de AS date) IS NULL OR c.dataCompra >= :de) " +
            "AND (CAST(:ate AS date) IS NULL OR c.dataCompra <= :ate) " +
            "AND (:filtrarFornecedor = false OR f.id = :fornecedorId " +
            "     OR EXISTS (SELECT 1 FROM CompraItem ci WHERE ci.compra = c AND ci.fornecedor.id = :fornecedorId))")
    Page<Compra> buscarComFiltros(@Param("usuarioId") UUID usuarioId,
                                  @Param("status") StatusCompra status,
                                  @Param("filtrarFornecedor") boolean filtrarFornecedor,
                                  @Param("fornecedorId") UUID fornecedorId,
                                  @Param("de") LocalDate de,
                                  @Param("ate") LocalDate ate,
                                  Pageable pageable);

    /** Compras (não excluídas) em que o cadastro é fornecedor no cabeçalho ou em alguma linha. */
    @Query("SELECT c FROM Compra c LEFT JOIN c.fornecedor f WHERE c.usuario.id = :usuarioId AND c.deletedAt IS NULL " +
            "AND (f.id = :fornecedorId " +
            "     OR EXISTS (SELECT 1 FROM CompraItem ci WHERE ci.compra = c AND ci.fornecedor.id = :fornecedorId))")
    List<Compra> findDoFornecedor(@Param("usuarioId") UUID usuarioId, @Param("fornecedorId") UUID fornecedorId);
}
