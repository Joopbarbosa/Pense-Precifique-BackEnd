package com.penseprecifique.api.shared.dto.request.insumo;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** V0.13.0 (#516, DT-NOVA-1) — substituição de um Insumo usado como componente de Item de Catálogo,
 * mesmo espírito de {@code SubstituicaoInsumoRequestDTO} (ficha técnica), mas o vínculo aqui é por
 * {@code ItemCatalogoComponente.id}, não por produto (Insumo nunca pôde ser componente de item de
 * catálogo antes de RN-NOVA-1). */
public record SubstituicaoVinculoCatalogoInsumoRequestDTO(

        @NotNull(message = "O id do vínculo é obrigatório")
        UUID vinculoId,

        @NotNull(message = "O novo insumo é obrigatório")
        UUID novoInsumoId
) {}
