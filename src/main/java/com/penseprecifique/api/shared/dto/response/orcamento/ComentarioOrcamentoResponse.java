package com.penseprecifique.api.shared.dto.response.orcamento;

import java.time.LocalDateTime;
import java.util.UUID;

/** V0.18.0 (#1038, RN-NOVA-22) — um comentário do orçamento, com data e hora. */
public record ComentarioOrcamentoResponse(UUID id, String texto, LocalDateTime criadoEm) {}
