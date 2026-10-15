package com.penseprecifique.api.cliente;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** V0.17.0 — #720 (RN-NOVA-14, DT-NOVA-12). Cenários CEN-NOVO-47 a 50: o backend diz o destino e se o cadastro ainda existe. */
class DestinoCadastroTest {

    @Test
    void cen47_produtoCadastradoAbreOProdutoEInativoContinuaAbrindo() {
        UUID id = UUID.randomUUID();
        Produto vivo = Produto.builder().id(id).ativo(false).build();

        assertEquals(id, DestinoCadastro.produto(vivo));
    }

    @Test
    void cen48_itemDeCatalogoAbreOCatalogoAQuePertence() {
        UUID catalogoId = UUID.randomUUID();
        ItemCatalogo item = ItemCatalogo.builder().id(UUID.randomUUID()).catalogo(Catalogo.builder().id(catalogoId).build()).build();

        assertEquals(catalogoId, DestinoCadastro.catalogoDoItem(item));
    }

    @Test
    void cen49_insumoDeCompraAbreOInsumo() {
        UUID id = UUID.randomUUID();

        assertEquals(id, DestinoCadastro.insumo(Insumo.builder().id(id).build()));
    }

    @Test
    void cen50_cadastroExcluidoOuAusenteNaoTemDestino() {
        Produto excluido = Produto.builder().id(UUID.randomUUID()).deletedAt(LocalDateTime.now()).build();
        ItemCatalogo itemExcluido = ItemCatalogo.builder().id(UUID.randomUUID()).deletedAt(LocalDateTime.now())
                .catalogo(Catalogo.builder().id(UUID.randomUUID()).build()).build();
        Insumo insumoExcluido = Insumo.builder().id(UUID.randomUUID()).deletedAt(LocalDateTime.now()).build();

        assertNull(DestinoCadastro.produto(excluido));
        assertNull(DestinoCadastro.produto(null));
        assertNull(DestinoCadastro.catalogoDoItem(itemExcluido));
        assertNull(DestinoCadastro.catalogoDoItem(null));
        assertNull(DestinoCadastro.insumo(insumoExcluido));
        assertNull(DestinoCadastro.insumo(null));
    }
}
