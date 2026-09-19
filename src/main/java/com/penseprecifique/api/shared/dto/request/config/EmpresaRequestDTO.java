package com.penseprecifique.api.shared.dto.request.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record EmpresaRequestDTO(
        @NotBlank(message = "O nome da empresa é obrigatório")
        String nome,

        @Email(message = "E-mail inválido")
        String email,

        String whatsapp,
        String endereco,
        String logoUrl,

        /** #488 (V0.12.0) — substituição total: o que vier aqui passa a ser o horário da empresa.
         *  Nulo mantém o horário atual; lista vazia apaga o horário configurado. */
        @Valid
        List<HorarioFuncionamentoRequestDTO> horarios
) {}
