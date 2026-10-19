package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.caixa.*;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelUpdateRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V0.17.0 #993 (RN-NOVA-30, DT-NOVA-26, CEN-NOVO-96): a resposta da venda do Caixa traz o nome do cliente e o nome da forma de
 * pagamento (a modal de dados mostra os dois), mesmo quando o cliente foi excluído ou a forma foi desativada depois.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendaCaixaDetalheIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CaixaTurnoService caixaTurnoService;
    @Autowired VendaCaixaService vendaCaixaService;
    @Autowired MetodoPagamentoConfiguravelService metodoPagamentoService;

    private Usuario novaConta(String prefixo) {
        Usuario u = usuarioRepository.save(Usuario.builder().email(prefixo + "-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
        metodoPagamentoService.seedMetodosPadrao(u);
        return u;
    }

    private UUID metodoId(TipoMetodoPagamento tipo) {
        return metodoPagamentoService.listar().stream().filter(m -> m.tipo() == tipo).findFirst().orElseThrow().id();
    }

    private Produto produto(Usuario u, String nome, String preco) {
        return produtoRepository.save(Produto.builder().usuario(u).numero(1).nome(nome).tipo(TipoProduto.PRODUTO).tempoProducao(1)
                .rendimento(BigDecimal.ONE).precoVenda(new BigDecimal(preco)).estoqueAtual(new BigDecimal("10")).permitirEstoqueNegativo(true).ativo(true).build());
    }

    private VendaCaixaResponseDTO vender(UUID clienteId, Produto p, int quantidade, UUID metodo, String valor) {
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        Object r = vendaCaixaService.registrarVenda(new VendaCaixaRequestDTO(clienteId,
                List.of(new VendaCaixaItemRequestDTO(null, p.getId(), new BigDecimal(quantidade), null)), null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodo, new BigDecimal(valor), null)), null));
        return (VendaCaixaResponseDTO) r;
    }

    @Test
    void vendaTrazONomeDoClienteEODaFormaDePagamento() {
        Usuario u = novaConta("detalhe");
        Cliente maria = clienteRepository.save(Cliente.builder().usuario(u).numero(1).nome("Maria Silva").ehCliente(true).ativa(true).build());
        Produto p = produto(u, "Laço pequeno", "15.00");

        VendaCaixaResponseDTO venda = vender(maria.getId(), p, 2, metodoId(TipoMetodoPagamento.PIX), "30.00");

        assertEquals("Maria Silva", venda.clienteNome());
        assertEquals("Pix", venda.pagamentos().get(0).metodoPagamentoNome());
        VendaCaixaResponseDTO buscada = vendaCaixaService.buscarPorId(venda.id());
        assertEquals("Maria Silva", buscada.clienteNome());
        assertEquals("Pix", buscada.pagamentos().get(0).metodoPagamentoNome());
        assertEquals(1, vendaCaixaService.listarPorTurno(venda.caixaTurnoId()).size());
        assertEquals("Maria Silva", vendaCaixaService.listarPorTurno(venda.caixaTurnoId()).get(0).clienteNome());
    }

    @Test
    void vendaSemClienteTemNomeNulo() {
        Usuario u = novaConta("sem-cliente");
        VendaCaixaResponseDTO venda = vender(null, produto(u, "Laço", "15.00"), 1, metodoId(TipoMetodoPagamento.DINHEIRO), "15.00");

        assertNull(venda.clienteNome());
        assertEquals("Dinheiro", venda.pagamentos().get(0).metodoPagamentoNome());
    }

    @Test
    void clienteInativadoEFormaDesativadaDepoisContinuamComONomeQueExistia() {
        Usuario u = novaConta("excluidos");
        Cliente ana = clienteRepository.save(Cliente.builder().usuario(u).numero(1).nome("Ana Souza").ehCliente(true).ativa(true).build());
        UUID credito = metodoId(TipoMetodoPagamento.CARTAO_CREDITO);
        VendaCaixaResponseDTO venda = vender(ana.getId(), produto(u, "Caderno", "45.00"), 1, credito, "45.00");

        ana.setAtiva(false);
        clienteRepository.saveAndFlush(ana);
        metodoPagamentoService.atualizar(credito, new MetodoPagamentoConfiguravelUpdateRequestDTO(false, null, null, null, null, null));

        VendaCaixaResponseDTO depois = vendaCaixaService.buscarPorId(venda.id());
        assertEquals("Ana Souza", depois.clienteNome());
        assertEquals("Cartão Crédito", depois.pagamentos().get(0).metodoPagamentoNome());
    }

    @Test
    void vendaDeOutraContaNaoExisteParaQuemPergunta() {
        Usuario dona = novaConta("dona");
        VendaCaixaResponseDTO venda = vender(null, produto(dona, "Laço", "15.00"), 1, metodoId(TipoMetodoPagamento.DINHEIRO), "15.00");

        novaConta("intrusa"); // a sessão passa a ser de outra conta
        assertThrows(ResourceNotFoundException.class, () -> vendaCaixaService.buscarPorId(venda.id()));
    }
}
