package com.penseprecifique.api.compra;

import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoService;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.dto.response.compra.ImpactoCompraResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * #543/RN-NOVA-8 (V0.15.0, DT-NOVA-7) — impacto de uma mudança de custo de insumos (confirmar ou
 * cancelar compra) nos produtos. Síncrono, em memória (fichas técnicas pequenas), sem CTE recursiva.
 *
 * <p>Diretos: ficha técnica usa um insumo cujo custo mudou. Indiretos: busca em largura pelos produtos
 * que usam um produto afetado como componente, com visitados e limite de 20 níveis (mesmo limite da
 * detecção de ciclo de #462). Custo antes/depois recalculado em ordem de dependência: um produto-base
 * afetado entra no cálculo do pai com o seu custo antes/depois, não com o precoCusto persistido.
 *
 * <p>Além de montar o modal, {@link #aplicar} recalcula o {@code precoCusto} PERSISTIDO dos produtos
 * afetados na mesma ordem (via {@link ProdutoService#recalcularPrecoCustoPersistido}, que nunca toca o
 * preço de venda). Sem isso, quem usa um produto como componente continuaria com o custo antigo dele
 * — divergência registrada em decisoes-compras.md.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImpactoCompraService {

    private static final int LIMITE_NIVEIS = 20;

    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final ProdutoService produtoService;

    /** Custo de um insumo antes e depois da operação. */
    public record MudancaCusto(Insumo insumo, BigDecimal antes, BigDecimal depois) {}

    /**
     * @param mudancas insumo → custo antes/depois (só os que de fato mudaram entram no cálculo)
     */
    public ImpactoCompraResponse aplicar(UUID usuarioId, List<MudancaCusto> mudancas) {
        List<MudancaCusto> mudaram = mudancas.stream()
                .filter(m -> m.antes().compareTo(m.depois()) != 0).toList();
        if (mudaram.isEmpty()) {
            return ImpactoCompraResponse.semAlteracao();
        }

        Map<UUID, BigDecimal> insumoAntes = new HashMap<>();
        Map<UUID, BigDecimal> insumoDepois = new HashMap<>();
        mudaram.forEach(m -> {
            insumoAntes.put(m.insumo().getId(), m.antes());
            insumoDepois.put(m.insumo().getId(), m.depois());
        });

        // 1) Diretos e indiretos (BFS), com o nível de cada um.
        Map<UUID, Produto> afetados = new LinkedHashMap<>();
        Set<UUID> diretos = new java.util.HashSet<>();
        Deque<Produto> fila = new ArrayDeque<>();
        for (MudancaCusto m : mudaram) {
            for (Produto p : fichaTecnicaItemRepository.findProdutosByInsumoId(m.insumo().getId())) {
                diretos.add(p.getId());
                if (afetados.putIfAbsent(p.getId(), p) == null) {
                    fila.add(p);
                }
            }
        }
        Map<UUID, Integer> nivel = new HashMap<>();
        diretos.forEach(id -> nivel.put(id, 1));
        while (!fila.isEmpty()) {
            Produto atual = fila.poll();
            int n = nivel.get(atual.getId());
            if (n >= LIMITE_NIVEIS) {
                continue;
            }
            for (Produto pai : fichaTecnicaItemRepository.findProdutosByProdutoBaseId(atual.getId())) {
                if (afetados.putIfAbsent(pai.getId(), pai) == null) {
                    nivel.put(pai.getId(), n + 1);
                    fila.add(pai);
                }
            }
        }

        // 2) Ordem de dependência: um produto só é calculado depois de todos os seus componentes
        //    afetados (profundidade máxima dentro do subgrafo afetado).
        Map<UUID, Integer> profundidade = new HashMap<>();
        afetados.keySet().forEach(id -> profundidade(id, afetados, profundidade, 0));
        List<Produto> ordem = new ArrayList<>(afetados.values());
        ordem.sort(Comparator.comparing((Produto p) -> profundidade.get(p.getId())).thenComparing(Produto::getNome));

        // 3) Antes/depois com os custos substitutos, e persistência na mesma ordem.
        BigDecimal valorHora = produtoService.valorHora(usuarioId);
        Map<UUID, BigDecimal> produtoAntes = new HashMap<>();
        Map<UUID, BigDecimal> produtoDepois = new HashMap<>();
        List<ImpactoCompraResponse.ProdutoImpacto> produtos = new ArrayList<>();
        for (Produto p : ordem) {
            BigDecimal antes = produtoService.simularCustoUnitario(p, insumoAntes, produtoAntes, valorHora);
            BigDecimal depois = produtoService.simularCustoUnitario(p, insumoDepois, produtoDepois, valorHora);
            produtoAntes.put(p.getId(), antes);
            produtoDepois.put(p.getId(), depois);
            produtoService.recalcularPrecoCustoPersistido(p.getId());
            if (antes.compareTo(depois) != 0) {
                produtos.add(new ImpactoCompraResponse.ProdutoImpacto(p.getId(),
                        IdentificadorFormatter.formatar("PRO", p.getNumero()), p.getNome(), p.getTipo(),
                        diretos.contains(p.getId()), antes, depois,
                        produtoService.precoSugerido(antes, p.getMargemLucro()),
                        produtoService.precoSugerido(depois, p.getMargemLucro()),
                        p.getPrecoVenda(), Boolean.TRUE.equals(p.getOverride())));
            }
        }
        produtos.sort(Comparator.comparing((ImpactoCompraResponse.ProdutoImpacto i) -> !i.direto())
                .thenComparing(ImpactoCompraResponse.ProdutoImpacto::nome));

        List<ImpactoCompraResponse.InsumoImpacto> insumos = mudaram.stream()
                .map(m -> new ImpactoCompraResponse.InsumoImpacto(m.insumo().getId(),
                        IdentificadorFormatter.formatar("INS", m.insumo().getNumero()), m.insumo().getNome(),
                        m.insumo().getUnidadeMedida() != null ? m.insumo().getUnidadeMedida().getSigla() : null,
                        m.antes(), m.depois()))
                .toList();
        return new ImpactoCompraResponse(true, insumos, produtos);
    }

    /** Maior distância até um componente afetado (0 = só insumos/produtos não afetados na ficha). */
    private int profundidade(UUID id, Map<UUID, Produto> afetados, Map<UUID, Integer> memo, int guarda) {
        Integer conhecido = memo.get(id);
        if (conhecido != null) {
            return conhecido;
        }
        if (guarda > LIMITE_NIVEIS) {
            return 0;
        }
        int max = 0;
        for (FichaTecnicaItem item : fichaTecnicaItemRepository.findByProdutoId(id)) {
            UUID base = item.getProdutoBase() != null ? item.getProdutoBase().getId() : null;
            if (base != null && afetados.containsKey(base) && !Objects.equals(base, id)) {
                max = Math.max(max, 1 + profundidade(base, afetados, memo, guarda + 1));
            }
        }
        memo.put(id, max);
        return max;
    }
}
