package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraItemResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #576/RN-NOVA-28 (V0.15.0) — desconto de linha e de nota. CEN-NOVO-37, 38 e 39. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraDescontoIT {

    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numero = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-desconto-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).unidadeMedida(un)
                .estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private static CompraItemRequest linha(Insumo i, String qtd, String cheio, TipoDesconto tipo, String desconto) {
        return new CompraItemRequest(i.getId(), null, new BigDecimal(qtd), null, new BigDecimal(cheio), tipo,
                desconto != null ? new BigDecimal(desconto) : null);
    }

    private static CompraRequest compra(List<CompraItemRequest> itens, TipoDesconto notaTipo, String nota) {
        return new CompraRequest(LocalDate.now(), false, null, false, null, null, itens, notaTipo,
                nota != null ? new BigDecimal(nota) : null);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    @Test
    void cen37_descontoDeLinhaEDeNotaComRateio() {
        Insumo fita = insumo("Fita de cetim");
        Insumo cola = insumo("Cola Branca 1L");
        CompraResponse c = compraService.confirmarNova(compra(List.of(
                linha(fita, "10", "30.00", TipoDesconto.PERCENTUAL, "10"),
                linha(cola, "3", "45.90", TipoDesconto.VALOR, "1.90")), TipoDesconto.PERCENTUAL, "5")).compra();

        CompraItemResponse f = c.itens().get(0);
        CompraItemResponse k = c.itens().get(1);
        igual("3.00", f.descontoLinha());
        igual("1.90", k.descontoLinha());
        igual("3.55", c.descontoNota());          // 71,00 × 5%
        igual("1.35", f.descontoNota());          // 3,55 × 27 ÷ 71
        igual("2.20", k.descontoNota());          // 3,55 × 44 ÷ 71
        igual("25.65", f.precoTotal());
        igual("41.80", k.precoTotal());
        igual("67.45", c.total());
        igual("75.90", c.totalCheio());
        igual("8.45", c.totalDescontos());
        // Custo médio da Fita usa o preço pago: estoque 0 → 25,65 ÷ 10 = 2,565.
        igual("2.565", insumoRepository.findById(fita.getId()).orElseThrow().getCustoUnitario());
    }

    @Test
    void cen38_descontoIgualAoPrecoCheioBloqueia() {
        Insumo papel = insumo("Papel");
        BusinessException e1 = assertThrows(BusinessException.class, () -> compraService.confirmarNova(compra(List.of(
                linha(papel, "1", "12.40", TipoDesconto.VALOR, "12.40")), null, null)));
        assertTrue(e1.getMessage().contains("o desconto precisa ser menor que o preço cheio"), e1.getMessage());
        BusinessException e2 = assertThrows(BusinessException.class, () -> compraService.confirmarNova(compra(List.of(
                linha(papel, "1", "12.40", TipoDesconto.PERCENTUAL, "100")), null, null)));
        assertTrue(e2.getMessage().contains("maior que 0 e menor que 100"), e2.getMessage());
        BusinessException e3 = assertThrows(BusinessException.class, () -> compraService.confirmarNova(compra(List.of(
                linha(papel, "1", "12.40", null, null)), TipoDesconto.VALOR, "12.40")));
        assertTrue(e3.getMessage().contains("desconto da nota precisa ser menor"), e3.getMessage());
    }

    @Test
    void cen39_sobraDeCentavosVaiParaAPrimeiraDeMaiorValor() {
        Insumo a = insumo("A");
        Insumo b = insumo("B");
        Insumo c = insumo("C");
        CompraResponse r = compraService.criarRascunho(compra(List.of(
                linha(a, "1", "10.00", null, null), linha(b, "1", "10.00", null, null), linha(c, "1", "10.00", null, null)),
                TipoDesconto.VALOR, "1.00"));
        igual("0.34", r.itens().get(0).descontoNota());
        igual("0.33", r.itens().get(1).descontoNota());
        igual("0.33", r.itens().get(2).descontoNota());
        igual("29.00", r.total());
    }

    @Test
    void duplicarCopiaOsDescontosEFormaAntigaSemDescontoContinuaValendo() {
        Insumo fita = insumo("Fita");
        CompraResponse original = compraService.confirmarNova(compra(List.of(
                linha(fita, "4", "18.35", TipoDesconto.VALOR, "0.35")), TipoDesconto.PERCENTUAL, "10")).compra();
        igual("16.20", original.total()); // (18,35 − 0,35) − 10% = 16,20
        CompraResponse copia = compraService.duplicar(original.id());
        igual("16.20", copia.total());
        assertEquals(TipoDesconto.PERCENTUAL, copia.descontoNotaTipo());

        // Request sem os campos novos (helpers antigos): precoTotal vale como preço cheio e pago.
        CompraResponse antiga = compraService.criarRascunho(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, BigDecimal.ONE, new BigDecimal("7.45")))));
        igual("7.45", antiga.itens().get(0).precoCheio());
        igual("7.45", antiga.total());
    }
}
