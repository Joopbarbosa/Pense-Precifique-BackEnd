package com.penseprecifique.api.shared.dto.request.catalogo;

import lombok.Getter;
import lombok.Setter;

/**
 * Nome do catálogo duplicado (RN-047). Opcional — se vier em branco, o Service usa
 * o nome original com sufixo "(cópia)".
 */
@Getter
@Setter
public class DuplicarCatalogoRequest {

    private String novoNome;
}
