package com.penseprecifique.api.financeiro;

/** Corpo de criar/renomear categoria. A validação do nome (2 a 40) é do serviço, para devolver sempre bloqueio por campo. */
public record CategoriaFinanceiraRequest(String nome) {}
