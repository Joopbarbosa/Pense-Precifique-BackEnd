package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.insumo.InsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class FichaTecnicaService {

    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final InsumoRepository insumoRepository;
    private final ProdutoRepository produtoRepository;

    public BigDecimal salvarFichaTecnica(Produto produto, List<FichaTecnicaItemRequest> itens, UUID usuarioId) {
        fichaTecnicaItemRepository.deleteByProdutoId(produto.getId());

        List<FichaTecnicaItem> itensSalvos = itens.stream().map(req -> {
            boolean temInsumo = req.getInsumoId() != null;
            boolean temProdutoBase = req.getProdutoBaseId() != null;

            if (temInsumo == temProdutoBase) {
                throw new BusinessException(
                        "Cada item da ficha técnica deve referenciar exatamente um insumo ou um produto base.");
            }

            FichaTecnicaItem.FichaTecnicaItemBuilder builder = FichaTecnicaItem.builder()
                    .produto(produto)
                    .quantidade(req.getQuantidade());

            if (temInsumo) {
                Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getInsumoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + req.getInsumoId()));
                // INS-011 — insumo inativo não pode ser adicionado a nova ficha técnica.
                if (!Boolean.TRUE.equals(insumo.getAtivo())) {
                    throw new BusinessException("Este insumo está inativo e não pode ser adicionado. Reative-o para continuar.");
                }
                validarQuantidadeInsumo(insumo, req.getQuantidade());
                builder.insumo(insumo).produtoBase(null);
            } else {
                Produto produtoBase = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getProdutoBaseId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto base não encontrado: " + req.getProdutoBaseId()));
                // RN-NOVA-8 (V0.10.0, #462) — PDT-015 revisada: aceita Produto OU Customização como
                // componente, ambos ativos (antes só PRODUTO; CUSTOMIZACAO era BLOQUEIO).
                if (!Boolean.TRUE.equals(produtoBase.getAtivo())) {
                    throw new BusinessException("Apenas produtos/customizações ativos podem ser usados como componente de ficha técnica.");
                }
                // RN-NOVA-9 (V0.10.0, #462, DT-NOVA-3) — proteção contra ciclo direto/indireto no
                // grafo de componentes, ampliada por RN-NOVA-8 aceitar mais um tipo de componente.
                validarSemCiclo(produto, produtoBase);
                builder.produtoBase(produtoBase).insumo(null);
            }

            return fichaTecnicaItemRepository.save(builder.build());
        }).toList();

        return calcularPrecoCusto(itensSalvos);
    }

    @Transactional(readOnly = true)
    public BigDecimal recalcularPrecoCusto(UUID produtoId) {
        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produtoId);
        return calcularPrecoCusto(itens);
    }

    /**
     * #228 — resolução de vínculo por substituição: troca o insumo usado em todas as linhas da ficha
     * técnica de {@code produtoId} que referenciam {@code insumoAntigoId}, preservando a quantidade já
     * configurada em cada linha. Não recalcula {@code produto.precoCusto} — quem chama é responsável por
     * disparar o recálculo persistido (ver {@code ProdutoService.recalcularPrecoCustoPersistido}).
     */
    public void substituirInsumoEmProduto(UUID produtoId, UUID insumoAntigoId, UUID novoInsumoId, UUID usuarioId) {
        Insumo novoInsumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(novoInsumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + novoInsumoId));
        if (!Boolean.TRUE.equals(novoInsumo.getAtivo())) {
            throw new BusinessException("O insumo substituto está inativo e não pode ser usado. Reative-o para continuar.");
        }

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoIdAndInsumoId(produtoId, insumoAntigoId);
        for (FichaTecnicaItem item : itens) {
            validarQuantidadeInsumo(novoInsumo, item.getQuantidade());
            item.setInsumo(novoInsumo);
            fichaTecnicaItemRepository.save(item);
        }
    }

    private void validarQuantidadeInsumo(Insumo insumo, BigDecimal quantidade) {
        if (!insumo.getFracionavel()) {
            if (quantidade.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(
                        "O insumo '" + insumo.getNome() + "' não pode ser usado em fração — informe uma quantidade inteira.");
            }
        } else {
            if (quantidade.stripTrailingZeros().scale() > 2) {
                throw new BusinessException("Quantidade aceita no máximo 2 casas decimais.");
            }
        }
    }

    /**
     * RN-NOVA-9 (V0.10.0, #462, DT-NOVA-3) — proteção contra ciclo direto/indireto no grafo de
     * componentes de ficha técnica. Percorre (DFS, em memória, limite de 20 níveis) a cadeia de
     * fichas técnicas a partir do componente sendo adicionado, bloqueando se ela alcançar de volta
     * o próprio produto/customização cuja ficha técnica está sendo salva.
     */
    private void validarSemCiclo(Produto produto, Produto componente) {
        if (formaCiclo(componente.getId(), produto.getId(), new HashSet<>(), 0)) {
            throw new BusinessException(
                    "Não é possível adicionar '" + componente.getNome() + "' como componente de '"
                            + produto.getNome() + "': formaria um ciclo (um deles acabaria dependendo "
                            + "do custo do outro, direta ou indiretamente).");
        }
    }

    private boolean formaCiclo(UUID atualId, UUID alvoId, Set<UUID> visitados, int profundidade) {
        if (profundidade > 20) {
            throw new BusinessException(
                    "Cadeia de componentes profunda demais para verificar (mais de 20 níveis) — "
                            + "revise a ficha técnica antes de tentar novamente.");
        }
        if (atualId.equals(alvoId)) {
            return true;
        }
        if (!visitados.add(atualId)) {
            return false;
        }
        for (FichaTecnicaItem item : fichaTecnicaItemRepository.findByProdutoId(atualId)) {
            if (item.getProdutoBase() != null
                    && formaCiclo(item.getProdutoBase().getId(), alvoId, visitados, profundidade + 1)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal calcularPrecoCusto(List<FichaTecnicaItem> itens) {
        return itens.stream()
                .map(item -> {
                    BigDecimal custo = item.getInsumo() != null
                            ? item.getInsumo().getCustoUnitario()
                            : item.getProdutoBase().getPrecoCusto();
                    return item.getQuantidade().multiply(custo);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
