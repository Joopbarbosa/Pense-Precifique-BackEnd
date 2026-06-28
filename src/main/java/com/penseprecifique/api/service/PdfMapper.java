package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.*;
import com.penseprecifique.api.domain.enums.MetodoPagamento;
import com.penseprecifique.api.dto.pdf.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PdfMapper {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public OrcamentoPdfData toOrcamentoPdfData(Orcamento orc, Empresa empresa, List<OrcamentoItem> itens) {
        return OrcamentoPdfData.builder()
            .numeroFormatado(String.format("%04d", orc.getNumero()))
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
            .itens(mapearItens(itens))
            .build();
    }

    public ReciboPdfData toReciboPdfData(Orcamento orc, Empresa empresa) {
        return ReciboPdfData.builder()
            .numeroFormatado(String.format("%04d", orc.getNumero()))
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
            .numeroFormatado(String.format("%04d", orc.getNumero()))
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
            .numeroFormatado(String.format("%04d", orc.getNumero()))
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
            .numeroFormatado(String.format("%04d", orc.getNumero()))
            .nomeCliente(orc.getCliente() != null ? orc.getCliente().getNome() : "—")
            .nomeEmpresa(empresa != null ? empresa.getNome() : "Studio")
            .emailEmpresa(empresa != null ? empresa.getEmail() : null)
            .telefoneEmpresa(empresa != null ? empresa.getWhatsapp() : null)
            .valorRecebido(orc.getValorSinal() != null ? formatarMoeda(orc.getValorSinal()) : "—")
            .dataEstorno(orc.getDataEstornoSinal() != null ? formatarData(orc.getDataEstornoSinal()) : "—")
            .build();
    }

    private List<ItemPdfData> mapearItens(List<OrcamentoItem> itens) {
        if (itens == null || itens.isEmpty()) {
            return List.of();
        }
        return itens.stream()
            .map(item -> ItemPdfData.builder()
                .nomeProduto(item.getProduto() != null ? item.getProduto().getNome() : "—")
                .customizacoes("—")
                .quantidade(item.getQuantidade() != null ? item.getQuantidade().toString() : "—")
                .precoUnitario(item.getPrecoUnitario() != null ? formatarMoeda(item.getPrecoUnitario()) : "—")
                .subtotal(item.getSubtotal() != null ? formatarMoeda(item.getSubtotal()) : "—")
                .build())
            .collect(Collectors.toList());
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        return String.format("R$ %.2f", valor).replace(".", ",").replace(",", ".");
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
