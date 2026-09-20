package com.penseprecifique.api.shared.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    /**
     * #354 — rede de segurança adicional além da allowlist de PageableOrdenacaoResolver: qualquer
     * caminho de query que gere este tipo de erro (ex. UnknownPathException do Hibernate por
     * propriedade de Sort inexistente na entidade) vira 400 em vez de vazar como 500 genérico.
     * Mensagem propositalmente genérica — não expõe detalhe interno de query/Hibernate ao cliente.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidDataAccessApiUsage(InvalidDataAccessApiUsageException ex) {
        log.warn("Parâmetro de consulta inválido", ex);
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Parâmetro de consulta inválido.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    /**
     * #421 — achado da skill seguranca-resiliencia (Fase 5/Schemathesis, RN-NOVA-1
     * em DECISOES_V0.9.0.md): parâmetro de path/query com tipo incompatível (ex.:
     * {@code @PathVariable UUID} recebendo "0" ou texto arbitrário) lançava
     * {@code MethodArgumentTypeMismatchException}, não coberta antes, caindo no
     * handler genérico e devolvendo 500 em vez de 400. Mensagem propositalmente
     * genérica — não expõe o tipo Java esperado ao cliente.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parâmetro de path/query com tipo inválido: {}", ex.getName());
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Valor inválido para o parâmetro '" + ex.getName() + "'.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    /**
     * #455 — achado residual do gate seguranca-resiliencia ao validar #421 (contagem de 500 caiu
     * de 87/87 para 1/87, este era o 1 restante). Causa raiz confirmada via log real: valor de
     * query param com sequência percent-encoded malformada (ex. {@code sort=%v}, hex inválido
     * após {@code %}) faz {@code StringUtils.uriDecode} do Spring lançar
     * {@code IllegalArgumentException} dentro de {@code SortHandlerMethodArgumentResolver} —
     * ANTES do controller, nem chega na allowlist de {@code PageableOrdenacaoResolver}. Handler
     * genérico o bastante pra cobrir esse caso, mas não indiscriminado: nenhum código de aplicação
     * deste projeto lança {@code IllegalArgumentException} sem capturar internamente
     * (confirmado — os únicos 2 usos, em {@code JwtTokenProvider}/{@code OrcamentoService}, já
     * têm catch local) — não há caso legítimo hoje em que isso mascare um bug real de negócio.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Parâmetro de requisição inválido (IllegalArgumentException)", ex);
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Parâmetro de requisição inválido.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    /**
     * OpenProject #518 — teto do framework (spring.servlet.multipart.max-file-size) fica bem
     * acima do limite de negócio (RN-NOVA-6, 5MB) de propósito, pra deixar a mensagem específica
     * pro Service tratar o caso comum; isso aqui é só a rede de segurança pro caso extremo
     * (arquivo maior que o próprio teto do framework), que nunca deveria virar 500 genérico.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Arquivo muito grande. O tamanho máximo permitido é 5MB.",
                HttpStatus.BAD_REQUEST.value(),
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
