package com.penseprecifique.api.shared.dto.response.cliente;

/**
 * #537 (V0.15.0) — contadores dos filtros da tela Clientes e Fornecedores, agregados no backend
 * (mesmo padrão de GET /insumos/contagens, #336). Clientes/fornecedores contam só ativos; um
 * registro com os dois papéis conta nos dois.
 */
public record ClienteContagensResponse(
        long ativos,
        long clientes,
        long fornecedores,
        long inativos
) {}
