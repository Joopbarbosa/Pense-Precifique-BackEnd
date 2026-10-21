package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.dto.request.compra.AdiarPrevisaoCompraRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.18.0 (#549, RN-NOVA-18) — compra pendente de recebimento na Agenda e "Adiar" a previsão. CEN-NOVO-45, 47, 48, 50. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompromissosCompraIT {

    @Autowired AgendaService agendaService;
    @Autowired CompraService compraService;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente fornecedor;
    private LocalDate hoje;
    private int proximoNumero = 1;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder()
                .email("agenda-compra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        fornecedor = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Fornecedor Aviamentos").ativa(true).build());
    }

    private Compra pendente(int previsaoEmDias) {
        return compraRepository.save(Compra.builder().usuario(usuario).numero(proximoNumero++)
                .status(StatusCompra.PENDENTE_RECEBIMENTO).dataCompra(hoje.minusDays(1)).fornecedor(fornecedor)
                .previsaoEntrega(hoje.plusDays(previsaoEmDias)).build());
    }

    private List<CompromissoResponse> compras(int dias) {
        return agendaService.compromissos(hoje, hoje.plusDays(dias)).stream()
                .filter(c -> c.tipo() == TipoCompromisso.RECEBIMENTO_COMPRA).toList();
    }

    /** CEN-NOVO-45 — a previsão aparece no dia, com o fornecedor no título. */
    @Test
    void previsaoApareceNoDia() {
        seed();
        Compra c = pendente(3);
        List<CompromissoResponse> r = compras(7);
        assertEquals(1, r.size());
        assertEquals("Entrega de compra — COM-" + c.getNumero() + " (Fornecedor Aviamentos)", r.get(0).titulo());
        assertEquals(hoje.plusDays(3), r.get(0).dataExibicao());
        assertEquals(c.getId(), r.get(0).referenciaId());
    }

    /** CEN-NOVO-50 (v2) — compra atrasada fica no dia original e segue até hoje. */
    @Test
    void compraAtrasadaContinuaAteHoje() {
        seed();
        pendente(-3);
        CompromissoResponse c = compras(1).get(0);
        assertTrue(c.atrasado());
        assertEquals(3, c.diasAtraso());
        assertEquals(hoje.minusDays(3), c.dataExibicao());
        assertEquals(hoje, c.dataFim());
    }

    /** RN-NOVA-5 — confirmada, cancelada ou excluída sai da Agenda. */
    @Test
    void confirmadaCanceladaOuExcluidaSai() {
        seed();
        Compra c = pendente(2);
        assertEquals(1, compras(5).size());
        c.setStatus(StatusCompra.CONFIRMADA);
        compraRepository.save(c);
        assertTrue(compras(5).isEmpty());
        c.setStatus(StatusCompra.CANCELADA);
        compraRepository.save(c);
        assertTrue(compras(5).isEmpty());
        c.setStatus(StatusCompra.PENDENTE_RECEBIMENTO);
        c.setDeletedAt(java.time.LocalDateTime.now());
        compraRepository.save(c);
        assertTrue(compras(5).isEmpty());
    }

    /** CEN-NOVO-47 — adiar para data futura move o compromisso e a compra continua pendente. */
    @Test
    void adiarMoveOCompromisso() {
        seed();
        Compra c = pendente(2);
        compraService.adiarPrevisao(c.getId(), new AdiarPrevisaoCompraRequest(hoje.plusDays(7)));
        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, compraRepository.findById(c.getId()).orElseThrow().getStatus());
        assertEquals(hoje.plusDays(7), compras(10).get(0).dataExibicao());
        compraService.adiarPrevisao(c.getId(), new AdiarPrevisaoCompraRequest(hoje)); // hoje vale
        assertEquals(hoje, compras(10).get(0).dataExibicao());
    }

    /** CEN-NOVO-48 — data passada, anterior à data da compra, nula ou compra que não está pendente: bloqueia e a previsão não muda. */
    @Test
    void adiarInvalidoBloqueia() {
        seed();
        Compra c = pendente(2);
        assertThrows(BusinessException.class, () -> compraService.adiarPrevisao(c.getId(), new AdiarPrevisaoCompraRequest(hoje.minusDays(2))));
        assertEquals(hoje.plusDays(2), compraRepository.findById(c.getId()).orElseThrow().getPrevisaoEntrega());
        c.setStatus(StatusCompra.CONFIRMADA);
        compraRepository.save(c);
        assertThrows(BusinessException.class, () -> compraService.adiarPrevisao(c.getId(), new AdiarPrevisaoCompraRequest(hoje.plusDays(5))));
    }

    /** RN-NOVA-6 — compra de outra conta: não encontrada, sem revelar a existência. */
    @Test
    void compraDeOutraContaNaoEncontrada() {
        seed();
        Compra c = pendente(2);
        Usuario outro = usuarioRepository.save(Usuario.builder().email("outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outro.getEmail(), null, List.of()));
        assertThrows(com.penseprecifique.api.shared.exception.ResourceNotFoundException.class,
                () -> compraService.adiarPrevisao(c.getId(), new AdiarPrevisaoCompraRequest(hoje.plusDays(5))));
        assertTrue(compras(5).isEmpty());
    }
}
