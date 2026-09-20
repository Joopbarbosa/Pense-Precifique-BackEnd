package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.caixa.*;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reabertura de RN-NOVA-1 (V0.12.0, #487) — achado do teste manual do usuário: "o correto é ter
 * produtos e catálogos e eu posso adicionar customização em um produto ou catálogo". Venda de
 * Caixa passa a aceitar origem XOR ItemCatalogo/Produto direto, com customizações ad-hoc
 * (escolhidas na hora, para as duas origens) — ver modulos/CAIXA/decisoes-caixa.md.
 *
 * <p>V0.13.0 (#516, RN-NOVA-1/9) — Item de Catálogo passou de "1 produto principal + customizações
 * anexadas" (cada uma com preço próprio, somado ao subtotal do item) para composição livre de N
 * componentes sem preço próprio (RN-NOVA-2/3 — o preço já é do item inteiro). Vender um item de
 * Catálogo agora baixa/reverte o estoque de TODOS os componentes, mas eles não aparecem mais em
 * {@code VendaCaixaItemResponseDTO.customizacoes()} (isso continua exclusivo da customização
 * ad-hoc, RN-030) — a verificação passa a ser via estoque + {@code ItemCatalogoComponenteRepository}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VendaCaixaItemCatalogoIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ItemCatalogoRepository itemCatalogoRepository;
    @Autowired ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;
    @Autowired CaixaTurnoService caixaTurnoService;
    @Autowired VendaCaixaService vendaCaixaService;
    @Autowired MetodoPagamentoConfiguravelService metodoPagamentoService;

    private final AtomicInteger contador = new AtomicInteger(1);
    @Autowired PasswordEncoder passwordEncoder;

    /** #487 (V0.12.0) — cancelar venda exige a senha da usuaria logada. */
    private static final String SENHA_TESTE = "senha-de-teste-123";

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("venda-catalogo-" + UUID.randomUUID() + "@test.com")
                .senhaHash(passwordEncoder.encode(SENHA_TESTE)).ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        metodoPagamentoService.seedMetodosPadrao(usuario);
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
    }

    private Produto criarProduto(String nome, TipoProduto tipo, BigDecimal precoVenda, BigDecimal estoqueAtual) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(contador.getAndIncrement()).nome(nome).tipo(tipo)
                .tempoProducao(1).rendimento(BigDecimal.ONE).precoVenda(precoVenda)
                .estoqueAtual(estoqueAtual).permitirEstoqueNegativo(true).ativo(true).build());
    }

    /** V0.13.0 (#516) — monta um Item de Catálogo direto no banco (bypass do Service, mesmo
     * espírito do teste original) com N componentes Produto-base, preço fixo já calculado. */
    private ItemCatalogo criarItemCatalogo(Catalogo catalogo, BigDecimal precoVenda, Produto... componentes) {
        ItemCatalogo item = itemCatalogoRepository.save(ItemCatalogo.builder()
                .catalogo(catalogo).nome("Kit Teste").tempoProducao(0)
                .precoVenda(precoVenda).override(true).build());
        for (Produto componente : componentes) {
            itemCatalogoComponenteRepository.save(ItemCatalogoComponente.builder()
                    .itemCatalogo(item).produtoBase(componente).quantidade(BigDecimal.ONE).build());
        }
        return item;
    }

    private UUID metodoDinheiroId() {
        return metodoPagamentoService.listar().stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.DINHEIRO).findFirst().orElseThrow().id();
    }

    @Test
    void vendeItemDeCatalogoComComponentesBaixaEstoqueDeTodosEles() {
        seedUsuario();
        Produto produtoPrincipal = criarProduto("Caixa de Bombom", TipoProduto.PRODUTO, new BigDecimal("20.00"), new BigDecimal("10"));
        Produto segundoComponente = criarProduto("Laço Personalizado", TipoProduto.CUSTOMIZACAO, new BigDecimal("5.00"), new BigDecimal("10"));
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Teste").ativo(true).build());
        ItemCatalogo item = criarItemCatalogo(catalogo, new BigDecimal("20.00"), produtoPrincipal, segundoComponente);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(item.getId(), null, BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodoDinheiroId(), new BigDecimal("20.00"), null)),
                null);

        Object resultado = vendaCaixaService.registrarVenda(request);
        assertInstanceOf(VendaCaixaResponseDTO.class, resultado);
        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) resultado;

        // preço do item já é fixo (override) — 20.00, sem soma adicional por componente (RN-NOVA-2/3)
        assertEquals(0, new BigDecimal("20.00").compareTo(venda.total()));
        assertEquals(1, venda.itens().size());
        assertEquals(item.getId(), venda.itens().get(0).itemCatalogoId());
        // componentes de catálogo não viram "customizacoes" na resposta (RN-030 é só ad-hoc)
        assertEquals(0, venda.itens().get(0).customizacoes().size());

        Produto principalAtualizado = produtoRepository.findById(produtoPrincipal.getId()).orElseThrow();
        Produto segundoAtualizado = produtoRepository.findById(segundoComponente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("9").compareTo(principalAtualizado.getEstoqueAtual()));
        assertEquals(0, new BigDecimal("9").compareTo(segundoAtualizado.getEstoqueAtual()));
    }

    @Test
    void vendeProdutoDiretoComCustomizacaoAdHoc() {
        seedUsuario();
        Produto produtoDireto = criarProduto("Caneca Simples", TipoProduto.PRODUTO, new BigDecimal("15.00"), new BigDecimal("10"));
        Produto customizacaoAdHoc = criarProduto("Gravação a Laser", TipoProduto.CUSTOMIZACAO, new BigDecimal("8.00"), new BigDecimal("10"));

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, produtoDireto.getId(), BigDecimal.ONE,
                        List.of(new VendaCaixaItemCustomizacaoRequestDTO(customizacaoAdHoc.getId(), 1)))),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodoDinheiroId(), new BigDecimal("23.00"), null)),
                null);

        Object resultado = vendaCaixaService.registrarVenda(request);
        assertInstanceOf(VendaCaixaResponseDTO.class, resultado);
        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) resultado;

        // 15.00 (produto) + 8.00 (customização ad-hoc) = 23.00
        assertEquals(0, new BigDecimal("23.00").compareTo(venda.total()));
        assertNull(venda.itens().get(0).itemCatalogoId());
        assertEquals(1, venda.itens().get(0).customizacoes().size());

        Produto customizacaoAtualizada = produtoRepository.findById(customizacaoAdHoc.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("9").compareTo(customizacaoAtualizada.getEstoqueAtual()));
    }

    @Test
    void cancelamentoReverteEstoqueDeTodosOsComponentesDoItemDeCatalogo() {
        seedUsuario();
        Produto produtoPrincipal = criarProduto("Caixa de Bombom", TipoProduto.PRODUTO, new BigDecimal("20.00"), new BigDecimal("10"));
        Produto segundoComponente = criarProduto("Laço Personalizado", TipoProduto.CUSTOMIZACAO, new BigDecimal("5.00"), new BigDecimal("10"));
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Teste").ativo(true).build());
        ItemCatalogo item = criarItemCatalogo(catalogo, new BigDecimal("20.00"), produtoPrincipal, segundoComponente);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(item.getId(), null, BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodoDinheiroId(), new BigDecimal("20.00"), null)),
                null);
        VendaCaixaResponseDTO venda = (VendaCaixaResponseDTO) vendaCaixaService.registrarVenda(request);

        vendaCaixaService.cancelarVenda(venda.id(),
                new CancelarVendaCaixaRequestDTO("Cliente desistiu da compra no balcão", SENHA_TESTE, true));

        Produto principalRevertido = produtoRepository.findById(produtoPrincipal.getId()).orElseThrow();
        Produto segundoRevertido = produtoRepository.findById(segundoComponente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("10").compareTo(principalRevertido.getEstoqueAtual()));
        assertEquals(0, new BigDecimal("10").compareTo(segundoRevertido.getEstoqueAtual()));

        VendaCaixaResponseDTO vendaCancelada = vendaCaixaService.buscarPorId(venda.id());
        assertEquals(StatusVendaCaixa.CANCELADA, vendaCancelada.status());
    }

    @Test
    void itemComProdutoEItemCatalogoAoMesmoTempoEBloqueado() {
        seedUsuario();
        Produto produto = criarProduto("Produto Qualquer", TipoProduto.PRODUTO, new BigDecimal("10.00"), new BigDecimal("10"));
        Catalogo catalogo = catalogoRepository.save(Catalogo.builder()
                .usuario(usuario).numero(1).nome("Catálogo Teste").ativo(true).build());
        ItemCatalogo item = criarItemCatalogo(catalogo, new BigDecimal("10.00"), produto);

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(item.getId(), produto.getId(), BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodoDinheiroId(), new BigDecimal("10.00"), null)),
                null);

        assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
    }

    @Test
    void itemSemProdutoESemItemCatalogoEBloqueado() {
        seedUsuario();

        VendaCaixaRequestDTO request = new VendaCaixaRequestDTO(
                null,
                List.of(new VendaCaixaItemRequestDTO(null, null, BigDecimal.ONE, null)),
                null, null,
                List.of(new VendaCaixaPagamentoRequestDTO(metodoDinheiroId(), new BigDecimal("10.00"), null)),
                null);

        assertThrows(BusinessException.class, () -> vendaCaixaService.registrarVenda(request));
    }
}
