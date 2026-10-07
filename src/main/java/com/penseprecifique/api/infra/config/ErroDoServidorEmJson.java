package com.penseprecifique.api.infra.config;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * #814 — erro que o Tomcat recusa antes de chegar ao Spring (linha de requisição ou consulta além do limite do
 * servidor, por exemplo) saía como página HTML "HTTP Status 400". Agora sai no formato JSON padrão de erro, com
 * mensagem fixa por status (nenhum detalhe interno do servidor).
 */
@Configuration
public class ErroDoServidorEmJson {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> erroDoTomcatEmJson() {
        return fabrica -> fabrica.addContextCustomizers(contexto -> {
            if (contexto.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(RelatorioDeErroJson.class.getName());
            }
        });
    }

    public static class RelatorioDeErroJson extends ErrorReportValve {

        private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RelatorioDeErroJson.class);

        @Override
        protected void report(Request request, Response response, Throwable throwable) {
            int status = response.getStatus();
            if (status < 400 || response.getContentWritten() > 0 || !response.isError()) {
                return;
            }
            String mensagem = switch (status) {
                case 400 -> "Requisição inválida.";
                case 401 -> "Não autorizado";
                case 403 -> "Sem permissão para este recurso";
                case 404 -> "Endereço não encontrado.";
                case 405 -> "Esta operação não é permitida para este endereço.";
                case 414 -> "Endereço muito longo.";
                case 431 -> "Cabeçalhos da requisição muito grandes.";
                default -> status >= 500 ? "Erro interno do servidor" : "Requisição não atendida.";
            };
            try {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"message\":\"" + mensagem + "\",\"status\":" + status + ",\"timestamp\":\""
                        + LocalDateTime.now() + "\",\"fieldErrors\":null,\"titulo\":null,\"motivo\":null,"
                        + "\"comoResolver\":null,\"itens\":null}");
                response.finishResponse();
            } catch (IOException | IllegalStateException e) {
                // resposta já comprometida ou cliente desconectado: só registra
                LOG.debug("Resposta de erro do servidor não escrita: {}", e.toString());
            }
        }
    }
}
