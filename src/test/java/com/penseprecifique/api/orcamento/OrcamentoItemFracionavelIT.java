package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * RN-NOVA-7 (V0.10.0, #461, reversão de RN-NOVA-6) — badge fracionável volta ao Orçamento, lida ao
 * vivo do Produto (valor final, já considerando fracionavelOverride) — não é snapshot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoItemFracionavelIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Cliente cliente;

    private void seedUsuarioECliente() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-item-fracionavel-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente Fracionável").ativa(true).build());
    }

    private Produto novoProduto(int numero, boolean fracionavel, boolean override) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome("Produto " + numero).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).precoVenda(new BigDecimal("10.00"))
                .fracionavel(fracionavel).fracionavelOverride(override).build());
    }

    private UUID criarOrcamentoComItem(Produto produto) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(new BigDecimal("50"));
        item.setPrecoUnitario(new BigDecimal("10.00"));
        item.setQuantidade(1);

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));

        return orcamentoService.criar(req).getId();
    }

    @Test
    void itemReflexoFracionavelTrueDoProduto() {
        seedUsuarioECliente();
        Produto produto = novoProduto(1, true, false);
        UUID orcamentoId = criarOrcamentoComItem(produto);

        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        OrcamentoItemResponse item = detalhe.getItens().get(0);

        assertEquals(Boolean.TRUE, item.getFracionavel());
    }

    @Test
    void itemReflexoFracionavelFalseComOverrideManual() {
        seedUsuarioECliente();
        // override manual (artesã marcou "não fracionável" na ficha técnica) — RN-NOVA-2/#299.
        Produto produto = novoProduto(1, false, true);
        UUID orcamentoId = criarOrcamentoComItem(produto);

        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        OrcamentoItemResponse item = detalhe.getItens().get(0);

        assertFalse(item.getFracionavel(), "reflete o valor final (com override), não o calculado");
    }

    @Test
    void itemReflexoFracionavelAoVivoAposMudarNoProduto() {
        // Lido ao vivo a cada GET — não é snapshot do momento em que o item foi adicionado.
        seedUsuarioECliente();
        Produto produto = novoProduto(1, true, false);
        UUID orcamentoId = criarOrcamentoComItem(produto);

        produto.setFracionavel(false);
        produto.setFracionavelOverride(true);
        produtoRepository.save(produto);

        OrcamentoDetalheResponse detalhe = orcamentoService.buscarPorId(orcamentoId);
        OrcamentoItemResponse item = detalhe.getItens().get(0);

        assertFalse(item.getFracionavel(), "orçamento não congela o valor — reflete a mudança no produto");
    }
}
