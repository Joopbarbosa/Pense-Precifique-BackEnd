package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.CaixaTurno;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.17.0 #1002 (QA da #973, RN-NOVA-26, CEN-NOVO-77): as vendas do CEN-NOVO-76 — CX-1 R$ 15,00 em Dinheiro, CX-2 R$ 45,00 em Pix,
 * CX-3 R$ 25,00 em Pix e CX-4 R$ 25,00 em Pix cancelada. O empate de R$ 25,00 desempata pelo número, do maior para o menor,
 * nos dois sentidos; depois, os filtros de status, forma e busca do cenário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class HistoricoVendasEmpateIT {

    @Autowired HistoricoVendasService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired VendaCaixaRepository vendas;
    @Autowired VendaCaixaPagamentoRepository pagamentos;
    @Autowired CaixaTurnoRepository turnos;
    @Autowired MetodoPagamentoConfiguravelRepository metodos;

    private Usuario usuario;
    private CaixaTurno turno;
    private MetodoPagamentoConfiguravel dinheiro;
    private MetodoPagamentoConfiguravel pix;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("hist-empate-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        turno = turnos.save(CaixaTurno.builder().usuario(usuario).dataAbertura(LocalDate.of(2026, 10, 1).atStartOfDay())
                .valorAbertura(BigDecimal.ZERO).status(StatusCaixaTurno.ABERTO).build());
        dinheiro = metodos.save(MetodoPagamentoConfiguravel.builder().usuario(usuario).tipo(TipoMetodoPagamento.DINHEIRO).ativo(true).ordem(1).build());
        pix = metodos.save(MetodoPagamentoConfiguravel.builder().usuario(usuario).tipo(TipoMetodoPagamento.PIX).ativo(true).ordem(2).build());
        venda(1, "15", StatusVendaCaixa.CONCLUIDA, dinheiro, 9, 9);
        venda(2, "45", StatusVendaCaixa.CONCLUIDA, pix, 9, 10);
        venda(3, "25", StatusVendaCaixa.CONCLUIDA, pix, 10, 9);
        venda(4, "25", StatusVendaCaixa.CANCELADA, pix, 10, 11);
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private void venda(int numero, String total, StatusVendaCaixa status, MetodoPagamentoConfiguravel metodo, int dia, int hora) {
        VendaCaixa v = vendas.save(VendaCaixa.builder().usuario(usuario).numero(numero).status(status).caixaTurno(turno)
                .dataVenda(LocalDate.of(2026, 10, dia).atTime(hora, 0)).subtotal(bd(total)).total(bd(total)).build());
        pagamentos.save(VendaCaixaPagamento.builder().vendaCaixa(v).metodoPagamento(metodo).valor(bd(total)).build());
    }

    private List<String> ids(Page<HistoricoVendaResponse> p) {
        return p.getContent().stream().map(HistoricoVendaResponse::identificador).toList();
    }

    @Test
    void semOrdenacaoMostraDataMaisRecentePrimeiroDepoisNumeroDecrescenteComTotalDeQuatro() {
        Page<HistoricoVendaResponse> p = service.listar(null, null, null, null, null, null, 0, 20);
        assertEquals(List.of("CX-4", "CX-3", "CX-2", "CX-1"), ids(p));
        assertEquals(4, p.getTotalElements());
    }

    @Test
    void empateDeTotalDesempataPeloNumeroDoMaiorParaOMenorNosDoisSentidos() {
        assertEquals(List.of("CX-1", "CX-4", "CX-3", "CX-2"), ids(service.listar(null, null, null, null, null, "total,asc", 0, 20)));
        assertEquals(List.of("CX-2", "CX-4", "CX-3", "CX-1"), ids(service.listar(null, null, null, null, null, "total,desc", 0, 20)));
    }

    @Test
    void filtroDeStatusCanceladaMostraSoCx4() {
        assertEquals(List.of("CX-4"), ids(service.listar(null, null, List.of("CANCELADA"), null, null, null, 0, 20)));
    }

    @Test
    void formaPixComBuscaCx2MostraSoCx2() {
        assertEquals(List.of("CX-4", "CX-3", "CX-2"), ids(service.listar(null, null, null, "Pix", null, null, 0, 20)), "só as vendas em Pix");
        assertEquals(List.of("CX-2"), ids(service.listar(null, null, null, "Pix", "CX-2", null, 0, 20)));
    }

    @Test
    void buscaSemResultadoDevolveListaVaziaParaAMensagemNenhumaVendaEncontrada() {
        Page<HistoricoVendaResponse> p = service.listar(null, null, null, null, "7", null, 0, 20);
        assertTrue(p.getContent().isEmpty());
        assertEquals(0, p.getTotalElements());
    }
}
