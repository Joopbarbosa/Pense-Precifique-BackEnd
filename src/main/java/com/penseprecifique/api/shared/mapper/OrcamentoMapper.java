package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemComponente;
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
        response.setDataPagamento(orcamento.getDataPagamento()); // DT-NOVA-4/#466 — achado do teste manual
        response.setDataEntrega(orcamento.getDataEntrega()); // #560/RN-NOVA-22
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
        return toItemResponse(item, null, null, null);
    }

    public OrcamentoItemResponse toItemResponse(OrcamentoItem item, List<OrcamentoItemCustomizacao> customizacoes) {
        return toItemResponse(item, customizacoes, null, null);
    }

    public OrcamentoItemResponse toItemResponse(OrcamentoItem item, List<OrcamentoItemCustomizacao> customizacoes,
                                                 List<FichaTecnicaItem> fichaTecnicaProduto) {
        return toItemResponse(item, customizacoes, fichaTecnicaProduto, null);
    }

    /**
     * V0.13.0 (#516, RN-NOVA-1) — item de Catálogo deixou de ter "o produto vendido" único
     * (composição livre de N componentes, Insumo XOR Produto-base). {@code fichaTecnicaProduto}
     * (origem avulsa) e {@code componentesCatalogo} (origem Catálogo) são mutuamente exclusivos,
     * mesma XOR de {@code item.getProduto()}/{@code item.getItemCatalogo()} — o chamador só
     * preenche o que se aplica à origem do item.
     */
    public OrcamentoItemResponse toItemResponse(OrcamentoItem item, List<OrcamentoItemCustomizacao> customizacoes,
                                                 List<FichaTecnicaItem> fichaTecnicaProduto,
                                                 List<OrcamentoItemComponente> componentesCatalogo) {
        OrcamentoItemResponse response = new OrcamentoItemResponse();
        response.setId(item.getId());
        if (item.getItemCatalogo() != null) {
            response.setItemCatalogoId(item.getItemCatalogo().getId());
            response.setCatalogoIdentificador(
                    IdentificadorFormatter.formatar("CTG", item.getItemCatalogo().getCatalogo().getNumero()));
            response.setCatalogoNome(item.getItemCatalogo().getCatalogo().getNome());
            // Item de Catálogo tem nome próprio desde RN-NOVA-1 (V0.13.0) — sem produtoId nem
            // badges de estoque/fracionável de um único produto (estoque agora é por componente,
            // N deles; ver Divergência de Premissa em decisoes-catalogo.md).
            response.setNomeProduto(item.getItemCatalogo().getNome());
            if (componentesCatalogo != null) {
                response.setAlgumInsumoNaoFracionavel(algumComponenteNaoFracionavel(componentesCatalogo));
            }
        } else {
            Produto produto = item.getProduto();
            response.setProdutoId(produto.getId());
            response.setNomeProduto(produto.getNome());
            response.setPermitirEstoqueNegativo(produto.getPermitirEstoqueNegativo());
            response.setEstoqueAtual(produto.getEstoqueAtual());
            // RN-NOVA-7 (V0.10.0, #461) — reversão de RN-NOVA-6: badge fracionável volta ao
            // Orçamento, lida ao vivo do Produto (valor final, já com fracionavelOverride resolvido).
            response.setFracionavel(produto.getFracionavel());
            if (fichaTecnicaProduto != null) {
                response.setAlgumInsumoNaoFracionavel(fichaTecnicaProduto.stream()
                        .anyMatch(i -> i.getInsumo() != null && Boolean.FALSE.equals(i.getInsumo().getFracionavel())));
            }
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
        return response;
    }

    /** #238/DECISOES_GLOBAIS — mesmo cálculo agregado de {@code ItemCatalogoMapper} (V0.13.0),
     * aplicado ao snapshot de componentes de um item de orçamento em vez da composição viva do
     * catálogo; deliberadamente não compartilhado entre os dois (tipos de componente diferentes). */
    private boolean algumComponenteNaoFracionavel(List<OrcamentoItemComponente> componentes) {
        return componentes.stream()
                .anyMatch(c -> c.getInsumo() != null && Boolean.FALSE.equals(c.getInsumo().getFracionavel()));
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
