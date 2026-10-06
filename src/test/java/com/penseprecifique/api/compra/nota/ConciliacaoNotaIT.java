package com.penseprecifique.api.compra.nota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.AcaoFornecedor;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.Escolha;
import com.penseprecifique.api.shared.dto.response.compra.CompraItemResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.ItemConciliacao;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.16.0 (#681, RN-NOVA-12 a 15) — conciliação na leitura e gravação do vínculo ao criar o rascunho, com
 * o leitor-fiscal simulado e a IA sem resposta (sugestão pulada). Cobre CEN-NOVO-14, 17, 18, 19, 21, 52 (limpeza do vínculo) e 59.
 */
class ConciliacaoNotaIT extends NotaServicosSimulados {

    private static final String ESTRELA = "11222333000181";
    private static final String AURORA = "11444777000161";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger CHAVES = new AtomicInteger(1);

    @Autowired NotaCompraService notaCompraService;
    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired VinculoItemNotaRepository vinculoRepository;
    @Autowired CompraService compraService;
    @Autowired HistoricoVinculoNotaService historico;

    private Usuario usuario;
    private UnidadeMedida un;
    private int numero = 1;

    @BeforeEach
    void seed() {
        LEITOR.resetAll();
        IA.resetAll();
        usuario = usuarioRepository.save(Usuario.builder()
                .email("conciliacao-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        un = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
    }

    private Insumo insumo(String nome, String marca) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome(nome).marca(marca)
                .unidadeMedida(un).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private static BigDecimal v(String s) {
        return new BigDecimal(s);
    }

    private static NotaLida.Item item(String nome, String qtd, String valorFinal) {
        return new NotaLida.Item(nome, v(qtd), v(valorFinal), null, null, "UN", null, null);
    }

    private static NotaLida nota(String cnpj, String descontoGeral, NotaLida.Item... itens) {
        String chave = String.format("35261011222333000181650010000%015d", CHAVES.getAndIncrement());
        BigDecimal soma = List.of(itens).stream().map(NotaLida.Item::valorFinal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = descontoGeral == null ? BigDecimal.ZERO : v(descontoGeral);
        return new NotaLida(new NotaLida.Emitente(cnpj, cnpj.equals(ESTRELA) ? "Papelaria Estrela" : "Distribuidora Aurora", "SP"),
                chave, "11", "1", LocalDate.now().minusDays(1).toString(), soma.subtract(desconto), desconto, null,
                List.of(itens), "NFCE_QR", "LEITOR_UF", false, "SP", "SP-1", List.of());
    }

    private NotaLeituraResponse ler(NotaLida nota) throws Exception {
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(JSON.writeValueAsString(nota))));
        return notaCompraService.ler("NFCE", null, nota.chaveAcesso(), null, false);
    }

    private List<CompraItemResponse> confirmar(NotaLeituraResponse leitura, Escolha... escolhas) {
        return notaCompraService.criarRascunho(new NotaRascunhoRequest(leitura.nota(), leitura.assinatura(), List.of(escolhas),
                AcaoFornecedor.SEM_FORNECEDOR, null), null, false).compra().itens();
    }

    private static Escolha liga(int pos, Insumo insumo, String fator, OrigemLigacao origem) {
        return new Escolha(pos, insumo.getId(), v(fator), false, origem);
    }

    private static Escolha ignora(int pos) {
        return new Escolha(pos, null, null, true, null);
    }

    private static void igual(String esperado, BigDecimal valor) {
        assertEquals(0, new BigDecimal(esperado).compareTo(valor), "esperado " + esperado + ", veio " + valor);
    }

    @Test
    void cen14_cen21_vinculoSalvoConciliaNaProximaNotaDoMesmoEmitenteENaoEmOutro() throws Exception {
        Insumo papel = insumo("Folha especial", null);
        NotaLeituraResponse primeira = ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00")));
        assertEquals(OrigemLigacao.SEM_LIGACAO, primeira.itens().get(0).origemLigacao(), "nome não casa: a artesã liga à mão");
        confirmar(primeira, liga(0, papel, "100", null));

        ItemConciliacao segunda = ler(nota(ESTRELA, null, item("papel couché 250g a4  c/100", "2", "76.00"))).itens().get(0);
        assertEquals(OrigemLigacao.VINCULO_SALVO, segunda.origemLigacao());
        assertEquals(papel.getId(), segunda.insumo().id());
        igual("100", segunda.fator());

        // RN-NOVA-26 (CEN-NOVO-68): o vínculo de outro fornecedor com o mesmo nome de item é proposto antes da IA.
        ItemConciliacao outroEmitente = ler(nota(AURORA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))).itens().get(0);
        assertEquals(OrigemLigacao.VINCULO_OUTRO_FORNECEDOR, outroEmitente.origemLigacao());
        assertEquals(papel.getId(), outroEmitente.insumo().id());
        igual("100", outroEmitente.fator());
    }

    @Test
    void cen68_vinculoDeOutroFornecedorLigaSemIaEGravaOVinculoDoFornecedorDaNota() throws Exception {
        Insumo papel = insumo("Folha especial", null);
        confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, papel, "100", null));

        NotaLeituraResponse leitura = ler(nota(AURORA, null, item("PAPEL COUCHE 250G A4 C/100", "2", "76.00")));
        assertEquals(OrigemLigacao.VINCULO_OUTRO_FORNECEDOR, leitura.itens().get(0).origemLigacao());
        assertEquals(0, IA.getAllServeEvents().size(), "a IA não é chamada quando o vínculo resolve");
        confirmar(leitura, liga(0, papel, "100", OrigemLigacao.VINCULO_OUTRO_FORNECEDOR));

        VinculoItemNota gravado = vinculoRepository.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(
                usuario.getId(), AURORA, "papel couche 250g a4 c/100").orElseThrow();
        assertEquals(OrigemVinculoItemNota.OUTRO_FORNECEDOR, gravado.getOrigem());
        assertEquals(papel.getId(), gravado.getInsumo().getId());
        assertEquals(OrigemLigacao.VINCULO_SALVO, ler(nota(AURORA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))).itens().get(0).origemLigacao());
    }

    @Test
    void cen69_nomeVinculadoAInsumosDiferentesNaoLigaSozinhoEMostraOsDoisCandidatos() throws Exception {
        Insumo a = insumo("Folha especial", null);
        Insumo b = insumo("Papel grosso", null);
        confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, a, "100", null));
        confirmar(ler(nota(AURORA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, b, "50", OrigemLigacao.VINCULO_OUTRO_FORNECEDOR));

        ItemConciliacao item = ler(nota("12345678000195", null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))).itens().get(0);
        assertEquals(OrigemLigacao.SEM_LIGACAO, item.origemLigacao());
        assertNull(item.insumo());
        assertEquals(java.util.Set.of(a.getId(), b.getId()),
                item.candidatos().stream().map(NotaLeituraResponse.InsumoProposto::id).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void cen68_vinculoIgnoradoOuDeInsumoInativoDeOutroFornecedorNaoConta() throws Exception {
        Insumo caneta = insumo("Caneta gel azul", null);
        confirmar(ler(nota(ESTRELA, null, item("LANCHE NATURAL", "1", "9.00"), item("MARCADOR PRETO", "1", "3.00"))),
                ignora(0), liga(1, caneta, "1", null));
        caneta.setAtivo(false);
        insumoRepository.save(caneta);

        List<ItemConciliacao> itens = ler(nota(AURORA, null, item("LANCHE NATURAL", "1", "9.00"), item("MARCADOR PRETO", "1", "3.00"))).itens();
        assertEquals(OrigemLigacao.SEM_LIGACAO, itens.get(0).origemLigacao(), "item ignorado por outro fornecedor não vira proposta");
        assertEquals(OrigemLigacao.SEM_LIGACAO, itens.get(1).origemLigacao(), "insumo inativo não é proposto");
    }

    @Test
    void cen66_vinculoGuardaACompraEOHistoricoDevolveOIdentificador() throws Exception {
        Insumo caneta = insumo("Caneta gel azul", null);
        NotaLeituraResponse leitura = ler(nota(ESTRELA, null, item("CANETA GEL AZUL", "3", "12.00")));
        var compra = notaCompraService.criarRascunho(new NotaRascunhoRequest(leitura.nota(), leitura.assinatura(),
                List.of(liga(0, caneta, "1", OrigemLigacao.CASAMENTO_NOME)), AcaoFornecedor.SEM_FORNECEDOR, null), null, false).compra();

        var registro = historico.listar(null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20)).getContent().get(0);
        assertEquals(compra.id(), registro.compraId());
        assertEquals(compra.identificador(), registro.compraIdentificador());

        compraService.excluirRascunho(compra.id());
        var depois = historico.listar(null, null, null, null, null, org.springframework.data.domain.PageRequest.of(0, 20)).getContent().get(0);
        assertNull(depois.compraId(), "compra excluída deixa de ser destino");
    }

    @Test
    void cen21_confirmarGravaOVinculoComOrigemEFator() throws Exception {
        Insumo caneta = insumo("Caneta gel azul", null);
        NotaLeituraResponse leitura = ler(nota(ESTRELA, null, item("CANETA GEL AZUL", "3", "12.00")));
        assertEquals(OrigemLigacao.CASAMENTO_NOME, leitura.itens().get(0).origemLigacao());
        confirmar(leitura, liga(0, caneta, "1", OrigemLigacao.CASAMENTO_NOME));

        VinculoItemNota vinculo = vinculoRepository.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(
                usuario.getId(), ESTRELA, "caneta gel azul").orElseThrow();
        assertEquals(caneta.getId(), vinculo.getInsumo().getId());
        igual("1", vinculo.getFator());
        assertEquals(OrigemVinculoItemNota.CASAMENTO_NOME, vinculo.getOrigem());
        assertEquals("Papelaria Estrela", vinculo.getEmitenteNome());
    }

    @Test
    void cen17_conciliacaoParcialMostraCadaOrigemEOQueSobraSemLigacao() throws Exception {
        Insumo papel = insumo("Folha especial", null);
        Insumo caneta = insumo("Caneta gel azul", null);
        confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, papel, "100", null));

        List<ItemConciliacao> itens = ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"),
                item("CANETA GEL AZUL", "3", "12.00"), item("LANCHE NATURAL", "1", "9.00"))).itens();

        assertEquals(OrigemLigacao.VINCULO_SALVO, itens.get(0).origemLigacao());
        assertEquals(OrigemLigacao.CASAMENTO_NOME, itens.get(1).origemLigacao());
        assertEquals(caneta.getId(), itens.get(1).insumo().id());
        assertEquals(OrigemLigacao.SEM_LIGACAO, itens.get(2).origemLigacao());
        assertNull(itens.get(2).insumo());
        assertEquals(3, itens.size(), "a modal mostra todos os itens");
    }

    @Test
    void cen18_ignorarFicaLembradoParaOEmitente() throws Exception {
        Insumo caneta = insumo("Caneta gel azul", null);
        List<CompraItemResponse> linhas = confirmar(ler(nota(ESTRELA, null, item("CANETA GEL AZUL", "3", "12.00"),
                item("LANCHE NATURAL", "1", "9.00"))), liga(0, caneta, "1", OrigemLigacao.CASAMENTO_NOME), ignora(1));
        assertEquals(1, linhas.size(), "item ignorado não vira linha");

        ItemConciliacao lanche = ler(nota(ESTRELA, null, item("LANCHE NATURAL", "2", "18.00"), item("CANETA GEL AZUL", "1", "4.00"))).itens().get(0);
        assertEquals(OrigemLigacao.VINCULO_SALVO, lanche.origemLigacao());
        assertTrue(lanche.ignorar());
    }

    @Test
    void cen19_fatorMultiplicaAQuantidadeEDivideOPrecoUnitario() throws Exception {
        Insumo papel = insumo("Papel couché A4 250g", null);
        List<CompraItemResponse> linhas = confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))),
                liga(0, papel, "100", OrigemLigacao.CASAMENTO_NOME));

        igual("100", linhas.get(0).quantidade());
        igual("0.38", linhas.get(0).precoUnitario());
    }

    @Test
    void cen62_precoUnitarioPagoDepoisDoRateioDoDescontoGeral() throws Exception {
        Insumo papel = insumo("Papel couché A4 250g", null);
        Insumo caneta = insumo("Caneta gel azul", null);
        List<CompraItemResponse> linhas = confirmar(ler(nota(ESTRELA, "5.00", item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"),
                item("CANETA GEL AZUL", "3", "12.00"))),
                liga(0, papel, "100", OrigemLigacao.CASAMENTO_NOME), liga(1, caneta, "1", OrigemLigacao.CASAMENTO_NOME));

        igual("3.80", linhas.get(0).descontoNota());
        igual("34.20", linhas.get(0).precoTotal());
        igual("0.342", linhas.get(0).precoUnitario());
        igual("1.20", linhas.get(1).descontoNota());
        igual("10.80", linhas.get(1).precoTotal());
        igual("3.60", linhas.get(1).precoUnitario());
    }

    @Test
    void corrigirOVinculoAtualizaAMemoria() throws Exception {
        Insumo papel = insumo("Folha especial", null);
        Insumo outro = insumo("Folha comum", null);
        confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, papel, "100", null));
        confirmar(ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))), liga(0, outro, "50", OrigemLigacao.VINCULO_SALVO));

        ItemConciliacao terceira = ler(nota(ESTRELA, null, item("PAPEL COUCHE 250G A4 C/100", "1", "38.00"))).itens().get(0);
        assertEquals(outro.getId(), terceira.insumo().id());
        igual("50", terceira.fator());
        assertEquals(OrigemVinculoItemNota.MANUAL, vinculoRepository.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(
                usuario.getId(), ESTRELA, "papel couche 250g a4 c/100").orElseThrow().getOrigem());
    }

    @Test
    void cen59_vinculoParaInsumoInativoNaoEAplicadoEMostraCandidatosAtivos() throws Exception {
        Insumo antigo = insumo("Caneta gel azul", null);
        confirmar(ler(nota(ESTRELA, null, item("CANETA GEL AZUL", "1", "4.00"))), liga(0, antigo, "1", OrigemLigacao.CASAMENTO_NOME));
        antigo.setAtivo(false);
        insumoRepository.save(antigo);
        Insumo azul = insumo("Caneta azul", null);
        Insumo gel = insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero++).nome("Caneta gel")
                .estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ZERO).unidadeMedida(azul.getUnidadeMedida()).build());
        insumo("Caneta preta", null);

        ItemConciliacao item = ler(nota(ESTRELA, null, item("CANETA GEL AZUL", "1", "4.00"))).itens().get(0);

        assertEquals(OrigemLigacao.SEM_LIGACAO, item.origemLigacao());
        assertEquals(ConciliacaoNotaService.AVISO_INSUMO_INATIVO, item.aviso());
        assertEquals(List.of(azul.getId(), gel.getId()), item.candidatos().stream().map(NotaLeituraResponse.InsumoProposto::id).toList());
    }

    @Test
    void notaSemCnpjValidoNaoGravaVinculo() throws Exception {
        Insumo caneta = insumo("Caneta gel azul", null);
        NotaLida semCnpj = new NotaLida(new NotaLida.Emitente("12345678901", "Produtor", "SP"),
                String.format("35261011222333000181650010000%015d", CHAVES.getAndIncrement()), "11", "1",
                LocalDate.now().minusDays(1).toString(), v("4.00"), null, null, List.of(item("CANETA GEL AZUL", "1", "4.00")),
                "NFCE_QR", "LEITOR_UF", false, "SP", "SP-1", List.of());
        confirmar(ler(semCnpj), liga(0, caneta, "1", OrigemLigacao.CASAMENTO_NOME));

        assertTrue(vinculoRepository.findAll().stream().noneMatch(vi -> vi.getUsuario().getId().equals(usuario.getId())));
    }
}
