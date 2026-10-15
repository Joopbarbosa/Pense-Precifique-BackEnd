package com.penseprecifique.api.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MudancaPrecoSugeridoRepository extends JpaRepository<MudancaPrecoSugerido, UUID> {

    /** Mudanças de um produto numa janela de data/hora (usada para intercalar com a página do histórico de estoque). */
    @Query("SELECT m FROM MudancaPrecoSugerido m WHERE m.produto.id = :produtoId AND m.createdAt >= :de AND m.createdAt <= :ate " +
            "ORDER BY m.createdAt DESC")
    List<MudancaPrecoSugerido> findDoProdutoNaJanela(@Param("produtoId") UUID produtoId,
                                                     @Param("de") LocalDateTime de, @Param("ate") LocalDateTime ate);
}
