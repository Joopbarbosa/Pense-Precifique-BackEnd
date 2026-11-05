package com.penseprecifique.api.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {

    Optional<CategoriaFinanceira> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<CategoriaFinanceira> findByUsuarioIdAndNomeNormalizado(UUID usuarioId, String nomeNormalizado);

    boolean existsByUsuarioIdAndNomeNormalizado(UUID usuarioId, String nomeNormalizado);

    List<CategoriaFinanceira> findByUsuarioIdOrderBySemeadaDescNomeAsc(UUID usuarioId);

    List<CategoriaFinanceira> findByUsuarioIdAndAtivaTrueOrderBySemeadaDescNomeAsc(UUID usuarioId);

    /** Idempotente e seguro sob concorrência: a segunda inserção do mesmo nome é ignorada pelo índice único. */
    @Modifying
    @Query(value = "INSERT INTO categorias_financeiras (id, usuario_id, nome, nome_normalizado, semeada, ativa, created_at, updated_at) "
            + "VALUES (:id, :usuarioId, :nome, :normalizado, TRUE, TRUE, now(), now()) "
            + "ON CONFLICT (usuario_id, nome_normalizado) DO NOTHING", nativeQuery = true)
    int semear(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId, @Param("nome") String nome, @Param("normalizado") String normalizado);
}
