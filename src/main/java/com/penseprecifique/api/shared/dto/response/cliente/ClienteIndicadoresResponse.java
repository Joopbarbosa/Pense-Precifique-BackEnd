package com.penseprecifique.api.shared.dto.response.cliente;

/**
 * #560 (V0.15.0) — GET /clientes/{id}/indicadores. As duas seções vêm sempre calculadas; o frontend
 * exibe cada uma conforme o papel do registro (um registro que perdeu o papel ainda tem histórico).
 */
public record ClienteIndicadoresResponse(
        IndicadoresClienteResponse cliente,
        IndicadoresFornecedorResponse fornecedor
) {}
