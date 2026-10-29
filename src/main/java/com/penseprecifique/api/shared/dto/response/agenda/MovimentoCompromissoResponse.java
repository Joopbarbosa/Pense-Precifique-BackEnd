package com.penseprecifique.api.shared.dto.response.agenda;

import java.time.LocalDate;
import java.util.List;

/**
 * V0.18.0 (#1181, RN-NOVA-44) — o que a janela de confirmação mostra: data de antes e de depois, o que muda na origem, os AVISOS
 * (permitem confirmar) e o BLOQUEIO (impede; Confirmar fica desabilitado, com o motivo).
 */
public record MovimentoCompromissoResponse(
        String titulo,
        LocalDate dataAntes,
        LocalDate dataDepois,
        String efeito,
        List<String> avisos,
        String bloqueio
) {}
