package com.penseprecifique.api.shared.dto.request.orcamento;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.19.0 (#1185, RN-NOVA-38/39/42, UC-NOVO-4) — dados do passo "Pagamento" da aprovação. Só é VALIDADO aqui
 * ({@code POST /orcamentos/{id}/pagamento/validar}); a gravação, as parcelas e a mudança de status são da #1174.
 */
@Getter
@Setter
public class PagamentoOrcamentoRequest {

    /** Método ATIVO de Configurações > Métodos de Pagamento (RN-NOVA-42). */
    private UUID metodoPagamentoId;

    /** "Tem sinal?" — ausente equivale a Não (padrão do passo). */
    private Boolean temSinal;

    /** Como o sinal foi informado; ausente equivale a PERCENTUAL (com 50% quando o percentual também falta). */
    private TipoSinal tipoSinal;

    private BigDecimal percentualSinal;
    private BigDecimal valorSinal;

    /** Data limite do sinal (obrigatória com sinal). */
    private LocalDate sinalAte;

    /** Vencimento do saldo (ou do total, sem sinal); ausente assume a data da aprovação (hoje). */
    private LocalDate vencimentoSaldo;

    public enum TipoSinal { PERCENTUAL, VALOR }
}
