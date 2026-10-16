package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.17.0 #973 (RN-NOVA-26, CEN-NOVO-76 a 78): histórico das vendas do Caixa — ordem, desempate, filtros e entrada hostil. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class HistoricoVendasIT {

    @Autowired HistoricoVendasService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired VendaCaixaRepository vendas;
    @Autowired CaixaTurnoRepository turnos;

    private Usuario usuario;
    private Cliente maria;
    private Cliente ana;
    private CaixaTurno turno;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("historico-caixa-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        maria = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria Silva").ehCliente(true).ativa(true).build());
        ana = clientes.save(Cliente.builder().usuario(usuario).numero(2).nome("Ana Ôrtega").ehCliente(true).ativa(true).build());
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(LocalDate.of(2026, 10, 1).atStartOfDay())
                .valorAbertura(BigDecimal.ZERO).status(StatusCaixaTurno.ABERTO).build());
        // CX-1 consumidor final, dia 9; CX-2 Maria, dia 10; CX-3 Ana, cancelada, dia 10
        venda(1, null, LocalDate.of(2026, 10, 9), "15", StatusVendaCaixa.CONCLUIDA);
        venda(2, maria, LocalDate.of(2026, 10, 10), "45", StatusVendaCaixa.CONCLUIDA);
        venda(3, ana, LocalDate.of(2026, 10, 10), "30", StatusVendaCaixa.CANCELADA);
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private void venda(int numero, Cliente cliente, LocalDate dia, String total, StatusVendaCaixa status) {
        vendas.save(VendaCaixa.builder().usuario(usuario).cliente(cliente).numero(numero).status(status).caixaTurno(turno)
                .dataVenda(dia.atTime(10, 0)).subtotal(bd(total)).total(bd(total)).build());
    }

    private List<String> ids(Page<HistoricoVendaResponse> p) {
        return p.getContent().stream().map(HistoricoVendaResponse::identificador).toList();
    }

    @Test
    void semOrdenacaoPedidaMaisRecentePrimeiroComDesempatePeloNumeroMaiorPrimeiro() {
        assertEquals(List.of("CX-3", "CX-2", "CX-1"), ids(service.listar(null, null, null, null, null, null, 0, 20)));
    }

    @Test
    void ordenaPeloTotalNosDoisSentidosComDesempateNoNumero() {
        assertEquals(List.of("CX-1", "CX-3", "CX-2"), ids(service.listar(null, null, null, null, null, "total,asc", 0, 20)));
        assertEquals(List.of("CX-2", "CX-3", "CX-1"), ids(service.listar(null, null, null, null, null, "total,desc", 0, 20)));
    }

    @Test
    void clienteOrdenaPeloTextoExibidoComConsumidorFinalESemAcento() {
        // "Ana" < "Consumidor final" < "Maria"
        assertEquals(List.of("CX-3", "CX-1", "CX-2"), ids(service.listar(null, null, null, null, null, "cliente,asc", 0, 20)));
    }

    @Test
    void statusConcluidaAntesDeCanceladaNoSentidoCrescenteComDesempateNoNumero() {
        assertEquals(List.of("CX-2", "CX-1", "CX-3"), ids(service.listar(null, null, null, null, null, "status,asc", 0, 20)));
    }

    @Test
    void buscaPorNumeroOuPorNomeDoCliente() {
        assertEquals(List.of("CX-2"), ids(service.listar(null, null, null, null, "CX-2", null, 0, 20)));
        assertEquals(List.of("CX-2"), ids(service.listar(null, null, null, null, "2", null, 0, 20)));
        assertEquals(List.of("CX-2"), ids(service.listar(null, null, null, null, "maria", null, 0, 20)));
        assertEquals(List.of("CX-3"), ids(service.listar(null, null, null, null, "ortega", null, 0, 20)), "sem acento e sem maiúsculas");
    }

    @Test
    void filtraPorStatusEPorFormaNaoInformada() {
        assertEquals(List.of("CX-3"), ids(service.listar(null, null, List.of("CANCELADA"), null, null, null, 0, 20)));
        assertEquals(3, service.listar(null, null, null, "Não informada", null, null, 0, 20).getTotalElements());
        assertEquals(0, service.listar(null, null, null, "Pix", null, null, 0, 20).getTotalElements());
    }

    @Test
    void consumidorFinalAparecePorExtenso() {
        HistoricoVendaResponse cx1 = service.listar(null, null, null, null, "1", null, 0, 20).getContent().get(0);
        assertEquals("Consumidor final", cx1.cliente());
    }

    @Test
    void paginaDeVinteEConfereOTotalDoFiltro() {
        Page<HistoricoVendaResponse> segunda = service.listar(null, null, null, null, null, null, 1, 2);
        assertEquals(3, segunda.getTotalElements());
        assertEquals(List.of("CX-1"), ids(segunda));
    }

    @Test
    void entradaHostilViraBloqueioNuncaErroInterno() {
        assertThrows(BusinessException.class, () -> service.listar(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), null, null, null, null, 0, 20));
        assertThrows(BusinessException.class, () -> service.listar(null, null, null, null, null, null, -1, 20));
        assertThrows(BusinessException.class, () -> service.listar(null, null, null, null, null, null, 0, 0));
        assertThrows(BusinessException.class, () -> service.listar(null, null, null, null, null, null, 0, 101));
        assertThrows(BusinessException.class, () -> service.listar(null, null, null, null, null, "senhaHash,asc", 0, 20));
    }
}
