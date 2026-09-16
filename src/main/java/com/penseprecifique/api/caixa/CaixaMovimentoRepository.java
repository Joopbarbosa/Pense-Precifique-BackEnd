package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.CaixaMovimento;
import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CaixaMovimentoRepository extends JpaRepository<CaixaMovimento, UUID> {

    List<CaixaMovimento> findByCaixaTurnoIdOrderByDataMovimentoDesc(UUID caixaTurnoId);

    /** RN-NOVA-9 — soma de sangrias/suprimentos de um turno, usada no cálculo do fechamento. */
    @Query("SELECT COALESCE(SUM(m.valor), 0) FROM CaixaMovimento m " +
            "WHERE m.caixaTurno.id = :caixaTurnoId AND m.tipo = :tipo")
    BigDecimal somarPorTipo(@Param("caixaTurnoId") UUID caixaTurnoId, @Param("tipo") TipoCaixaMovimento tipo);
}
