package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * V0.19.0 (#1174, RN-NOVA-10) — pagamento combinado na aprovação, já validado. {@code valorSinal} nulo = sem sinal.
 * Com sinal menor que o total nascem Sinal e Saldo; com sinal de 100% só o Sinal; sem sinal, uma parcela Total.
 */
public record PagamentoDoOrcamento(BigDecimal total, BigDecimal valorSinal, LocalDate sinalAte, LocalDate vencimentoSaldo) {

    public List<NovaParcela> parcelas() {
        List<NovaParcela> lista = new ArrayList<>(2);
        if (valorSinal == null) {
            lista.add(new NovaParcela(total, vencimentoSaldo, null, PapelParcela.TOTAL));
            return lista;
        }
        lista.add(new NovaParcela(valorSinal, sinalAte, null, PapelParcela.SINAL));
        BigDecimal saldo = total.subtract(valorSinal);
        if (saldo.signum() > 0) {
            lista.add(new NovaParcela(saldo, vencimentoSaldo, null, PapelParcela.SALDO));
        }
        return lista;
    }
}
