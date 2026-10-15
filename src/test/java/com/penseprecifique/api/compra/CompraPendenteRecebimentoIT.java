package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraConfirmacaoResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraContagensResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.17.0 — #958 (RN-NOVA-5, RN-NOVA-6, DT-NOVA-5). Cenários CEN-NOVO-13, 15, 20, 21, 25 e 51. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompraPendenteRecebimentoIT {

    @Autowired CompraService compraService;
    @Autowired CompraRepository compraRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;

    private Usuario usuario;
    private Insumo fita;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("pendente-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        UnidadeMedida m = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Metro").sigla("m").build());
        fita = insumoRepository.save(Insumo.builder().usuario(usuario).numero(1).nome("Fita de cetim")
                .unidadeMedida(m).estoqueAtual(new BigDecimal("10")).custoUnitario(new BigDecimal("2.00"))
                .permitirEstoqueNegativo(true).build());
    }

    private CompraRequest pendente(String quantidade, LocalDate previsao) {
        return pendente(quantidade, previsao, LocalDate.now());
    }

    private CompraRequest pendente(String quantidade, LocalDate previsao, LocalDate dataCompra) {
        List<CompraItemRequest> itens = List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal(quantidade), new BigDecimal("30.00")));
        return new CompraRequest(dataCompra, false, null, false, null, null, itens, null, null, null, false, previsao);
    }

    private CompraResponse confirmarPendente(LocalDate previsao) {
        return compraService.confirmarNova(pendente("10", previsao)).compra();
    }

    private void forcarPrevisao(UUID id, LocalDate previsao) {
        Compra c = compraRepository.findById(id).orElseThrow();
        c.setPrevisaoEntrega(previsao);
        compraRepository.save(c);
    }

    private Insumo insumoAtual() {
        return insumoRepository.findById(fita.getId()).orElseThrow();
    }

    @Test
    void cen13_pendenteNaoMexeEmEstoqueCustoMovimentacaoNemImpacto() {
        LocalDate previsao = LocalDate.now().plusDays(6);
        CompraConfirmacaoResponse r = compraService.confirmarNova(pendente("10", previsao));

        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, r.compra().status());
        assertEquals(previsao, r.compra().previsaoEntrega());
        assertNull(r.compra().diasAtraso());
        assertFalse(r.impacto().alterouCustos());
        assertEquals(0, new BigDecimal("10").compareTo(insumoAtual().getEstoqueAtual()));
        assertEquals(0, new BigDecimal("2.00").compareTo(insumoAtual().getCustoUnitario()));
        assertTrue(movimentacaoInsumoRepository.findAll().stream()
                .noneMatch(m -> m.getInsumo().getId().equals(fita.getId())));
        assertNull(r.compra().dataEntrada());
    }

    @Test
    void cen15_atrasoSoDepoisDaPrevisaoEContaDiasInteiros() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(3));
        LocalDate hoje = LocalDate.now();

        forcarPrevisao(r.id(), hoje);
        assertNull(compraService.buscar(r.id()).diasAtraso());

        forcarPrevisao(r.id(), hoje.minusDays(3));
        assertEquals(3, compraService.buscar(r.id()).diasAtraso());
        CompraResumoResponse linha = compraService.listarComFiltros(List.of(), List.of(), null, null, List.of(), null, null,
                false, true, PageRequest.of(0, 10)).getContent().get(0);
        assertEquals(3, linha.diasAtraso());
    }

    @Test
    void cen20_cancelarPendenteNaoEstornaNada() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(6));

        CompraConfirmacaoResponse c = compraService.cancelar(r.id(), new CancelarCompraRequest(
                "Fornecedor não tem mais o produto em estoque, pedido desfeito", null));

        assertEquals(StatusCompra.CANCELADA, c.compra().status());
        assertEquals(0, new BigDecimal("10").compareTo(insumoAtual().getEstoqueAtual()));
        assertTrue(movimentacaoInsumoRepository.findAll().stream()
                .noneMatch(m -> m.getInsumo().getId().equals(fita.getId())));
    }

    @Test
    void cen21_editarPendenteMantemPendenteEExcluirNaoReaproveitaONumero() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(6));
        LocalDate nova = LocalDate.now().plusDays(11);

        CompraResponse editada = compraService.atualizarRascunho(r.id(), pendente("12", nova));

        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, editada.status());
        assertEquals(nova, editada.previsaoEntrega());
        assertEquals(0, new BigDecimal("12").compareTo(editada.itens().get(0).quantidade()));

        compraService.excluirRascunho(r.id());
        assertThrows(RuntimeException.class, () -> compraService.buscar(r.id()));
        CompraResponse seguinte = compraService.criarRascunho(pendente("1", nova));
        assertEquals(r.numero() + 1, seguinte.numero());
    }

    @Test
    void editarPendenteExigePrevisaoEAceitaManterAPrevisaoJaAtrasada() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(6));
        assertThrows(BusinessException.class, () -> compraService.atualizarRascunho(r.id(), pendente("10", null)));

        LocalDate atrasada = LocalDate.now().minusDays(2);
        LocalDate compraAntiga = LocalDate.now().minusDays(10);
        forcarPrevisao(r.id(), atrasada);
        CompraResponse ok = compraService.atualizarRascunho(r.id(), pendente("11", atrasada, compraAntiga));
        assertEquals(atrasada, ok.previsaoEntrega());
        assertThrows(BusinessException.class,
                () -> compraService.atualizarRascunho(r.id(), pendente("11", LocalDate.now().minusDays(1), compraAntiga)));
    }

    @Test
    void cen25_contagensDePendentesEAtrasadas() {
        CompraResponse a = confirmarPendente(LocalDate.now().plusDays(3));
        confirmarPendente(LocalDate.now().plusDays(15));
        compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(fita.getId(), null, new BigDecimal("1"), new BigDecimal("3.00")))));
        forcarPrevisao(a.id(), LocalDate.now().minusDays(1));

        CompraContagensResponse c = compraService.contagens();

        assertEquals(2, c.pendentes());
        assertEquals(1, c.atrasadas());
        assertEquals(1, c.confirmadas());
        assertEquals(3, c.todas());
        assertEquals(2, compraService.listarComFiltros(List.of(StatusCompra.PENDENTE_RECEBIMENTO), List.of(), null, null,
                List.of(), null, null, false, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, compraService.listarComFiltros(List.of(), List.of(), null, null, List.of(), null, null,
                false, true, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void cen51_observacaoCurtaNoCancelamentoEBloqueadaPelaValidacaoEACompraSegue() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(6));

        var violacoes = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new CancelarCompraRequest("desisti", null));

        assertFalse(violacoes.isEmpty());
        assertEquals(StatusCompra.PENDENTE_RECEBIMENTO, compraService.buscar(r.id()).status());
    }

    @Test
    void pendenteNaoEDuplicadaNemTemPagamentoAlteradoPeloPatch() {
        CompraResponse r = confirmarPendente(LocalDate.now().plusDays(6));
        assertThrows(BusinessException.class, () -> compraService.duplicar(r.id()));
    }
}
