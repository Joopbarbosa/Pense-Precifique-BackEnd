package com.penseprecifique.api.shared.dto.request.agenda;

import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.18.0 (#1181, RN-NOVA-43/44) — mover um card derivado (orçamento, produção ou compra) para outro dia. O avulso se move pela
 * edição do próprio compromisso. {@code referenciaId} é o orçamento, a produção ou a compra do card.
 */
public record MoverCompromissoRequest(
        @NotNull(message = "Informe o tipo do compromisso")
        TipoCompromisso tipo,
        @NotNull(message = "Informe o compromisso")
        UUID referenciaId,
        @NotNull(message = "Informe a nova data")
        LocalDate novaData
) {}
