package com.penseprecifique.api.shared.dto.request.cliente;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClienteRequest {

    @NotBlank(message = "O nome do cliente é obrigatório")
    private String nome;

    private String email;

    private String whatsapp;

    private String endereco;

    private String observacoes;
}
