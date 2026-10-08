package com.penseprecifique.api.compra.nota;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.infra.storage.R2StorageClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * V0.16.0 (#683, #681) — base dos testes da nota: leitor-fiscal e provedor de IA simulados, num único
 * contexto Spring compartilhado pelas classes filhas (cada contexto a mais abre outro pool de conexões e o
 * Postgres de teste recusa com "too many clients"). Sem stub, a IA responde 404 e a sugestão é pulada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class NotaServicosSimulados {

    static final WireMockServer LEITOR = new WireMockServer(wireMockConfig().dynamicPort());
    static final WireMockServer IA = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        LEITOR.start();
        IA.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LEITOR.stop();
            IA.stop();
        }));
    }

    @MockitoBean R2StorageClient r2StorageClient;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("leitor-fiscal.base-url", () -> "http://localhost:" + LEITOR.port());
        registry.add("leitor-fiscal.chave-produto", () -> "chave-de-teste-do-pocket");
        registry.add("nota.assinatura.segredo", () -> "segredo-de-teste-do-pocket");
        registry.add("sugestao-ia.habilitada", () -> "true");
        registry.add("sugestao-ia.base-url", () -> "http://localhost:" + IA.port());
        registry.add("sugestao-ia.chave", () -> "chave-ia-de-teste");
        registry.add("sugestao-ia.modelo", () -> "modelo-de-teste");
        registry.add("sugestao-ia.limite-mensal-por-conta", () -> "200");
    }
}
