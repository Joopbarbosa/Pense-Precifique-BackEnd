package com.penseprecifique.api.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MudancaPrecoSugeridoInsumoRepository extends JpaRepository<MudancaPrecoSugeridoInsumo, UUID> {

    /**
     * Vínculos de um insumo com as mudanças de preço sugerido que o custo dele provocou, numa janela de data/hora. Só as de
     * produto (a janela alimenta o histórico do insumo, que lista produtos): as linhas de item de Catálogo (V0.18.0) têm
     * o produto vazio e ficam de fora pelo {@code JOIN FETCH m.produto}.
     */
    @Query("SELECT mi FROM MudancaPrecoSugeridoInsumo mi JOIN FETCH mi.mudanca m JOIN FETCH m.produto " +
            "WHERE mi.insumo.id = :insumoId AND m.createdAt >= :de AND m.createdAt <= :ate ORDER BY m.createdAt DESC")
    List<MudancaPrecoSugeridoInsumo> findDoInsumoNaJanela(@Param("insumoId") UUID insumoId,
                                                          @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);
}
