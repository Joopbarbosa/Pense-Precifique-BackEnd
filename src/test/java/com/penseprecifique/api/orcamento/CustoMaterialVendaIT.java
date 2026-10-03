package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.ConfiguracaoService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.CustoMaterialService;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.config.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #575/RN-NOVA-26 (V0.15.0) — CEN-NOVO-34: custo de material gravado quando o orçamento passa de PAGO
 * para ENTREGUE, sem mão de obra, com produto componente pelo material dele; mudar o custo depois não
 * altera o gravado.
 *
 * <p>Caderno A5 (rende 2): papel 100 × 0,12 + capa 2 × 1,85 + espiral 2 × 0,95 + marcador (produto,
 * material 0,45) × 2 = 18,50 ÷ 2 = 9,25; 30 min de mão de obra a R$ 20,00/h ficam fora.
 * Customização "Nome gravado": tinta 0,4 × 1,50 = 0,60.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CustoMaterialVendaIT {

    @Autowired OrcamentoService orcamentoService;
    @Autowired CustoMaterialService custoMaterialService;
    @Autowired ConfiguracaoService configuracaoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired OrcamentoItemRepository orcamentoItemRepository;
    @Autowired OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired com.penseprecifique.api.compra.DashboardCompraService dashboardCompraService;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numero = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("custo-material-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
        configuracaoService.upsertConfiguracao(new ConfiguracaoRequestDTO(new BigDecimal("20.00"), new BigDecimal("50")));
    }

    private Insumo insumo(String nome, String custo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).unidadeMedida(un)
                .custoUnitario(new BigDecimal(custo)).estoqueAtual(new BigDecimal("1000")).permitirEstoqueNegativo(true)
                .fracionavel(true).build());
    }

    private Produto produto(String nome, TipoProduto tipo, String rendimento, int tempo) {
        return produtoRepository.save(Produto.builder().usuario(usuario).numero(numero++).nome(nome).tipo(tipo)
                .tempoProducao(tempo).rendimento(new BigDecimal(rendimento)).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).precoVenda(new BigDecimal("38.90")).build());
    }

    private void ficha(Produto p, Insumo i, Produto base, String qtd) {
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder().produto(p).insumo(i).produtoBase(base)
                .quantidade(new BigDecimal(qtd)).build());
    }

    @Test
    void cen34_gravaCustoDeMaterialAoEntregarSemMaoDeObra() {
        Insumo papel = insumo("Papel", "0.12");
        Insumo capa = insumo("Capa", "1.85");
        Insumo espiral = insumo("Espiral", "0.95");
        Insumo fitinha = insumo("Fitinha", "0.45");
        Insumo tinta = insumo("Tinta", "1.50");
        Produto marcador = produto("Marcador", TipoProduto.PRODUTO, "1", 10);
        ficha(marcador, fitinha, null, "1");
        Produto caderno = produto("Caderno A5", TipoProduto.PRODUTO, "2", 30);
        ficha(caderno, papel, null, "100");
        ficha(caderno, capa, null, "2");
        ficha(caderno, espiral, null, "2");
        ficha(caderno, null, marcador, "2");
        Produto nome = produto("Nome gravado", TipoProduto.CUSTOMIZACAO, "1", 5);
        ficha(nome, tinta, null, "0.4");

        Cliente cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Ana").ativa(true).build());
        Orcamento orc = orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(9)
                .status(StatusOrcamento.PAGO).subtotal(new BigDecimal("134.70")).total(new BigDecimal("130.00"))
                .dataPagamento(LocalDateTime.now()).build());
        OrcamentoItem item = orcamentoItemRepository.save(OrcamentoItem.builder().orcamento(orc).produto(caderno)
                .quantidade(3).precoUnitario(new BigDecimal("38.90")).subtotal(new BigDecimal("116.70")).build());
        OrcamentoItemCustomizacao cust = orcamentoItemCustomizacaoRepository.save(OrcamentoItemCustomizacao.builder()
                .orcamentoItem(item).produto(nome).quantidade(3).precoUnitario(new BigDecimal("6.00"))
                .subtotal(new BigDecimal("18.00")).build());

        orcamentoService.avancarStatus(orc.getId(), new AvancaStatusRequest()); // PAGO -> ENTREGUE

        assertEquals(StatusOrcamento.ENTREGUE, orcamentoRepository.findById(orc.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("9.2500"), orcamentoItemRepository.findById(item.getId()).orElseThrow().getCustoMaterialUnitario());
        assertEquals(new BigDecimal("0.6000"), orcamentoItemCustomizacaoRepository.findById(cust.getId()).orElseThrow().getCustoMaterialUnitario());

        // Custo do papel muda depois: o gravado não acompanha, o cálculo de hoje sim.
        papel.setCustoUnitario(new BigDecimal("0.20"));
        insumoRepository.save(papel);
        assertEquals(new BigDecimal("9.2500"), orcamentoItemRepository.findById(item.getId()).orElseThrow().getCustoMaterialUnitario());
        // Fora dos serviços transacionais o cálculo roda numa transação (fichas carregam insumos sob demanda).
        assertEquals(new BigDecimal("13.2500"), transactionTemplate.execute(t -> custoMaterialService.novoCalculo().produto(caderno))); // +100 × 0,08 ÷ 2

        // CEN-NOVO-35/36 — CMV no painel: ORC-9 com custo gravado (3 × (9,25 + 0,60) = 29,55) + uma venda
        // antiga sem custo gravado, estimada pelo custo de hoje (2 laços × 1,37 = 2,74).
        Insumo fita = insumo("Fita", "1.37");
        Produto laco = produto("Laço", TipoProduto.PRODUTO, "1", 20);
        ficha(laco, fita, null, "1");
        Orcamento antigo = orcamentoRepository.save(Orcamento.builder().usuario(usuario).cliente(cliente).numero(10)
                .status(StatusOrcamento.ENTREGUE).subtotal(new BigDecimal("24.00")).total(new BigDecimal("24.00"))
                .dataEntrega(LocalDateTime.now()).build());
        orcamentoItemRepository.save(OrcamentoItem.builder().orcamento(antigo).produto(laco).quantidade(2)
                .precoUnitario(new BigDecimal("12.00")).subtotal(new BigDecimal("24.00")).build());

        java.time.LocalDate hoje = java.time.LocalDate.now();
        var cmv = dashboardCompraService.dashboard(hoje.withDayOfMonth(1), hoje).cmv();
        assertEquals(0, new BigDecimal("32.29").compareTo(cmv.valor()), "CMV " + cmv.valor());       // 29,55 + 2,74
        assertEquals(0, new BigDecimal("154.00").compareTo(cmv.faturamento()));                      // 130,00 + 24,00
        assertEquals(new BigDecimal("20.97"), cmv.percentual());
        assertEquals(0, new BigDecimal("2.74").compareTo(cmv.estimado()));
        assertEquals(0, cmv.vendasSemCusto());
    }
}
