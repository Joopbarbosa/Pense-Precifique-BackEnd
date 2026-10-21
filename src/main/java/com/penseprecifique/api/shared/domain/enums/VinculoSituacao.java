package com.penseprecifique.api.shared.domain.enums;

/**
 * V0.18.0 (DT-NOVA-5) — como a exceção de uma ocorrência trata cada vínculo: herda o do mestre, tem um próprio
 * (definido) ou foi removido só naquela ocorrência.
 */
public enum VinculoSituacao {
    HERDA, DEFINIDO, REMOVIDO
}
