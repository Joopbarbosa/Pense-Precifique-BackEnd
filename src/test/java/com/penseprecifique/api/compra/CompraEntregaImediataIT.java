package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 — #957 (RN-NOVA-4, DT-NOVA-1). Cenários CEN-NOVO-10, 11, 12 e 14. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraEntregaImediataIT {

    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;

    private Usuario usuario;
    private Insumo fita;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("entrega-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        UnidadeMedida m = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
        fita = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Fita de cetim")
                .unidadeMedida(m).estoqueAtual(new BigDecimal("10")).custoUnitario(new BigDecimal("2.00"))
                .permitirEstoqueNegativo(true).build());
    }

    private CompraRequest request(LocalDate data, Boolean entregaImediata, LocalDate previsao) {
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("10"), new BigDecimal("30.00")));
        return new CompraRequest(data, false, null, false, null, null, itens, null, null, null, entregaImediata, previsao);
    }

    @Test
    void cen10_semInformarOCampoVaiComoSimEAPrevisaoNaoEGuardada() {
        CompraResponse r = compraService.criarRascunho(request(LocalDate.now(), null, LocalDate.now().plusDays(5)));

        assertEquals(Boolean.TRUE, r.entregaImediata());
        assertNull(r.previsaoEntrega());
    }

    @Test
    void cen10_rascunhoComNaoAceitaFicarSemPrevisao() {
        CompraResponse r = compraService.criarRascunho(request(LocalDate.now(), false, null));

        assertEquals(Boolean.FALSE, r.entregaImediata());
        assertNull(r.previsaoEntrega());
        assertEquals(StatusCompra.RASCUNHO, r.status());
    }

    @Test
    void cen11_entregaImediataSimConfirmaDaEntradaNoEstoqueEGravaADataDeEntrada() {
        LocalDate hoje = LocalDate.now();
        CompraResponse r = compraService.confirmarNova(request(hoje, true, null)).compra();

        assertEquals(StatusCompra.CONFIRMADA, r.status());
        assertEquals(hoje, r.dataEntrada());
        Insumo i = insumoRepository.findById(fita.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20").compareTo(i.getEstoqueAtual()));
        assertEquals(0, new BigDecimal("2.50").compareTo(i.getCustoUnitario()));
    }

    @Test
    void cen12_naoSemPrevisaoBloqueiaEOEstoqueNaoMuda() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> compraService.confirmarNova(request(LocalDate.now(), false, null)));

        assertTrue(e.getMessage().contains("Informe a previsão de entrega"));
        assertEquals(0, new BigDecimal("10").compareTo(insumoRepository.findById(fita.getId()).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void cen14_previsaoAnteriorAHojeOuADataDaCompraBloqueia() {
        LocalDate hoje = LocalDate.now();

        BusinessException anteriorHoje = assertThrows(BusinessException.class,
                () -> compraService.confirmarNova(request(hoje, false, hoje.minusDays(1))));
        assertTrue(anteriorHoje.getMessage().contains("anterior à data da compra"));

        BusinessException anteriorCompra = assertThrows(BusinessException.class,
                () -> compraService.confirmarNova(request(hoje.minusDays(3), false, hoje.minusDays(2))));
        assertTrue(anteriorCompra.getMessage().contains("anterior a hoje"));

        CompraResponse ok = compraService.criarRascunho(request(hoje, false, hoje));
        assertEquals(hoje, ok.previsaoEntrega());
    }

    @Test
    void duplicarVoltaParaSimENaoCopiaAPrevisao() {
        LocalDate hoje = LocalDate.now();
        CompraResponse r = compraService.confirmarNova(request(hoje, true, null)).compra();

        CompraResponse copia = compraService.duplicar(r.id());

        assertEquals(Boolean.TRUE, copia.entregaImediata());
        assertNull(copia.previsaoEntrega());
        assertNull(copia.dataEntrada());
    }
}
