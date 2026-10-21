package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V0.17.0 #994 (RN-NOVA-31 item 3): com a paginação numerada, os filtros de estoque da lista de Insumos (baixo, negativo e
 * positivo) valem para todas as páginas — são aplicados no servidor, com os mesmos critérios dos contadores da lista.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoFiltroEstoqueIT {

    @Autowired InsumoService insumoService;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private final AtomicInteger contador = new AtomicInteger(1);

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder().email("insumo-filtro-estoque-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo criar(String nome, String estoque, String minimo, boolean ativo) {
        UnidadeMedida un = unidadeMedidaRepository.findByUsuarioIdAndSiglaIgnoreCaseAndDeletedAtIsNull(usuario.getId(), "un")
                .orElseGet(() -> unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("un").sigla("un").build()));
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(contador.getAndIncrement()).nome(nome).unidadeMedida(un)
                .custoUnitario(BigDecimal.TEN).estoqueAtual(new BigDecimal(estoque)).estoqueMinimo(minimo == null ? null : new BigDecimal(minimo))
                .permitirEstoqueNegativo(true).fracionavel(true).ativo(ativo).build());
    }

    private List<String> nomes(String estoque) {
        // página de 2 em 2 para provar que o filtro vale para todas as páginas, não só para a janela carregada
        Page<InsumoResponseDTO> p = insumoService.listar(null, null, false, estoque, PageRequest.of(0, 2));
        return p.getContent().stream().map(InsumoResponseDTO::nome).toList();
    }

    @Test
    void filtrosDeEstoqueSaoAplicadosNoServidorEComTotalDoFiltro() {
        seed();
        criar("A baixo 1", "1", "5", true);
        criar("B baixo 2", "2", "5", true);
        criar("C baixo 3", "0.5", "5", true);
        criar("D normal", "10", "5", true);
        criar("E negativo", "-3", null, true);
        criar("F inativo abaixo do mínimo", "1", "5", false);

        Page<InsumoResponseDTO> baixo = insumoService.listar(null, null, false, "BAIXO", PageRequest.of(0, 2));
        assertEquals(3, baixo.getTotalElements());
        assertEquals(List.of("A baixo 1", "B baixo 2"), baixo.getContent().stream().map(InsumoResponseDTO::nome).toList());
        assertEquals(List.of("C baixo 3"), insumoService.listar(null, null, false, "BAIXO", PageRequest.of(1, 2))
                .getContent().stream().map(InsumoResponseDTO::nome).toList());

        assertEquals(List.of("E negativo"), nomes("negativo"));
        assertEquals(5, insumoService.listar(null, null, false, "POSITIVO", PageRequest.of(0, 2)).getTotalElements());
        assertEquals(6, insumoService.listar(null, null, false, null, PageRequest.of(0, 2)).getTotalElements());
    }

    @Test
    void filtroDeEstoqueForaDaListaViraBloqueio() {
        seed();
        assertThrows(BusinessException.class, () -> insumoService.listar(null, null, false, "QUASE", PageRequest.of(0, 2)));
    }
}
