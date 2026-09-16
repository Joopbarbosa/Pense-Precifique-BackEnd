package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository extends JpaRepository<Orcamento, UUID> {

    /**
     * Frente 3/P-BE-CONSOLIDADO-001 — substitui as 4 combinações antigas de derived query
     * (status x busca) por uma única query com condições opcionais, já incluindo o filtro novo de
     * intervalo de createdAt (dataCriacaoDe/dataCriacaoAte). Mesmo padrão de
     * ProducaoRepository.buscarIdsOrdenados: CAST só na checagem "IS NULL" (Postgres não infere tipo
     * de parâmetro usado sozinho numa comparação sem contexto de coluna), nunca na comparação real.
     * dataCriacaoDe/dataCriacaoAte chegam do Service já convertidos pra LocalDateTime (início/fim do
     * dia), não LocalDate cru — createdAt é timestamp, comparação direta LocalDate x LocalDateTime
     * não é portável em JPQL.
     */
    @Query(value = """
            SELECT o FROM Orcamento o
            WHERE o.usuario.id = :usuarioId
            AND o.deletedAt IS NULL
            AND (:status IS NULL OR o.status = :status)
            AND (CAST(:busca AS string) IS NULL OR LOWER(o.cliente.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            AND (CAST(:dataCriacaoDeInicio AS timestamp) IS NULL OR o.createdAt >= :dataCriacaoDeInicio)
            AND (CAST(:dataCriacaoAteFim AS timestamp) IS NULL OR o.createdAt <= :dataCriacaoAteFim)
            """,
            countQuery = """
            SELECT COUNT(o) FROM Orcamento o
            WHERE o.usuario.id = :usuarioId
            AND o.deletedAt IS NULL
            AND (:status IS NULL OR o.status = :status)
            AND (CAST(:busca AS string) IS NULL OR LOWER(o.cliente.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
            AND (CAST(:dataCriacaoDeInicio AS timestamp) IS NULL OR o.createdAt >= :dataCriacaoDeInicio)
            AND (CAST(:dataCriacaoAteFim AS timestamp) IS NULL OR o.createdAt <= :dataCriacaoAteFim)
            """)
    Page<Orcamento> buscar(@Param("usuarioId") UUID usuarioId,
                            @Param("status") StatusOrcamento status,
                            @Param("busca") String busca,
                            @Param("dataCriacaoDeInicio") LocalDateTime dataCriacaoDeInicio,
                            @Param("dataCriacaoAteFim") LocalDateTime dataCriacaoAteFim,
                            Pageable pageable);

    Optional<Orcamento> findByIdAndUsuarioIdAndDeletedAtIsNull(UUID id, UUID usuarioId);

    Optional<Orcamento> findTopByUsuarioIdOrderByNumeroDesc(UUID usuarioId);

    long countByUsuarioIdAndDeletedAtIsNull(UUID usuarioId);

    long countByUsuarioIdAndStatusInAndDeletedAtIsNull(UUID usuarioId, List<StatusOrcamento> statuses);

    // DT-NOVA-4 (V0.10.0, #466) — receita usava status = PAGO (igualdade exata) para achar
    // orçamentos pagos. Com RN-NOVA-10 (PAGO deixa de ser terminal, ENTREGUE vem depois), isso
    // faria a receita cair assim que um orçamento avançasse pra ENTREGUE — troca por dataPagamento
    // IS NOT NULL, setado uma única vez na transição pra PAGO e nunca sobrescrito (mesmo motivo
    // que já mudou o preview de documentos no frontend). sumTotalPagoNoPeriodo também troca
    // updatedAt por dataPagamento — updatedAt muda de novo quando o orçamento avança pra ENTREGUE,
    // o que atribuiria a receita ao mês errado.
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Orcamento o WHERE o.usuario.id = :uid AND o.dataPagamento IS NOT NULL AND o.deletedAt IS NULL")
    BigDecimal sumTotalPago(@Param("uid") UUID uid);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Orcamento o WHERE o.usuario.id = :uid AND o.dataPagamento >= :inicio AND o.dataPagamento < :fim AND o.deletedAt IS NULL")
    BigDecimal sumTotalPagoNoPeriodo(@Param("uid") UUID uid, @Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    List<Orcamento> findTop5ByUsuarioIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID usuarioId);
}
