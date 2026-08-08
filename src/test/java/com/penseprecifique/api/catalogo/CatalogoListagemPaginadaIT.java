package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #133/RN-057 — GET /catalogos migrou de ordenação em memória (List) para Pageable server-side,
 * mesmo padrão de ProducaoService#listar (#158). Cobre paginação e ordenação por numero/nome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogoListagemPaginadaIT {

    @Autowired CatalogoService catalogoService;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("catalogo-pag-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private void novoCatalogo(String nome) {
        CatalogoRequest request = new CatalogoRequest();
        request.setNome(nome);
        catalogoService.cadastrar(request);
    }

    @Test
    void paginacaoRespeitaSizeETotalElements() {
        seedUsuario();
        novoCatalogo("Catálogo A");
        novoCatalogo("Catálogo B");
        novoCatalogo("Catálogo C");

        Page<CatalogoResponse> pagina = catalogoService.listar(null, PageRequest.of(0, 2));

        assertEquals(2, pagina.getContent().size());
        assertEquals(3, pagina.getTotalElements());
        assertEquals(2, pagina.getTotalPages());
    }

    @Test
    void ordenacaoPorNomeAscendente() {
        seedUsuario();
        novoCatalogo("Zebra");
        novoCatalogo("Abelha");
        novoCatalogo("Mandacaru");

        Page<CatalogoResponse> pagina = catalogoService.listar(null, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "nome")));

        assertEquals(List.of("Abelha", "Mandacaru", "Zebra"),
                pagina.getContent().stream().map(CatalogoResponse::getNome).toList());
    }

    @Test
    void semSortDefaultENumeroDesc() {
        seedUsuario();
        novoCatalogo("Primeiro");
        novoCatalogo("Segundo");
        novoCatalogo("Terceiro");

        Page<CatalogoResponse> pagina = catalogoService.listar(null, PageRequest.of(0, 10));

        assertEquals(List.of("Terceiro", "Segundo", "Primeiro"),
                pagina.getContent().stream().map(CatalogoResponse::getNome).toList());
    }

    @Test
    void campoDeOrdenacaoInvalidoLancaExcecao() {
        seedUsuario();
        novoCatalogo("Catálogo A");

        assertThrows(RuntimeException.class,
                () -> catalogoService.listar(null, PageRequest.of(0, 10, Sort.by("campoInexistente"))));
    }

    @Test
    void buscaPorNomeFiltraCaseInsensitive() {
        seedUsuario();
        novoCatalogo("Bolos Especiais");
        novoCatalogo("Docinhos");

        Page<CatalogoResponse> pagina = catalogoService.listar("bolos", PageRequest.of(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("Bolos Especiais", pagina.getContent().get(0).getNome());
    }
}
