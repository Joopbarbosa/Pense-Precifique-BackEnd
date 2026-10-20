package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.pdf.ItemPdfData;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemCustomizacaoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * V0.17.0 #1017 (RN-NOVA-36): os PDFs do cliente NÃO mostram a marca — o nome do item no PDF segue o nome do cadastro, sem "— Marca".
 * Mesmo caminho de produção: OrcamentoService#criar e depois o PdfMapper usado pelo PdfService.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfMapperMarcaInalteradoIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;
    @Autowired OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    @Autowired PdfMapper pdfMapper;

    @Test
    @Transactional
    void pdfNaoMostraAMarcaDoProduto() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("pdf-marca-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Cliente PDF Marca").ativa(true).build());
        Produto produto = produtoRepository.save(Produto.builder().usuario(usuario).numero(1).nome("Caderno").marca("Pense")
                .tipo(TipoProduto.PRODUTO).tempoProducao(60).precoVenda(new BigDecimal("20.00")).build());

        OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
        itemReq.setProdutoId(produto.getId());
        itemReq.setMargemAplicada(new BigDecimal("50"));
        itemReq.setPrecoUnitario(new BigDecimal("20.00"));
        itemReq.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(true);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemReq));

        OrcamentoDetalheResponse criado = orcamentoService.criar(req);
        Orcamento orcamento = orcamentoRepository.findById(criado.getId()).orElseThrow();
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoes = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId, item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, itens, customizacoes);

        assertEquals("Caderno", dados.getItens().get(0).getNomeProduto(), "o PDF do cliente mostra só o nome, sem a marca");
        assertEquals("Caderno — Pense", criado.getItens().get(0).getNomeProduto(), "a tela mostra Nome — Marca");
    }
}
