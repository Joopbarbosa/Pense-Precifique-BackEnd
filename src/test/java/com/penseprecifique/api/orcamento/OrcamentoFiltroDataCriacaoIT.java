package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.response.OrcamentoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frente 3/P-BE-CONSOLIDADO-001 — filtro dataCriacaoDe/dataCriacaoAte em GET /orcamentos, mesmo
 * padrão de dataInicioDe/dataInicioAte já implementado em GET /producoes (item avulso
 * P-BE-NUMERO-SORT/#184/#192). Grava createdAt manualmente pra controlar o cenário — Orcamento só
 * usa "now()" no @PrePersist quando o campo chega nulo, então setar explicitamente sobrevive.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoFiltroDataCriacaoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-filtrodata-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Filtro Data").ativa(true).build());
    }

    private Orcamento novoOrcamento(int numero, LocalDateTime createdAt, StatusOrcamento status) {
        return orcamentoRepository.save(Orcamento.builder()
                .usuario(usuario).cliente(cliente).numero(numero)
                .status(status != null ? status : StatusOrcamento.RASCUNHO)
                .createdAt(createdAt).build());
    }

    @Test
    void filtraIsoladoPorIntervaloDeDataCriacao() {
        seedUsuarioECliente();
        novoOrcamento(1, LocalDateTime.of(2026, 1, 5, 10, 0), null);
        novoOrcamento(2, LocalDateTime.of(2026, 1, 15, 10, 0), null);
        novoOrcamento(3, LocalDateTime.of(2026, 2, 1, 10, 0), null);

        Page<OrcamentoResponse> resultado = orcamentoService.listar(
                null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), PageRequest.of(0, 20));

        assertEquals(2, resultado.getTotalElements());
    }

    @Test
    void combinaComBuscaEStatus() {
        seedUsuarioECliente();
        novoOrcamento(1, LocalDateTime.of(2026, 1, 5, 10, 0), StatusOrcamento.ENVIADO);
        novoOrcamento(2, LocalDateTime.of(2026, 1, 10, 10, 0), StatusOrcamento.RASCUNHO);

        Page<OrcamentoResponse> resultado = orcamentoService.listar(
                StatusOrcamento.ENVIADO, "Filtro Data",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), PageRequest.of(0, 20));

        assertEquals(1, resultado.getTotalElements());
        assertEquals(StatusOrcamento.ENVIADO, resultado.getContent().get(0).getStatus());
    }

    @Test
    void intervaloSemCorrespondenciaRetornaVazio() {
        seedUsuarioECliente();
        novoOrcamento(1, LocalDateTime.of(2026, 1, 5, 10, 0), null);

        Page<OrcamentoResponse> resultado = orcamentoService.listar(
                null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 20));

        assertTrue(resultado.isEmpty());
    }

    @Test
    void semParametrosDeDataMantemComportamentoAtual() {
        seedUsuarioECliente();
        novoOrcamento(1, LocalDateTime.of(2026, 1, 5, 10, 0), null);
        novoOrcamento(2, LocalDateTime.of(2026, 6, 1, 10, 0), null);

        Page<OrcamentoResponse> resultado = orcamentoService.listar(
                null, null, null, null, PageRequest.of(0, 20));

        assertEquals(2, resultado.getTotalElements());
    }
}
