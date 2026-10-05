package com.penseprecifique.api.compra;

import com.penseprecifique.api.caixa.VendaCaixaItemComponenteRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemCustomizacaoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemComponenteRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.produto.CustoMaterialService;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemComponente;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItemComponente;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItemCustomizacao;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoVendaCmv;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #577/RN-NOVA-27 (V0.15.0, DT-NOVA-18) — vendas de um período com o CMV de cada uma: quantidade ×
 * custo de material gravado na venda (RN-NOVA-26), de itens e de customizações. Mesmo critério de venda
 * do detalhe do cliente (Decisão 13): orçamento ENTREGUE pela data de entrega + venda do Caixa CONCLUIDA
 * pela data da venda. Linha sem custo gravado (venda anterior à V0.15.0) é estimada pelo custo de hoje;
 * linha sem como calcular (sem produto nem catálogo) fica fora e marca a venda como "sem custo".
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmvService {

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final OrcamentoItemComponenteRepository orcamentoItemComponenteRepository;
    private final VendaCaixaRepository vendaCaixaRepository;
    private final VendaCaixaItemRepository vendaCaixaItemRepository;
    private final VendaCaixaItemCustomizacaoRepository vendaCaixaItemCustomizacaoRepository;
    private final VendaCaixaItemComponenteRepository vendaCaixaItemComponenteRepository;
    private final CustoMaterialService custoMaterialService;

    /** Uma venda com o total (faturamento) e o CMV já somado; {@code estimado} = parte do CMV estimada. */
    public record Venda(UUID id, TipoVendaCmv tipo, String identificador, String cliente, String itens,
                        LocalDate data, BigDecimal faturamento, BigDecimal cmv, BigDecimal estimado,
                        boolean semCusto, boolean custoEstimado) {}

    public List<Venda> vendas(UUID usuarioId, LocalDate de, LocalDate ate) {
        CustoMaterialService.Calculo calculo = custoMaterialService.novoCalculo();
        List<Venda> vendas = new ArrayList<>();
        vendas.addAll(orcamentos(usuarioId, de, ate, calculo));
        vendas.addAll(caixa(usuarioId, de, ate, calculo));
        return vendas;
    }

    private List<Venda> orcamentos(UUID usuarioId, LocalDate de, LocalDate ate, CustoMaterialService.Calculo calculo) {
        List<Orcamento> orcamentos = orcamentoRepository.findByUsuarioIdAndStatusAndDeletedAtIsNullAndDataEntregaBetween(
                usuarioId, StatusOrcamento.ENTREGUE, de.atStartOfDay(), ate.plusDays(1).atStartOfDay().minusNanos(1));
        if (orcamentos.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrcamentoItem>> itens = orcamentoItemRepository.findByOrcamentoIdIn(
                orcamentos.stream().map(Orcamento::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getOrcamento().getId()));
        List<UUID> itemIds = itens.values().stream().flatMap(List::stream).map(OrcamentoItem::getId).toList();
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoes = itemIds.isEmpty() ? Map.of()
                : orcamentoItemCustomizacaoRepository.findByOrcamentoItemIdIn(itemIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getOrcamentoItem().getId()));
        Map<UUID, List<OrcamentoItemComponente>> componentes = itemIds.isEmpty() ? Map.of()
                : orcamentoItemComponenteRepository.findByOrcamentoItemIdIn(itemIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getOrcamentoItem().getId()));

        List<Venda> vendas = new ArrayList<>();
        for (Orcamento o : orcamentos) {
            Acumulado a = new Acumulado();
            for (OrcamentoItem i : itens.getOrDefault(o.getId(), List.of())) {
                BigDecimal qtd = BigDecimal.valueOf(i.getQuantidade());
                a.somar(qtd, i.getCustoMaterialUnitario(), () -> estimarItem(i.getProduto(), i.getItemCatalogo(),
                        componentes.getOrDefault(i.getId(), List.of()).stream()
                                .map(c -> new CustoMaterialService.Componente(c.getInsumo(), c.getProdutoBase(), c.getQuantidade()))
                                .toList(), qtd, calculo));
                for (OrcamentoItemCustomizacao c : customizacoes.getOrDefault(i.getId(), List.of())) {
                    a.somar(BigDecimal.valueOf(c.getQuantidade()), c.getCustoMaterialUnitario(),
                            () -> c.getProduto() != null ? calculo.produto(c.getProduto()) : null);
                }
            }
            String resumo = itens.getOrDefault(o.getId(), List.of()).stream()
                    .map(i -> resumir(BigDecimal.valueOf(i.getQuantidade()), i.getProduto(), i.getItemCatalogo()))
                    .collect(Collectors.joining("; "));
            vendas.add(a.venda(o.getId(), TipoVendaCmv.ORCAMENTO, IdentificadorFormatter.formatar("ORC", o.getNumero()),
                    o.getCliente().getNome(), resumo, o.getDataEntrega().toLocalDate(), o.getTotal()));
        }
        return vendas;
    }

    private List<Venda> caixa(UUID usuarioId, LocalDate de, LocalDate ate, CustoMaterialService.Calculo calculo) {
        List<VendaCaixa> vendasCaixa = vendaCaixaRepository.findByUsuarioIdAndStatusAndDataVendaBetween(
                usuarioId, StatusVendaCaixa.CONCLUIDA, de.atStartOfDay(), ate.plusDays(1).atStartOfDay().minusNanos(1));
        if (vendasCaixa.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<VendaCaixaItem>> itens = vendaCaixaItemRepository.findByVendaCaixaIdIn(
                vendasCaixa.stream().map(VendaCaixa::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getVendaCaixa().getId()));
        List<UUID> itemIds = itens.values().stream().flatMap(List::stream).map(VendaCaixaItem::getId).toList();
        Map<UUID, List<VendaCaixaItemCustomizacao>> customizacoes = itemIds.isEmpty() ? Map.of()
                : vendaCaixaItemCustomizacaoRepository.findByVendaCaixaItemIdIn(itemIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getVendaCaixaItem().getId()));
        Map<UUID, List<VendaCaixaItemComponente>> componentes = itemIds.isEmpty() ? Map.of()
                : vendaCaixaItemComponenteRepository.findByVendaCaixaItemIdIn(itemIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getVendaCaixaItem().getId()));

        List<Venda> vendas = new ArrayList<>();
        for (VendaCaixa v : vendasCaixa) {
            Acumulado a = new Acumulado();
            for (VendaCaixaItem i : itens.getOrDefault(v.getId(), List.of())) {
                a.somar(i.getQuantidade(), i.getCustoMaterialUnitario(), () -> estimarItem(i.getProduto(), i.getItemCatalogo(),
                        componentes.getOrDefault(i.getId(), List.of()).stream()
                                .map(c -> new CustoMaterialService.Componente(c.getInsumo(), c.getProdutoBase(), c.getQuantidade()))
                                .toList(), i.getQuantidade(), calculo));
                for (VendaCaixaItemCustomizacao c : customizacoes.getOrDefault(i.getId(), List.of())) {
                    a.somar(BigDecimal.valueOf(c.getQuantidade()), c.getCustoMaterialUnitario(),
                            () -> c.getProduto() != null ? calculo.produto(c.getProduto()) : null);
                }
            }
            String resumo = itens.getOrDefault(v.getId(), List.of()).stream()
                    .map(i -> resumir(i.getQuantidade(), i.getProduto(), i.getItemCatalogo()))
                    .collect(Collectors.joining("; "));
            vendas.add(a.venda(v.getId(), TipoVendaCmv.VENDA_CAIXA, IdentificadorFormatter.formatar("CX", v.getNumero()),
                    v.getCliente() != null ? v.getCliente().getNome() : null, resumo,
                    v.getDataVenda().toLocalDate(), v.getTotal()));
        }
        return vendas;
    }

    private static String resumir(BigDecimal quantidade, Produto produto, ItemCatalogo itemCatalogo) {
        String nome = itemCatalogo != null ? itemCatalogo.getNome() : produto != null ? produto.getNome() : "Item sem referência";
        return quantidade.stripTrailingZeros().toPlainString().replace('.', ',') + " × " + nome;
    }

    /** Custo de hoje de uma linha sem custo gravado; nulo = sem como calcular. */
    private static BigDecimal estimarItem(Produto produto, ItemCatalogo itemCatalogo,
                                          List<CustoMaterialService.Componente> componentes, BigDecimal quantidade,
                                          CustoMaterialService.Calculo calculo) {
        if (itemCatalogo != null) {
            return componentes.isEmpty() ? calculo.itemCatalogoAtual(itemCatalogo) : calculo.itemCatalogo(componentes, quantidade);
        }
        return produto != null ? calculo.produto(produto) : null;
    }

    private static final class Acumulado {
        private BigDecimal cmv = BigDecimal.ZERO;
        private BigDecimal estimado = BigDecimal.ZERO;
        private boolean semCusto;
        private boolean custoEstimado;

        void somar(BigDecimal quantidade, BigDecimal gravado, java.util.function.Supplier<BigDecimal> estimativa) {
            if (quantidade == null) {
                return;
            }
            if (gravado != null) {
                cmv = cmv.add(quantidade.multiply(gravado));
                return;
            }
            BigDecimal hoje = estimativa.get();
            if (hoje == null) {
                semCusto = true;
                return;
            }
            custoEstimado = true;
            BigDecimal parte = quantidade.multiply(hoje);
            cmv = cmv.add(parte);
            estimado = estimado.add(parte);
        }

        Venda venda(UUID id, TipoVendaCmv tipo, String identificador, String cliente, String itens,
                    LocalDate data, BigDecimal total) {
            return new Venda(id, tipo, identificador, cliente, itens, data, total != null ? total : BigDecimal.ZERO,
                    cmv.setScale(2, RoundingMode.HALF_UP), estimado, semCusto, custoEstimado);
        }
    }
}
