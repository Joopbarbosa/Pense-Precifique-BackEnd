package com.penseprecifique.api.shared.dto.request.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EmpresaRequestDTO(
        @NotBlank(message = "O nome da empresa é obrigatório")
        @Size(max = 255, message = "O nome da empresa deve ter no máximo 255 caracteres")
        String nome,

        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail muito longo")
        String email,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String whatsapp,
        String endereco,
        String logoUrl,

        /** #488 (V0.12.0) — substituição total: o que vier aqui passa a ser o horário da empresa.
         *  Nulo mantém o horário atual; lista vazia apaga o horário configurado. */
        @Valid
        List<HorarioFuncionamentoRequestDTO> horarios
) {}
