package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.dto.request.agenda.MoverCompromissoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.MovimentoCompromissoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.18.0 (#1181, RN-NOVA-43 e 44) — simular e mover o card de recebimento de compra, e as recusas de Saldo e avulso. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgendaMovimentoIT {

    @Autowired AgendaMovimentoService service;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente fornecedor;
    private LocalDate hoje;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder().email("agenda-mov-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        fornecedor = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Fornecedor").ativa(true).build());
    }

    private Compra compra(StatusCompra status, int previsaoEmDias) {
        return compraRepository.save(Compra.builder().usuario(usuario).numero(1).status(status).dataCompra(hoje.minusDays(1))
                .fornecedor(fornecedor).previsaoEntrega(hoje.plusDays(previsaoEmDias)).build());
    }

    private MoverCompromissoRequest pedido(TipoCompromisso tipo, UUID id, LocalDate data) {
        return new MoverCompromissoRequest(tipo, id, data);
    }

    @Test
    void simularNaoGravaEMoverGrava() {
        seed();
        Compra c = compra(StatusCompra.PENDENTE_RECEBIMENTO, 3);
        LocalDate nova = hoje.plusDays(10);
        MovimentoCompromissoResponse s = service.simular(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), nova));
        assertEquals(hoje.plusDays(3), s.dataAntes());
        assertEquals(nova, s.dataDepois());
        assertNull(s.bloqueio());
        assertEquals(hoje.plusDays(3), compraRepository.findById(c.getId()).orElseThrow().getPrevisaoEntrega());

        service.mover(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), nova));
        assertEquals(nova, compraRepository.findById(c.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void dataAnteriorAHojeBloqueiaEMoverRecusa() {
        seed();
        Compra c = compra(StatusCompra.PENDENTE_RECEBIMENTO, 3);
        MoverCompromissoRequest req = pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), hoje.minusDays(30));
        assertNotNull(service.simular(req).bloqueio());
        assertThrows(BusinessException.class, () -> service.mover(req));
        assertEquals(hoje.plusDays(3), compraRepository.findById(c.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void compraRecebidaNaoMove() {
        seed();
        Compra c = compra(StatusCompra.CONFIRMADA, 3);
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), hoje.plusDays(5))));
    }

    @Test
    void saldoEAvulsoNaoMovemPorAqui() {
        seed();
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.SALDO_A_RECEBER, UUID.randomUUID(), hoje)));
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.AVULSO, UUID.randomUUID(), hoje)));
    }

    @Test
    void idDeOutraContaOuInexistenteDa404() {
        seed();
        Compra c = compra(StatusCompra.PENDENTE_RECEBIMENTO, 3);
        Usuario outra = usuarioRepository.save(Usuario.builder().email("agenda-mov-outra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(outra.getEmail(), null, List.of()));
        assertThrows(ResourceNotFoundException.class, () -> service.mover(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), hoje.plusDays(5))));
        assertThrows(ResourceNotFoundException.class, () -> service.simular(pedido(TipoCompromisso.ENTREGA_ESTIMADA, UUID.randomUUID(), hoje.plusDays(5))));
        assertEquals(hoje.plusDays(3), compraRepository.findById(c.getId()).orElseThrow().getPrevisaoEntrega());
    }

    @Test
    void dataExtremaEhRecusada() {
        seed();
        Compra c = compra(StatusCompra.PENDENTE_RECEBIMENTO, 3);
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), LocalDate.of(1999, 12, 31))));
        assertThrows(BusinessException.class, () -> service.simular(pedido(TipoCompromisso.RECEBIMENTO_COMPRA, c.getId(), LocalDate.of(2101, 1, 1))));
    }
}
