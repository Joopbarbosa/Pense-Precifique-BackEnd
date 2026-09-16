package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-1 (V0.10.0, #442, altera INS-003) — cadastro de insumo não gera mais MovimentacaoInsumo/
 * LoteCompra automática. Custo unitário continua sendo calculado e persistido desde o cadastro,
 * mas o histórico de movimentação nasce vazio e o estoque nasce em 0 — entrada real de estoque só
 * via "Registrar compra" ou "Entrada manual".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoCadastroSemMovimentacaoAutomaticaIT {

    @Autowired InsumoService insumoService;
    @Autowired MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private void seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("cadastro-sem-mov-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void cadastroComCustoEQuantidadeNaoGeraMovimentacaoNemLote() {
        seedUsuario();

        // R$ 45,00 ÷ 10 unidades = custo unitário R$ 4,50 — valores não-redondos de propósito.
        InsumoCreateRequestDTO request = new InsumoCreateRequestDTO(
                "Papelão 30x30cm " + UUID.randomUUID(), null, "un", true, null, true,
                BigDecimal.ZERO, new BigDecimal("45.00"), new BigDecimal("10"));

        InsumoResponseDTO criado = insumoService.cadastrar(request);

        assertEquals(0, new BigDecimal("4.500000").compareTo(criado.custoUnitario()));
        assertEquals(0, BigDecimal.ZERO.compareTo(criado.estoqueAtual()));

        Page<?> movimentacoes = movimentacaoInsumoRepository
                .findByInsumoIdOrderByCreatedAtDesc(criado.id(), PageRequest.of(0, 20));
        assertTrue(movimentacoes.isEmpty(),
                "Cadastro não deve gerar nenhuma MovimentacaoInsumo (RN-NOVA-1/#442)");
    }
}
