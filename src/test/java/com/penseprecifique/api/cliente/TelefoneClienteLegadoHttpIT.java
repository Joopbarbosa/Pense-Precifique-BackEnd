package com.penseprecifique.api.cliente;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * V0.17.0 #1000 (QA da #964, RN-NOVA-19, CEN-NOVO-59): cliente gravado antes da regra com o telefone "123" abre com o
 * valor exibido normalmente; ao alterar só o nome, o servidor bloqueia com "Telefone inválido" no campo, até a artesã
 * corrigir ou apagar o número. O estado antigo é montado direto no banco, porque a API já não aceita gravar "123".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TelefoneClienteLegadoHttpIT {

    private static final String SENHA = "senha-valida-123";

    @Autowired MockMvc mvc;
    @Autowired ClienteRepository clientes;
    private final ObjectMapper json = new ObjectMapper();

    private String token() throws Exception {
        String email = "legado-" + UUID.randomUUID() + "@studio.com";
        MvcResult r = mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\",\"confirmarSenha\":\"" + SENHA + "\",\"nomeEmpresa\":\"Ateliê\"}"))
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private UUID clienteMariaComTelefoneAntigo(String t) throws Exception {
        MvcResult criado = mvc.perform(post("/clientes").header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Maria Silva\",\"ehCliente\":true,\"whatsapp\":\"(11) 91234-5678\"}")).andReturn();
        assertEquals(201, criado.getResponse().getStatus(), criado.getResponse().getContentAsString());
        JsonNode n = json.readTree(criado.getResponse().getContentAsString());
        UUID id = UUID.fromString(n.get("id").asText());
        Cliente c = clientes.findById(id).orElseThrow();
        c.setWhatsapp("123"); // gravado antes da versão com a regra
        clientes.save(c);
        return id;
    }

    private MvcResult editar(String t, UUID id, String corpo) throws Exception {
        return mvc.perform(put("/clientes/" + id).header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn();
    }

    @Test
    void telefoneAntigoInvalidoApareceNormalmenteEBloqueiaAoSalvarOutroCampo() throws Exception {
        String t = token();
        UUID id = clienteMariaComTelefoneAntigo(t);

        mvc.perform(get("/clientes/" + id).header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.whatsapp").value("123"));

        // a tela devolve o telefone antigo junto com o nome novo: bloqueia no campo e nada é gravado
        mvc.perform(put("/clientes/" + id).header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria Souza\",\"ehCliente\":true,\"whatsapp\":\"123\"}"))
                .andExpect(jsonPath("$.fieldErrors.whatsapp").value("Telefone inválido"));
        mvc.perform(get("/clientes/" + id).header("Authorization", "Bearer " + t))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.whatsapp").value("123"));
    }

    @Test
    void apagarOuCorrigirOTelefoneAntigoLiberaOSalvamento() throws Exception {
        String t = token();
        UUID id = clienteMariaComTelefoneAntigo(t);

        assertEquals(200, editar(t, id, "{\"nome\":\"Maria Souza\",\"ehCliente\":true,\"whatsapp\":\"\"}").getResponse().getStatus());
        mvc.perform(get("/clientes/" + id).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.nome").value("Maria Souza"));

        UUID outro = clienteMariaComTelefoneAntigo(t);
        assertEquals(200, editar(t, outro, "{\"nome\":\"Maria Souza\",\"ehCliente\":true,\"whatsapp\":\"(11) 98765-4321\"}").getResponse().getStatus());
        mvc.perform(get("/clientes/" + outro).header("Authorization", "Bearer " + t)).andExpect(jsonPath("$.whatsapp").value("(11) 98765-4321"));
    }
}
