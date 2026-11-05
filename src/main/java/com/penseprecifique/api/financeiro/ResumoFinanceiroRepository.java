package com.penseprecifique.api.financeiro;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * V0.19.0 (#1176, DT-NOVA-6) — consultas agregadas, somente leitura, do resumo financeiro. Tudo filtrado pela usuária.
 * Não escreve nada nem altera o Caixa: só lê vendas e turnos.
 */
public interface ResumoFinanceiroRepository extends Repository<Parcela, UUID> {

    /** Baixas do tipo no período, pela data da baixa. Parcela Cancelada nunca entra (situação BAIXADA apenas). */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.BAIXADA AND p.dataBaixa BETWEEN :de AND :ate")
    BigDecimal somaBaixas(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                          @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Baixas por dia (data, soma). */
    @Query("SELECT p.dataBaixa, SUM(p.valor) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.BAIXADA AND p.dataBaixa BETWEEN :de AND :ate " +
            "GROUP BY p.dataBaixa")
    List<Object[]> baixasPorDia(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                                @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Vendas do Caixa CONCLUÍDAS no período [de, ateExclusivo): soma do total líquido (o troco não conta). */
    @Query("SELECT COALESCE(SUM(v.total), 0) FROM VendaCaixa v WHERE v.usuario.id = :usuarioId " +
            "AND v.status = com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa.CONCLUIDA " +
            "AND v.dataVenda >= :de AND v.dataVenda < :ateExclusivo")
    BigDecimal somaVendasCaixa(@Param("usuarioId") UUID usuarioId, @Param("de") LocalDateTime de,
                               @Param("ateExclusivo") LocalDateTime ateExclusivo);

    /** Vendas do Caixa CONCLUÍDAS por dia (data, soma). */
    @Query("SELECT CAST(v.dataVenda AS LocalDate), SUM(v.total) FROM VendaCaixa v WHERE v.usuario.id = :usuarioId " +
            "AND v.status = com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa.CONCLUIDA " +
            "AND v.dataVenda >= :de AND v.dataVenda < :ateExclusivo GROUP BY CAST(v.dataVenda AS LocalDate)")
    List<Object[]> vendasCaixaPorDia(@Param("usuarioId") UUID usuarioId, @Param("de") LocalDateTime de,
                                     @Param("ateExclusivo") LocalDateTime ateExclusivo);

    /** Valores de abertura dos turnos abertos no período, do mais antigo para o mais novo (RN-NOVA-19 usa o primeiro). */
    @Query("SELECT t.valorAbertura FROM CaixaTurno t WHERE t.usuario.id = :usuarioId " +
            "AND t.dataAbertura >= :de AND t.dataAbertura < :ateExclusivo ORDER BY t.dataAbertura ASC, t.id ASC")
    List<BigDecimal> aberturasDoPeriodo(@Param("usuarioId") UUID usuarioId, @Param("de") LocalDateTime de,
                                        @Param("ateExclusivo") LocalDateTime ateExclusivo, Pageable pageable);

    /** Previsto do período: parcelas com vencimento no período, Em aberto ou baixadas, de lançamento que não está fora da projeção. */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao <> com.penseprecifique.api.financeiro.SituacaoParcela.CANCELADA AND p.lancamento.foraProjecao = false " +
            "AND p.vencimento BETWEEN :de AND :ate")
    BigDecimal somaPrevista(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo,
                            @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** Parcelas Em aberto que entram na projeção (não "fora da projeção"), com vencimento até a data, por vencimento (inclui vencidas). */
    @Query("SELECT p.vencimento, SUM(p.valor) FROM Parcela p WHERE p.usuario.id = :usuarioId AND p.lancamento.tipo = :tipo " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.lancamento.foraProjecao = false " +
            "AND p.vencimento <= :ate GROUP BY p.vencimento")
    List<Object[]> emAbertoProjetadoAte(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoLancamento tipo, @Param("ate") LocalDate ate);

    /** Despesas realizadas do período por categoria gravada. O estorno de sinal não tem categoria e fica de fora (RN-NOVA-23). */
    @Query("SELECT c.id, c.nome, SUM(p.valor) FROM Parcela p JOIN p.lancamento l JOIN l.categoria c WHERE p.usuario.id = :usuarioId " +
            "AND l.tipo = com.penseprecifique.api.financeiro.TipoLancamento.A_PAGAR " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.BAIXADA AND p.dataBaixa BETWEEN :de AND :ate " +
            "GROUP BY c.id, c.nome")
    List<Object[]> despesasPorCategoria(@Param("usuarioId") UUID usuarioId, @Param("de") LocalDate de, @Param("ate") LocalDate ate);

    /** RN-NOVA-21 — Em aberto (pagar e receber) vencidas ou até o limite; ordem estável: vencimento, maior valor, lançamento criado primeiro, número, id. */
    @EntityGraph(attributePaths = {"lancamento", "metodoPagamento"})
    @Query(value = "SELECT p FROM Parcela p WHERE p.usuario.id = :usuarioId " +
            "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.vencimento <= :limite " +
            "ORDER BY p.vencimento ASC, p.valor DESC, p.lancamento.createdAt ASC, p.numero ASC, p.id ASC",
            countQuery = "SELECT COUNT(p) FROM Parcela p WHERE p.usuario.id = :usuarioId " +
                    "AND p.situacao = com.penseprecifique.api.financeiro.SituacaoParcela.EM_ABERTO AND p.vencimento <= :limite")
    Page<Parcela> proximosVencimentos(@Param("usuarioId") UUID usuarioId, @Param("limite") LocalDate limite, Pageable pageable);
}
