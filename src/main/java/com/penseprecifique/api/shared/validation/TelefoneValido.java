package com.penseprecifique.api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * V0.17.0 #964 (RN-NOVA-19, DT-NOVA-15) — telefone ou WhatsApp de empresa e de cliente/fornecedor.
 * Vazio é permitido. Válido: nacional (10 ou 11 dígitos com DDD) ou estrangeiro ({@code +} seguido
 * de 8 a 15 dígitos, com até 25 caracteres no total). A regra vive num único ponto, usado por
 * empresa e por cliente.
 */
@Documented
@Constraint(validatedBy = ValidadorTelefone.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface TelefoneValido {

    String message() default ValidadorTelefone.MENSAGEM;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
