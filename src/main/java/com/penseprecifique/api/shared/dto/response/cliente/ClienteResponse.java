package com.penseprecifique.api.shared.dto.response.cliente;

import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
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
    private String telefone;
    private String site;
    private String endereco;
    private String observacoes;
    private boolean ehCliente;
    private boolean ehFornecedor;
    private TipoPessoa tipoPessoa;
    // Normalizado (sem máscara, maiúsculo); a máscara é aplicada só no frontend.
    private String documento;
    private boolean ativa;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
