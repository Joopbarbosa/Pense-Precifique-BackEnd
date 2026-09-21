package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoService;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OpenProject #516 — CEN-NOVO-3 (DECISOES_V0.13.0.md): "editar item de catálogo depois de criado
 * um orçamento não altera o orçamento já criado" (RN-048, snapshot no momento da venda). Sem
 * cobertura automatizada antes deste teste especificamente para o novo modelo de composição livre
 * (N componentes) — {@code OrcamentoMargemAplicadaIT} já cria orçamento com origem Catálogo, mas
 * nenhum teste editava a composição DEPOIS de criado o orçamento para confirmar o snapshot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoItemCatalogoSnapshotIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;

    @Test
    void editarComposicaoDoItemDeCatalogoNaoAlteraOrcamentoJaCriado() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-catalogo-snapshot-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Snapshot").ativa(true).build());

        Produto produtoOriginal = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Original").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoCusto(new BigDecimal("5.0000"))
                .estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("10.00")).build());
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Snapshot").ativo(true).build());
        ItemCatalogo item = itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).nome("Kit Snapshot").tempoProducao(0)
                .precoVenda(new BigDecimal("30.00")).override(true).build());
        itemCatalogoComponenteRepository.save(ItemCatalogoComponente.builder()
                .itemCatalogo(item).produtoBase(produtoOriginal).quantidade(BigDecimal.ONE).build());

        OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
        itemReq.setItemCatalogoId(item.getId());
        itemReq.setQuantidade(1);
        OrcamentoRequest orcamentoReq = new OrcamentoRequest();
        orcamentoReq.setClienteId(cliente.getId());
        orcamentoReq.setMetodoPagamento(MetodoPagamento.PIX);
        orcamentoReq.setTemPrazoProducao(true);
        orcamentoReq.setPrazoProducaoDias(5);
        orcamentoReq.setItens(List.of(itemReq));

        OrcamentoDetalheResponse orcamentoCriado = orcamentoService.criar(orcamentoReq);
        OrcamentoItemResponse itemNoOrcamento = orcamentoCriado.getItens().get(0);
        assertEquals(0, new BigDecimal("30.00").compareTo(itemNoOrcamento.getPrecoUnitario()),
                "snapshot inicial precisa refletir o preço do item de catálogo no momento da criação");

        // Edita a composição do item de catálogo DEPOIS de criado o orçamento: troca de componente
        // e novo preço de venda.
        Produto produtoNovo = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Produto Novo").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoCusto(new BigDecimal("8.0000"))
                .estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("15.00")).build());
        ItemCatalogoComponenteRequest novoComponente = new ItemCatalogoComponenteRequest();
        novoComponente.setProdutoBaseId(produtoNovo.getId());
        novoComponente.setQuantidade(BigDecimal.ONE);
        ItemCatalogoRequest editarRequest = new ItemCatalogoRequest();
        editarRequest.setNome("Kit Snapshot Editado");
        editarRequest.setTempoProducao(0);
        editarRequest.setComponentes(List.of(novoComponente));
        editarRequest.setPrecoVenda(new BigDecimal("99.00"));
        ItemCatalogoResponse itemEditado = itemCatalogoService.editar(item.getId(), editarRequest);

        // O orçamento já criado mantém o snapshot original — buscar de novo pra garantir que não é
        // só o objeto em memória da 1ª chamada.
        OrcamentoDetalheResponse orcamentoReconsultado = orcamentoService.buscarPorId(orcamentoCriado.getId());
        OrcamentoItemResponse itemReconsultado = orcamentoReconsultado.getItens().get(0);
        assertEquals(0, new BigDecimal("30.00").compareTo(itemReconsultado.getPrecoUnitario()),
                "editar o item de catálogo depois não pode alterar o preço já snapshotado no orçamento (RN-048)");

        // O item de catálogo, por outro lado, reflete a nova composição/preço.
        assertEquals(0, new BigDecimal("99.00").compareTo(itemEditado.getPrecoVenda()));
        assertEquals("Kit Snapshot Editado", itemEditado.getNome());
    }
}
