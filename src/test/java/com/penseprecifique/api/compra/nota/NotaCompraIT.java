package com.penseprecifique.api.compra.nota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.infra.storage.R2StorageClient;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.ComprovanteTipo;
import com.penseprecifique.api.shared.domain.enums.OrigemCompra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.AcaoFornecedor;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.Escolha;
import com.penseprecifique.api.shared.dto.response.compra.CompraItemResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaRascunhoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V0.16.0 (#683) — registrar compra por nota, com o leitor-fiscal simulado. Cobre CEN-NOVO-9 a 13, 33 a 35,
 * 42, 43, 50 (parte de salvar rascunho), 55, 60, 61 e 62, mais a assinatura entre leitura e rascunho e a
 * tradução dos erros do serviço. A conciliação automática tem testes próprios (#681, ConciliacaoNotaIT).
 */
class NotaCompraIT extends NotaServicosSimulados {

    private static final String CNPJ = "11222333000181";
    private static final String CHAVE = "35261011222333000181650010000000011000000019";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired NotaCompraService notaCompraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired CompraRepository compraRepository;

    private Usuario usuario;
    private UnidadeMedida un;
    private Insumo fita;
    private Insumo cola;
    private int numero = 1;

    @BeforeEach
    void seed() {
        LEITOR.resetAll();
        IA.resetAll();
        usuario = usuarioRepository.save(Usuario.builder()
                .email("nota-compra-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
        fita = insumo("Fita de cetim");
        cola = insumo("Cola branca");
    }

    private Insumo insumo(String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).unidadeMedida(un)
                .estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private Cliente cadastro(boolean fornecedor, boolean cliente) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numero++).nome("Papelaria Central")
                .ehFornecedor(fornecedor).ehCliente(cliente).tipoPessoa(TipoPessoa.JURIDICA).documento(CNPJ).ativa(true).build());
    }

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static NotaLida.Item item(String nome, String qtd, String final_, String bruto, String desconto) {
        return new NotaLida.Item(nome, v(qtd), v(final_), bruto == null ? null : v(bruto), desconto == null ? null : v(desconto), "UN", null, null);
    }

    private static NotaLida nota(String chave, String origem, String total, String descontoGeral, String acrescimos, NotaLida.Item... itens) {
        return new NotaLida(new NotaLida.Emitente(CNPJ, "Papelaria Central", "SP"), chave, "11", "1",
                LocalDate.now().minusDays(1).toString(), v(total), descontoGeral == null ? null : v(descontoGeral),
                acrescimos == null ? null : v(acrescimos), List.of(itens), origem, "LEITOR_UF", false, "SP", "SP-1", List.of());
    }

    private static NotaLida notaPadrao() {
        return nota(CHAVE, "NFCE_QR", "75.00", null, null,
                item("FITA CETIM 10MM", "10", "30.00", null, null), item("COLA BRANCA 1L", "3", "45.00", null, null));
    }

    private void leitorDevolve(NotaLida nota) throws Exception {
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(JSON.writeValueAsString(nota))));
    }

    private String link(String chave) {
        return "https://www.nfce.fazenda.sp.gov.br/qrcode?p=" + chave + "|2|1|1|ABCDEF";
    }

    private NotaRascunhoRequest pedido(NotaLeituraResponse leitura, AcaoFornecedor acao, String link, Escolha... escolhas) {
        return new NotaRascunhoRequest(leitura.nota(), leitura.assinatura(), List.of(escolhas), acao, link);
    }

    private Escolha liga(int pos, Insumo insumo, String fator) {
        return new Escolha(pos, insumo.getId(), v(fator), false);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    // ---------------------------------------------------------------------------------- leitura

    @Test
    void leituraPorQrDevolveNotaAssinaturaEItensConciliados() throws Exception {
        leitorDevolve(notaPadrao());
        NotaLeituraResponse r = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);

        assertEquals(CHAVE, r.nota().chaveAcesso());
        assertNotNull(r.assinatura());
        assertEquals(2, r.itens().size());
        // #681 — "FITA CETIM 10MM" casa sozinho com o insumo "Fita de cetim" (único candidato).
        assertEquals(NotaLeituraResponse.OrigemLigacao.CASAMENTO_NOME, r.itens().get(0).origemLigacao());
        assertEquals(fita.getId(), r.itens().get(0).insumo().id());
        assertEquals(NotaLeituraResponse.SituacaoFornecedor.NAO_CADASTRADO, r.fornecedor().situacao());
        LEITOR.verify(postRequestedFor(urlEqualTo("/v1/leituras"))
                .withHeader("Authorization", equalTo("Bearer chave-de-teste-do-pocket"))
                .withHeader("X-Conta-Id", equalTo(usuario.getId().toString()))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(matchingJsonPath("$.modelo", equalTo("NFCE")))
                .withRequestBody(matchingJsonPath("$.qrUrl"))
                .withRequestBody(notMatching(".*confirmouEnvioIa.*")));
        assertTrue(compraRepository.findByUsuarioIdAndChaveAcessoAndDeletedAtIsNull(usuario.getId(), CHAVE).isEmpty(), "leitura não grava nada");
    }

    @Test
    void cen33_notaJaConfirmadaBloqueiaSemChamarOServico() throws Exception {
        salvarCompra(CHAVE, StatusCompra.CONFIRMADA, null);
        BusinessException e = assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false));
        assertTrue(e.getMessage().contains("já foi registrada"), e.getMessage());
        assertTrue(e.getItens().get(0).startsWith("COM-"), "traz o número da compra para o link");
        LEITOR.verify(0, postRequestedFor(urlEqualTo("/v1/leituras")));
    }

    @Test
    void cen34_notaDeCompraCanceladaTambemBloqueia() {
        salvarCompra(CHAVE, StatusCompra.CANCELADA, null);
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false));
    }

    @Test
    void cen35_rascunhoExcluidoNaoBloqueia() throws Exception {
        salvarCompra(CHAVE, StatusCompra.RASCUNHO, LocalDateTime.now());
        leitorDevolve(notaPadrao());
        NotaLeituraResponse r = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertNull(r.rascunhoExistente());
        assertNotNull(r.nota());
    }

    @Test
    void cen13_notaComRascunhoAbreORascunhoExistenteSemLerDeNovo() {
        Compra existente = salvarCompra(CHAVE, StatusCompra.RASCUNHO, null);
        NotaLeituraResponse r = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertEquals(existente.getId(), r.rascunhoExistente().id());
        assertNull(r.nota());
        LEITOR.verify(0, postRequestedFor(urlEqualTo("/v1/leituras")));
    }

    @Test
    void cen55_chaveComLetraNoCnpjEGuardadaEmMaiusculaEComparadaSemDiferenciarCaixa() throws Exception {
        String chaveComLetra = "3526101ABC2233300018165001000000001100000001".substring(0, 44).toUpperCase();
        assertEquals(44, chaveComLetra.length());
        leitorDevolve(nota(chaveComLetra, "NFCE_QR", "10.00", null, null, item("FITA CETIM", "1", "10.00", null, null)));
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(chaveComLetra), null, null, false);
        var r = notaCompraService.criarRascunho(pedido(leitura, AcaoFornecedor.SEM_FORNECEDOR, link(chaveComLetra), liga(0, fita, "1")), null, false);
        assertEquals(chaveComLetra, r.compra().chaveAcesso());

        BusinessException repetida = assertThrows(BusinessException.class, () -> {
            // a nota agora está em RASCUNHO: a leitura abre o rascunho; confirmamos a compra e lemos de novo em minúsculas
            Compra c = compraRepository.findById(r.compra().id()).orElseThrow();
            c.setStatus(StatusCompra.CONFIRMADA);
            compraRepository.save(c);
            notaCompraService.ler("NFCE", null, chaveComLetra.toLowerCase(), null, false);
        });
        assertTrue(repetida.getMessage().contains("já foi registrada"), repetida.getMessage());
    }

    // ---------------------------------------------------------------------------------- rascunho

    @Test
    void cen9_rascunhoGeradoPelaNotaComOrigemChaveDataEComprovanteLink() throws Exception {
        Cliente fornecedor = cadastro(true, false);
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertEquals(NotaLeituraResponse.SituacaoFornecedor.FORNECEDOR_CADASTRADO, leitura.fornecedor().situacao());

        NotaRascunhoResponse r = notaCompraService.criarRascunho(
                pedido(leitura, null, link(CHAVE), liga(0, fita, "1"), liga(1, cola, "1")), null, false);

        var compra = r.compra();
        assertEquals(StatusCompra.RASCUNHO, compra.status());
        assertEquals(OrigemCompra.NFCE_QR, compra.origem());
        assertEquals(CHAVE, compra.chaveAcesso());
        assertEquals(LocalDate.now().minusDays(1), compra.dataCompra());
        assertEquals(fornecedor.getId(), compra.fornecedor().id());
        assertEquals(ComprovanteTipo.LINK, compra.comprovanteTipo());
        assertEquals(link(CHAVE), compra.comprovanteUrl());
        assertEquals(2, compra.itens().size());
        igual("75.00", compra.total());
        assertTrue(r.avisos().isEmpty(), "sem diferença no total, sem aviso");
    }

    @Test
    void cen10_cen11_cen12_fornecedorNuncaECriadoOuAlteradoSozinho() throws Exception {
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);

        // sem cadastro e sem escolha: bloqueia, nada criado
        BusinessException semAcao = assertThrows(BusinessException.class, () -> notaCompraService.criarRascunho(
                pedido(leitura, null, link(CHAVE), liga(0, fita, "1"), liga(1, cola, "1")), null, false));
        assertTrue(semAcao.getMessage().contains("Escolha o que fazer"), semAcao.getMessage());
        assertTrue(clienteRepository.findByUsuarioIdAndDocumento(usuario.getId(), CNPJ).isEmpty());

        // cadastrar como fornecedor (CEN-NOVO-11)
        var r = notaCompraService.criarRascunho(pedido(leitura, AcaoFornecedor.CADASTRAR, link(CHAVE),
                liga(0, fita, "1"), liga(1, cola, "1")), null, false);
        Cliente criado = clienteRepository.findByUsuarioIdAndDocumento(usuario.getId(), CNPJ).orElseThrow();
        assertTrue(criado.getEhFornecedor());
        assertFalse(criado.getEhCliente());
        assertEquals(criado.getId(), r.compra().fornecedor().id());
    }

    @Test
    void cen11_semFornecedorSegueSemFornecedorENaoCadastra() throws Exception {
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        var r = notaCompraService.criarRascunho(pedido(leitura, AcaoFornecedor.SEM_FORNECEDOR, link(CHAVE),
                liga(0, fita, "1"), liga(1, cola, "1")), null, false);
        assertNull(r.compra().fornecedor());
        assertTrue(clienteRepository.findByUsuarioIdAndDocumento(usuario.getId(), CNPJ).isEmpty());
    }

    @Test
    void cen12_emitenteQueSoECliente_oferecePapelFornecedorEAArtesaConfirma() throws Exception {
        Cliente soCliente = cadastro(false, true);
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertEquals(NotaLeituraResponse.SituacaoFornecedor.SO_CLIENTE, leitura.fornecedor().situacao());

        assertThrows(BusinessException.class, () -> notaCompraService.criarRascunho(
                pedido(leitura, null, link(CHAVE), liga(0, fita, "1"), liga(1, cola, "1")), null, false));
        assertFalse(clienteRepository.findById(soCliente.getId()).orElseThrow().getEhFornecedor(), "nada mudou sem confirmação");

        var r = notaCompraService.criarRascunho(pedido(leitura, AcaoFornecedor.ADICIONAR_PAPEL, link(CHAVE),
                liga(0, fita, "1"), liga(1, cola, "1")), null, false);
        assertTrue(clienteRepository.findById(soCliente.getId()).orElseThrow().getEhFornecedor());
        assertEquals(soCliente.getId(), r.compra().fornecedor().id());
    }

    @Test
    void cen60_doisItensParaOMesmoInsumoViramUmaLinhaComAvisoNaSimulacao() throws Exception {
        cadastro(true, false);
        leitorDevolve(nota(CHAVE, "NFCE_QR", "55.00", null, null,
                item("FITA CETIM 10MM", "10", "30.00", "33.00", "3.00"), item("FITA CETIM 15MM", "5", "25.00", null, null)));
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        var pedido = pedido(leitura, null, link(CHAVE), liga(0, fita, "1"), liga(1, fita, "1"));

        NotaRascunhoResponse simulada = notaCompraService.criarRascunho(pedido, null, true);
        assertNull(simulada.compra(), "simular não grava");
        assertEquals(1, simulada.linhas().size());
        assertEquals("2 itens serão somados na mesma linha do insumo Fita de cetim", simulada.avisos().get(0).mensagem());
        assertTrue(compraRepository.findByUsuarioIdAndChaveAcessoAndDeletedAtIsNull(usuario.getId(), CHAVE).isEmpty());

        var real = notaCompraService.criarRascunho(pedido, null, false);
        assertEquals(1, real.compra().itens().size());
        igual("15", real.compra().itens().get(0).quantidade());
        igual("58.00", real.compra().itens().get(0).precoCheio());
        igual("3.00", real.compra().itens().get(0).descontoLinha());
        igual("55.00", real.compra().itens().get(0).precoTotal());
    }

    @Test
    void cen61_cen62_itemIgnoradoReduzODescontoGeralEOprecoPagoSaiDoRateio() throws Exception {
        cadastro(true, false);
        leitorDevolve(nota(CHAVE, "NFCE_QR", "80.00", "20.00", null,
                item("FITA CETIM", "4", "10.00", null, null), item("COLA BRANCA", "1", "90.00", null, null)));
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        var r = notaCompraService.criarRascunho(pedido(leitura, null, link(CHAVE), liga(0, fita, "1"),
                new Escolha(1, null, null, true)), null, false);

        igual("2.00", r.compra().descontoNota());
        CompraItemResponse linha = r.compra().itens().get(0);
        igual("10.00", linha.precoCheio());
        igual("8.00", linha.precoTotal());       // 10 − 2 (parte do desconto da nota)
        igual("2.0000", linha.precoUnitario());  // 8,00 ÷ 4
        assertTrue(r.avisos().isEmpty(), "a conferência usa todos os itens lidos e fecha: 100 − 20 = 80");
    }

    @Test
    void cen42_cen43_diferencaSemExplicacaoViraAvisoEDescontosLegitimosNao() throws Exception {
        cadastro(true, false);
        leitorDevolve(nota(CHAVE, "NFCE_QR", "90.00", null, null, item("FITA CETIM", "1", "100.00", null, null)));
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        var com = notaCompraService.criarRascunho(pedido(leitura, null, link(CHAVE), liga(0, fita, "1")), null, false);
        assertEquals("DIFERENCA_NO_TOTAL", com.avisos().get(0).codigo());
        assertEquals("AVISO", com.avisos().get(0).tipo());
        assertTrue(com.avisos().get(0).mensagem().contains("10,00"));

        String outraChave = "35261011222333000181650010000000021000000028";
        leitorDevolve(nota(outraChave, "NFCE_QR", "95.00", "10.00", "5.00", item("COLA BRANCA", "1", "100.00", null, null)));
        NotaLeituraResponse leitura2 = notaCompraService.ler("NFCE", link(outraChave), null, null, false);
        var sem = notaCompraService.criarRascunho(pedido(leitura2, null, link(outraChave), liga(0, cola, "1")), null, false);
        assertTrue(sem.avisos().isEmpty());
        assertEquals("Acréscimos da nota: R$ 5,00", sem.compra().observacoes());
        igual("10.00", sem.compra().descontoNota());
    }

    @Test
    void cen50_rascunhoDeCompraPodeSerSalvoSemTravarPorInsumoAindaNaoCompletado() throws Exception {
        // a regra de bloquear a confirmação com insumo em rascunho é da #687; aqui só o salvar funciona
        cadastro(true, false);
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertEquals(StatusCompra.RASCUNHO, notaCompraService.criarRascunho(
                pedido(leitura, null, link(CHAVE), liga(0, fita, "1"), liga(1, cola, "1")), null, false).compra().status());
    }

    // ---------------------------------------------------------------------------------- assinatura e comprovante

    @Test
    void notaAlteradaDepoisDaLeituraEhRecusada() throws Exception {
        cadastro(true, false);
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        NotaLida adulterada = nota(CHAVE, "NFCE_QR", "75.00", null, null,
                item("FITA CETIM 10MM", "10", "1.00", null, null), item("COLA BRANCA 1L", "3", "45.00", null, null));
        BusinessException e = assertThrows(BusinessException.class, () -> notaCompraService.criarRascunho(
                new NotaRascunhoRequest(adulterada, leitura.assinatura(), List.of(liga(0, fita, "1"), liga(1, cola, "1")), null, link(CHAVE)), null, false));
        assertTrue(e.getMessage().contains("expirou ou foi alterada"), e.getMessage());
        assertTrue(compraRepository.findByUsuarioIdAndChaveAcessoAndDeletedAtIsNull(usuario.getId(), CHAVE).isEmpty());
    }

    @Test
    void linkDeComprovanteTrocadoDepoisDaLeituraEhRecusado() throws Exception {
        cadastro(true, false);
        leitorDevolve(notaPadrao());
        NotaLeituraResponse leitura = notaCompraService.ler("NFCE", link(CHAVE), null, null, false);
        assertThrows(BusinessException.class, () -> notaCompraService.criarRascunho(
                pedido(leitura, null, "https://exemplo.interno/admin", liga(0, fita, "1"), liga(1, cola, "1")), null, false));
    }

    @Test
    void xmlGuardaOArquivoOriginalComoComprovante() throws Exception {
        cadastro(true, false);
        when(r2StorageClient.upload(anyString(), any(byte[].class), anyString())).thenReturn("http://r2.local/comprovante.xml");
        leitorDevolve(nota(CHAVE, "NFE_XML", "75.00", null, null, item("FITA CETIM 10MM", "10", "30.00", null, null),
                item("COLA BRANCA 1L", "3", "45.00", null, null)));
        MockMultipartFile xml = new MockMultipartFile("arquivo", "nota.xml", "application/xml", "<nfeProc/>".getBytes(StandardCharsets.UTF_8));

        NotaLeituraResponse leitura = notaCompraService.ler("NFE", null, null, xml, false);
        LEITOR.verify(postRequestedFor(urlEqualTo("/v1/leituras"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .withRequestBody(containing("name=\"modelo\""))
                .withRequestBody(containing("name=\"arquivo\""))
                .withRequestBody(containing("name=\"confirmouEnvioIa\"")));
        var r = notaCompraService.criarRascunho(pedido(leitura, null, null, liga(0, fita, "1"), liga(1, cola, "1")), xml, false);

        assertEquals(OrigemCompra.NFE_XML, r.compra().origem());
        assertEquals(ComprovanteTipo.XML, r.compra().comprovanteTipo());
        assertEquals("nota.xml", r.compra().comprovanteNome());
        assertEquals("http://r2.local/comprovante.xml", r.compra().comprovanteUrl());
        verify(r2StorageClient).upload(anyString(), any(byte[].class), eq("application/xml"));
    }

    @Test
    void pdfOuFotoSemConfirmarOEnvioAIaEhRecusadoAntesDeChamarOServico() {
        MockMultipartFile pdf = new MockMultipartFile("arquivo", "nota.pdf", "application/pdf", "%PDF-1.4 teste".getBytes(StandardCharsets.UTF_8));
        BusinessException e = assertThrows(BusinessException.class, () -> notaCompraService.ler("NFE", null, null, pdf, false));
        assertTrue(e.getMessage().contains("Confirme"), e.getMessage());
        LEITOR.verify(0, postRequestedFor(urlEqualTo("/v1/leituras")));
    }

    @Test
    void arquivoComTipoOuConteudoNaoAceitoEhRecusado() {
        MockMultipartFile exe = new MockMultipartFile("arquivo", "x.exe", "application/octet-stream", new byte[]{1, 2, 3});
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFE", null, null, exe, true));
        MockMultipartFile falsoPdf = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", "nao e pdf".getBytes(StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFE", null, null, falsoPdf, true));
        MockMultipartFile webp = new MockMultipartFile("arquivo", "x.webp", "image/webp", new byte[]{1, 2, 3, 4});
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFE", null, null, webp, true));
        verify(r2StorageClient, never()).upload(anyString(), any(byte[].class), anyString());
    }

    @Test
    void exatamenteUmaEntradaEModeloValido() {
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", null, null, null, false));
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), CHAVE, null, false));
        assertThrows(BusinessException.class, () -> notaCompraService.ler("XYZ", link(CHAVE), null, null, false));
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFE", link(CHAVE), null, null, false));
        assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", null, "123", null, false));
    }

    // ---------------------------------------------------------------------------------- erros do serviço

    @Test
    void erroDeBloqueioDoServicoRepassaOsCamposDoModal() {
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"tipo\":\"BLOQUEIO\",\"codigo\":\"LIMITE_IA_CONTA\",\"titulo\":\"Limite de leituras\","
                        + "\"motivo\":\"Você usou todas as leituras por IA deste mês.\",\"comoResolver\":\"Registre a compra à mão.\",\"itens\":[]}")));
        BusinessException e = assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false));
        assertEquals("Limite de leituras", e.getTitulo());
        assertEquals("Registre a compra à mão.", e.getComoResolver());
    }

    @Test
    void erro5xxOuCorpoIlegivelViraBloqueioGenericoSemDetalheInterno() {
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(500).withBody("java.lang.NullPointerException at x.y.Z")));
        BusinessException e = assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false));
        assertEquals(LeitorFiscalClient.MSG_INDISPONIVEL, e.getMessage());
        assertFalse(e.getMotivo().contains("NullPointer"));

        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(200).withBody("isto não é json")));
        assertEquals(LeitorFiscalClient.MSG_INDISPONIVEL,
                assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false)).getMessage());

        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(401)));
        assertEquals(LeitorFiscalClient.MSG_INDISPONIVEL,
                assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false)).getMessage());
    }

    @Test
    void notaLidaForaDoContratoEhRecusada() throws Exception {
        leitorDevolve(nota("curta", "NFCE_QR", "1.00", null, null, item("FITA", "1", "1.00", null, null)));
        assertEquals(LeitorFiscalClient.MSG_INDISPONIVEL,
                assertThrows(BusinessException.class, () -> notaCompraService.ler("NFCE", link(CHAVE), null, null, false)).getMessage());
    }

    private Compra salvarCompra(String chave, StatusCompra status, LocalDateTime excluidaEm) {
        Compra compra = Compra.builder().usuario(usuario).numero(numero++).status(status).dataCompra(LocalDate.now())
                .origem(OrigemCompra.NFCE_QR).chaveAcesso(chave).deletedAt(excluidaEm).build();
        return compraRepository.save(compra);
    }
}
