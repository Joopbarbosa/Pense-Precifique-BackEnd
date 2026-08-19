package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.dto.pdf.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PdfMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat MOEDA_FORMATTER =
        new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.of("pt", "BR")));

    public OrcamentoPdfData toOrcamentoPdfData(Orcamento orc, Empresa empresa, List<OrcamentoItem> itens,
            Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem) {
        return OrcamentoPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .status(formatarStatusOrcamento(orc.getStatus()))
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .telefoneCliente(orc.getCliente() != null ? orc.getCliente().getWhatsapp() : null)
            .emailCliente(orc.getCliente() != null ? orc.getCliente().getEmail() : null)
            .dataEmissao(formatarData(orc.getCreatedAt()))
            .dataValidade(orc.getDataValidade() != null ? formatarData(orc.getDataValidade()) : "Não definida")
            .prazoProducao(formatarPrazo(orc.getPrazoProducaoDias()))
            .inicioProducao(formatarInicio(orc))
            .metodoPagamento(formatarMetodoPagamento(orc.getMetodoPagamento()))
            .sinalAtivo(orc.getSinalAtivo() != null && orc.getSinalAtivo())
            .valorSinal(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : null)
            .restanteAposSinal(orc.getValorSinal() != null && orc.getTotal() != null ?
                formatarMoeda(orc.getTotal().subtract(orc.getValorSinal())) : null)
            .percentualSinal(orc.getPercentualSinal() != null ? orc.getPercentualSinal() + "%" : null)
            .subtotal(formatarMoeda(orc.getSubtotal()))
            .desconto(formatarDesconto(orc))
            .percentualDesconto(formatarPercentualDesconto(orc))
            .total(formatarMoeda(orc.getTotal()))
            .observacoes(orc.getObservacoes())
            .itens(mapearItens(itens, customizacoesPorItem))
            .build();
    }

    /**
     * Remonta {@link OrcamentoPdfData} (achatado, usado pelo template Thymeleaf local) no formato
     * aninhado {@code {empresa, documento}} exigido por {@code orcamentoSchema} do microsserviço
     * pense-precifique-pdf (contrato-pdf.md seção 1) — reaproveita a formatação já validada em
     * {@link #toOrcamentoPdfData}, só reempacota.
     *
     * <p>{@code logoUrl} vai sempre {@code null} nesta rodada — decisão explícita do MVP
     * (contrato-pdf.md seção 1: campo existe na entidade {@code Empresa} mas nunca foi populado).
     */
    public PdfMicroservicoOrcamentoPayload toMicroservicoPayload(OrcamentoPdfData dados) {
        PdfMicroservicoEmpresaPayload empresa =
            toEmpresaPayload(dados.getNomeEmpresa(), dados.getEmailEmpresa(), dados.getTelefoneEmpresa());

        List<PdfMicroservicoItemPayload> itens = mapearItensPayload(dados.getItens());

        PdfMicroservicoDocumentoOrcamentoPayload documento = PdfMicroservicoDocumentoOrcamentoPayload.builder()
            .numeroFormatado(dados.getNumeroFormatado())
            .status(dados.getStatus())
            .nomeCliente(dados.getNomeCliente())
            .telefoneCliente(dados.getTelefoneCliente())
            .emailCliente(dados.getEmailCliente())
            .dataEmissao(dados.getDataEmissao())
            .dataValidade(dados.getDataValidade())
            .prazoProducao(dados.getPrazoProducao())
            .inicioProducao(dados.getInicioProducao())
            .metodoPagamento(dados.getMetodoPagamento())
            .sinalAtivo(dados.isSinalAtivo())
            .valorSinal(dados.getValorSinal())
            .restanteAposSinal(dados.getRestanteAposSinal())
            .percentualSinal(dados.getPercentualSinal())
            .subtotal(dados.getSubtotal())
            .desconto(dados.getDesconto())
            .percentualDesconto(dados.getPercentualDesconto())
            .total(dados.getTotal())
            .observacoes(dados.getObservacoes())
            .itens(itens)
            .build();

        return PdfMicroservicoOrcamentoPayload.builder()
            .empresa(empresa)
            .documento(documento)
            .build();
    }

    /**
     * P-F007b — ganhou {@code itens}/{@code customizacoesPorItem} (mesma origem de dados de
     * {@link #toOrcamentoPdfData}) para restaurar "Detalhes do pedido"/"Próximos passos" do mock,
     * cortadas em #248 por falta de dado no schema original (ver comentário removido de
     * {@code ReciboSinalDoc.jsx}).
     */
    public ReciboPdfData toReciboPdfData(Orcamento orc, Empresa empresa, List<OrcamentoItem> itens,
            Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem) {
        return ReciboPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .telefoneCliente(orc.getCliente() != null ? orc.getCliente().getWhatsapp() : null)
            .emailCliente(orc.getCliente() != null ? orc.getCliente().getEmail() : null)
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .metodoRecebido(orc.getMetodoSinalRecebido() != null ?
                formatarMetodoPagamento(orc.getMetodoSinalRecebido()) : "—")
            .valorRecebido(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : "—")
            .dataEmissao(formatarData(LocalDateTime.now()))
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(formatarPrazo(orc.getPrazoProducaoDias()))
            .inicioProducao(formatarInicio(orc))
            .itens(mapearItens(itens, customizacoesPorItem))
            .valorTotalPedido(formatarMoeda(orc.getTotal()))
            .percentualSinal(orc.getPercentualSinal() != null ? orc.getPercentualSinal() + "%" : "—")
            .restante(orc.getValorSinal() != null && orc.getTotal() != null ?
                formatarMoeda(orc.getTotal().subtract(orc.getValorSinal())) : "—")
            .observacoes(orc.getObservacoes())
            .build();
    }

    public ReciboPagamentoPdfData toReciboPagamentoPdfData(Orcamento orc, ReciboPagamento recibo, Empresa empresa,
            List<OrcamentoItem> itens, Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem) {
        return ReciboPagamentoPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .telefoneCliente(orc.getCliente() != null ? orc.getCliente().getWhatsapp() : null)
            .emailCliente(orc.getCliente() != null ? orc.getCliente().getEmail() : null)
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .metodoPagamento(formatarMetodoPagamento(orc.getMetodoPagamento()))
            .valorTotal(formatarMoeda(recibo.getValorTotal()))
            .valorSinalPago(formatarMoeda(recibo.getValorSinalPago()))
            .valorRestantePago(formatarMoeda(recibo.getValorRestantePago()))
            .totalQuitado(formatarMoeda(recibo.getTotalQuitado()))
            .dataEmissao(formatarData(LocalDateTime.now()))
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(formatarPrazo(orc.getPrazoProducaoDias()))
            .inicioProducao(formatarInicio(orc))
            .dataPagamento(recibo.getDataPagamento() != null ? formatarData(recibo.getDataPagamento()) : "—")
            .itens(mapearItens(itens, customizacoesPorItem))
            .observacoes(orc.getObservacoes())
            .build();
    }

    /**
     * #248 (última migração da Epic) — mesmo padrão de {@link #toReciboSinalMicroservicoPayload}:
     * reempacota {@link ReciboPagamentoPdfData} (achatado, {@code toReciboPagamentoPdfData}) no
     * formato aninhado {@code {empresa, documento}} exigido pelo microsserviço, sem recalcular
     * nada.
     */
    public PdfMicroservicoReciboPagamentoPayload toReciboPagamentoMicroservicoPayload(ReciboPagamentoPdfData dados) {
        return PdfMicroservicoReciboPagamentoPayload.builder()
            .empresa(toEmpresaPayload(dados.getNomeEmpresa(), dados.getEmailEmpresa(), dados.getTelefoneEmpresa()))
            .documento(PdfMicroservicoDocumentoReciboPagamentoPayload.builder()
                .numeroFormatado(dados.getNumeroFormatado())
                .nomeCliente(dados.getNomeCliente())
                .telefoneCliente(dados.getTelefoneCliente())
                .emailCliente(dados.getEmailCliente())
                .metodoPagamento(dados.getMetodoPagamento())
                .valorTotal(dados.getValorTotal())
                .valorSinalPago(dados.getValorSinalPago())
                .valorRestantePago(dados.getValorRestantePago())
                .totalQuitado(dados.getTotalQuitado())
                .dataEmissao(dados.getDataEmissao())
                .dataAprovacao(dados.getDataAprovacao())
                .prazoProducao(dados.getPrazoProducao())
                .inicioProducao(dados.getInicioProducao())
                .dataPagamento(dados.getDataPagamento())
                .itens(mapearItensPayload(dados.getItens()))
                .observacoes(dados.getObservacoes())
                .build())
            .build();
    }

    public ReciboPdfData toReciboPdfDataMulta(Orcamento orc, Empresa empresa, List<OrcamentoItem> itens,
            Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem) {
        return ReciboPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .telefoneCliente(orc.getCliente() != null ? orc.getCliente().getWhatsapp() : null)
            .emailCliente(orc.getCliente() != null ? orc.getCliente().getEmail() : null)
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .percentualMulta(orc.getPercentualMulta() != null ? orc.getPercentualMulta() + "%" : "—")
            .valorMulta(orc.getValorMulta() != null ? formatarMoeda(orc.getValorMulta()) : "—")
            .motivo(orc.getCancelamentoMotivo())
            .dataEmissao(formatarData(LocalDateTime.now()))
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(formatarPrazo(orc.getPrazoProducaoDias()))
            .inicioProducao(formatarInicio(orc))
            .dataCancelamento(orc.getDataCancelamento() != null ? formatarData(orc.getDataCancelamento()) : "—")
            .itens(mapearItens(itens, customizacoesPorItem))
            .build();
    }

    public ReciboPdfData toReciboPdfDataEstorno(Orcamento orc, Empresa empresa) {
        return ReciboPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .valorRecebido(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : "—")
            .dataEstorno(orc.getDataEstornoSinal() != null ? formatarData(orc.getDataEstornoSinal()) : "—")
            .build();
    }

    /**
     * #248 (Frente A) — mesmo padrão de {@link #toMicroservicoPayload}: reempacota
     * {@link ReciboPdfData} (achatado, {@code toReciboPdfData}) no formato aninhado
     * {@code {empresa, documento}} exigido pelo microsserviço, sem recalcular nada.
     */
    public PdfMicroservicoReciboSinalPayload toReciboSinalMicroservicoPayload(ReciboPdfData dados) {
        return PdfMicroservicoReciboSinalPayload.builder()
            .empresa(toEmpresaPayload(dados.getNomeEmpresa(), dados.getEmailEmpresa(), dados.getTelefoneEmpresa()))
            .documento(PdfMicroservicoDocumentoReciboSinalPayload.builder()
                .numeroFormatado(dados.getNumeroFormatado())
                .nomeCliente(dados.getNomeCliente())
                .telefoneCliente(dados.getTelefoneCliente())
                .emailCliente(dados.getEmailCliente())
                .metodoRecebido(dados.getMetodoRecebido())
                .valorRecebido(dados.getValorRecebido())
                .dataEmissao(dados.getDataEmissao())
                .dataAprovacao(dados.getDataAprovacao())
                .prazoProducao(dados.getPrazoProducao())
                .inicioProducao(dados.getInicioProducao())
                .itens(mapearItensPayload(dados.getItens()))
                .valorTotalPedido(dados.getValorTotalPedido())
                .percentualSinal(dados.getPercentualSinal())
                .restante(dados.getRestante())
                .observacoes(dados.getObservacoes())
                .build())
            .build();
    }

    /**
     * {@code motivo} não tem fallback "—" em {@link #toReciboPdfDataMulta} (só
     * {@code orc.getCancelamentoMotivo()} direto) — vai {@code null} quando o cancelamento não tem
     * motivo registrado, por isso o campo é {@code .nullable()} em {@code pdfMultaSchema}
     * (contrato-pdf.md).
     */
    public PdfMicroservicoPdfMultaPayload toPdfMultaMicroservicoPayload(ReciboPdfData dados) {
        return PdfMicroservicoPdfMultaPayload.builder()
            .empresa(toEmpresaPayload(dados.getNomeEmpresa(), dados.getEmailEmpresa(), dados.getTelefoneEmpresa()))
            .documento(PdfMicroservicoDocumentoPdfMultaPayload.builder()
                .numeroFormatado(dados.getNumeroFormatado())
                .nomeCliente(dados.getNomeCliente())
                .telefoneCliente(dados.getTelefoneCliente())
                .emailCliente(dados.getEmailCliente())
                .motivo(dados.getMotivo())
                .percentualMulta(dados.getPercentualMulta())
                .valorMulta(dados.getValorMulta())
                .dataEmissao(dados.getDataEmissao())
                .dataAprovacao(dados.getDataAprovacao())
                .prazoProducao(dados.getPrazoProducao())
                .inicioProducao(dados.getInicioProducao())
                .dataCancelamento(dados.getDataCancelamento())
                .itens(mapearItensPayload(dados.getItens()))
                .build())
            .build();
    }

    public PdfMicroservicoReciboEstornoPayload toReciboEstornoMicroservicoPayload(ReciboPdfData dados) {
        return PdfMicroservicoReciboEstornoPayload.builder()
            .empresa(toEmpresaPayload(dados.getNomeEmpresa(), dados.getEmailEmpresa(), dados.getTelefoneEmpresa()))
            .documento(PdfMicroservicoDocumentoReciboEstornoPayload.builder()
                .numeroFormatado(dados.getNumeroFormatado())
                .nomeCliente(dados.getNomeCliente())
                .valorRecebido(dados.getValorRecebido())
                .dataEstorno(dados.getDataEstorno())
                .build())
            .build();
    }

    private PdfMicroservicoEmpresaPayload toEmpresaPayload(String nomeEmpresa, String emailEmpresa, String telefoneEmpresa) {
        return PdfMicroservicoEmpresaPayload.builder()
            .nome(nomeEmpresa)
            .email(emailEmpresa)
            .whatsapp(telefoneEmpresa)
            .logoUrl(null)
            .build();
    }

    private List<ItemPdfData> mapearItens(List<OrcamentoItem> itens,
            Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }
        return itens.stream()
            .map(item -> ItemPdfData.builder()
                .nomeProduto(item.getProdutoVendido().getNome())
                .customizacoes(formatarCustomizacoes(
                        customizacoesPorItem != null ? customizacoesPorItem.get(item.getId()) : null))
                .quantidade(item.getQuantidade() != null ? item.getQuantidade().toString() : "—")
                .precoUnitario(item.getPrecoUnitario() != null ? formatarMoeda(item.getPrecoUnitario()) : "—")
                .subtotal(item.getSubtotal() != null ? formatarMoeda(item.getSubtotal()) : "—")
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Reaproveitado por {@link #toMicroservicoPayload} (Orçamento) e
     * {@link #toReciboSinalMicroservicoPayload} (P-F007b) — mesma tradução
     * {@link ItemPdfData} → {@link PdfMicroservicoItemPayload}, extraída para não duplicar o
     * stream nos dois lugares.
     */
    private List<PdfMicroservicoItemPayload> mapearItensPayload(List<ItemPdfData> itens) {
        return itens.stream()
            .map(item -> PdfMicroservicoItemPayload.builder()
                .nomeProduto(item.getNomeProduto())
                .customizacoes(semPlaceholder(item.getCustomizacoes()))
                .quantidade(item.getQuantidade())
                .precoUnitario(item.getPrecoUnitario())
                .subtotal(item.getSubtotal())
                .build())
            .collect(Collectors.toList());
    }

    private String formatarCustomizacoes(List<OrcamentoItemCustomizacao> customizacoes) {
        if (customizacoes == null || customizacoes.isEmpty()) {
            return "—";
        }
        return customizacoes.stream()
            .map(c -> c.getProduto().getNome())
            .collect(Collectors.joining(", "));
    }

    /**
     * {@code formatarCustomizacoes} usa "—" como placeholder de exibição pro template Thymeleaf
     * local (mantido como está — fora do escopo desta integração). O schema do microsserviço
     * (contrato-pdf.md seção 1) exige {@code null} explícito quando o item não tem customização,
     * nunca o placeholder — só {@link #toMicroservicoPayload} precisa dessa tradução.
     */
    private String semPlaceholder(String valorFormatado) {
        return "—".equals(valorFormatado) ? null : valorFormatado;
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        return "R$ " + MOEDA_FORMATTER.format(valor);
    }

    private String formatarMetodoPagamento(MetodoPagamento metodo) {
        if (metodo == null) {
            return "—";
        }
        return switch (metodo) {
            case PIX -> "Pix";
            case DINHEIRO -> "Dinheiro";
            case CREDITO -> "Crédito";
            case DEBITO -> "Débito";
            case TRANSFERENCIA -> "Transferência";
            case BOLETO -> "Boleto Bancário";
            case OUTRO -> "Outro";
        };
    }

    private String formatarDesconto(Orcamento orc) {
        if (orc.getDescontoValor() == null || orc.getDescontoValor().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal descontoCalculado = orc.getSubtotal().subtract(orc.getTotal());
        return formatarMoeda(descontoCalculado);
    }

    /**
     * Diferente de {@link #formatarDesconto} — {@code descontoValor} só É o percentual quando
     * {@code descontoTipo == PERCENTUAL} (confirmado em {@code OrcamentoService.calcularTotal});
     * quando o tipo é {@code VALOR}, {@code descontoValor} é um valor monetário, não um
     * percentual, e este campo deve ficar {@code null}.
     */
    private String formatarPercentualDesconto(Orcamento orc) {
        if (orc.getDescontoTipo() != TipoDesconto.PERCENTUAL) {
            return null;
        }
        if (orc.getDescontoValor() == null || orc.getDescontoValor().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return orc.getDescontoValor() + "%";
    }

    /**
     * P-F008 — Seção 4 ("Status do pedido") do Design aprovado do Orçamento/Preview exige o rótulo
     * pronto no payload (templates não traduzem nada). RASCUNHO vira "Aguardando aprovação" — desvio
     * aprovado sobre o Design original (ver DECISOES_V0.8.1.md); os demais valores usam o mesmo
     * rótulo já exibido no frontend (STATUS_LABEL, pense-precifique-frontend/src/constants/statusOrcamento.ts)
     * para manter as duas UIs consistentes.
     */
    private String formatarStatusOrcamento(StatusOrcamento status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case RASCUNHO -> "Aguardando aprovação";
            case ENVIADO -> "Enviado";
            case APROVADO -> "Aprovado";
            case AGUARDANDO_SINAL -> "Aguardando Sinal";
            case SINAL_PAGO -> "Sinal Pago";
            case EM_PRODUCAO -> "Em Produção";
            case FINALIZADO -> "Finalizado";
            case ENTREGUE -> "Entregue";
            case PAGO -> "Pago";
            case CANCELADO -> "Cancelado";
        };
    }

    private String formatarInicio(Orcamento orc) {
        if (orc.getInicioAssimQueAprovado() != null && orc.getInicioAssimQueAprovado()) {
            return "Assim que aprovado";
        }
        if (orc.getDataInicioEstimada() != null) {
            return orc.getDataInicioEstimada().format(DATE_FORMATTER);
        }
        return "—";
    }

    private String formatarData(LocalDateTime data) {
        if (data == null) {
            return "—";
        }
        return data.format(DATE_FORMATTER);
    }

    private String formatarPrazo(Integer dias) {
        if (dias == null) {
            return "—";
        }
        return dias + (dias == 1 ? " dia útil" : " dias úteis");
    }

}
