package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V0.17.0 #1017 (RN-NOVA-36, CEN-NOVO-122): item de catálogo com o mesmo nome e a mesma marca no mesmo catálogo é bloqueado no campo Nome
 * (sem diferenciar maiúsculas, acentos e espaços); marca diferente, e mesmo nome em outro catálogo, são aceitos pela regra; item antigo
 * repetido segue editável.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class MarcaItemCatalogoIT {

    @Autowired ItemCatalogoService service;
    @Autowired ItemCatalogoRepository itens;
    @Autowired CatalogoRepository catalogos;
    @Autowired UsuarioRepository usuarios;

    private Usuario usuario;
    private Catalogo natal;
    private Catalogo pascoa;

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private void preparar() {
        usuario = usuarios.save(Usuario.builder().email("mic-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        natal = catalogos.save(Catalogo.builder().usuario(usuario).numero(1).nome("Natal 2026").build());
        pascoa = catalogos.save(Catalogo.builder().usuario(usuario).numero(2).nome("Páscoa 2026").build());
    }

    private ItemCatalogo item(Catalogo c, String nome, String marca) {
        return itens.save(ItemCatalogo.builder().catalogo(c).nome(nome).marca(marca).tempoProducao(0).precoVenda(new BigDecimal("10.00")).build());
    }

    private ItemCatalogoRequest pedido(String nome, String marca) {
        ItemCatalogoRequest r = new ItemCatalogoRequest();
        r.setNome(nome);
        r.setMarca(marca);
        r.setTempoProducao(0);
        ItemCatalogoComponenteRequest c = new ItemCatalogoComponenteRequest();
        c.setProdutoBaseId(UUID.randomUUID()); // o bloqueio acontece antes de qualquer componente ser lido
        c.setQuantidade(BigDecimal.ONE);
        r.setComponentes(List.of(c));
        return r;
    }

    @Test
    void mesmoNomeEMarcaNoMesmoCatalogoBloqueiaNoCampoNome() {
        preparar();
        item(natal, "Fita de cetim", "Pense");

        BusinessException e = assertThrows(BusinessException.class, () -> service.adicionar(natal.getId(), pedido(" fita de CETIM ", "pense")));

        assertEquals("nome", e.getCampo());
        assertEquals("Já existe um item neste catálogo com este nome e esta marca", e.getMessage());
    }

    @Test
    void mesmoNomeEMarcaEmOutroCatalogoNaoBloqueiaPelaUnicidade() {
        preparar();
        item(natal, "Fita de cetim", "Pense");

        // a unicidade é por catálogo: no outro catálogo a checagem passa e o erro que sobra é o do componente inexistente, não o de nome repetido
        BusinessException e = null;
        try {
            service.adicionar(pascoa.getId(), pedido("Fita de cetim", "Pense"));
        } catch (BusinessException ex) {
            e = ex;
        } catch (RuntimeException ignorado) {
            // componente inexistente: prova que passou da checagem de nome + marca
        }
        if (e != null) assertEquals(false, e.getMessage().startsWith("Já existe um item neste catálogo"));
    }

    @Test
    void marcaDiferenteNaoBloqueia() {
        preparar();
        item(natal, "Fita de cetim", "Pense");

        BusinessException e = null;
        try {
            service.adicionar(natal.getId(), pedido("Fita de cetim", "Luxo"));
        } catch (BusinessException ex) {
            e = ex;
        } catch (RuntimeException ignorado) {
            // componente inexistente: passou da checagem
        }
        if (e != null) assertEquals(false, e.getMessage().startsWith("Já existe um item neste catálogo"));
    }
}
