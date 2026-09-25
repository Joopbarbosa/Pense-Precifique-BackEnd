package com.penseprecifique.api.catalogo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.infra.security.JwtTokenProvider;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenProject #516/#518 — CEN-NOVO-1 (nenhum componente bloqueia) e CEN-NOVO-7 (descrição acima
 * de 150 caracteres bloqueia) via HTTP real (`@Valid` só atua na borda do Controller — os demais
 * testes de Item de Catálogo chamam o Service direto, o que não exercita essa validação). Mesmo
 * padrão de {@code ProdutoQuantidadeInvalidaIT} (MockMvc + JWT real).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class ItemCatalogoValidacaoHttpIT {

    @Autowired MockMvc mockMvc;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired ObjectMapper objectMapper;

    private int proximoNumeroInsumo = 1;

    private Usuario novoUsuario() {
        return usuarioRepository.save(Usuario.builder()
                .email("item-catalogo-validacao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
    }

    private UUID novoCatalogoViaHttp(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/catalogos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Catálogo Validação HTTP " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return UUID.fromString((String) body.get("id"));
    }

    @Test
    void adicionarItemSemComponenteRetorna400ComMensagemAmigavel() throws Exception {
        Usuario usuario = novoUsuario();
        String token = jwtTokenProvider.generateToken(usuario);
        UUID catalogoId = novoCatalogoViaHttp(token);

        String body = """
                {
                  "nome": "Item Sem Componente",
                  "tempoProducao": 0,
                  "componentes": []
                }
                """;

        mockMvc.perform(post("/catalogos/" + catalogoId + "/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.componentes").value("É preciso pelo menos 1 componente"));
    }

    @Test
    void adicionarItemComDescricaoAcimaDe150CaracteresRetorna400ComMensagemAmigavel() throws Exception {
        Usuario usuario = novoUsuario();
        String token = jwtTokenProvider.generateToken(usuario);
        UUID catalogoId = novoCatalogoViaHttp(token);

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(proximoNumeroInsumo++).nome("Insumo Validação").unidadeMedida(unidadeMedida(usuario, "un"))
                .custoUnitario(new BigDecimal("1.0000")).estoqueAtual(BigDecimal.TEN).fracionavel(true)
                .permitirEstoqueNegativo(true).build());

        String descricao151Caracteres = "a".repeat(151);
        String body = objectMapper.writeValueAsString(Map.of(
                "nome", "Item Descrição Longa",
                "tempoProducao", 0,
                "descricao", descricao151Caracteres,
                "componentes", java.util.List.of(Map.of("insumoId", insumo.getId().toString(), "quantidade", 1))
        ));

        mockMvc.perform(post("/catalogos/" + catalogoId + "/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.descricao").value("A descrição não pode ter mais de 150 caracteres"));
    }

    /**
     * OpenProject #525 (achado Schemathesis, gate seguranca-resiliencia) — corpo multipart sem a
     * parte 'arquivo' (ex.: cliente enviando corpo malformado) caía no catch-all genérico e virava
     * 500 em vez de 400. Prova do bug + verificação da correção (GlobalExceptionHandler ganhou
     * handler para MissingServletRequestPartException).
     */
    @Test
    void uploadDeFotoSemAPartesArquivoRetorna400EmVezDe500() throws Exception {
        Usuario usuario = novoUsuario();
        String token = jwtTokenProvider.generateToken(usuario);
        UUID catalogoId = novoCatalogoViaHttp(token);

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(proximoNumeroInsumo++).nome("Insumo Upload Foto").unidadeMedida(unidadeMedida(usuario, "un"))
                .custoUnitario(new BigDecimal("1.0000")).estoqueAtual(BigDecimal.TEN).fracionavel(true)
                .permitirEstoqueNegativo(true).build());

        MvcResult itemResult = mockMvc.perform(post("/catalogos/" + catalogoId + "/itens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nome", "Item Upload Sem Arquivo",
                                "tempoProducao", 0,
                                "componentes", java.util.List.of(Map.of("insumoId", insumo.getId().toString(), "quantidade", 1))
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        Map<?, ?> item = objectMapper.readValue(itemResult.getResponse().getContentAsString(), Map.class);

        // multipart sem nenhum part "arquivo" — dispara MissingServletRequestPartException
        mockMvc.perform(multipart("/catalogos/" + catalogoId + "/itens/" + item.get("id") + "/foto")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Arquivo não enviado corretamente. Tente novamente."));
    }

    private UnidadeMedida unidadeMedida(Usuario usuario, String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
