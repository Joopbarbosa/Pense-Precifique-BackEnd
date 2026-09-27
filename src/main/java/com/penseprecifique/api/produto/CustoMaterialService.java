package com.penseprecifique.api.produto;

import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * #575/RN-NOVA-26 (V0.15.0, DT-NOVA-16) — custo de material por unidade, sem mão de obra nem margem,
 * base do CMV (RN-NOVA-27). Usa sempre o custo atual dos insumos (custo médio ponderado), nunca o
 * {@code Produto.precoCusto} gravado (que inclui mão de obra).
 *
 * <p>Produto: soma da ficha técnica ÷ rendimento; insumo = quantidade × custo; produto componente =
 * quantidade × custo de material dele (recursivo). Item de catálogo: soma dos componentes gravados no
 * pedido (quantidades já multiplicadas pela quantidade do item) ÷ quantidade do item.
 *
 * <p>Uma instância de {@link Calculo} por operação guarda o que já foi calculado (o mesmo produto base
 * aparece em várias fichas) e corta ciclos de ficha técnica.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustoMaterialService {

    private static final int ESCALA = 4;

    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;

    /** Componente de item de catálogo, vindo do snapshot do pedido ou do cadastro atual. */
    public record Componente(Insumo insumo, Produto produtoBase, BigDecimal quantidade) {}

    public Calculo novoCalculo() {
        return new Calculo();
    }

    public final class Calculo {
        private final Map<UUID, BigDecimal> memo = new HashMap<>();

        private Calculo() {
        }

        /** Custo de material de 1 unidade do produto. */
        public BigDecimal produto(Produto produto) {
            return arredondar(material(produto, new HashSet<>()));
        }

        /**
         * Custo de material de 1 unidade de item de catálogo, a partir dos componentes do pedido
         * ({@code quantidade} de cada um = por unidade × {@code quantidadeItem}).
         */
        public BigDecimal itemCatalogo(List<Componente> componentesDoPedido, BigDecimal quantidadeItem) {
            BigDecimal total = BigDecimal.ZERO;
            for (Componente c : componentesDoPedido) {
                total = total.add(c.quantidade().multiply(custoComponente(c.insumo(), c.produtoBase())));
            }
            BigDecimal divisor = quantidadeItem != null && quantidadeItem.signum() > 0 ? quantidadeItem : BigDecimal.ONE;
            return total.divide(divisor, ESCALA, RoundingMode.HALF_UP);
        }

        /** Item de catálogo sem snapshot de componentes (pedido antigo): usa a composição atual. */
        public BigDecimal itemCatalogoAtual(ItemCatalogo item) {
            BigDecimal total = BigDecimal.ZERO;
            for (ItemCatalogoComponente c : itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId())) {
                total = total.add(c.getQuantidade().multiply(custoComponente(c.getInsumo(), c.getProdutoBase())));
            }
            return arredondar(total);
        }

        private BigDecimal custoComponente(Insumo insumo, Produto produtoBase) {
            if (insumo != null) {
                return nz(insumo.getCustoUnitario());
            }
            return produtoBase != null ? material(produtoBase, new HashSet<>()) : BigDecimal.ZERO;
        }

        private BigDecimal material(Produto produto, Set<UUID> caminho) {
            BigDecimal pronto = memo.get(produto.getId());
            if (pronto != null) {
                return pronto;
            }
            if (!caminho.add(produto.getId())) {
                return BigDecimal.ZERO; // ciclo na ficha técnica: não soma de novo
            }
            BigDecimal lote = BigDecimal.ZERO;
            for (FichaTecnicaItem f : fichaTecnicaItemRepository.findByProdutoId(produto.getId())) {
                BigDecimal unitario = f.getInsumo() != null
                        ? nz(f.getInsumo().getCustoUnitario())
                        : f.getProdutoBase() != null ? material(f.getProdutoBase(), caminho) : BigDecimal.ZERO;
                lote = lote.add(f.getQuantidade().multiply(unitario));
            }
            caminho.remove(produto.getId());
            BigDecimal rendimento = produto.getRendimento();
            BigDecimal divisor = rendimento != null && rendimento.signum() > 0 ? rendimento : BigDecimal.ONE;
            BigDecimal porUnidade = lote.divide(divisor, 8, RoundingMode.HALF_UP);
            memo.put(produto.getId(), porUnidade);
            return porUnidade;
        }
    }

    private static BigDecimal arredondar(BigDecimal v) {
        return v.setScale(ESCALA, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
