package com.penseprecifique.api.producao;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.TipoOrigemProducao;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.producao.AgruparProducoesResponse;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #470 (V0.10.0) — achado do Frontend: ProducaoResponse (listagem) não expunha tipoOrigem, campo
 * necessário pra decidir se a opção "Desagrupar" cabe no menu de 3 pontinhos sem round-trip por
 * linha (RN-NOVA-5/#450). Já existia em ProducaoDetalheResponse; faltava só na listagem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoListagemTipoOrigemIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    @Test
    void listagemExpoeTipoOrigemAgrupamento() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-listagem-tipo-origem-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true).fracionavel(true).build());

        Produto produtoA = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo A").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("10.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoA).insumo(insumo).quantidade(new BigDecimal("1")).build());

        Produto produtoB = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(2).nome("Bolo B").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("15.00")).build());
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produtoB).insumo(insumo).quantidade(new BigDecimal("1")).build());

        CriarProducaoRequest criarA = new CriarProducaoRequest();
        criarA.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest itemA = new ProducaoProdutoRequest();
        itemA.setProdutoId(produtoA.getId());
        itemA.setQuantidade(BigDecimal.ONE);
        criarA.setProdutos(List.of(itemA));
        UUID p1 = producaoService.criarProducao(criarA).getId();

        CriarProducaoRequest criarB = new CriarProducaoRequest();
        criarB.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest itemB = new ProducaoProdutoRequest();
        itemB.setProdutoId(produtoB.getId());
        itemB.setQuantidade(BigDecimal.ONE);
        criarB.setProdutos(List.of(itemB));
        UUID p2 = producaoService.criarProducao(criarB).getId();

        AgruparProducoesRequest agrupar = new AgruparProducoesRequest();
        agrupar.setProducaoIds(List.of(p1, p2));
        agrupar.setEstadoDestino(EstadoProducao.AGUARDANDO_INICIO);
        agrupar.setJustificativa("Agrupamento de teste automatizado para #470.");
        AgruparProducoesResponse resultado = (AgruparProducoesResponse) producaoService.agrupar(agrupar);
        UUID agrupadaId = resultado.getProducaoNova().getId();

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));
        ProducaoResponse agrupada = pagina.getContent().stream()
                .filter(pr -> pr.getId().equals(agrupadaId)).findFirst().orElseThrow();

        assertEquals(TipoOrigemProducao.AGRUPAMENTO, agrupada.getTipoOrigem());
    }
}
