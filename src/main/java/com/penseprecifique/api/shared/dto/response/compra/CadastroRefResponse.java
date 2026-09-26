package com.penseprecifique.api.shared.dto.response.compra;

import java.util.UUID;

/** Referência resumida a um cadastro de Clientes e Fornecedores (CLI-N), exibido mesmo se inativo. */
public record CadastroRefResponse(UUID id, String identificador, String nome, boolean ativa) {}
