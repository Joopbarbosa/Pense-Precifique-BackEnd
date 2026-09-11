package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ORC-020 (REVISÃO)/RN-NOVA-23 (#313, P-B006) — margem_aplicada volta a ser gravada nos pontos de
 * entrada que produzem um {@code OrcamentoItem} (produto avulso e item de catálogo). A origem
 * avulsa já lia {@code itemReq.getMargemAplicada()} desde RN-054/V0.6.1.1, mas nunca tinha
 * cobertura explícita do valor persistido (só presença do campo no request) — este teste fecha a
 * lacuna. A origem Catálogo nunca lia o campo (achado do Passo 0 desta tarefa); passa a gravar o
 * mesmo valor enviado pelo Frontend, sem recálculo (Backend só grava snapshot).
 *
 * Customização (RN-NOVA-23) não entra aqui: {@code OrcamentoItemCustomizacao} não tem coluna
 * {@code margem_aplicada} nem campo equivalente em {@code OrcamentoItemCustomizacaoRequest} — não
 * existe hoje um OrcamentoItem para uma customização isolada (ela é sempre anexada a um item via
 * {@code salvarCustomizacao}). Decisão explícita do usuário (DECISOES_V0.8.3.md, P-B006) foi não
 * criar coluna/mecanismo novo nesta tarefa — ver achado registrado lá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoMargemAplicadaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-margem-aplicada-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Margem Aplicada").ativa(true).build());
    }

    private OrcamentoRequest requestBase(OrcamentoItemRequest item) {
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        return req;
    }

    @Test
    void produtoAvulsoGravaMargemAplicadaComValorExato() {
        seedUsuarioECliente();
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Avulso").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("10.00")).build());

        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("37.50"));
        item.setPrecoUnitario(new BigDecimal("13.75"));
        item.setQuantidade(2);

        OrcamentoDetalheResponse resultado = orcamentoService.criar(requestBase(item));

        OrcamentoItemResponse itemResp = resultado.getItens().get(0);
        assertEquals(0, new BigDecimal("37.50").compareTo(itemResp.getMargemAplicada()));

        OrcamentoItem persistido = orcamentoItemRepository.findByOrcamentoId(resultado.getId()).get(0);
        assertEquals(0, new BigDecimal("37.50").compareTo(persistido.getMargemAplicada()),
                "valor exato precisa bater com o enviado no request — não só presença do campo");
    }

    @Test
    void itemDeCatalogoGravaMargemAplicadaComValorExato() {
        seedUsuarioECliente();
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Margem").ativo(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Catálogo").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("20.00")).build());
        ItemCatalogo itemCatalogo = itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(1)
                .precoVenda(new BigDecimal("20.00")).build());

        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setItemCatalogoId(itemCatalogo.getId());
        item.setMargemAplicada(new BigDecimal("62.30"));
        item.setQuantidade(1);

        OrcamentoDetalheResponse resultado = orcamentoService.criar(requestBase(item));

        OrcamentoItemResponse itemResp = resultado.getItens().get(0);
        assertEquals(0, new BigDecimal("62.30").compareTo(itemResp.getMargemAplicada()),
                "RN-NOVA-23/ORC-020 (REVISÃO) — origem Catálogo passa a gravar margem_aplicada, achado do Passo 0 (antes ficava sempre null)");

        OrcamentoItem persistido = orcamentoItemRepository.findByOrcamentoId(resultado.getId()).get(0);
        assertEquals(0, new BigDecimal("62.30").compareTo(persistido.getMargemAplicada()));
    }

    @Test
    void itemDeCatalogoSemMargemAplicadaNoRequestPersisteNulo() {
        seedUsuarioECliente();
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Sem Margem").ativo(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Produto Sem Margem").tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .precoVenda(new BigDecimal("15.00")).build());
        ItemCatalogo itemCatalogo = itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).produto(produto).quantidadePacote(1)
                .precoVenda(new BigDecimal("15.00")).build());

        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setItemCatalogoId(itemCatalogo.getId());
        item.setQuantidade(1);

        OrcamentoDetalheResponse resultado = orcamentoService.criar(requestBase(item));

        OrcamentoItem persistido = orcamentoItemRepository.findByOrcamentoId(resultado.getId()).get(0);
        assertNull(persistido.getMargemAplicada(),
                "coluna nullable, sem CHECK — Frontend antigo/telas não migradas continuam funcionando sem enviar o campo");
    }
}
