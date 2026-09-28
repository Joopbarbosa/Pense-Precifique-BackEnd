package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.response.compra.CadastroRefResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraItemResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResponse;
import com.penseprecifique.api.shared.dto.response.compra.CompraResumoResponse;
import com.penseprecifique.api.shared.dto.response.compra.InsumoRefResponse;
import com.penseprecifique.api.shared.dto.response.compra.MetodoPagamentoRefResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.TreeSet;

@Component
public class CompraMapper {

    public CompraResponse toResponse(Compra compra, List<CompraItem> itens) {
        return new CompraResponse(
                compra.getId(),
                compra.getNumero(),
                IdentificadorFormatter.formatar("COM", compra.getNumero()),
                compra.getStatus(),
                compra.getDataCompra(),
                Boolean.TRUE.equals(compra.getMultiplosFornecedores()),
                toRef(compra.getFornecedor()),
                Boolean.TRUE.equals(compra.getPago()),
                toRef(compra.getMetodoPagamento()),
                compra.getObservacoes(),
                compra.getOrigem(),
                total(itens),
                itens.stream().map(this::toItemResponse).toList(),
                compra.getConfirmadaEm(),
                compra.getCanceladaEm(),
                compra.getObservacaoCancelamento(),
                compra.getCreatedAt(),
                compra.getUpdatedAt(),
                compra.getDescontoNotaTipo(),
                compra.getDescontoNotaInformado(),
                compra.getDescontoNota(),
                itens.stream().map(CompraItem::getPrecoCheio).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
                totalDescontos(itens),
                compra.getParcelas(),
                compra.getListaCompra() == null ? null : new CompraResponse.ListaCompraRef(compra.getListaCompra().getId(),
                        IdentificadorFormatter.formatar("LST", compra.getListaCompra().getNumero())));
    }

    /** #576 — soma dos descontos de linha e das partes da nota. */
    public static BigDecimal totalDescontos(List<CompraItem> itens) {
        return itens.stream().map(i -> nz(i.getDescontoLinha()).add(nz(i.getDescontoNota())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** RN-NOVA-24 — "Fita de cetim ×12,5 m, Cola ×3 un" (quantidade com vírgula, sem zeros à direita). */
    public static String resumoItens(List<CompraItem> itens) {
        return itens.stream().map(i -> i.getInsumo().getNome() + (i.getQuantidade() != null
                        ? " ×" + i.getQuantidade().stripTrailingZeros().toPlainString().replace('.', ',')
                                + (i.getInsumo().getUnidadeMedida() != null ? " " + i.getInsumo().getUnidadeMedida().getSigla() : "")
                        : ""))
                .collect(Collectors.joining(", "));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public CompraResumoResponse toResumo(Compra compra, List<CompraItem> itens) {
        TreeSet<String> fornecedores = new TreeSet<>();
        itens.stream().map(CompraItem::getFornecedor).filter(Objects::nonNull)
                .forEach(f -> fornecedores.add(f.getNome()));
        if (fornecedores.isEmpty() && compra.getFornecedor() != null) {
            fornecedores.add(compra.getFornecedor().getNome());
        }
        return new CompraResumoResponse(
                compra.getId(),
                compra.getNumero(),
                IdentificadorFormatter.formatar("COM", compra.getNumero()),
                compra.getStatus(),
                compra.getDataCompra(),
                List.copyOf(fornecedores),
                Boolean.TRUE.equals(compra.getPago()),
                total(itens),
                itens.size(),
                resumoItens(itens));
    }

    public CompraItemResponse toItemResponse(CompraItem item) {
        return new CompraItemResponse(
                item.getId(),
                item.getOrdem(),
                toRef(item.getInsumo()),
                toRef(item.getFornecedor()),
                item.getQuantidade(),
                item.getPrecoTotal(),
                precoUnitario(item.getPrecoTotal(), item.getQuantidade()),
                item.getPrecoUnitarioPago(),
                item.getCustoUnitarioAnterior(),
                item.getCustoUnitarioPosterior(),
                item.getPrecoCheio(),
                item.getDescontoTipo(),
                item.getDescontoInformado(),
                item.getDescontoLinha(),
                item.getDescontoNota());
    }

    /** Preço total ÷ quantidade, 4 casas (mesma escala do custo do insumo); nulo se faltar um dos dois. */
    public static BigDecimal precoUnitario(BigDecimal precoTotal, BigDecimal quantidade) {
        if (precoTotal == null || quantidade == null || quantidade.signum() == 0) {
            return null;
        }
        return precoTotal.divide(quantidade, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal total(List<CompraItem> itens) {
        return itens.stream().map(CompraItem::getPrecoTotal).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public CadastroRefResponse toRef(Cliente cadastro) {
        if (cadastro == null) {
            return null;
        }
        return new CadastroRefResponse(cadastro.getId(),
                IdentificadorFormatter.formatar("CLI", cadastro.getNumero()),
                cadastro.getNome(), Boolean.TRUE.equals(cadastro.getAtiva()));
    }

    public InsumoRefResponse toRef(Insumo insumo) {
        return new InsumoRefResponse(insumo.getId(),
                IdentificadorFormatter.formatar("INS", insumo.getNumero()),
                insumo.getNome(), insumo.getMarca(),
                insumo.getUnidadeMedida() != null ? insumo.getUnidadeMedida().getSigla() : null,
                Boolean.TRUE.equals(insumo.getAtivo()));
    }

    public MetodoPagamentoRefResponse toRef(MetodoPagamentoConfiguravel metodo) {
        if (metodo == null) {
            return null;
        }
        return new MetodoPagamentoRefResponse(metodo.getId(), metodo.getTipo(),
                rotulo(metodo), Boolean.TRUE.equals(metodo.getAtivo()));
    }

    /**
     * Tipos fixos não têm nome gravado (seed de V40); mesmo rótulo do frontend
     * ({@code constants/metodoPagamentoConfiguravel.ts}). OUTRO usa o nome dado pela artesã.
     */
    public static String rotulo(MetodoPagamentoConfiguravel metodo) {
        if (metodo.getNome() != null && !metodo.getNome().isBlank()) {
            return metodo.getNome();
        }
        return rotulo(metodo.getTipo());
    }

    public static String rotulo(TipoMetodoPagamento tipo) {
        return switch (tipo) {
            case DINHEIRO -> "Dinheiro";
            case PIX -> "Pix";
            case CARTAO_CREDITO -> "Cartão Crédito";
            case CARTAO_DEBITO -> "Cartão Débito";
            case OUTRO -> "Outro";
        };
    }
}
