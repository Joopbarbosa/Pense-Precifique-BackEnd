package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
import com.penseprecifique.api.shared.dto.response.compra.EvolucaoPrecoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** V0.15.0 — #548 (RN-NOVA-15). Cenários CEN-NOVO-24 e CEN-NOVO-25. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DashboardCompraIT {

    @Autowired DashboardCompraService dashboardCompraService;
    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroCadastro = 1;
    private final LocalDate hoje = LocalDate.now();

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("dash-compra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private Cliente fornecedor(String nome) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(false).ehFornecedor(true).ativa(true).build());
    }

    private CompraResponse confirmada(LocalDate data, Cliente f, Insumo i, String qtd, String preco) {
        return compraService.confirmarNova(new CompraRequest(data, false, f != null ? f.getId() : null, false, null, null,
                List.of(new CompraItemRequest(i.getId(), null, new BigDecimal(qtd), new BigDecimal(preco))))).compra();
    }

    @Test
    void cen24_dashboardIgnoraRascunhoECancelada() {
        Insumo cola = insumo("Cola");
        confirmada(hoje, null, cola, "10", "100.00");                                       // COM-10 do cenário
        CompraResponse cancelada = confirmada(hoje, null, cola, "5", "50.00");
        compraService.cancelar(cancelada.id(), new CancelarCompraRequest("Pedido duplicado por engano no sistema.", true));
        compraService.criarRascunho(new CompraRequest(hoje, false, null, false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, BigDecimal.ONE, new BigDecimal("30.00")))));

        DashboardComprasResponse d = dashboardCompraService.dashboard();
        assertEquals(0, new BigDecimal("100.00").compareTo(d.totalGastoMes()));
        assertEquals(0, new BigDecimal("100.00").compareTo(d.totalGastoAno()));
    }

    @Test
    void totalDoAnoSomaMesesAnterioresEFornecedorMaisUsado() {
        Insumo cola = insumo("Cola");
        Cliente papelaria = fornecedor("Papelaria Central");
        Cliente atacado = fornecedor("Atacado Arte");
        LocalDate inicioDoAno = hoje.withDayOfYear(1);
        confirmada(hoje, papelaria, cola, "1", "12.40");
        confirmada(inicioDoAno, papelaria, cola, "1", "7.35");
        confirmada(hoje, atacado, cola, "1", "9.99");
        confirmada(hoje.minusYears(1), atacado, cola, "1", "99.00"); // ano passado: fora dos totais
        confirmada(hoje.minusYears(1), atacado, cola, "1", "99.00");

        DashboardComprasResponse d = dashboardCompraService.dashboard();
        boolean janeiro = hoje.getMonthValue() == 1;
        assertEquals(0, new BigDecimal(janeiro ? "29.74" : "22.39").compareTo(d.totalGastoMes()));
        assertEquals(0, new BigDecimal("29.74").compareTo(d.totalGastoAno()));
        // Atacado aparece em 3 compras (2 do ano passado), Papelaria em 2
        assertEquals("Atacado Arte", d.fornecedorMaisUsado().fornecedor().nome());
        assertEquals(3, d.fornecedorMaisUsado().quantidadeCompras());
    }

    @Test
    void insumoComMaiorAumentoEm90Dias() {
        Insumo cola = insumo("Cola");
        Insumo fita = insumo("Fita");
        Insumo papel = insumo("Papel");
        confirmada(hoje.minusDays(60), null, cola, "2", "24.00"); // 12,00
        confirmada(hoje.minusDays(5), null, cola, "2", "30.00");  // 15,00 → +25,00%
        confirmada(hoje.minusDays(40), null, fita, "10", "15.00"); // 1,50
        confirmada(hoje.minusDays(3), null, fita, "10", "16.50");  // 1,65 → +10,00%
        confirmada(hoje.minusDays(120), null, papel, "1", "1.00"); // fora da janela
        confirmada(hoje.minusDays(2), null, papel, "1", "9.00");   // só 1 na janela: não conta

        DashboardComprasResponse.InsumoMaiorAumento a = dashboardCompraService.dashboard().insumoMaiorAumento();
        assertEquals("Cola", a.insumo().nome());
        assertEquals(new BigDecimal("25.00"), a.variacaoPercentual());
        assertEquals(0, new BigDecimal("12.00").compareTo(a.precoInicial()));
        assertEquals(0, new BigDecimal("15.00").compareTo(a.precoFinal()));
    }

    @Test
    void semDadosSuficientes() {
        Insumo cola = insumo("Cola");
        confirmada(hoje, null, cola, "1", "12.00");
        DashboardComprasResponse d = dashboardCompraService.dashboard();
        assertNull(d.insumoMaiorAumento());
        assertNull(d.fornecedorMaisUsado());
    }

    @Test
    void cen25_graficoDePrecoPago() {
        Insumo cola = insumo("Cola Branca 1L");
        Cliente papelaria = fornecedor("Papelaria Central");
        confirmada(hoje.minusDays(50), papelaria, cola, "3", "36.00"); // 12,00
        confirmada(hoje.minusDays(10), null, cola, "2", "30.00");      // 15,00
        confirmada(hoje.minusMonths(5), null, cola, "1", "1.00");      // fora de 3 meses

        EvolucaoPrecoResponse e = dashboardCompraService.evolucaoPreco(List.of(cola.getId()), null, null);
        assertEquals(hoje.minusMonths(3), e.de());
        List<EvolucaoPrecoResponse.Ponto> pontos = e.series().get(0).pontos();
        assertEquals(2, pontos.size());
        assertEquals(0, new BigDecimal("12.00").compareTo(pontos.get(0).precoUnitarioPago()));
        assertEquals("Papelaria Central", pontos.get(0).fornecedor());
        assertEquals(hoje.minusDays(50), pontos.get(0).data());
        assertEquals(new BigDecimal("0.00"), pontos.get(0).variacaoPercentual());
        assertEquals(0, new BigDecimal("15.00").compareTo(pontos.get(1).precoUnitarioPago()));
        assertEquals(new BigDecimal("25.00"), pontos.get(1).variacaoPercentual());
        assertEquals("COM-2", pontos.get(1).identificador());
    }

    @Test
    void graficoLimitaA5Insumos() {
        List<UUID> seis = List.of(insumo("A").getId(), insumo("B").getId(), insumo("C").getId(),
                insumo("D").getId(), insumo("E").getId(), insumo("F").getId());
        assertEquals("Escolha no máximo 5 insumos.", assertThrows(BusinessException.class,
                () -> dashboardCompraService.evolucaoPreco(seis, null, null)).getMessage());
        assertEquals("Escolha pelo menos um insumo.", assertThrows(BusinessException.class,
                () -> dashboardCompraService.evolucaoPreco(List.of(), null, null)).getMessage());
    }
}
