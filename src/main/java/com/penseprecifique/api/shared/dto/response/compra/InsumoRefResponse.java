package com.penseprecifique.api.shared.dto.response.compra;

import java.util.UUID;

/** Referência resumida a um insumo (INS-N), com a sigla da unidade. */
public record InsumoRefResponse(UUID id, String identificador, String nome, String marca, String unidade, boolean ativo) {}
