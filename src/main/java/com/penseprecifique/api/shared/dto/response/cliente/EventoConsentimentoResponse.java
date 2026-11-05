package com.penseprecifique.api.shared.dto.response.cliente;

import com.penseprecifique.api.cliente.TipoEventoConsentimento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoConsentimentoResponse(UUID id, TipoEventoConsentimento tipo, LocalDate data,
                                          String descricao, LocalDateTime registradoEm) {}
