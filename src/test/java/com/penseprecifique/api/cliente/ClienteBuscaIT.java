package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #356 — GET /clientes padroniza o parâmetro de busca de {@code nome} para {@code busca}, mesmo
 * nome usado por Produto/Insumo/Orçamento/Catálogo/Produção. Cobre o filtro em si (substring,
 * case-insensitive, vazio/nulo retorna tudo) — não existia teste dedicado até esta tarefa,
 * só {@link ClienteOrdenacaoIT}, que cobre {@code sort=}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClienteBuscaIT {

    @Autowired ClienteService clienteService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private UUID cAId, cBId, cCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-busca-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        cAId = novoCliente("Maria Souza", 1, "maria@test.com");
        cBId = novoCliente("João Souza", 2, "joao@test.com");
        cCId = novoCliente("Ana Pereira", 3, "ana@test.com");
    }

    private UUID novoCliente(String nome, int numero, String email) {
        return clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(numero).nome(nome).email(email).ativa(true).build()).getId();
    }

    @Test
    void filtraPorSubstringCaseInsensitive() {
        seed();
        List<UUID> ids = idsEncontrados("souza");
        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(cAId, cBId)));
        assertTrue(!ids.contains(cCId));
    }

    @Test
    void filtraPorSubstringMaiuscula() {
        seed();
        List<UUID> ids = idsEncontrados("MARIA");
        assertEquals(List.of(cAId), ids);
    }

    @Test
    void semTermoRetornaTodos() {
        seed();
        List<UUID> ids = idsEncontrados(null);
        assertEquals(3, ids.size());
    }

    @Test
    void termoEmBrancoRetornaTodos() {
        seed();
        List<UUID> ids = idsEncontrados("   ");
        assertEquals(3, ids.size());
    }

    @Test
    void semResultadoRetornaListaVazia() {
        seed();
        List<UUID> ids = idsEncontrados("inexistente");
        assertEquals(List.of(), ids);
    }

    private List<UUID> idsEncontrados(String busca) {
        Page<ClienteResponse> pagina = clienteService.listar(busca, PageRequest.of(0, 20));
        return pagina.getContent().stream().map(ClienteResponse::getId).toList();
    }
}
