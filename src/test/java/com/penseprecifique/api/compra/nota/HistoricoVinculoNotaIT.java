package com.penseprecifique.api.compra.nota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.compra.*;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/** #688 CEN22/23/24: memória futura, tenant, fatores e filtros, banco test_* isolado. */
@Transactional
class HistoricoVinculoNotaIT extends NotaServicosSimulados {
    @Autowired HistoricoVinculoNotaService historico;
    @Autowired VinculoItemNotaRepository vinculos;
    @Autowired UsuarioRepository usuarios;
    @Autowired InsumoRepository insumos;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ClienteRepository clientes;
    @Autowired ConciliacaoNotaService conciliacao;
    @Autowired NotaCompraService notas;
    @Autowired CompraService compras;
    @Autowired Validator validator;
    @Autowired EntityManager em;
    private Usuario usuario;
    private Insumo azul;
    private Insumo preta;
    private UnidadeMedida unidade;
    private static final String ESTRELA = "11222333000181";
    private static final String AURORA = "11444777000161";
    private int numero;

    @BeforeEach
    void preparar() {
        LEITOR.resetAll(); IA.resetAll(); numero = 1;
        usuario = usuarios.save(Usuario.builder().email("historico688-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        autenticar(usuario);
        unidade = unidades.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build());
        azul = insumo(usuario, "Caneta gel azul"); preta = insumo(usuario, "Caneta gel preta");
    }
    private void autenticar(Usuario u) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of())); }
    private Insumo insumo(Usuario u, String nome) {
        return insumos.save(Insumo.builder().usuario(u).numero(numero++).nome(nome).unidadeMedida(unidade)
                .estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }
    private VinculoItemNota vinculo(String cnpj, String nome, Insumo insumo) {
        return vinculos.saveAndFlush(VinculoItemNota.builder().usuario(usuario).emitenteCnpj(cnpj)
                .emitenteNome(cnpj.equals(ESTRELA) ? "Papelaria Estrela" : "Distribuidora Aurora").nomeItem(nome)
                .nomeItemNormalizado(CasamentoPorNome.normalizarChave(nome)).insumo(insumo).fator(new BigDecimal("1.1250"))
                .origem(OrigemVinculoItemNota.CASAMENTO_NOME).build());
    }
    private NotaLida nota() {
        return new NotaLida(new NotaLida.Emitente(ESTRELA, "Papelaria Estrela", "SP"), "35" + "1".repeat(42), "31", "1",
                LocalDate.now().toString(), new BigDecimal("17.43"), null, null,
                List.of(new NotaLida.Item("CANETA GEL AZUL", new BigDecimal("3"), new BigDecimal("17.43"), null, null, "UN", null, null)),
                "NFCE_QR", "LEITOR_UF", false, "SP", "SP-test688", List.of());
    }
    private UUID rascunho() throws Exception {
        NotaLida nota = nota();
        LEITOR.stubFor(post(urlEqualTo("/v1/leituras")).willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(new ObjectMapper().findAndRegisterModules().writeValueAsString(nota))));
        var leitura = notas.ler("NFCE", null, nota.chaveAcesso(), null, false);
        return notas.criarRascunho(new NotaRascunhoRequest(leitura.nota(), leitura.assinatura(),
                List.of(new NotaRascunhoRequest.Escolha(0, azul.getId(), BigDecimal.ONE, false)),
                NotaRascunhoRequest.AcaoFornecedor.SEM_FORNECEDOR, null), null, false).compra().id();
    }
    @Test
    void cen22_listaBuscaFiltraPaginaEExibeCadastroOuEmitente() {
        vinculo(ESTRELA, "CANETA GEL AZUL", azul); vinculo(ESTRELA, "PAPEL A4", azul);
        vinculo(ESTRELA, "FITA AZUL", preta); vinculo(AURORA, "CANETA GEL AZUL", preta);
        var fornecedor = clientes.save(Cliente.builder().usuario(usuario).numero(1).nome("Estrela cadastrada").documento(ESTRELA).ehFornecedor(true).build());
        var pagina = historico.listar(null, fornecedor.getId(), null, null, null, PageRequest.of(0, 20));
        assertEquals(3, pagina.getTotalElements()); assertTrue(pagina.stream().allMatch(v -> v.fornecedorNome().equals("Estrela cadastrada")));
        assertEquals(1, historico.listar(null, null, null, preta.getId(), null, PageRequest.of(0, 1)).getNumberOfElements());
        assertEquals(4, historico.listar("caneta gel", null, null, null, null, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(1, historico.listar("FITA", null, null, null, null, PageRequest.of(0, 20)).getTotalElements());
        var semCadastro = historico.listar(null, null, "11.444.777/0001-61", null, null, PageRequest.of(0, 20)).getContent().getFirst();
        assertNull(semCadastro.fornecedorId()); assertEquals("Distribuidora Aurora", semCadastro.fornecedorNome());
        assertEquals(0, historico.listar("%_", null, null, null, null, PageRequest.of(0, 20)).getTotalElements());
    }
    @Test
    void cnpjAlfanumericoSemCadastroEFornecedorOutraContaNaoVazam() {
        vinculo("12ABC34501DE35", "PAPEL ESPECIAL", azul);
        assertEquals(1, historico.listar(null, null, "12.abc.345/01de-35", null, null, PageRequest.of(0, 20)).getTotalElements());
        var outro = usuarios.save(Usuario.builder().email("fornecedor688-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        var f = clientes.save(Cliente.builder().usuario(outro).numero(1).nome("Outra conta").documento(ESTRELA).ehFornecedor(true).build());
        assertThrows(ResourceNotFoundException.class, () -> historico.listar(null, f.getId(), null, null, null, PageRequest.of(0, 20)));
        assertThrows(BusinessException.class, () -> historico.listar(null, null, "12ABC34501DE36", null, null, PageRequest.of(0, 20)));
    }
    @Test
    void cen23_editaSoProximasNotasERascunhoConfirmadoNaoMuda() throws Exception {
        UUID compra = rascunho(); var v = vinculos.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(usuario.getId(), ESTRELA, "caneta gel azul").orElseThrow();
        var resposta = historico.editar(v.getId(), new VinculoNotaRequest(preta.getId(), new BigDecimal("2.5000")));
        assertEquals(OrigemVinculoItemNota.MANUAL, resposta.origem()); assertEquals(preta.getId(), resposta.insumo().id());
        em.clear(); assertEquals(azul.getId(), compras.buscar(compra).itens().getFirst().insumo().id());
        var futura = conciliacao.conciliar(usuario.getId(), ESTRELA, nota().itens()).itens().getFirst();
        assertEquals(OrigemLigacao.VINCULO_SALVO, futura.origemLigacao()); assertEquals(preta.getId(), futura.insumo().id()); assertEquals(new BigDecimal("2.5000"), futura.fator());
        compras.confirmar(compra, null);
        historico.editar(v.getId(), new VinculoNotaRequest(azul.getId(), new BigDecimal("0.7500")));
        em.clear(); assertEquals(StatusCompra.CONFIRMADA, compras.buscar(compra).status()); assertEquals(azul.getId(), compras.buscar(compra).itens().getFirst().insumo().id());
    }
    @Test
    void cen24_desfazerVoltaParaCasamentoENaoMudaCompra() throws Exception {
        UUID compra = rascunho(); var v = vinculos.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(usuario.getId(), ESTRELA, "caneta gel azul").orElseThrow();
        historico.desfazer(v.getId()); em.flush();
        assertFalse(vinculos.existsById(v.getId()));
        assertEquals(OrigemLigacao.CASAMENTO_NOME, conciliacao.conciliar(usuario.getId(), ESTRELA, nota().itens()).itens().getFirst().origemLigacao());
        assertEquals(azul.getId(), compras.buscar(compra).itens().getFirst().insumo().id());
    }
    @Test
    void marcaIgnorarLimpaDestinoDesmarcaExigeDestinoEValidaFator() {
        var v = vinculo(ESTRELA, "CANETA GEL AZUL", azul);
        var ignorado = historico.ignorar(v.getId(), new IgnorarVinculoNotaRequest(true, null, null));
        assertTrue(ignorado.ignorar()); assertNull(ignorado.insumo()); assertNull(ignorado.fator());
        assertTrue(conciliacao.conciliar(usuario.getId(), ESTRELA, nota().itens()).itens().getFirst().ignorar());
        assertEquals(1, historico.listar(null, null, null, null, true, PageRequest.of(0, 20)).getTotalElements());
        assertThrows(BusinessException.class, () -> historico.ignorar(v.getId(), new IgnorarVinculoNotaRequest(false, null, null)));
        for (String fator : List.of("0", "-1", "1.00001", "100000000000")) {
            assertThrows(BusinessException.class, () -> historico.editar(v.getId(), new VinculoNotaRequest(preta.getId(), new BigDecimal(fator))));
        }
        var normal = historico.ignorar(v.getId(), new IgnorarVinculoNotaRequest(false, preta.getId(), new BigDecimal("0.1250")));
        assertFalse(normal.ignorar()); assertEquals(preta.getId(), normal.insumo().id()); assertEquals(OrigemVinculoItemNota.MANUAL, normal.origem());
        assertFalse(validator.validate(new VinculoNotaRequest(null, BigDecimal.ZERO)).isEmpty());
    }
    @Test
    void uuidDeOutraContaNaoPodeSerLidoEditadoIgnoradoOuDesfeito() {
        var v = vinculo(ESTRELA, "CANETA GEL AZUL", azul);
        var outro = usuarios.save(Usuario.builder().email("outro688-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        var insumoOutro = insumo(outro, "Caneta outra conta");
        assertThrows(ResourceNotFoundException.class, () -> historico.editar(v.getId(), new VinculoNotaRequest(insumoOutro.getId(), BigDecimal.ONE)));
        autenticar(outro);
        assertEquals(0, historico.listar(null, null, null, azul.getId(), null, PageRequest.of(0, 20)).getTotalElements());
        assertThrows(ResourceNotFoundException.class, () -> historico.editar(v.getId(), new VinculoNotaRequest(insumoOutro.getId(), BigDecimal.ONE)));
        assertThrows(ResourceNotFoundException.class, () -> historico.ignorar(v.getId(), new IgnorarVinculoNotaRequest(true, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> historico.desfazer(v.getId()));
    }
    @Test
    void destinoInativoOuExcluidoBloqueiaAtivoAceitoOrdenacaoInvalidaBloqueia() {
        var v = vinculo(ESTRELA, "CANETA GEL AZUL", azul);
        preta.setAtivo(false); insumos.saveAndFlush(preta);
        assertThrows(BusinessException.class, () -> historico.editar(v.getId(), new VinculoNotaRequest(preta.getId(), BigDecimal.ONE)));
        preta.setAtivo(true); insumos.saveAndFlush(preta);
        assertEquals(preta.getId(), historico.editar(v.getId(), new VinculoNotaRequest(preta.getId(), BigDecimal.ONE)).insumo().id());
        preta.setDeletedAt(java.time.LocalDateTime.now()); insumos.saveAndFlush(preta);
        assertThrows(ResourceNotFoundException.class, () -> historico.editar(v.getId(), new VinculoNotaRequest(preta.getId(), BigDecimal.ONE)));
        assertThrows(BusinessException.class, () -> historico.listar(null, null, null, null, null, PageRequest.of(0, 20, Sort.by("usuario.senhaHash"))));
        assertThrows(BusinessException.class, () -> historico.listar(null, null, null, null, null, PageRequest.of(0, 101)));
        assertThrows(BusinessException.class, () -> historico.listar("x".repeat(501), null, null, null, null, PageRequest.of(0, 20)));
    }
}
