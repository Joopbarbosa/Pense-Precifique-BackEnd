package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #354 — GET /orcamentos?sort= com campo inexistente na entidade derrubava a request com 500
 * (UnknownPathException/InvalidDataAccessApiUsageException); passou a validar contra allowlist
 * (PageableOrdenacaoResolver) e responder 400. Cobre os 5 campos ordenáveis (total, createdAt,
 * status, cliente.nome, numero) e a rejeição de campo fora da allowlist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoOrdenacaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;

    private Usuario usuario;
    private UUID oAId, oBId, oCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-ord-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Cliente clienteAlfa = novoCliente("Alfa", 1);
        Cliente clienteBravo = novoCliente("Bravo", 2);
        Cliente clienteCharlie = novoCliente("Charlie", 3);

        oAId = novoOrcamento(clienteAlfa, 100, new BigDecimal("50.00"),
                LocalDateTime.of(2026, 1, 10, 10, 0), StatusOrcamento.RASCUNHO);
        oBId = novoOrcamento(clienteBravo, 101, new BigDecimal("150.00"),
                LocalDateTime.of(2026, 1, 20, 10, 0), StatusOrcamento.APROVADO);
        oCId = novoOrcamento(clienteCharlie, 102, new BigDecimal("100.00"),
                LocalDateTime.of(2026, 1, 5, 10, 0), StatusOrcamento.ENVIADO);
    }

    private Cliente novoCliente(String nome, int numero) {
        return clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(numero).nome(nome).ativa(true).build());
    }

    private UUID novoOrcamento(Cliente cliente, int numero, BigDecimal total, LocalDateTime createdAt,
                                StatusOrcamento status) {
        Orcamento orcamento = orcamentoRepository.save(Orcamento.builder()
                .usuario(usuario).cliente(cliente).numero(numero).total(total)
                .status(status).createdAt(createdAt).build());
        return orcamento.getId();
    }

    @Test
    void ordenaPorTotalAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "total"));
        assertEquals(List.of(oAId, oCId, oBId), asc); // 50, 100, 150

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "total"));
        assertEquals(List.of(oBId, oCId, oAId), desc);
    }

    @Test
    void ordenaPorCreatedAtAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "createdAt"));
        assertEquals(List.of(oCId, oAId, oBId), asc); // 05/01, 10/01, 20/01

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertEquals(List.of(oBId, oAId, oCId), desc);
    }

    @Test
    void ordenaPorNumeroAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "numero"));
        assertEquals(List.of(oAId, oBId, oCId), asc);

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "numero"));
        assertEquals(List.of(oCId, oBId, oAId), desc);
    }

    @Test
    void ordenaPorClienteNomeAlfabetico() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "cliente.nome"));
        assertEquals(List.of(oAId, oBId, oCId), asc); // Alfa, Bravo, Charlie

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "cliente.nome"));
        assertEquals(List.of(oCId, oBId, oAId), desc);
    }

    @Test
    void ordenaPorStatus() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "status"));
        // APROVADO < ENVIADO < RASCUNHO alfabeticamente
        assertEquals(List.of(oBId, oCId, oAId), asc);
    }

    @Test
    void campoForaDaAllowlistLancaBusinessException() {
        seed();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "identificador"));
        assertThrows(BusinessException.class,
                () -> orcamentoService.listar(null, null, null, null, pageable));
    }

    private List<UUID> idsNaOrdem(Sort sort) {
        Page<OrcamentoResponse> pagina = orcamentoService.listar(null, null, null, null,
                PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(OrcamentoResponse::getId).toList();
    }
}
