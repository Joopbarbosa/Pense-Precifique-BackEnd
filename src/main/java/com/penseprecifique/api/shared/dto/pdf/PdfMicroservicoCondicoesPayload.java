package com.penseprecifique.api.shared.dto.pdf;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * V0.19.0 (#445, RN-NOVA-45, DT-NOVA-11) — página 2 "Condições de pagamento" do orçamento de Venda.
 * Espelha {@code condicoesSchema} do microsserviço. {@code modo} é {@code ANTES_APROVACAO}
 * (usa {@code formasAceitas} e {@code aviso}) ou {@code APROVADO} (usa {@code forma}, {@code sinal},
 * {@code sinalAte}, {@code saldo}, {@code vencimentoSaldo} e {@code recebido}). Tudo já formatado.
 */
@Data
@Builder
public class PdfMicroservicoCondicoesPayload {
    private String modo;
    private List<String> formasAceitas;
    private String aviso;
    private String forma;
    private String sinal;
    private String sinalAte;
    private String saldo;
    private String vencimentoSaldo;
    private String recebido;
}
