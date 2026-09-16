package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixaPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface VendaCaixaPagamentoRepository extends JpaRepository<VendaCaixaPagamento, UUID> {

    List<VendaCaixaPagamento> findByVendaCaixaId(UUID vendaCaixaId);

    /** RN-NOVA-9 — soma dos pagamentos em DINHEIRO das vendas CONCLUIDA de um turno, usada no
     * cálculo de CaixaTurnoService.fecharTurno(). Venda CANCELADA não entra (estoque e efeito
     * financeiro já revertidos no cancelamento, RN-NOVA-4). */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM VendaCaixaPagamento p " +
            "WHERE p.vendaCaixa.caixaTurno.id = :turnoId " +
            "AND p.vendaCaixa.status = com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa.CONCLUIDA " +
            "AND p.metodoPagamento.tipo = com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento.DINHEIRO")
    BigDecimal somarPagamentosDinheiroPorTurno(@Param("turnoId") UUID turnoId);
}
