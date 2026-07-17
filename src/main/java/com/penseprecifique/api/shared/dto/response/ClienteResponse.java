package com.penseprecifique.api.shared.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class ClienteResponse {

    private UUID id;
    private Integer numero;
    private String identificador;
    private String nome;
    private String email;
    private String whatsapp;
    private String endereco;
    private String observacoes;
    private boolean ativa;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
