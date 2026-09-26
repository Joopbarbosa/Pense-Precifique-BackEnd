package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.FornecedorInsumo;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.GerarListaCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.PreviaListaCompraResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V0.15.0 — #546 (RN-NOVA-12, RN-NOVA-13). Cenários CEN-NOVO-21, 22, 23. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ListaCompraIT {

    @Autowired ListaCompraService listaCompraService;
    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired FornecedorInsumoRepository fornecedorInsumoRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numeroInsumo = 1;
    private int numeroCadastro = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("lista-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome, String estoque, String minimo) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numeroInsumo++).nome(nome)
                .unidadeMedida(un).estoqueAtual(new BigDecimal(estoque)).custoUnitario(BigDecimal.ONE)
                .estoqueMinimo(minimo != null ? new BigDecimal(minimo) : null).build());
    }

    private Cliente fornecedor(String nome) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(false).ehFornecedor(true).ativa(true).build());
    }

    private void vinculo(Cliente f, Insumo i, String preco) {
        fornecedorInsumoRepository.save(FornecedorInsumo.builder().usuario(usuario).fornecedor(f).insumo(i)
                .precoReferencia(preco != null ? new BigDecimal(preco) : null).build());
    }

    private static List<String> nomes(PreviaListaCompraResponse p) {
        return p.linhas().stream().map(l -> l.insumo().nome()).toList();
    }

    @Test
    void cen21_filtrosDeEstoqueSomam() {
        insumo("Cola Branca 1L", "1", "5");
        insumo("Papel Kraft", "-2", null);
        insumo("Fita de cetim", "20", "5");
        Insumo inativo = insumo("Glitter", "0", "3");
        inativo.setAtivo(false);
        insumoRepository.save(inativo);

        PreviaListaCompraResponse p = listaCompraService.previa(true, true, null, null);
        assertEquals(List.of("Cola Branca 1L", "Papel Kraft"), nomes(p));
        assertEquals(0, new BigDecimal("4").compareTo(p.linhas().get(0).quantidadeSugerida()));
        assertEquals(0, new BigDecimal("2").compareTo(p.linhas().get(1).quantidadeSugerida()));

        // só "abaixo do mínimo": insumo sem mínimo nunca entra, mesmo negativo
        assertEquals(List.of("Cola Branca 1L"), nomes(listaCompraService.previa(true, false, null, null)));
    }

    @Test
    void selecaoManualSempreAcrescentaEFornecedorRestringe() {
        Insumo cola = insumo("Cola", "1", "5");
        Insumo kraft = insumo("Kraft", "-2", null);
        Insumo fita = insumo("Fita", "20", "5");
        Cliente papelaria = fornecedor("Papelaria Central");
        vinculo(papelaria, cola, "13.20");

        PreviaListaCompraResponse manual = listaCompraService.previa(true, false, null, List.of(fita.getId()));
        assertEquals(List.of("Cola", "Fita"), nomes(manual));
        assertNull(manual.linhas().get(1).quantidadeSugerida());

        PreviaListaCompraResponse restrita = listaCompraService.previa(true, true, papelaria.getId(), null);
        assertEquals(List.of("Cola"), nomes(restrita));
        assertEquals("Papelaria Central", restrita.linhas().get(0).fornecedorSugerido().nome());

        // fornecedor sem filtro de estoque: todos os insumos vinculados a ele
        assertEquals(List.of("Cola"), nomes(listaCompraService.previa(false, false, papelaria.getId(), null)));
        assertEquals(List.of(), nomes(listaCompraService.previa(false, false, null, null)));
        assertTrue(kraft.getId() != null);
    }

    @Test
    void cen22_fornecedorSugeridoPeloMenorPreco() {
        Insumo cola = insumo("Cola Branca 1L", "1", "5");
        Cliente papelaria = fornecedor("Papelaria Central");
        Cliente atacado = fornecedor("Atacado Arte");
        Cliente inativo = fornecedor("Barato Inativo");
        inativo.setAtiva(false);
        clienteRepository.save(inativo);
        vinculo(papelaria, cola, "15.00");
        vinculo(atacado, cola, "13.50");
        vinculo(inativo, cola, "9.90");

        PreviaListaCompraResponse.Linha linha = listaCompraService.previa(true, false, null, null).linhas().get(0);
        assertEquals("Atacado Arte", linha.fornecedorSugerido().nome());
        assertEquals(0, new BigDecimal("13.50").compareTo(linha.precoReferencia()));
        // opções para trocar: só vínculos válidos, do menor preço para o maior
        assertEquals(List.of("Atacado Arte", "Papelaria Central"),
                linha.fornecedores().stream().map(o -> o.fornecedor().nome()).toList());
    }

    @Test
    void semPrecoDeReferenciaSugereFornecedorDaCompraMaisRecente() {
        Insumo cola = insumo("Cola", "1", "5");
        Cliente antigo = fornecedor("Antigo");
        Cliente recente = fornecedor("Recente");
        compraService.confirmarNova(new CompraRequest(LocalDate.now().minusDays(10), false, antigo.getId(), false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, BigDecimal.ONE, BigDecimal.TEN))));
        compraService.confirmarNova(new CompraRequest(LocalDate.now().minusDays(2), false, recente.getId(), false, null, null,
                List.of(new CompraItemRequest(cola.getId(), null, BigDecimal.ONE, BigDecimal.TEN))));
        // a confirmação criou vínculos com preço; zera os preços para cair no critério da compra
        fornecedorInsumoRepository.findByUsuarioIdAndInsumoId(usuario.getId(), cola.getId()).forEach(v -> {
            v.setPrecoReferencia(null);
            fornecedorInsumoRepository.save(v);
        });

        assertEquals("Recente", listaCompraService.previa(false, false, null, List.of(cola.getId()))
                .linhas().get(0).fornecedorSugerido().nome());
    }

    @Test
    void cen23_listaGeradaEImutavelEViraCompra() {
        Insumo cola = insumo("Cola", "1", "5");
        Insumo kraft = insumo("Kraft", "-2", null);
        Cliente papelaria = fornecedor("Papelaria Central");
        vinculo(papelaria, cola, "13.20");

        ListaCompraResponse lista = listaCompraService.gerar(new GerarListaCompraRequest(List.of(
                new GerarListaCompraRequest.Linha(cola.getId(), new BigDecimal("4"), papelaria.getId()),
                new GerarListaCompraRequest.Linha(kraft.getId(), new BigDecimal("2.5"), null))));
        assertEquals("LST-1", lista.identificador());
        assertEquals(0, new BigDecimal("13.20").compareTo(lista.itens().get(0).precoReferencia()));

        Insumo c = insumoRepository.findById(cola.getId()).orElseThrow();
        c.setEstoqueAtual(new BigDecimal("50"));
        c.setNome("Cola renomeada");
        insumoRepository.save(c);

        ListaCompraResponse retrato = listaCompraService.buscar(lista.id());
        assertEquals("Cola", retrato.itens().get(0).insumoNome());
        assertEquals(0, new BigDecimal("1").compareTo(retrato.itens().get(0).estoqueAtual()));
        assertEquals(0, new BigDecimal("4").compareTo(retrato.itens().get(0).quantidade()));

        CompraResponse rascunho = listaCompraService.criarCompra(lista.id());
        assertEquals(StatusCompra.RASCUNHO, rascunho.status());
        assertTrue(rascunho.multiplosFornecedores()); // Papelaria + "sem fornecedor"
        assertEquals(2, rascunho.itens().size());
        assertNull(rascunho.itens().get(0).precoTotal());
        assertEquals("Papelaria Central", rascunho.itens().get(0).fornecedor().nome());
        assertNull(rascunho.itens().get(1).fornecedor());

        // a lista pode gerar mais de um rascunho; histórico mostra a quantidade de itens
        listaCompraService.criarCompra(lista.id());
        assertEquals(2L, listaCompraService.historico(PageRequest.of(0, 10)).getContent().get(0).quantidadeItens());
    }

    @Test
    void criarCompraComFornecedorUnicoNasceEmModoUnico() {
        Insumo cola = insumo("Cola", "1", "5");
        Insumo fita = insumo("Fita", "1", "5");
        Cliente papelaria = fornecedor("Papelaria Central");
        ListaCompraResponse lista = listaCompraService.gerar(new GerarListaCompraRequest(List.of(
                new GerarListaCompraRequest.Linha(cola.getId(), BigDecimal.ONE, papelaria.getId()),
                new GerarListaCompraRequest.Linha(fita.getId(), BigDecimal.ONE, papelaria.getId()))));
        CompraResponse rascunho = listaCompraService.criarCompra(lista.id());
        assertFalse(rascunho.multiplosFornecedores());
        assertEquals("Papelaria Central", rascunho.fornecedor().nome());
    }

    @Test
    void gerarVazioOuComQuantidadeInvalidaBloqueia() {
        Insumo cola = insumo("Cola", "1", "5");
        assertEquals("Escolha pelo menos um insumo para gerar a lista.", assertThrows(BusinessException.class,
                () -> listaCompraService.gerar(new GerarListaCompraRequest(List.of()))).getMessage());
        BusinessException ex = assertThrows(BusinessException.class, () -> listaCompraService.gerar(
                new GerarListaCompraRequest(List.of(new GerarListaCompraRequest.Linha(cola.getId(), null, null)))));
        assertTrue(ex.getMessage().contains("Linha 1 (Cola): informe uma quantidade maior que zero"), ex.getMessage());
        // nada foi gravado: a próxima lista é LST-1
        assertEquals("LST-1", listaCompraService.gerar(new GerarListaCompraRequest(List.of(
                new GerarListaCompraRequest.Linha(cola.getId(), BigDecimal.ONE, null)))).identificador());
    }
}
