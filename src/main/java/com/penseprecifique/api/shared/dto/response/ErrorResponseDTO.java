package com.penseprecifique.api.shared.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * V0.15.0 (#602, RN-NOVA-32/DT-NOVA-21) — {@code titulo}, {@code motivo}, {@code comoResolver} e
 * {@code itens} são opcionais: preenchidos só pelos erros já convertidos para a modal de erro padrão
 * ("o que aconteceu" = {@code message}, "por quê" = {@code motivo}, "como resolver" = {@code comoResolver},
 * {@code itens} = um problema por linha quando há vários). Os demais erros saem só com {@code message}.
 */
public record ErrorResponseDTO(
        String message,
        int status,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors,
        String titulo,
        String motivo,
        String comoResolver,
        List<String> itens
) {
    public ErrorResponseDTO(String message, int status, LocalDateTime timestamp, Map<String, String> fieldErrors) {
        this(message, status, timestamp, fieldErrors, null, null, null, null);
    }
}
