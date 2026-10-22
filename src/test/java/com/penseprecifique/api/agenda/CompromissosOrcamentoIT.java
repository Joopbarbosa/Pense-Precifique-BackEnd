package com.penseprecifique.api.agenda;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.agenda.CompromissoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.18.0 (#1035) — compromissos do orçamento na Agenda: entrega estimada (RN-NOVA-9), validade (RN-NOVA-11), sinal a
 * receber (RN-NOVA-12), saldo a receber (RN-NOVA-13) e as datas de envio e finalização (RN-NOVA-14).
 * CEN-NOVO-20, 23, 24, 27 a 34. As datas são relativas a hoje (relógio de Brasília).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CompromissosOrcamentoIT {

    @Autowired AgendaService agendaService;
    @Autowired OrcamentoService orcamentoService;
    @Autowired OrcamentoRepository orcamentoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ClienteRepository clienteRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired Clock clock;

    private Usuario usuario;
    private Cliente cliente;
    private LocalDate hoje;

    private void seed() {
        hoje = LocalDate.now(clock);
        usuario = usuarioRepository.save(Usuario.builder()
                .email("agenda-orc-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
        cliente = clienteRepository.save(Cliente.builder().usuario(usuario).numero(1).nome("Maria").ativa(true).build());
    }

    private OrcamentoRequest pedido(boolean sinalAtivo, Integer prazoDias, java.time.LocalDateTime validade, LocalDate sinalAte) {
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero((int) (Math.random() * 100000)).nome("Produto " + UUID.randomUUID()).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).estoqueAtual(new BigDecimal("100")).permitirEstoqueNegativo(true)
                .rendimento(new BigDecimal("10")).precoVenda(new BigDecimal("50.00")).build());
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setProdutoId(produto.getId());
        item.setMargemAplicada(BigDecimal.ZERO);
        item.setPrecoUnitario(new BigDecimal("500.00"));
        item.setQuantidade(1);
        OrcamentoRequest req = new OrcamentoRequest();
        req.setClienteId(cliente.getId());
        req.setMetodoPagamento(MetodoPagamento.PIX);
        req.setTemPrazoProducao(prazoDias != null);
        req.setPrazoProducaoDias(prazoDias);
        req.setItens(List.of(item));
        req.setSinalAtivo(sinalAtivo);
        if (sinalAtivo) req.setPercentualSinal(new BigDecimal("30"));
        req.setDataValidade(validade);
        req.setSinalAte(sinalAte);
        return req;
    }

    private void avancar(UUID id, int vezes) {
        AvancaStatusRequest sinal = new AvancaStatusRequest();
        sinal.setMetodoSinalRecebido(MetodoPagamento.PIX);
        for (int i = 0; i < vezes; i++) {
            orcamentoService.avancarStatus(id, sinal);
        }
    }

    private String ident(UUID id) {
        return com.penseprecifique.api.util.IdentificadorFormatter.formatar("ORC", orcamentoService.buscarPorId(id).getNumero());
    }

    private List<CompromissoResponse> agenda(LocalDate de, LocalDate ate) {
        return agendaService.compromissos(de, ate);
    }

    private List<CompromissoResponse> doTipo(List<CompromissoResponse> lista, TipoCompromisso tipo) {
        return lista.stream().filter(c -> c.tipo() == tipo).toList();
    }

    /** CEN-NOVO-20 — a entrega estimada aparece no dia certo, com o título "Entrega estimada — ORC-N (cliente)". */
    @Test
    void entregaEstimadaApareceNoDiaDaEntrega() {
        seed();
        UUID id = orcamentoService.criar(pedido(false, 5, null, null)).getId();
        avancar(id, 2); // ENVIADO, APROVADO
        OrcamentoDetalheResponse d = orcamentoService.buscarPorId(id);
        List<CompromissoResponse> entregas = doTipo(agenda(hoje, hoje.plusDays(10)), TipoCompromisso.ENTREGA_ESTIMADA);
        assertEquals(1, entregas.size());
        assertEquals(hoje.plusDays(5), entregas.get(0).dataExibicao());
        assertEquals("Entrega estimada — " + ident(id) + " (Maria)", entregas.get(0).titulo());
        assertFalse(entregas.get(0).atrasado());
    }

    /** CEN-NOVO-23 e 24 — a validade aparece só no dia e some ao aprovar. */
    @Test
    void validadeApareceSoNoDiaESomeAoAprovar() {
        seed();
        UUID id = orcamentoService.criar(pedido(false, null, hoje.plusDays(2).atTime(12, 0), null)).getId();
        avancar(id, 1); // ENVIADO
        String ident = ident(id);
        assertEquals(1, doTipo(agenda(hoje.plusDays(2), hoje.plusDays(2)), TipoCompromisso.VALIDADE_ORCAMENTO).size());
        assertEquals("Validade — " + ident + " vence hoje",
                doTipo(agenda(hoje.plusDays(2), hoje.plusDays(2)), TipoCompromisso.VALIDADE_ORCAMENTO).get(0).titulo());
        assertTrue(doTipo(agenda(hoje.plusDays(1), hoje.plusDays(1)), TipoCompromisso.VALIDADE_ORCAMENTO).isEmpty());
        assertTrue(doTipo(agenda(hoje.plusDays(3), hoje.plusDays(3)), TipoCompromisso.VALIDADE_ORCAMENTO).isEmpty());
        avancar(id, 1); // APROVADO
        assertTrue(doTipo(agenda(hoje.plusDays(2), hoje.plusDays(2)), TipoCompromisso.VALIDADE_ORCAMENTO).isEmpty());
    }

    /** CEN-NOVO-27 — sinal a receber no dia de "Sinal até", com o valor. */
    @Test
    void sinalAReceberComSinalAteApareceNoDia() {
        seed();
        UUID id = orcamentoService.criar(pedido(true, 10, null, hoje.plusDays(2))).getId();
        avancar(id, 3); // ENVIADO, APROVADO, AGUARDANDO_SINAL
        List<CompromissoResponse> sinais = doTipo(agenda(hoje, hoje.plusDays(5)), TipoCompromisso.SINAL_A_RECEBER);
        assertEquals(1, sinais.size());
        assertEquals(hoje.plusDays(2), sinais.get(0).dataExibicao());
        assertEquals("Sinal a receber — " + ident(id) + " (Maria) — R$ 150,00", sinais.get(0).titulo());
        assertEquals(0, new BigDecimal("150.00").compareTo(sinais.get(0).valor()));
        // CEN-NOVO-17: com sinal ainda não pago não existe a entrega estimada
        assertTrue(doTipo(agenda(hoje, hoje.plusDays(30)), TipoCompromisso.ENTREGA_ESTIMADA).isEmpty());
    }

    /** CEN-NOVO-28 — sem "Sinal até" o lembrete fica em hoje, sem destaque de atraso. */
    @Test
    void semSinalAteOLembreteFicaEmHoje() {
        seed();
        UUID id = orcamentoService.criar(pedido(true, 10, null, null)).getId();
        avancar(id, 3);
        List<CompromissoResponse> sinais = doTipo(agenda(hoje, hoje), TipoCompromisso.SINAL_A_RECEBER);
        assertEquals(1, sinais.size());
        assertFalse(sinais.get(0).atrasado());
        assertEquals(hoje, sinais.get(0).dataExibicao());
    }

    /** CEN-NOVO-29 — "Sinal até" vencido fica atrasado e sobe para hoje com os dias de atraso. */
    @Test
    void sinalAteVencidoFicaAtrasado() {
        seed();
        UUID id = orcamentoService.criar(pedido(true, 10, null, hoje.plusDays(1))).getId();
        avancar(id, 3);
        Orcamento o = orcamentoRepository.findById(id).orElseThrow();
        o.setSinalAte(hoje.minusDays(3)); // o tempo passou: a data combinada ficou para trás
        orcamentoRepository.save(o);
        List<CompromissoResponse> sinais = doTipo(agenda(hoje.minusDays(3), hoje), TipoCompromisso.SINAL_A_RECEBER);
        assertEquals(1, sinais.size());
        assertTrue(sinais.get(0).atrasado());
        assertEquals(3, sinais.get(0).diasAtraso());
        assertEquals(hoje.minusDays(3), sinais.get(0).dataExibicao());
    }

    /** CEN-NOVO-30 — "Sinal até" no passado bloqueia ao criar; sem sinal ativo também bloqueia; editar mantendo o gravado passa. */
    @Test
    void sinalAteNoPassadoBloqueia() {
        seed();
        assertThrows(BusinessException.class, () -> orcamentoService.criar(pedido(true, 10, null, hoje.minusDays(1))));
        assertThrows(BusinessException.class, () -> orcamentoService.criar(pedido(false, 10, null, hoje.plusDays(1))));
        UUID id = orcamentoService.criar(pedido(true, 10, null, hoje.plusDays(1))).getId();
        Orcamento o = orcamentoRepository.findById(id).orElseThrow();
        o.setSinalAte(hoje.minusDays(2)); // passou do prazo; o orçamento ainda é rascunho
        orcamentoRepository.save(o);
        OrcamentoRequest mesmoValor = pedido(true, 10, null, hoje.minusDays(2));
        assertNotNull(orcamentoService.editar(id, mesmoValor)); // manter o valor gravado não bloqueia
        assertThrows(BusinessException.class, () -> orcamentoService.editar(id, pedido(true, 10, null, hoje.minusDays(5))));
    }

    /** CEN-NOVO-31 — pagar o sinal tira o lembrete e cria a entrega estimada contada do dia do pagamento. */
    @Test
    void pagarOSinalTiraOLembreteECriaAEntrega() {
        seed();
        UUID id = orcamentoService.criar(pedido(true, 10, null, null)).getId();
        avancar(id, 3);
        assertEquals(1, doTipo(agenda(hoje, hoje), TipoCompromisso.SINAL_A_RECEBER).size());
        avancar(id, 1); // SINAL_PAGO
        assertTrue(doTipo(agenda(hoje, hoje), TipoCompromisso.SINAL_A_RECEBER).isEmpty());
        List<CompromissoResponse> entregas = doTipo(agenda(hoje, hoje.plusDays(15)), TipoCompromisso.ENTREGA_ESTIMADA);
        assertEquals(1, entregas.size());
        assertEquals(hoje.plusDays(10), entregas.get(0).dataExibicao());
    }

    /** CEN-NOVO-32 — saldo a receber = total menos o sinal pago, no dia da entrega estimada; some ao pagar. */
    @Test
    void saldoComSinalPagoENoDiaDaEntrega() {
        seed();
        UUID id = orcamentoService.criar(pedido(true, 4, null, null)).getId();
        avancar(id, 6); // ... SINAL_PAGO, EM_PRODUCAO, FINALIZADO
        assertEquals("FINALIZADO", orcamentoService.buscarPorId(id).getStatus().name());
        List<CompromissoResponse> saldos = doTipo(agenda(hoje, hoje.plusDays(5)), TipoCompromisso.SALDO_A_RECEBER);
        assertEquals(1, saldos.size());
        assertEquals(hoje.plusDays(4), saldos.get(0).dataExibicao());
        assertEquals(0, new BigDecimal("350.00").compareTo(saldos.get(0).valor()));
        assertTrue(saldos.get(0).titulo().endsWith("— R$ 350,00"));
        avancar(id, 1); // PAGO
        assertTrue(doTipo(agenda(hoje, hoje.plusDays(5)), TipoCompromisso.SALDO_A_RECEBER).isEmpty());
    }

    /** CEN-NOVO-33 — sem sinal e sem prazo o saldo é o total e fica em hoje, sem destaque. */
    @Test
    void saldoSemSinalESemPrazoFicaEmHoje() {
        seed();
        UUID id = orcamentoService.criar(pedido(false, null, null, null)).getId();
        avancar(id, 2); // ENVIADO, atalho de aprovação direta -> FINALIZADO
        assertEquals("FINALIZADO", orcamentoService.buscarPorId(id).getStatus().name());
        List<CompromissoResponse> saldos = doTipo(agenda(hoje, hoje), TipoCompromisso.SALDO_A_RECEBER);
        assertEquals(1, saldos.size());
        assertEquals(hoje, saldos.get(0).dataExibicao());
        assertFalse(saldos.get(0).atrasado());
        assertEquals(0, new BigDecimal("500.00").compareTo(saldos.get(0).valor()));
    }

    /** CEN-NOVO-34 — envio e finalização gravados uma vez; orçamento sem elas (anterior à V0.18.0) segue sem data. */
    @Test
    void datasDeEnvioEFinalizacaoSaoGravadasUmaVez() {
        seed();
        UUID id = orcamentoService.criar(pedido(false, 3, null, null)).getId();
        assertNull(orcamentoService.buscarPorId(id).getDataEnvio());
        avancar(id, 1);
        var enviado = orcamentoService.buscarPorId(id).getDataEnvio();
        assertNotNull(enviado);
        avancar(id, 4); // APROVADO, EM_PRODUCAO (sem sinal), FINALIZADO... conferido abaixo
        OrcamentoDetalheResponse d = orcamentoService.buscarPorId(id);
        assertNotNull(d.getDataFinalizacao());
        var finalizado = d.getDataFinalizacao();
        avancar(id, 1);
        assertEquals(enviado, orcamentoService.buscarPorId(id).getDataEnvio());
        assertEquals(finalizado, orcamentoService.buscarPorId(id).getDataFinalizacao());
        Orcamento antigo = orcamentoRepository.findById(id).orElseThrow();
        antigo.setDataEnvio(null);
        antigo.setDataFinalizacao(null);
        orcamentoRepository.save(antigo);
        assertNull(orcamentoService.buscarPorId(id).getDataEnvio());
        assertNull(orcamentoService.buscarPorId(id).getDataFinalizacao());
    }
}
