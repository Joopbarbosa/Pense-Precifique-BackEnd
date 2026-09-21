package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoContagensResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RN-NOVA-4 (V0.10.0, #336) — contadores por filtro agregados no backend, não sobre a janela
 * paginada já carregada no cliente (mesma causa raiz do bug original de #336).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoContagensIT {

    @Autowired InsumoService insumoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private final AtomicInteger contador = new AtomicInteger(1);

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-contadores-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo criar(boolean ativo, BigDecimal estoqueAtual, BigDecimal estoqueMinimo) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(contador.getAndIncrement())
                .nome("Insumo " + UUID.randomUUID()).unidadeMedida(unidadeMedida("un"))
                .custoUnitario(BigDecimal.TEN).estoqueAtual(estoqueAtual).estoqueMinimo(estoqueMinimo)
                .permitirEstoqueNegativo(true).fracionavel(true).ativo(ativo).build());
    }

    @Test
    void contadoresRefletemMaisDe20InsumosSemDependerDePaginacao() {
        seedUsuario();
        // 25 insumos ativos com estoque normal (força mais de 1 página de 20).
        for (int i = 0; i < 25; i++) {
            criar(true, new BigDecimal("100"), null);
        }
        criar(false, new BigDecimal("10"), null); // inativo
        criar(true, new BigDecimal("2"), new BigDecimal("5")); // estoque baixo
        criar(true, new BigDecimal("-3"), null); // estoque negativo
        criar(true, BigDecimal.ZERO, null); // nem negativo nem positivo (não conta em nenhum dos dois)

        InsumoContagensResponse contadores = insumoService.contagens();

        assertEquals(29, contadores.todos());
        assertEquals(28, contadores.ativos());
        assertEquals(1, contadores.inativos());
        assertEquals(1, contadores.estoqueBaixo());
        assertEquals(1, contadores.estoqueNegativo());
        // 25 normais + o inativo (10 > 0) + o de estoque baixo (2 > 0) — isPositive não filtra por
        // ativo, só pelo sinal do estoque (mesma semântica de ListaInsumosPage.tsx:isPositive).
        assertEquals(27, contadores.estoquePositivo());
    }

    private UnidadeMedida unidadeMedida(String sigla) {
        return unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), sigla)
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder()
                        .usuario(usuario).nome(sigla).sigla(sigla).build()));
    }
}
