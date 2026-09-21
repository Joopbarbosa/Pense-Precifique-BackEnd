package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoComponente;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoComponenteResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ItemCatalogoMapper {

    public ItemCatalogo toEntity(ItemCatalogoRequest request, Catalogo catalogo) {
        return ItemCatalogo.builder()
                .catalogo(catalogo)
                .nome(request.getNome())
                .tempoProducao(request.getTempoProducao())
                .margemLucro(request.getMargemLucro())
                .descricao(request.getDescricao())
                .build();
        // precoVenda/override dependem do cálculo de precoSugerido (RN-NOVA-3) — o Service resolve depois
    }

    public ItemCatalogoComponente toComponenteEntity(ItemCatalogoComponenteRequest request, ItemCatalogo item,
                                                      Insumo insumo, Produto produtoBase) {
        return ItemCatalogoComponente.builder()
                .itemCatalogo(item)
                .insumo(insumo)
                .produtoBase(produtoBase)
                .quantidade(request.getQuantidade())
                .build();
    }

    public ItemCatalogoComponenteResponse toComponenteResponse(ItemCatalogoComponente componente) {
        ItemCatalogoComponenteResponse response = new ItemCatalogoComponenteResponse();
        response.setId(componente.getId());
        response.setQuantidade(componente.getQuantidade());

        BigDecimal custoUnitario;
        boolean ativo;
        if (componente.getInsumo() != null) {
            Insumo insumo = componente.getInsumo();
            custoUnitario = insumo.getCustoUnitario();
            response.setInsumoId(insumo.getId());
            response.setNomeInsumo(insumo.getNome());
            response.setFracionavelInsumo(insumo.getFracionavel());
            ativo = Boolean.TRUE.equals(insumo.getAtivo()) && insumo.getDeletedAt() == null;
        } else {
            Produto produtoBase = componente.getProdutoBase();
            custoUnitario = produtoBase.getPrecoCusto();
            response.setProdutoBaseId(produtoBase.getId());
            response.setNomeProdutoBase(produtoBase.getNome());
            response.setTipoProdutoBase(produtoBase.getTipo());
            ativo = Boolean.TRUE.equals(produtoBase.getAtivo()) && produtoBase.getDeletedAt() == null;
        }

        response.setCustoUnitario(custoUnitario);
        response.setCustoTotal(componente.getQuantidade().multiply(custoUnitario));
        response.setAtivo(ativo);
        return response;
    }

    public ItemCatalogoResponse toResponse(ItemCatalogo item, List<ItemCatalogoComponente> componentes) {
        ItemCatalogoResponse response = new ItemCatalogoResponse();
        response.setId(item.getId());
        response.setNome(item.getNome());
        response.setTempoProducao(item.getTempoProducao());
        response.setMargemLucro(item.getMargemLucro());
        response.setPrecoVenda(item.getPrecoVenda());
        response.setOverride(Boolean.TRUE.equals(item.getOverride()));
        response.setComponentes(componentes.stream().map(this::toComponenteResponse).toList());
        response.setFotoUrl(item.getFotoUrl());
        response.setDescricao(item.getDescricao());
        // custoTotal/precoSugerido (RN-NOVA-2/3) não são preenchidos aqui — calculados e setados pelo
        // Service, mesmo padrão já usado para custoTotalLote/precoSugerido de Produto
        return response;
    }

    public ItemCatalogoBuscaResponse toBuscaResponse(ItemCatalogo item, List<ItemCatalogoComponente> componentes) {
        ItemCatalogoBuscaResponse response = new ItemCatalogoBuscaResponse();
        response.setId(item.getId());
        response.setCatalogoId(item.getCatalogo().getId());
        response.setNome(item.getNome());
        response.setPrecoVenda(item.getPrecoVenda());
        response.setCatalogoNome(item.getCatalogo().getNome());
        response.setCatalogoNumero(item.getCatalogo().getNumero());
        response.setComponentes(componentes.stream().map(this::toComponenteResponse).toList());
        response.setAlgumComponenteNaoFracionavel(algumComponenteNaoFracionavel(componentes));
        response.setAlgumComponenteSemEstoque(algumComponenteSemEstoque(componentes));
        return response;
    }

    /** #238/DECISOES_GLOBAIS — mesmo cálculo agregado usado no resto do sistema, generalizado de
     * "insumos da ficha técnica do produto do item" para "componentes Insumo deste item" (V0.13.0). */
    public boolean algumComponenteNaoFracionavel(List<ItemCatalogoComponente> componentes) {
        return componentes.stream()
                .anyMatch(c -> c.getInsumo() != null && Boolean.FALSE.equals(c.getInsumo().getFracionavel()));
    }

    /** OpenProject #527 — bloqueio duro por componente: estoque zerado/negativo sem permitir
     * estoque negativo. Mesmo critério usado em {@code decidirSituacaoEstoque} (OrcamentoService),
     * sem a parte de "quantidade necessária" (não conhecida no momento da busca). */
    public boolean algumComponenteSemEstoque(List<ItemCatalogoComponente> componentes) {
        return componentes.stream().anyMatch(c -> {
            BigDecimal estoqueAtual;
            boolean permitirEstoqueNegativo;
            if (c.getInsumo() != null) {
                estoqueAtual = c.getInsumo().getEstoqueAtual();
                permitirEstoqueNegativo = Boolean.TRUE.equals(c.getInsumo().getPermitirEstoqueNegativo());
            } else {
                estoqueAtual = c.getProdutoBase().getEstoqueAtual();
                permitirEstoqueNegativo = Boolean.TRUE.equals(c.getProdutoBase().getPermitirEstoqueNegativo());
            }
            return estoqueAtual.compareTo(BigDecimal.ZERO) <= 0 && !permitirEstoqueNegativo;
        });
    }
}
