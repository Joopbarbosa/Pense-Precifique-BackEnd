package com.penseprecifique.api.producao;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.request.producao.TravarProducaoRequest;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #156 — GET /producoes (listagem) não carregava historicoStatus, então o front caía no fallback
 * TRAVADA_SISTEMA (badges.ts) mesmo quando a trava foi manual (travar()). Este teste falha antes da
 * correção (ProducaoResponse.historicoStatus sempre vazio/nulo) e passa depois.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoListagemHistoricoIT {

    @Autowired ProducaoService producaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    @Test
    void listagemExpoeHistoricoStatusDeTravaManual() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("prod-hist-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo farinha = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Farinha").marca("X").unidadeMedida("g")
                .estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true).fracionavel(true)
                .build());

        Produto bolo = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Bolo").tipo(TipoProduto.PRODUTO)
                .tempoProducao(60).rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("10.00")).build());

        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(bolo).insumo(farinha).quantidade(new BigDecimal("1")).build());

        CriarProducaoRequest criar = new CriarProducaoRequest();
        criar.setDataTerminoPrevista(LocalDate.now().plusDays(7));
        ProducaoProdutoRequest item = new ProducaoProdutoRequest();
        item.setProdutoId(bolo.getId());
        item.setQuantidade(new BigDecimal("5"));
        criar.setProdutos(List.of(item));

        UUID producaoId = producaoService.criarProducao(criar).getId();
        producaoService.iniciar(producaoId, new IniciarProducaoRequest());

        TravarProducaoRequest travar = new TravarProducaoRequest();
        travar.setJustificativa("Trava manual para teste automatizado do #156");
        producaoService.travar(producaoId, travar);

        Page<ProducaoResponse> pagina = producaoService.listar(null, null, null, null, PageRequest.of(0, 20));
        ProducaoResponse resposta = pagina.getContent().stream()
                .filter(p -> p.getId().equals(producaoId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Produção não encontrada na listagem"));

        assertNotNull(resposta.getHistoricoStatus(), "historicoStatus não pode ser nulo na listagem");
        assertTrue(resposta.getHistoricoStatus().stream()
                        .anyMatch(h -> h.getStatusNovo().name().equals("TRAVADA") && h.getOrigem().name().equals("USUARIO")),
                "historicoStatus deve conter a transição TRAVADA de origem USUARIO — sem isso o front cai no fallback TRAVADA_SISTEMA");
        assertEquals("TRAVADA", resposta.getEstado().name());
    }
}
