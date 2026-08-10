package com.penseprecifique.api.shared.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessException(BusinessException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        f -> f.getField(),
                        f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "Inválido",
                        (existing, replacement) -> existing
                ));

        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Erro de validação",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                fieldErrors
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String mensagem = "Corpo da requisição inválido.";

        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            String campo = invalidFormatException.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .reduce((primeiro, ultimo) -> ultimo)
                    .orElse(null);

            if (BigDecimal.class.isAssignableFrom(invalidFormatException.getTargetType())) {
                mensagem = campo != null
                        ? "Valor inválido para o campo '" + campo + "' — informe um número decimal (ex: 0.5)."
                        : "Valor inválido — informe um número decimal (ex: 0.5).";
            } else if (campo != null) {
                mensagem = "Valor inválido para o campo '" + campo + "'.";
            }
        }

        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                mensagem,
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO(
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now(),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(Exception ex) {
        log.error("Erro interno não tratado", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponseDTO(
                "Erro interno do servidor",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now(),
                null
        ));
    }
}
