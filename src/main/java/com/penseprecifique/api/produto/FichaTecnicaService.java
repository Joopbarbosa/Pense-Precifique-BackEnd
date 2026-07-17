package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.repository.InsumoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
                validarQuantidadeInsumo(insumo, req.getQuantidade());
                builder.insumo(insumo).produtoBase(null);
            } else {
                Produto produtoBase = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(req.getProdutoBaseId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto base não encontrado: " + req.getProdutoBaseId()));
                if (produtoBase.getTipo() != TipoProduto.PRODUTO_BASE) {
                    throw new BusinessException("O produto referenciado na ficha técnica deve ser do tipo PRODUTO_BASE.");
                }
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
