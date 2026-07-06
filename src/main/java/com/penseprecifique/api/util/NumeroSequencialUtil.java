package com.penseprecifique.api.util;

import java.util.Optional;

/**
 * RN-053 — numero sequencial por usuario (MAX+1), reaproveitado por Insumo/Produto/Cliente
 * ao inves de replicar o mesmo metodo privado ja existente em OrcamentoService/ProducaoService/CatalogoService.
 */
public final class NumeroSequencialUtil {

    private NumeroSequencialUtil() {
    }

    public static Integer proximoNumero(Optional<Integer> maiorNumeroAtual) {
        return maiorNumeroAtual.map(n -> n + 1).orElse(1);
    }
}
