package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RN-NOVA-5/DT-NOVA-4 (V0.14.0, #514) — "Baixa manual" generalizada para "Edição manual": mesmo
 * endpoint, campo {@code tipo} (ENTRADA/SAIDA) decide a direção. Motivo/observação (INS-007)
 * continuam obrigatórios e idênticos para as duas direções.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoBaixaManualIT {

    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-baixa-manual-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private static final String OBSERVACAO_VALIDA =
            "Ajuste manual de teste automatizado com mais de trinta caracteres.";

    private UUID criarInsumoComEstoque(BigDecimal estoqueInicial, boolean permitirEstoqueNegativo) {
        InsumoResponseDTO criado = insumoService.cadastrar(new InsumoCreateRequestDTO(
                "Insumo " + UUID.randomUUID(), null, unidadeMedida("kg").getId(), true, null, permitirEstoqueNegativo,
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("10")));
        Insumo insumo = insumoRepository.findById(criado.id()).orElseThrow();
        insumo.setEstoqueAtual(estoqueInicial);
        insumoRepository.save(insumo);
        return criado.id();
    }

    @Test
    void entradaManualAcrescentaEstoqueERegistraMovimentacaoTipoEntrada() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoque(new BigDecimal("10"), false);

        MovimentacaoInsumoResponseDTO mov = insumoService.baixaManual(insumoId, new BaixaManualInsumoRequestDTO(
                TipoMovimentacaoInsumo.ENTRADA, new BigDecimal("5"), MotivoMovimentacaoInsumo.CORRECAO,
                OBSERVACAO_VALIDA));

        assertEquals(TipoMovimentacaoInsumo.ENTRADA, mov.tipo());
        assertEquals(0, new BigDecimal("15").compareTo(insumoRepository.findById(insumoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void saidaManualContinuaSubtraindoEstoqueERegistraMovimentacaoTipoSaida() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoque(new BigDecimal("10"), false);

        MovimentacaoInsumoResponseDTO mov = insumoService.baixaManual(insumoId, new BaixaManualInsumoRequestDTO(
                TipoMovimentacaoInsumo.SAIDA, new BigDecimal("4"), MotivoMovimentacaoInsumo.PERDA,
                OBSERVACAO_VALIDA));

        assertEquals(TipoMovimentacaoInsumo.SAIDA, mov.tipo());
        assertEquals(0, new BigDecimal("6").compareTo(insumoRepository.findById(insumoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void entradaManualNuncaBloqueiaMesmoQuandoPermitirEstoqueNegativoEhFalso() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoque(new BigDecimal("0"), false);

        // ENTRADA só soma — não há como violar a trava de estoque negativo indo pra cima.
        insumoService.baixaManual(insumoId, new BaixaManualInsumoRequestDTO(
                TipoMovimentacaoInsumo.ENTRADA, new BigDecimal("3"), MotivoMovimentacaoInsumo.OUTRO,
                OBSERVACAO_VALIDA));

        assertEquals(0, new BigDecimal("3").compareTo(insumoRepository.findById(insumoId).orElseThrow().getEstoqueAtual()));
    }

    @Test
    void saidaManualContinuaBloqueadaSePermitirEstoqueNegativoForFalso() {
        seedUsuario();
        UUID insumoId = criarInsumoComEstoque(new BigDecimal("2"), false);

        BaixaManualInsumoRequestDTO request = new BaixaManualInsumoRequestDTO(
                TipoMovimentacaoInsumo.SAIDA, new BigDecimal("5"), MotivoMovimentacaoInsumo.PERDA,
                OBSERVACAO_VALIDA);

        assertThrows(BusinessException.class, () -> insumoService.baixaManual(insumoId, request));
        assertEquals(0, new BigDecimal("2").compareTo(insumoRepository.findById(insumoId).orElseThrow().getEstoqueAtual()));
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
