package com.penseprecifique.api.pdf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OpenProject #519 (RN-NOVA-8/DT-NOVA-5) — 6º tipo de documento no PdfMapper Pattern, mesmo padrão
 * de {@link PdfServiceReciboMicroservicoIT} (#248): catálogo real no banco até
 * {@code PdfService.gerarPdfCatalogo} chamar o microsserviço via HTTP (WireMock). Único documento
 * cujo guard de negócio é sobre o próprio recurso (Catalogo.ativo), não sobre um sub-estado de
 * Orçamento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfServiceCatalogoMicroservicoIT {

    private static final WireMockServer wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

    @DynamicPropertySource
    static void configurarUrlDoMicroservicoFalso(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("pdf.microservice.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void pararServidorFalso() {
        wireMockServer.stop();
    }

    @Autowired PdfService pdfService;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    @BeforeEach
    void resetarStubsEToken() {
        wireMockServer.resetAll();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-e2e-teste");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void limparRequisicaoFalsa() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Usuario criarUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("pdf-catalogo-e2e-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        return usuario;
    }

    private Catalogo criarCatalogo(Usuario usuario, boolean ativo) {
        return catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo de Teste").ativo(ativo).build());
    }

    /** Item direto no banco (bypass do Service, mesmo padrão de VendaCaixaItemCatalogoIT) — RN-NOVA-1
     * não é o foco deste teste, só precisa existir 1 item pra popular o payload do PDF. */
    private void criarItemCatalogo(Catalogo catalogo, String nome, String descricao, String fotoUrl) {
        ItemCatalogo item = ItemCatalogo.builder()
                .catalogo(catalogo).nome(nome).tempoProducao(0)
                .precoVenda(new BigDecimal("10.00")).override(true)
                .descricao(descricao).fotoUrl(fotoUrl)
                .build();
        itemCatalogoRepository.save(item);
    }

    @Test
    @Transactional
    void gerarPdfCatalogoChamaOMicroservicoQuandoAtivoERetornaOsBytes() {
        Usuario usuario = criarUsuario();
        Catalogo catalogo = criarCatalogo(usuario, true);
        criarItemCatalogo(catalogo, "Kit Presente P", "Sabonete + fita", "https://exemplo.r2.dev/foto.jpg");

        byte[] pdfFalso = "%PDF-1.4 catalogo falso".getBytes(StandardCharsets.UTF_8);
        wireMockServer.stubFor(post(urlPathMatching("/render/catalogo/" + catalogo.getId() + ".*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/pdf")
                        .withBody(pdfFalso)));

        byte[] resultado = pdfService.gerarPdfCatalogo(catalogo.getId());

        assertArrayEquals(pdfFalso, resultado);
    }

    @Test
    @Transactional
    void gerarPdfCatalogoBloqueiaAntesDeChamarOMicroservicoQuandoInativo() {
        Usuario usuario = criarUsuario();
        Catalogo catalogo = criarCatalogo(usuario, false);
        criarItemCatalogo(catalogo, "Kit Presente P", null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPdfCatalogo(catalogo.getId()));

        assertEquals("O catálogo precisa estar ativo para gerar o PDF.", ex.getMessage());
        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/render/catalogo/.*")));
    }

    @Test
    @Transactional
    void gerarPdfCatalogoPropagaMensagemAmigavelQuandoMicroservicoFalha() {
        Usuario usuario = criarUsuario();
        Catalogo catalogo = criarCatalogo(usuario, true);
        criarItemCatalogo(catalogo, "Kit Presente P", null, null);

        wireMockServer.stubFor(post(urlPathMatching("/render/catalogo/" + catalogo.getId() + ".*"))
                .willReturn(aResponse().withStatus(503)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> pdfService.gerarPdfCatalogo(catalogo.getId()));

        assertEquals("Geração de documento temporariamente indisponível. Tente novamente em instantes.",
                ex.getMessage());
    }
}
