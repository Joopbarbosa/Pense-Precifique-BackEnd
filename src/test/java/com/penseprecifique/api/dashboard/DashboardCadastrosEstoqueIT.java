package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.ConfiguracaoPrecificacaoRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V0.17.0 #974 (RN-NOVA-25, CEN-NOVO-72): valor do estoque, potencial de venda e insumos abaixo do mínimo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class DashboardCadastrosEstoqueIT {

    @Autowired DashboardCadastrosService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired InsumoRepository insumos;
    @Autowired ProdutoRepository produtos;
    @Autowired FichaTecnicaItemRepository fichas;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ConfiguracaoPrecificacaoRepository configuracoes;

    private Usuario usuario;
    private UnidadeMedida unidade;

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    @BeforeEach
    void preparar() {
        usuario = usuarios.save(Usuario.builder().email("dash-estoque-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        unidade = unidades.save(UnidadeMedida.builder().usuario(usuario).nome("un" + UUID.randomUUID()).sigla("u" + UUID.randomUUID().toString().substring(0, 6)).build());
        configuracoes.save(ConfiguracaoPrecificacao.builder().usuarioId(usuario.getId()).valorHora(bd("25.00")).build());
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    private Insumo insumo(String nome, int numero, String custo, String estoque, String minimo) {
        return insumos.save(Insumo.builder().usuario(usuario).numero(numero).nome(nome).unidadeMedida(unidade)
                .custoUnitario(bd(custo)).estoqueAtual(bd(estoque)).estoqueMinimo(minimo == null ? null : bd(minimo))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    @Test
    void cen72_materialProdutosProntosEPotencialConferemComAConta() {
        Insumo fita = insumo("Fita de cetim", 1, "3.00", "10", "15");
        insumo("Papel couchê", 2, "0.30", "100", "50");
        Insumo material = insumo("Material do laço", 3, "6.00", "0", null);
        Produto laco = produtos.save(Produto.builder().usuario(usuario).numero(1).nome("Laço pequeno").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("15.00")).estoqueAtual(bd("4")).ativo(true).build());
        fichas.save(FichaTecnicaItem.builder().produto(laco).insumo(material).quantidade(BigDecimal.ONE).build());
        produtos.save(Produto.builder().usuario(usuario).numero(2).nome("Laço grande").tipo(TipoProduto.PRODUTO)
                .rendimento(BigDecimal.ONE).tempoProducao(10).precoVenda(bd("20.00")).estoqueAtual(BigDecimal.ZERO).ativo(true).build());

        DashboardCadastrosResponse.Estoque e = service.dashboard(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null).estoque();

        assertEquals(0, bd("60.00").compareTo(e.valorMaterial()));
        assertEquals(0, bd("40.67").compareTo(e.valorProdutosProntos()));
        assertEquals(0, bd("100.67").compareTo(e.valorTotal()));
        assertEquals(0, bd("60.00").compareTo(e.potencialVenda()));
        assertEquals(3, e.valorItens().size(), "fita, papel e laço pequeno; o laço grande (sem estoque) e o material (estoque zero) não entram");
        assertEquals(1, e.insumosAbaixoMinimo(), "só a fita (10 < 15); o papel tem 100 e o mínimo é 50");
        assertEquals(0, bd("5").compareTo(e.abaixoMinimoItens().get(0).falta()));
        assertEquals(0, bd("0").compareTo(e.percentualCmv().valor()), "sem vendas no período, 0%");
        assertEquals(0, bd("0").compareTo(e.margemMaterial().valor()), "sem faturamento, margem 0%");
        assertEquals(fita.getId(), e.abaixoMinimoItens().get(0).id());
    }
}
