package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemCustomizacaoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoItemResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoProducaoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.ReciboPagamentoResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class OrcamentoMapper {

    public OrcamentoResponse toResponse(Orcamento orcamento) {
        OrcamentoResponse response = new OrcamentoResponse();
        response.setId(orcamento.getId());
        response.setNumero(orcamento.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("ORC", orcamento.getNumero()));
        response.setNomeCliente(orcamento.getCliente().getNome());
        response.setStatus(orcamento.getStatus());
        response.setTotal(orcamento.getTotal());
        response.setDataValidade(orcamento.getDataValidade());
        response.setCreatedAt(orcamento.getCreatedAt());
        response.setUpdatedAt(orcamento.getUpdatedAt());
        return response;
    }

    public OrcamentoDetalheResponse toDetalheResponse(Orcamento orcamento, List<OrcamentoItem> itens) {
        OrcamentoDetalheResponse response = new OrcamentoDetalheResponse();
        response.setId(orcamento.getId());
        response.setNumero(orcamento.getNumero());
        response.setClienteId(orcamento.getCliente().getId());
        response.setNomeCliente(orcamento.getCliente().getNome());
        response.setStatus(orcamento.getStatus());
        response.setMetodoPagamento(orcamento.getMetodoPagamento());
        response.setMetodoPagamentoObs(orcamento.getMetodoPagamentoObs());
        response.setPrazoProducaoDias(orcamento.getPrazoProducaoDias());
        response.setInicioAssimQueAprovado(orcamento.getInicioAssimQueAprovado());
        response.setDataInicioEstimada(orcamento.getDataInicioEstimada());
        response.setDataAprovacao(orcamento.getDataAprovacao());
        response.setSinalAtivo(orcamento.getSinalAtivo());
        response.setPercentualSinal(orcamento.getPercentualSinal());
        response.setValorSinal(orcamento.getValorSinal());
        response.setDataSinalPago(orcamento.getDataSinalPago());
        response.setMetodoSinalRecebido(orcamento.getMetodoSinalRecebido());
        response.setMetodoSinalRecebidoObs(orcamento.getMetodoSinalRecebidoObs());
        response.setSubtotal(orcamento.getSubtotal());
        response.setTipoDesconto(orcamento.getDescontoTipo().toString());
        response.setDescontoValor(orcamento.getDescontoValor());
        response.setTotal(orcamento.getTotal());
        response.setObservacoes(orcamento.getObservacoes());
        response.setDataValidade(orcamento.getDataValidade());
        response.setCancelamentoTipo(orcamento.getCancelamentoTipo());
        response.setPercentualMulta(orcamento.getPercentualMulta());
        response.setValorMulta(orcamento.getValorMulta());
        response.setValorDevolvidoMulta(orcamento.getValorDevolvidoMulta());
        response.setEstornoSinal(orcamento.getEstornoSinal());
        response.setDataEstornoSinal(orcamento.getDataEstornoSinal());
        response.setDataEntregaEstimada(calcularDataEntregaEstimada(orcamento));
        response.setItens(itens.stream().map(this::toItemResponse).toList());
        response.setCreatedAt(orcamento.getCreatedAt());
        response.setUpdatedAt(orcamento.getUpdatedAt());
        return response;
    }

    public OrcamentoItemResponse toItemResponse(OrcamentoItem item) {
        return toItemResponse(item, null, null);
    }

    public OrcamentoItemResponse toItemResponse(OrcamentoItem item, List<OrcamentoItemCustomizacao> customizacoes) {
        return toItemResponse(item, customizacoes, null);
    }

    public OrcamentoItemResponse toItemResponse(OrcamentoItem item, List<OrcamentoItemCustomizacao> customizacoes,
                                                 List<FichaTecnicaItem> fichaTecnicaProduto) {
        OrcamentoItemResponse response = new OrcamentoItemResponse();
        response.setId(item.getId());
        Produto produtoVendido = item.getProdutoVendido();
        response.setProdutoId(produtoVendido.getId());
        response.setNomeProduto(produtoVendido.getNome());
        if (item.getItemCatalogo() != null) {
            response.setItemCatalogoId(item.getItemCatalogo().getId());
            response.setCatalogoIdentificador(
                    IdentificadorFormatter.formatar("CTG", item.getItemCatalogo().getCatalogo().getNumero()));
            response.setCatalogoNome(item.getItemCatalogo().getCatalogo().getNome());
        }
        // ORC-020 (REVISÃO)/RN-NOVA-23 (#313) — margemAplicada volta a ser exposta nas duas origens,
        // não só na avulsa (achado do Passo 0: mapper restringia a leitura só ao branch else).
        response.setMargemAplicada(item.getMargemAplicada());
        response.setQuantidade(item.getQuantidade());
        response.setPrecoUnitario(item.getPrecoUnitario());
        response.setSubtotal(item.getSubtotal());
        if (customizacoes != null) {
            response.setCustomizacoes(customizacoes.stream().map(this::toItemCustomizacaoResponse).toList());
        }
        response.setPermitirEstoqueNegativo(produtoVendido.getPermitirEstoqueNegativo());
        response.setEstoqueAtual(produtoVendido.getEstoqueAtual());
        if (fichaTecnicaProduto != null) {
            response.setAlgumInsumoNaoFracionavel(fichaTecnicaProduto.stream()
                    .anyMatch(i -> i.getInsumo() != null && Boolean.FALSE.equals(i.getInsumo().getFracionavel())));
        }
        return response;
    }

    public OrcamentoItemCustomizacaoResponse toItemCustomizacaoResponse(OrcamentoItemCustomizacao c) {
        OrcamentoItemCustomizacaoResponse response = new OrcamentoItemCustomizacaoResponse();
        response.setId(c.getId());
        response.setProdutoId(c.getProduto().getId());
        response.setNomeProduto(c.getProduto().getNome());
        response.setQuantidade(c.getQuantidade());
        response.setPrecoUnitario(c.getPrecoUnitario());
        response.setSubtotal(c.getSubtotal());
        return response;
    }

    public ReciboPagamentoResponse toReciboPagamentoResponse(ReciboPagamento recibo) {
        ReciboPagamentoResponse response = new ReciboPagamentoResponse();
        response.setId(recibo.getId());
        response.setOrcamentoId(recibo.getOrcamento().getId());
        response.setNumeroOrcamento(recibo.getOrcamento().getNumero());
        response.setDataPagamento(recibo.getDataPagamento());
        response.setValorTotal(recibo.getValorTotal());
        response.setValorSinalPago(recibo.getValorSinalPago());
        response.setValorRestantePago(recibo.getValorRestantePago());
        response.setTotalQuitado(recibo.getTotalQuitado());
        response.setCreatedAt(recibo.getCreatedAt());
        return response;
    }

    /** RN-NOVA-6 (V0.8.2) — vínculo Orçamento↔Produção. */
    public OrcamentoProducaoResponse toOrcamentoProducaoResponse(OrcamentoProducao vinculo) {
        OrcamentoProducaoResponse response = new OrcamentoProducaoResponse();
        response.setId(vinculo.getId());
        response.setProducaoId(vinculo.getProducao().getId());
        response.setIdentificadorProducao(IdentificadorFormatter.formatar("PRD", vinculo.getProducao().getNumero()));
        LocalDate dataTerminoPrevista = vinculo.getProducao().getDataTerminoPrevista();
        response.setDataTerminoPrevista(dataTerminoPrevista);
        response.setEstouroPrazo(estouraPrazo(calcularDataEntregaEstimada(vinculo.getOrcamento()), dataTerminoPrevista));
        response.setCreatedAt(vinculo.getCreatedAt());
        return response;
    }

    /**
     * RN-ORC-VINC-04 (V0.8.2, #320) — data prometida ao cliente na proposta do orçamento, soma direta
     * de dias corridos (sem lógica de dias úteis — decisão registrada em {@code decisoes-orcamento.md},
     * não há precedente de cálculo de dias úteis reaproveitável no projeto). Nulo quando falta
     * {@code dataAprovacao} ou {@code prazoProducaoDias} (orçamento ainda não aprovado, ou sem prazo
     * de produção informado — RN-NOVA-3/ORC-018).
     */
    private LocalDate calcularDataEntregaEstimada(Orcamento orcamento) {
        if (orcamento.getDataAprovacao() == null || orcamento.getPrazoProducaoDias() == null) {
            return null;
        }
        return orcamento.getDataAprovacao().toLocalDate().plusDays(orcamento.getPrazoProducaoDias());
    }

    /** RN-ORC-VINC-04 — estoura quando o término real previsto da produção vinculada ultrapassa a
     * data prometida ao cliente. Sem aviso enquanto faltar qualquer um dos dois lados da comparação. */
    private boolean estouraPrazo(LocalDate dataEntregaEstimada, LocalDate dataTerminoPrevista) {
        return dataEntregaEstimada != null && dataTerminoPrevista != null
                && dataTerminoPrevista.isAfter(dataEntregaEstimada);
    }
}
