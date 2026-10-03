package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * #539/RN-NOVA-3 e #538/CEN-NOVO-4 (V0.15.0) — orçamento só aceita cliente novo ativo com papel
 * Cliente; editar mantendo o cliente já salvo continua funcionando mesmo com ele inativado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrcamentoClientePapelVinculoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;

    private Usuario usuario;
    private Produto produto;
    private int numeroCliente = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("orc-papel-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Laço")
                .tipo(TipoProduto.PRODUTO).tempoProducao(10).estoqueAtual(BigDecimal.TEN)
                .permitirEstoqueNegativo(true).precoVenda(new BigDecimal("12.90")).build());
    }

    private Cliente cadastro(String nome, boolean ehCliente, boolean ehFornecedor, boolean ativa) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCliente++).nome(nome)
                .ehCliente(ehCliente).ehFornecedor(ehFornecedor).ativa(ativa).build());
    }

    private OrcamentoRequest request(UUID clienteId, int quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("12.90"));
        item.setQuantidade(quantidade);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(clienteId);
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(item));
        return req;
    }

    @Test
    void criarComFornecedorPuroBloqueia() {
        Cliente atacado = cadastro("Atacado Arte", false, true, true);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> orcamentoService.criar(request(atacado.getId(), 1)));
        assertEquals("Este cadastro não está ativo como Cliente.", ex.getMessage());
    }

    @Test
    void criarComClienteInativoBloqueia() {
        Cliente inativa = cadastro("Mariana Costa", true, false, false);
        assertThrows(BusinessException.class, () -> orcamentoService.criar(request(inativa.getId(), 1)));
    }

    @Test
    void cen4_editarOrcamentoDeClienteInativadoMantendoOCliente() {
        Cliente mariana = cadastro("Mariana Costa", true, false, true);
        OrcamentoDetalheResponse criado = orcamentoService.criar(request(mariana.getId(), 1));

        mariana.setAtiva(false);
        clienteRepository.save(mariana);

        // mesmo cliente: salva normalmente (antes da V0.15.0, dava 404)
        OrcamentoDetalheResponse editado = orcamentoService.editar(criado.getId(), request(mariana.getId(), 3));
        assertEquals(0, new BigDecimal("38.70").compareTo(editado.getTotal()));

        // trocar para outro cadastro inativo: bloqueia
        Cliente outraInativa = cadastro("Outra", true, false, false);
        assertThrows(BusinessException.class,
                () -> orcamentoService.editar(criado.getId(), request(outraInativa.getId(), 3)));
    }
}
