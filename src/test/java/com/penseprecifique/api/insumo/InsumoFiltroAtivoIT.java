package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #336 (V0.10.0) — causa raiz do bug original: filtro de status ("Inativos") era aplicado
 * client-side sobre a janela paginada já carregada (usePaginatedList), então um insumo inativado
 * fora da 1ª página nunca aparecia, mesmo existindo. Fix: filtro server-side via parâmetro `ativo`
 * de GET /insumos (InsumoRepository.buscarComFiltros), igual à convenção já usada por `busca`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoFiltroAtivoIT {

    @Autowired InsumoService insumoService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private final AtomicInteger contador = new AtomicInteger(1);

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-filtro-ativo-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo criar(String nome, boolean ativo) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(contador.getAndIncrement())
                .nome(nome).unidadeMedida("un").custoUnitario(BigDecimal.TEN)
                .estoqueAtual(BigDecimal.TEN).permitirEstoqueNegativo(true).fracionavel(true)
                .ativo(ativo).build());
    }

    @Test
    void filtroAtivoFalseEncontraInativoForaDaPrimeiraPagina() {
        seedUsuario();
        // Ordenação padrão é por nome — nomeando o inativo para cair depois de 5 ativos numa
        // página de tamanho 5, ele nunca apareceria na página 0 sem filtro server-side.
        for (int i = 0; i < 5; i++) {
            criar("A Ativo " + i, true);
        }
        Insumo inativo = criar("Z Inativo", false);

        // Sem filtro, página 0 (tamanho 5) não contém o inativo — confirma o cenário do bug.
        Page<InsumoResponseDTO> semFiltro = insumoService.listar(null, null, PageRequest.of(0, 5));
        assertTrue(semFiltro.getContent().stream().noneMatch(i -> i.id().equals(inativo.getId())));

        // Com filtro ativo=false, o inativo aparece já na página 0, mesmo fora da janela padrão.
        Page<InsumoResponseDTO> filtrado = insumoService.listar(null, false, PageRequest.of(0, 5));
        assertEquals(1, filtrado.getTotalElements());
        assertEquals(inativo.getId(), filtrado.getContent().get(0).id());
    }

    @Test
    void filtroAtivoTrueExcluiInativos() {
        seedUsuario();
        criar("Ativo 1", true);
        criar("Ativo 2", true);
        criar("Inativo 1", false);

        Page<InsumoResponseDTO> filtrado = insumoService.listar(null, true, PageRequest.of(0, 20));

        assertEquals(2, filtrado.getTotalElements());
        assertTrue(filtrado.getContent().stream().allMatch(InsumoResponseDTO::ativo));
    }

    @Test
    void semFiltroAtivoRetornaAtivosEInativosJuntos() {
        seedUsuario();
        criar("Ativo 1", true);
        criar("Inativo 1", false);

        Page<InsumoResponseDTO> pagina = insumoService.listar(null, null, PageRequest.of(0, 20));

        assertEquals(2, pagina.getTotalElements());
    }
}
