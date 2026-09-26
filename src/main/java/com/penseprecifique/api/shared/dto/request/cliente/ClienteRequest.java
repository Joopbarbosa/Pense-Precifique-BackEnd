package com.penseprecifique.api.shared.dto.request.cliente;

import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * #483 (V0.15.0) — todo campo de texto tem {@code @Size} alinhado à coluna e recusa caractere nulo:
 * sem isso, o payload do fuzzing chegava ao Postgres e voltava 500 (ANALYSIS #483).
 */
@Getter
@Setter
public class ClienteRequest {

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 255, message = "Máximo de 255 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String nome;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String email;

    @Size(max = 20, message = "Máximo de 20 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String whatsapp;

    @Size(max = 20, message = "Máximo de 20 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String telefone;

    @Size(max = 255, message = "Máximo de 255 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String site;

    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String endereco;

    // #559/RN-NOVA-18
    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM)
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String observacoes;

    // #536/RN-NOVA-1 — pelo menos um papel (validado no Service, mensagem única da spec).
    private Boolean ehCliente;

    private Boolean ehFornecedor;

    // #536/RN-NOVA-17 — nulo = FISICA.
    private TipoPessoa tipoPessoa;

    // Aceita com ou sem máscara; o Service normaliza e valida conforme o tipoPessoa.
    @Size(max = 30, message = "Máximo de 30 caracteres")
    @Pattern(regexp = LimitesTexto.SEM_CARACTERE_NULO, message = LimitesTexto.SEM_CARACTERE_NULO_MENSAGEM)
    private String documento;
}
