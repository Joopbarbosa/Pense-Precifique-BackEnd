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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BUG-URGENTE (Passo 0 do épico #89) — Prova do bug: o PDF de orçamento gerado por
 * {@code PdfService}/{@code PdfMapper} mostra "—" para o nome do produto quando o item é avulso
 * (RN-054, sem {@code itemCatalogo}) e sempre "—" para customizações, mesmo quando existem de
 * verdade — enquanto {@code GET /orcamentos/{id}} (preview do frontend) já mostra os dois dados
 * corretamente via {@code OrcamentoItem#getProdutoVendido()} e
 * {@code OrcamentoItemCustomizacaoRepository}. Reproduz o cenário real via
 * {@code OrcamentoService#criar}, depois chama o mesmo {@code PdfMapper} usado em produção.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PdfMapperOrcamentoAvulsoCustomizacaoIT {

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
    void pdfDeItemAvulsoComCustomizacaoDeveExibirNomesReais() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("pdf-avulso-cust-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        Cliente cliente = clienteRepository.save(Cliente.builder()
                .usuario(usuario).numero(1).nome("Cliente PDF Avulso").ativa(true).build());

        Produto produtoAvulso = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo Vulcão de Chocolate").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).precoVenda(new BigDecimal("120.00")).build());
        Produto customizacao = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Cobertura de Ganache Dourada").tipo(TipoProduto.CUSTOMIZACAO)
                .tempoProducao(10).precoVenda(new BigDecimal("30.00")).build());

        OrcamentoItemCustomizacaoRequest custReq = new OrcamentoItemCustomizacaoRequest();
        custReq.setProdutoId(customizacao.getId());
        custReq.setQuantidade(1);

        OrcamentoItemRequest itemReq = new OrcamentoItemRequest();
        itemReq.setProdutoId(produtoAvulso.getId());
        itemReq.setMargemAplicada(new BigDecimal("50"));
        itemReq.setPrecoUnitario(new BigDecimal("120.00"));
        itemReq.setQuantidade(1);
        itemReq.setCustomizacoes(List.of(custReq));

        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setPrazoProducaoDias(5);
        req.setItens(List.of(itemReq));

        OrcamentoDetalheResponse criado = orcamentoService.criar(req);

        Orcamento orcamento = orcamentoRepository.findById(criado.getId()).orElseThrow();
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId,
                        item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));

        // Mesmo caminho de produção: PdfService monta este mesmo objeto antes de renderizar o template.
        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, null, itens, customizacoesPorItem);

        assertEquals(1, dados.getItens().size());
        ItemPdfData itemPdf = dados.getItens().get(0);

        assertEquals("Bolo Vulcão de Chocolate", itemPdf.getNomeProduto(),
                "item avulso (sem itemCatalogo) deveria mostrar o nome real do produto, não '—'");
        assertTrue(itemPdf.getCustomizacoes().contains("Cobertura de Ganache Dourada"),
                "customização real existente deveria aparecer no PDF, não '—'");
    }
}
