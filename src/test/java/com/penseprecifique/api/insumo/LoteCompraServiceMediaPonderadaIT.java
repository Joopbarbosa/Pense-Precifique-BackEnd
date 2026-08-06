package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blindagem — LoteCompraService#registrarCompraIndividual, média ponderada de custo com
 * estoqueAtual possivelmente negativo (RN-059, permitirEstoqueNegativo=true por padrão). Ver
 * comentário em LoteCompraService.java sobre a regra de fallback (estoque negativo tratado como 0
 * só para o cálculo do custo, nunca para o estoqueAtual real gravado).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LoteCompraServiceMediaPonderadaIT {

    @Autowired InsumoService insumoService;
    @Autowired LoteCompraService loteCompraService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private void seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("media-ponderada-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    /** Cria insumo com 10 unidades a custo unitário 10 (compra inicial de R$100). */
    private UUID criarInsumoComEstoqueDez() {
        InsumoResponseDTO criado = insumoService.cadastrar(new InsumoCreateRequestDTO(
                "Insumo " + UUID.randomUUID(), null, "kg", true, null, true,
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("10")));
        return criado.id();
    }

    private void baixarParaNegativo(UUID insumoId, BigDecimal quantidade) {
        insumoService.baixaManual(insumoId, new BaixaManualInsumoRequestDTO(
                quantidade, MotivoMovimentacaoInsumo.CORRECAO,
                "Baixa de teste automatizado para forçar estoque negativo (blindagem RN-084)."));
    }

    @Test
    void casoTrivialEstoquePositivoContinuaCalculandoMediaPonderadaNormal() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoqueDez(); // estoque 10, custo 10

        // Compra 10 unidades a custo 20 → média: (10*10 + 200) / (10+10) = 300/20 = 15
        loteCompraService.registrarCompraIndividual(
                insumoRepository.findById(insumoId).orElseThrow(),
                new BigDecimal("10"), new BigDecimal("200"), UUID.randomUUID());

        Insumo insumo = insumoRepository.findById(insumoId).orElseThrow();
        assertEquals(0, new BigDecimal("15.000000").compareTo(insumo.getCustoUnitario()));
        assertEquals(0, new BigDecimal("20").compareTo(insumo.getEstoqueAtual()));
    }

    @Test
    void estoqueNegativoNaoCompensadoIntegralmenteNaoGeraCustoNegativo() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoqueDez(); // estoque 10, custo 10
        baixarParaNegativo(insumoId, new BigDecimal("30")); // estoque vira -20

        Insumo antes = insumoRepository.findById(insumoId).orElseThrow();
        assertEquals(0, new BigDecimal("-20.0000").compareTo(antes.getEstoqueAtual()));

        // Compra 25 unidades por R$125 (custo real 5/unidade) com estoque anterior -20.
        // Sem a blindagem: ((-20*10)+125)/(-20+25) = -75/5 = -15 (custo negativo, sem sentido).
        // Com a blindagem (estoque negativo tratado como 0 no cálculo): (0+125)/(0+25) = 5.
        loteCompraService.registrarCompraIndividual(
                antes, new BigDecimal("25"), new BigDecimal("125"), UUID.randomUUID());

        Insumo depois = insumoRepository.findById(insumoId).orElseThrow();
        assertTrue(depois.getCustoUnitario().signum() >= 0,
                "custoUnitario não pode ficar negativo: " + depois.getCustoUnitario());
        assertEquals(0, new BigDecimal("5.000000").compareTo(depois.getCustoUnitario()));
        // Estoque real (não o usado no cálculo) continua sendo a soma de fato: -20 + 25 = 5.
        assertEquals(0, new BigDecimal("5.0000").compareTo(depois.getEstoqueAtual()));
    }

    @Test
    void compraQueCompensaExatamenteODeficitNaoLancaDivisaoPorZero() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoqueDez(); // estoque 10, custo 10
        baixarParaNegativo(insumoId, new BigDecimal("30")); // estoque vira -20

        Insumo antes = insumoRepository.findById(insumoId).orElseThrow();

        // Compra exatamente 20 unidades por R$200 (custo real 10/unidade) — sem a blindagem, o
        // denominador (estoqueAnterior + quantidade) seria -20+20 = 0 → ArithmeticException.
        loteCompraService.registrarCompraIndividual(
                antes, new BigDecimal("20"), new BigDecimal("200"), UUID.randomUUID());

        Insumo depois = insumoRepository.findById(insumoId).orElseThrow();
        assertEquals(0, new BigDecimal("10.000000").compareTo(depois.getCustoUnitario()));
        assertEquals(0, BigDecimal.ZERO.setScale(4).compareTo(depois.getEstoqueAtual()));
    }
}
