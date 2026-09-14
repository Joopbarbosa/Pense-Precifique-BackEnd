package com.penseprecifique.api.shared.dto.response.insumo;

/**
 * RN-NOVA-4 (V0.10.0, #336) — contadores por filtro da Lista de Insumos, agregados no backend.
 * Mesmos critérios exatos já usados na UI (ListaInsumosPage.tsx: isLow/isNegative/isPositive).
 */
public record InsumoContagensResponse(
        long todos,
        long ativos,
        long inativos,
        long estoqueBaixo,
        long estoqueNegativo,
        long estoquePositivo
) {}
