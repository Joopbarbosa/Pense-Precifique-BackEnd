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

    /** #719 — pagamentos de várias vendas de uma vez (formas de pagamento do Dashboard de Vendas). */
    List<VendaCaixaPagamento> findByVendaCaixaIdIn(java.util.Collection<UUID> vendaCaixaIds);

    /** RN-NOVA-9 — soma dos pagamentos em DINHEIRO das vendas CONCLUIDA de um turno, usada no
     * cálculo de CaixaTurnoService.fecharTurno(). Venda CANCELADA não entra (estoque e efeito
     * financeiro já revertidos no cancelamento, RN-NOVA-4). */
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM VendaCaixaPagamento p " +
            "WHERE p.vendaCaixa.caixaTurno.id = :turnoId " +
            "AND p.vendaCaixa.status = com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa.CONCLUIDA " +
            "AND p.metodoPagamento.tipo = com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento.DINHEIRO")
    BigDecimal somarPagamentosDinheiroPorTurno(@Param("turnoId") UUID turnoId);

    /** #1115 (CXA-009) — troco devolvido nas vendas CONCLUIDA do turno. O troco só existe quando há
     * linha em dinheiro (RN-NOVA-7), então sai da gaveta e deve ser descontado do dinheiro recebido. */
    @Query("SELECT COALESCE(SUM(v.troco), 0) FROM VendaCaixa v " +
            "WHERE v.caixaTurno.id = :turnoId " +
            "AND v.status = com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa.CONCLUIDA")
    BigDecimal somarTrocoPorTurno(@Param("turnoId") UUID turnoId);

    /** #1115 — dinheiro líquido que entrou na gaveta: recebido em dinheiro menos o troco. */
    default BigDecimal somarDinheiroLiquidoPorTurno(UUID turnoId) {
        return somarPagamentosDinheiroPorTurno(turnoId).subtract(somarTrocoPorTurno(turnoId));
    }
}
