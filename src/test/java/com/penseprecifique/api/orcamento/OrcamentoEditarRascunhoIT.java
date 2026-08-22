package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-4 (V0.8.2) — edição de orçamento em status RASCUNHO. Cobre os 4 casos numéricos do
 * prompt P-B003: item inalterado mantém snapshot (Caso 1), troca de produto = remover+adicionar
 * com snapshot novo (Caso 2), edição fora de RASCUNHO é rejeitada (Caso 3), remover item exclui
 * e recalcula o total (Caso 4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoEditarRascunhoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;

    private Usuario usuario;
    private Cliente cliente;
    private int proximoNumeroProduto = 1;
    private int proximoNumeroCatalogo = 1;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-editar-rascunho-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Editar Rascunho").ativa(true).build());
    }

    private Produto novoProduto(BigDecimal precoVenda) {
        int numero = proximoNumeroProduto++;
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true)
                .precoVenda(precoVenda).build());
    }

    /** Item de catálogo com preço vivo próprio — usado para exercitar o snapshot "preço vivo diverge". */
    private ItemCatalogo novoItemCatalogo(BigDecimal precoVenda) {
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(proximoNumeroCatalogo++).nome("Catálogo Teste " + UUID.randomUUID()).ativo(true).build());
        Produto produto = novoProduto(precoVenda);
        return itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(1)
                .precoVenda(precoVenda).build());
    }

    private OrcamentoItemRequest itemDeCatalogo(ItemCatalogo itemCatalogo, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setItemCatalogoId(itemCatalogo.getId());
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoItemRequest itemAvulso(Produto produto, BigDecimal precoUnitario, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(precoUnitario);
        item.setQuantidade(quantidade);
        return item;
    }

    private OrcamentoRequest requestBase(List<OrcamentoItemRequest> itens) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(itens);
        return req;
    }

    @Test
    void itemInalteradoMantemSnapshotSemRecalculo() {
        seedUsuarioECliente();
        ItemCatalogo itemCatalogo = novoItemCatalogo(new BigDecimal("50.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemDeCatalogo(itemCatalogo, 2)));
        OrcamentoDetalheResponse criado = orcamentoService.criar(criacao);
        assertEquals(0, new BigDecimal("100.00").compareTo(criado.getTotal()));

        // Preço vivo do item de catálogo muda depois da criação.
        itemCatalogo.setPrecoVenda(new BigDecimal("65.00"));
        itemCatalogoRepository.save(itemCatalogo);

        // Edição reenvia o mesmo item (mesma origem, mesma quantidade) — nada muda de fato.
        OrcamentoRequest edicao = requestBase(List.of(itemDeCatalogo(itemCatalogo, 2)));
        OrcamentoDetalheResponse editado = orcamentoService.editar(criado.getId(), edicao);

        assertEquals(1, editado.getItens().size());
        OrcamentoItemResponse item = editado.getItens().get(0);
        assertEquals(0, new BigDecimal("50.00").compareTo(item.getPrecoUnitario()));
        assertEquals(0, new BigDecimal("100.00").compareTo(item.getSubtotal()));
        assertEquals(0, new BigDecimal("100.00").compareTo(editado.getTotal()));
    }

    @Test
    void trocaDeProdutoRemoveEAdicionaComSnapshotNovo() {
        seedUsuarioECliente();
        ItemCatalogo itemA = novoItemCatalogo(new BigDecimal("20.00"));
        ItemCatalogo itemB = novoItemCatalogo(new BigDecimal("35.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemDeCatalogo(itemA, 3)));
        OrcamentoDetalheResponse criado = orcamentoService.criar(criacao);
        assertEquals(0, new BigDecimal("60.00").compareTo(criado.getTotal()));
        UUID idItemAntigo = criado.getItens().get(0).getId();

        // Edição troca o item de catálogo referenciado (produto A -> produto B), mesma quantidade.
        OrcamentoRequest edicao = requestBase(List.of(itemDeCatalogo(itemB, 3)));
        OrcamentoDetalheResponse editado = orcamentoService.editar(criado.getId(), edicao);

        assertEquals(1, editado.getItens().size());
        OrcamentoItemResponse itemNovo = editado.getItens().get(0);
        assertTrue(!itemNovo.getId().equals(idItemAntigo), "item novo deve ter id diferente do item removido");
        assertEquals(itemB.getId(), itemNovo.getItemCatalogoId());
        assertEquals(0, new BigDecimal("35.00").compareTo(itemNovo.getPrecoUnitario()));
        assertEquals(0, new BigDecimal("105.00").compareTo(itemNovo.getSubtotal()));
        assertEquals(0, new BigDecimal("105.00").compareTo(editado.getTotal()));
    }

    @Test
    void editarForaDeRascunhoRejeitaComBusinessException() {
        seedUsuarioECliente();
        Produto produto = novoProduto(new BigDecimal("100.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemAvulso(produto, new BigDecimal("100.00"), 1)));
        OrcamentoDetalheResponse criado = orcamentoService.criar(criacao);

        orcamentoService.avancarStatus(criado.getId(), new com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest()); // RASCUNHO -> ENVIADO

        OrcamentoRequest edicao = requestBase(List.of(itemAvulso(produto, new BigDecimal("100.00"), 1)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.editar(criado.getId(), edicao));
        assertTrue(ex.getMessage().toLowerCase().contains("rascunho"));
    }

    @Test
    void removerItemDaListaExcluiERecalculaTotal() {
        seedUsuarioECliente();
        ItemCatalogo itemA = novoItemCatalogo(new BigDecimal("40.00"));
        ItemCatalogo itemB = novoItemCatalogo(new BigDecimal("25.00"));

        OrcamentoRequest criacao = requestBase(List.of(itemDeCatalogo(itemA, 1), itemDeCatalogo(itemB, 1)));
        OrcamentoDetalheResponse criado = orcamentoService.criar(criacao);
        assertEquals(2, criado.getItens().size());
        assertEquals(0, new BigDecimal("65.00").compareTo(criado.getTotal()));

        // Edição envia só o item A — item B deve ser removido.
        OrcamentoRequest edicao = requestBase(List.of(itemDeCatalogo(itemA, 1)));
        OrcamentoDetalheResponse editado = orcamentoService.editar(criado.getId(), edicao);

        assertEquals(1, editado.getItens().size());
        assertEquals(itemA.getId(), editado.getItens().get(0).getItemCatalogoId());
        assertEquals(0, new BigDecimal("40.00").compareTo(editado.getTotal()));
    }
}
