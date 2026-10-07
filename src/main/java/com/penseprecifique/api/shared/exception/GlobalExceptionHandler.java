package com.penseprecifique.api.shared.exception;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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
                null,
                ex.getTitulo(),
                ex.getMotivo(),
                ex.getComoResolver(),
                ex.getItens()
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

    /**
     * #774 — no Spring Framework 7, {@code @Valid} num parâmetro que não é um objeto simples (lista no corpo, por
     * exemplo) lança esta exceção em vez de {@link MethodArgumentNotValidException}; sem tratamento caía no 500.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> fieldErrors = new java.util.LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors().forEach(erro -> {
            String campo = erro instanceof org.springframework.validation.FieldError fe ? fe.getField()
                    : resultado.getMethodParameter().getParameterName();
            fieldErrors.putIfAbsent(campo != null ? campo : "corpo", erro.getDefaultMessage() != null ? erro.getDefaultMessage() : "Inválido");
        }));
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Erro de validação", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), fieldErrors));
    }

    /**
     * #784 — NUL (0x00) em texto de corpo JSON chega ao PostgreSQL e volta como erro de acesso a dados; é entrada
     * inválida (400), não falha interna. Outros erros de acesso a dados continuam sendo tratados como antes (500).
     */
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataAccess(org.springframework.dao.DataAccessException ex) {
        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
            if (causa.getMessage() != null && causa.getMessage().contains("0x00")) {
                return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                        "O texto enviado contém caracteres inválidos.", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), null));
            }
        }
        return handleGenericException(ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String mensagem = "Corpo da requisição inválido.";

        if (ex.getCause() instanceof InvalidFormatException invalidFormatException) {
            String campo = invalidFormatException.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
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
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                             jakarta.servlet.http.HttpServletRequest request) {
        // #802 — "PUT /compras/confirmar" casa com "PUT /compras/{id}" (id = "confirmar"). Se existe um caminho literal
        // igual com outros métodos, o pedido é de método não permitido neste endereço (405), não de id inválido.
        java.util.Set<String> permitidos = metodosDoCaminhoLiteral(request);
        if (!permitidos.isEmpty() && !permitidos.contains(request.getMethod())) {
            return handleMethodNotSupported(new HttpRequestMethodNotSupportedException(request.getMethod(), permitidos));
        }
        log.warn("Parâmetro de path/query com tipo inválido: {}", ex.getName());
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Valor inválido para o parâmetro '" + ex.getName() + "'.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.penseprecifique.api.infra.config.MapeamentoDeMetodos mapeamentoDeMetodos;

    private java.util.Set<String> metodosDoCaminhoLiteral(jakarta.servlet.http.HttpServletRequest request) {
        return mapeamentoDeMetodos == null ? java.util.Set.of()
                : mapeamentoDeMetodos.metodosDoCaminhoMaisEspecifico(request.getRequestURI());
    }

    /**
     * #807 — variável de caminho em branco (ex.: "%20") não converte para UUID e o Spring a trata como ausente
     * ({@code MissingPathVariableException}); é entrada inválida do cliente (400), não falha interna.
     */
    @ExceptionHandler(org.springframework.web.bind.MissingPathVariableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPathVariable(org.springframework.web.bind.MissingPathVariableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Valor inválido para o parâmetro '" + ex.getVariableName() + "'.",
                HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), null));
    }

    /** #660 — parâmetro obrigatório ausente é erro do pedido, inclusive no fuzzing de schema. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Parâmetro obrigatório ausente: '" + ex.getParameterName() + "'.",
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

    /**
     * OpenProject #525 (achado Schemathesis, gate seguranca-resiliencia V0.13.0) — corpo
     * multipart malformado ou sem a parte esperada (ex.: campo 'arquivo' ausente) caía no
     * catch-all genérico e virava 500, apesar de ser exatamente o tipo de entrada inválida que
     * o contrato já trata como 400 pros demais casos de upload (ver
     * MaxUploadSizeExceededException acima).
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingServletRequestPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Arquivo não enviado corretamente. Tente novamente.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        ));
    }

    /**
     * #660 — JSON ou outro corpo não multipart em rota de upload é entrada inválida, não falha interna.
     * #762 (achado Schemathesis da V0.16.0) — multipart malformado pelo cliente (ex.: sem boundary; o Tomcat sinaliza
     * com {@code FileUploadException}) também é 400. Falha de infraestrutura (disco temporário etc.) continua 500.
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipartException(MultipartException ex) {
        boolean naoMultipart = "Current request is not a multipart request".equals(ex.getMessage());
        if (!naoMultipart && !(ex.getCause() instanceof FileUploadException)) {
            return handleGenericException(ex);
        }
        log.warn("Multipart inválido: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                naoMultipart ? "Arquivo não enviado corretamente. Envie o arquivo como formulário multipart."
                        : "Arquivo não enviado corretamente. Tente novamente.",
                HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), null));
    }

    /** #762 — parâmetro de consulta com nome vazio (ex.: {@code ?=1}): o Tomcat recusa; é entrada inválida (400). */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidParameter(InvalidParameterException ex) {
        log.warn("Parâmetro de consulta inválido: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                "Os parâmetros da consulta são inválidos.", HttpStatus.BAD_REQUEST.value(), LocalDateTime.now(), null));
    }

    /** #762 — método HTTP fora do contrato do endereço é 405, não 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(new ErrorResponseDTO(
                "Esta operação não é permitida para este endereço.", HttpStatus.METHOD_NOT_ALLOWED.value(),
                LocalDateTime.now(), null));
    }

    /** #762 — Content-Type que o endpoint não aceita é 415, não 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponseDTO(
                "O tipo de conteúdo enviado não é aceito por este endereço.", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                LocalDateTime.now(), null));
    }

    /**
     * #767 (V0.16.0) — caminho que não existe, com usuário autenticado, é 404 (antes caía no catch-all: 500 e stack
     * trace no log a cada varredura de caminhos). Sem token continua 401, antes de chegar aqui.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleCaminhoInexistente(Exception ex) {
        log.debug("Caminho inexistente: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponseDTO(
                "Endereço não encontrado.", HttpStatus.NOT_FOUND.value(), LocalDateTime.now(), null));
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
