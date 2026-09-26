package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.compra.ListaCompraService;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCompraPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoDocumentoListaComprasPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoListaComprasPayload;
import com.penseprecifique.api.shared.dto.request.compra.CancelarCompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.GerarListaCompraRequest;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.ListaCompraResponse;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 — #545 (RN-NOVA-11) e #547 (RN-NOVA-14): payload montado no Java (PdfMapper Pattern), o
 * microsserviço só renderiza. Valores não redondos e "—" para valor não informado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ComprasPdfPayloadIT {

    @Autowired ComprasPdfPayloadService comprasPdfPayloadService;
    @Autowired CompraService compraService;
    @Autowired ListaCompraService listaCompraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UnidadeMedidaRepository unidadeMedidaRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;

    private Usuario usuario;
    private UnidadeMedida folha;
    private int numeroCadastro = 1;

    @BeforeEach
    void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-pdf-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        folha = unidadeMedidaRepository.save(UnidadeMedida.builder().usuario(usuario).nome("Folha").sigla("fl").build());
    }

    private Insumo insumo(int numero, String nome) {
        return insumoRepository.save(Insumo.builder().usuario(usuario).numero(numero).nome(nome)
                .unidadeMedida(folha).estoqueAtual(BigDecimal.ZERO).custoUnitario(BigDecimal.ONE).build());
    }

    private Cliente fornecedor(String nome) {
        return clienteRepository.save(Cliente.builder().usuario(usuario).numero(numeroCadastro++).nome(nome)
                .ehCliente(false).ehFornecedor(true).ativa(true).build());
    }

    @Test
    void compraConfirmadaPaga() {
        Insumo papel = insumo(1, "Papel Kraft A4");
        Cliente papelaria = fornecedor("Papelaria Central");
        MetodoPagamentoConfiguravel pix = metodoPagamentoRepository.save(MetodoPagamentoConfiguravel.builder()
                .usuario(usuario).tipo(TipoMetodoPagamento.PIX).ativo(true).ordem(1).build());
        LocalDate data = LocalDate.now().minusDays(2);
        // 1.250 folhas por R$ 15,63 → R$ 0,0125 a folha (4 casas no PDF, sem zeros inúteis)
        CompraResponse c = compraService.confirmarNova(new CompraRequest(data, false, papelaria.getId(), true, pix.getId(),
                "Entrega na terça.", List.of(new CompraItemRequest(papel.getId(), null, new BigDecimal("1250"), new BigDecimal("15.63"))))).compra();

        PdfMicroservicoCompraPayload p = comprasPdfPayloadService.montarPayloadCompra(c.id());
        assertEquals("COM-1", p.getDocumento().getNumeroFormatado());
        assertEquals("Confirmada", p.getDocumento().getStatus());
        assertEquals("CONFIRMADA", p.getDocumento().getStatusCodigo());
        assertEquals(data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), p.getDocumento().getDataCompra());
        assertEquals("Papelaria Central", p.getDocumento().getFornecedor());
        assertFalse(p.getDocumento().isMultiplosFornecedores());
        assertEquals("Pago — Pix", p.getDocumento().getPagamento());
        assertEquals("R$ 15,63", p.getDocumento().getTotal());
        assertEquals("1.250 fl", p.getDocumento().getItens().get(0).getQuantidade());
        assertEquals("R$ 0,0125", p.getDocumento().getItens().get(0).getPrecoUnitario());
        assertEquals("Entrega na terça.", p.getDocumento().getObservacoes());
        assertNull(p.getDocumento().getObservacaoCancelamento());
    }

    @Test
    void rascunhoSemPrecoECanceladaComMotivo() {
        Insumo papel = insumo(1, "Papel");
        CompraResponse rascunho = compraService.criarRascunho(new CompraRequest(LocalDate.now(), true, null, false, null, null,
                List.of(new CompraItemRequest(papel.getId(), null, new BigDecimal("2.5"), null))));
        PdfMicroservicoCompraPayload p = comprasPdfPayloadService.montarPayloadCompra(rascunho.id());
        assertEquals("RASCUNHO", p.getDocumento().getStatusCodigo());
        assertEquals("Vários fornecedores", p.getDocumento().getFornecedor());
        assertEquals("Não pago", p.getDocumento().getPagamento());
        assertEquals("2,5 fl", p.getDocumento().getItens().get(0).getQuantidade());
        assertEquals("—", p.getDocumento().getItens().get(0).getPrecoTotal());
        assertEquals("—", p.getDocumento().getItens().get(0).getPrecoUnitario());
        assertEquals("R$ 0,00", p.getDocumento().getTotal());

        CompraResponse c = compraService.confirmarNova(new CompraRequest(LocalDate.now(), false, null, false, null, null,
                List.of(new CompraItemRequest(papel.getId(), null, BigDecimal.TEN, new BigDecimal("12.00"))))).compra();
        compraService.cancelar(c.id(), new CancelarCompraRequest("Fornecedor entregou o pedido errado, devolvido.", true));
        PdfMicroservicoCompraPayload cancelada = comprasPdfPayloadService.montarPayloadCompra(c.id());
        assertEquals("Cancelada", cancelada.getDocumento().getStatus());
        assertEquals("—", cancelada.getDocumento().getFornecedor());
        assertEquals("Fornecedor entregou o pedido errado, devolvido.", cancelada.getDocumento().getObservacaoCancelamento());
        assertTrue(cancelada.getDocumento().getDataCancelamento() != null);
    }

    @Test
    void listaAgrupadaPorFornecedorComSemFornecedorNoFim() {
        Insumo cola = insumo(1, "Cola");
        Insumo fita = insumo(2, "Fita");
        Insumo kraft = insumo(3, "Kraft");
        Cliente papelaria = fornecedor("Papelaria Central");
        Cliente atacado = fornecedor("Atacado Arte");
        ListaCompraResponse lista = listaCompraService.gerar(new GerarListaCompraRequest(List.of(
                new GerarListaCompraRequest.Linha(kraft.getId(), new BigDecimal("2.5"), null),
                new GerarListaCompraRequest.Linha(cola.getId(), new BigDecimal("4"), papelaria.getId()),
                new GerarListaCompraRequest.Linha(fita.getId(), new BigDecimal("12"), atacado.getId()))));

        PdfMicroservicoListaComprasPayload p = comprasPdfPayloadService.montarPayloadListaCompras(lista.id());
        assertEquals("LST-1", p.getDocumento().getNumeroFormatado());
        assertEquals(3, p.getDocumento().getQuantidadeItens());
        List<PdfMicroservicoDocumentoListaComprasPayload.Grupo> grupos = p.getDocumento().getGrupos();
        assertEquals(List.of("Atacado Arte", "Papelaria Central", "Sem fornecedor"),
                grupos.stream().map(PdfMicroservicoDocumentoListaComprasPayload.Grupo::getFornecedor).toList());
        assertEquals("2,5", grupos.get(2).getItens().get(0).getQuantidade());
        assertNull(grupos.get(2).getItens().get(0).getPrecoReferencia());
        assertEquals("fl", grupos.get(0).getItens().get(0).getUnidade());
    }
}
