package com.penseprecifique.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #699 (RN-NOVA-PP699-1/2/3) — e-mail de acesso normalizado em minúsculas: cadastro, login e troca de e-mail tratam
 * "A@x.com" e "a@x.com" como a mesma conta; o campo Nome não existe no cadastro; entrada hostil nunca vira 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class EmailMinusculoHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarioRepository;
    private final ObjectMapper json = new ObjectMapper();

    private String unico() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    private MvcResult enviar(String caminho, String corpo) throws Exception {
        return mvc.perform(post(caminho).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    private MvcResult cadastrar(String email) throws Exception {
        return enviar("/auth/register", "{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\"}");
    }

    private MvcResult entrar(String email, String senha) throws Exception {
        return enviar("/auth/login", "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}");
    }

    private String token(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    // ---------- positivos ----------

    @Test
    void cadastroGravaEmailEmMinusculasEDevolveNaResposta() throws Exception {
        String base = "ana-" + unico() + "@studio.com";
        MvcResult r = cadastrar("  " + base.toUpperCase().replace("STUDIO.COM", "Studio.COM") + " ");
        assertEquals(201, r.getResponse().getStatus());
        assertEquals(base, json.readTree(r.getResponse().getContentAsString()).get("email").asText());
        assertEquals(base, usuarioRepository.findByEmail(base).orElseThrow().getEmail());
    }

    @Test
    void loginComMaiusculasEEspacosEntraNaMesmaConta() throws Exception {
        String base = "login-" + unico() + "@studio.com";
        assertEquals(201, cadastrar(base).getResponse().getStatus());
        for (String variacao : new String[]{base, base.toUpperCase(), " " + base.toUpperCase() + "  ", "Login-" + base.substring(6)}) {
            MvcResult r = entrar(variacao, SENHA);
            assertEquals(200, r.getResponse().getStatus(), variacao);
            assertEquals(base, json.readTree(r.getResponse().getContentAsString()).get("email").asText());
        }
    }

    @Test
    void trocaDeEmailGravaEmMinusculasEBloqueiaOExistenteSoComMaiusculasDiferentes() throws Exception {
        String a = "troca-a-" + unico() + "@studio.com";
        String b = "troca-b-" + unico() + "@studio.com";
        String tokenA = token(cadastrar(a));
        assertEquals(201, cadastrar(b).getResponse().getStatus());

        // novo e-mail igual ao de outra conta, só com caixa diferente: bloqueio
        MvcResult dup = mvc.perform(put("/usuarios/me/email").header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoEmail\":\"" + b.toUpperCase() + "\",\"senhaAtual\":\"" + SENHA + "\"}")).andReturn();
        assertEquals(400, dup.getResponse().getStatus());
        assertTrue(dup.getResponse().getContentAsString().contains("E-mail já cadastrado"));

        // troca válida com maiúsculas: grava minúsculo
        String novo = "novo-" + unico() + "@studio.com";
        mvc.perform(put("/usuarios/me/email").header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"novoEmail\":\"  " + novo.toUpperCase().replace("STUDIO.COM", "Studio.com") + " \",\"senhaAtual\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(novo));
        assertEquals(200, entrar(novo.toUpperCase(), SENHA).getResponse().getStatus());
    }

    @Test
    void cadastroNaoExigeNomeEIgnoraCampoNomeDeClienteAntigo() throws Exception {
        String e1 = "sem-nome-" + unico() + "@studio.com";
        assertEquals(201, cadastrar(e1).getResponse().getStatus());
        String e2 = "com-nome-" + unico() + "@studio.com";
        MvcResult r = enviar("/auth/register", "{\"email\":\"" + e2 + "\",\"nome\":\"Ana\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\"}");
        assertEquals(201, r.getResponse().getStatus());
        assertTrue(!r.getResponse().getContentAsString().contains("\"nome\""));
    }

    // ---------- negativos ----------

    @Test
    void cadastroDuplicadoSoPorMaiusculasEBloqueio() throws Exception {
        String base = "dup-" + unico() + "@studio.com";
        assertEquals(201, cadastrar(base).getResponse().getStatus());
        for (String variacao : new String[]{base.toUpperCase(), " " + base + " ", "Dup-" + base.substring(4)}) {
            MvcResult r = cadastrar(variacao);
            assertEquals(400, r.getResponse().getStatus(), variacao);
            assertTrue(r.getResponse().getContentAsString().contains("E-mail já cadastrado"));
        }
    }

    @Test
    void loginComSenhaErradaOuContaInexistenteDaMesmaMensagem() throws Exception {
        String base = "neg-" + unico() + "@studio.com";
        cadastrar(base);
        for (MvcResult r : new MvcResult[]{entrar(base.toUpperCase(), "errada-123456"), entrar("naoexiste-" + unico() + "@x.com", SENHA)}) {
            assertEquals(400, r.getResponse().getStatus());
            assertTrue(r.getResponse().getContentAsString().contains("E-mail ou senha inválidos"));
        }
    }

    // ---------- entrada hostil: nunca 500 ----------

    @Test
    void entradaHostilNuncaVira500() throws Exception {
        String longo = "a".repeat(300) + "@x.com";
        String[] emails = {
                "", "   ", "sem-arroba", "@x.com", "a@", "a b@x.com", "a@x .com", "a@@x.com",
                "\\u0000@x.com", "á@çx.com", "ÁÉÍ" + unico() + "@ÇÃO.com.br", "İ" + unico() + "@x.com",
                "ａ@ｘ.com", longo, "<script>@x.com", "a@x.com'; DROP TABLE usuarios;--", "\\n@x.com"
        };
        for (String e : emails) {
            String corpoCad = "{\"email\":\"" + e + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\"}";
            int c = enviar("/auth/register", corpoCad).getResponse().getStatus();
            assertTrue(c < 500, "register com [" + e + "] devolveu " + c);
            int l = entrar(e, SENHA).getResponse().getStatus();
            assertTrue(l < 500, "login com [" + e + "] devolveu " + l);
        }
        String senhaGigante = "s".repeat(5000);
        assertTrue(entrar("x@x.com", senhaGigante).getResponse().getStatus() < 500);
        assertTrue(enviar("/auth/register", "{\"email\":\"g-" + unico() + "@x.com\",\"senha\":\"" + senhaGigante + "\",\"confirmarSenha\":\"" + senhaGigante + "\"}")
                .getResponse().getStatus() < 500);
    }

    @Test
    void corpoMalformadoOuTipoErradoNuncaVira500() throws Exception {
        String[] corpos = {
                "", "{", "[]", "null", "\"texto\"", "{\"email\":", "{\"email\":null,\"senha\":null}",
                "{\"email\":123,\"senha\":\"x\"}", "{\"email\":[\"a@x.com\"],\"senha\":\"x\"}",
                "{\"email\":{\"a\":1},\"senha\":\"x\"}", "{\"email\":true,\"senha\":false}", "{}"
        };
        for (String corpo : corpos) {
            for (String caminho : new String[]{"/auth/register", "/auth/login"}) {
                int c = enviar(caminho, corpo).getResponse().getStatus();
                assertTrue(c >= 400 && c < 500, caminho + " com [" + corpo + "] devolveu " + c);
            }
        }
        // content-type errado
        assertTrue(mvc.perform(post("/auth/login").contentType(MediaType.TEXT_PLAIN).content("a@x.com"))
                .andReturn().getResponse().getStatus() < 500);
        assertTrue(mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_XML).content("<a/>"))
                .andReturn().getResponse().getStatus() < 500);
    }

    @Test
    void trocaDeEmailHostilNuncaVira500() throws Exception {
        String t = token(cadastrar("hostil-" + unico() + "@studio.com"));
        for (String novo : new String[]{"", "  ", "x", "a".repeat(300) + "@x.com", "á b@x.com"}) {
            MvcResult r = mvc.perform(put("/usuarios/me/email").header("Authorization", "Bearer " + t)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"novoEmail\":\"" + novo + "\",\"senhaAtual\":\"" + SENHA + "\"}")).andReturn();
            assertTrue(r.getResponse().getStatus() >= 400 && r.getResponse().getStatus() < 500, "[" + novo + "] " + r.getResponse().getStatus());
        }
    }
}
