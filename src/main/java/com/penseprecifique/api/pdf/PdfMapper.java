package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.MetodoPagamento;
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
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .dataEmissao(formatarData(orc.getCreatedAt()))
            .dataValidade(orc.getDataValidade() != null ? formatarData(orc.getDataValidade()) : "Não definida")
            .prazoProducao(orc.getPrazoProducaoDias() != null ? orc.getPrazoProducaoDias() + " dias úteis" : "—")
            .inicioProducao(formatarInicio(orc))
            .metodoPagamento(formatarMetodoPagamento(orc.getMetodoPagamento()))
            .sinalAtivo(orc.getSinalAtivo() != null && orc.getSinalAtivo())
            .valorSinal(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : null)
            .restanteAposSinal(orc.getValorSinal() != null && orc.getTotal() != null ?
                formatarMoeda(orc.getTotal().subtract(orc.getValorSinal())) : null)
            .subtotal(formatarMoeda(orc.getSubtotal()))
            .desconto(formatarDesconto(orc))
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
        PdfMicroservicoEmpresaPayload empresa = PdfMicroservicoEmpresaPayload.builder()
            .nome(dados.getNomeEmpresa())
            .email(dados.getEmailEmpresa())
            .whatsapp(dados.getTelefoneEmpresa())
            .logoUrl(null)
            .build();

        List<PdfMicroservicoItemPayload> itens = dados.getItens().stream()
            .map(item -> PdfMicroservicoItemPayload.builder()
                .nomeProduto(item.getNomeProduto())
                .customizacoes(semPlaceholder(item.getCustomizacoes()))
                .quantidade(item.getQuantidade())
                .precoUnitario(item.getPrecoUnitario())
                .subtotal(item.getSubtotal())
                .build())
            .collect(Collectors.toList());

        PdfMicroservicoDocumentoOrcamentoPayload documento = PdfMicroservicoDocumentoOrcamentoPayload.builder()
            .numeroFormatado(dados.getNumeroFormatado())
            .nomeCliente(dados.getNomeCliente())
            .dataEmissao(dados.getDataEmissao())
            .dataValidade(dados.getDataValidade())
            .prazoProducao(dados.getPrazoProducao())
            .inicioProducao(dados.getInicioProducao())
            .metodoPagamento(dados.getMetodoPagamento())
            .sinalAtivo(dados.isSinalAtivo())
            .valorSinal(dados.getValorSinal())
            .restanteAposSinal(dados.getRestanteAposSinal())
            .subtotal(dados.getSubtotal())
            .desconto(dados.getDesconto())
            .total(dados.getTotal())
            .observacoes(dados.getObservacoes())
            .itens(itens)
            .build();

        return PdfMicroservicoOrcamentoPayload.builder()
            .empresa(empresa)
            .documento(documento)
            .build();
    }

    public ReciboPdfData toReciboPdfData(Orcamento orc, Empresa empresa) {
        return ReciboPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .metodoRecebido(orc.getMetodoSinalRecebido() != null ?
                formatarMetodoPagamento(orc.getMetodoSinalRecebido()) : "—")
            .valorRecebido(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : "—")
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(orc.getPrazoProducaoDias() != null ? orc.getPrazoProducaoDias() + " dias úteis" : "—")
            .inicioProducao(formatarInicio(orc))
            .build();
    }

    public ReciboPagamentoPdfData toReciboPagamentoPdfData(Orcamento orc, ReciboPagamento recibo, Empresa empresa) {
        return ReciboPagamentoPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .metodoPagamento(formatarMetodoPagamento(orc.getMetodoPagamento()))
            .valorTotal(formatarMoeda(recibo.getValorTotal()))
            .valorSinalPago(formatarMoeda(recibo.getValorSinalPago()))
            .valorRestantePago(formatarMoeda(recibo.getValorRestantePago()))
            .totalQuitado(formatarMoeda(recibo.getTotalQuitado()))
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(orc.getPrazoProducaoDias() != null ? orc.getPrazoProducaoDias() + " dias úteis" : "—")
            .inicioProducao(formatarInicio(orc))
            .dataPagamento(recibo.getDataPagamento() != null ? formatarData(recibo.getDataPagamento()) : "—")
            .build();
    }

    public ReciboPdfData toReciboPdfDataMulta(Orcamento orc, Empresa empresa) {
        BigDecimal valorMulta = calcularValorMulta(orc);
        return ReciboPdfData.builder()
            .numeroFormatado(String.valueOf(orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .percentualMulta(orc.getPercentualMulta() != null ? orc.getPercentualMulta() + "%" : "—")
            .valorMulta(valorMulta != null ? formatarMoeda(valorMulta) : "—")
            .motivo(orc.getCancelamentoMotivo())
            .dataAprovacao(orc.getDataAprovacao() != null ? formatarData(orc.getDataAprovacao()) : "—")
            .prazoProducao(orc.getPrazoProducaoDias() != null ? orc.getPrazoProducaoDias() + " dias úteis" : "—")
            .inicioProducao(formatarInicio(orc))
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

    private BigDecimal calcularValorMulta(Orcamento orc) {
        if (orc.getPercentualMulta() == null || orc.getTotal() == null) {
            return null;
        }
        return orc.getTotal().multiply(orc.getPercentualMulta()).divide(BigDecimal.valueOf(100));
    }
}
