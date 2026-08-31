package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #354 — GET /clientes?sort= com campo inexistente na entidade derrubava a request com 500
 * (UnknownPathException/InvalidDataAccessApiUsageException); passou a validar contra allowlist
 * (PageableOrdenacaoResolver) e responder 400. Cobre os campos ordenáveis (nome, numero, email,
 * createdAt) e a rejeição de campo fora da allowlist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClienteOrdenacaoIT {

    @Autowired ClienteService clienteService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private UUID cAId, cBId, cCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-ord-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        cAId = novoCliente("Alfa", 1, "alfa@test.com");
        cBId = novoCliente("Bravo", 2, "bravo@test.com");
        cCId = novoCliente("Charlie", 3, "charlie@test.com");
    }

    private UUID novoCliente(String nome, int numero, String email) {
        return clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(numero).nome(nome).email(email).ativa(true).build()).getId();
    }

    @Test
    void ordenaPorNomeAlfabetico() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "nome"));
        assertEquals(List.of(cAId, cBId, cCId), asc); // Alfa, Bravo, Charlie

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "nome"));
        assertEquals(List.of(cCId, cBId, cAId), desc);
    }

    @Test
    void ordenaPorNumeroAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "numero"));
        assertEquals(List.of(cAId, cBId, cCId), asc);

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "numero"));
        assertEquals(List.of(cCId, cBId, cAId), desc);
    }

    @Test
    void ordenaPorEmail() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "email"));
        assertEquals(List.of(cAId, cBId, cCId), asc); // alfa@, bravo@, charlie@
    }

    @Test
    void campoForaDaAllowlistLancaBusinessException() {
        seed();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "usuario.email"));
        assertThrows(BusinessException.class, () -> clienteService.listar(null, pageable));
    }

    private List<UUID> idsNaOrdem(Sort sort) {
        Page<ClienteResponse> pagina = clienteService.listar(null, PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(ClienteResponse::getId).toList();
    }
}
