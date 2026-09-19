package com.penseprecifique.api.shared.dto.response.caixa;

import java.math.BigDecimal;

/**
 * #488 (V0.12.0) — prévia do fechamento, sem persistir nada. O Frontend precisa do valor esperado
 * ANTES de enviar o fechamento para saber se deve exigir a justificativa; o Service revalida no
 * POST, nunca confiando no cliente. Segue o padrão `simular-*` já usado em Produção/Orçamento.
 */
public record FechamentoPreviaResponseDTO(
        BigDecimal valorAbertura,
        BigDecimal suprimentos,
        BigDecimal sangrias,
        BigDecimal vendasDinheiro,
        BigDecimal valorEsperado
) {}
