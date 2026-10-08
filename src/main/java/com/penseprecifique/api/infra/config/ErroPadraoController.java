package com.penseprecifique.api.infra.config;

import com.penseprecifique.api.shared.dto.response.ErrorResponseDTO;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * #785 — erros que o contêiner devolve por {@code sendError} (método recusado pelo Tomcat, por exemplo) saem no mesmo
 * formato JSON de {@link ErrorResponseDTO} que o OpenAPI declara, com mensagem fixa por status (nenhum detalhe interno).
 */
@RestController
public class ErroPadraoController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ErrorResponseDTO> erro(HttpServletRequest request) {
        Object codigo = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = codigo instanceof Integer c && HttpStatus.resolve(c) != null ? HttpStatus.valueOf(c)
                : HttpStatus.INTERNAL_SERVER_ERROR;
        String mensagem = switch (status.value()) {
            case 400 -> "Requisição inválida.";
            case 401 -> "Não autorizado";
            case 403 -> "Sem permissão para este recurso";
            case 404 -> "Endereço não encontrado.";
            case 405 -> "Esta operação não é permitida para este endereço.";
            case 415 -> "Tipo de conteúdo não aceito por este endereço.";
            default -> status.is5xxServerError() ? "Erro interno do servidor" : "Requisição não atendida.";
        };
        return ResponseEntity.status(status).body(new ErrorResponseDTO(mensagem, status.value(), LocalDateTime.now(), null));
    }
}
