package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InsumoRepository extends JpaRepository<Insumo, UUID> {

    // #336 (V0.10.0) — causa raiz do bug original ("insumo inativado não aparece no filtro de
    // inativados"): filtro de status era aplicado client-side sobre a janela paginada, violando a
    // convenção do projeto (busca sempre server-side). `ativo`/`busca` nulos = sem filtro (mesmo
    // resultado das duas queries derivadas acima, unificadas aqui para não duplicar combinação).
    // CAST(:busca AS string) obrigatório — sem ele, Hibernate/Postgres infere o parâmetro nulo
    // como bytea e `lower(bytea)` falha em runtime (só reproduz com busca=null, testado no fix).
    @Query("SELECT i FROM Insumo i WHERE i.usuario.id = :usuarioId AND i.deletedAt IS NULL " +
            "AND (:busca IS NULL OR LOWER(i.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%'))) " +
            "AND (:ativo IS NULL OR i.ativo = :ativo)")
    Page<Insumo> buscarComFiltros(@Param("usuarioId") UUID usuarioId, @Param("busca") String busca,
            @Param("ativo") Boolean ativo, Pageable pageable);

    // #298 (V0.14.0) — unidadeMedida agora é @ManyToOne LAZY; EntityGraph evita depender de sessão
    // aberta em quem consumir o retorno fora da transação de origem (ex.: toResponse() logo após).
    @EntityGraph(attributePaths = "unidadeMedida")
    Optional<Insumo> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId);

    boolean existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
            String nome, String marca, UUID usuarioId, UUID id);

    Optional<Insumo> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    // RN-NOVA-4 (V0.10.0, #336) — contadores por filtro, agregados no backend (não sobre a janela
    // paginada já carregada no cliente). Mesmos critérios exatos já usados client-side em
    // ListaInsumosPage.tsx (isLow/isNegative/isPositive), replicados aqui como fonte de verdade.
    long countByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);

    long countByUsuarioIdAndAtivoAndDeletedAtIsNull(UUID usuarioId, boolean ativo);

    @Query("SELECT COUNT(i) FROM Insumo i WHERE i.usuario.id = :usuarioId AND i.deletedAt IS NULL " +
            "AND i.ativo = true AND i.estoqueMinimo IS NOT NULL AND i.estoqueAtual < i.estoqueMinimo")
    long contarEstoqueBaixo(@Param("usuarioId") UUID usuarioId);

    long countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualLessThan(UUID usuarioId, java.math.BigDecimal valor);

    long countByUsuarioIdAndDeletedAtIsNullAndEstoqueAtualGreaterThan(UUID usuarioId, java.math.BigDecimal valor);

    // #298 (CEN-NOVO-9) — bloqueio de exclusão de UnidadeMedida em uso: basta 1 insumo vinculado.
    long countByUnidadeMedidaIdAndDeletedAtIsNull(UUID unidadeMedidaId);
}
