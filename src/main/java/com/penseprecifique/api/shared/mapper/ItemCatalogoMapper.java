package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.dto.request.catalogo.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CustomizacaoAnexadaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoBuscaResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemCatalogoMapper {

    public ItemCatalogoResponse toResponse(ItemCatalogo item, List<ItemCatalogoCustomizacao> customizacoes,
                                            List<FichaTecnicaItem> fichaTecnicaProduto) {
        ItemCatalogoResponse response = new ItemCatalogoResponse();
        response.setId(item.getId());
        response.setProdutoId(item.getProduto().getId());
        response.setProdutoNome(item.getProduto().getNome());
        response.setQuantidadePacote(item.getQuantidadePacote());
        response.setPrecoVenda(item.getPrecoVenda());
        response.setOverride(item.getOverride());
        response.setCustomizacoesAnexadas(customizacoes.stream().map(this::toCustomizacaoAnexadaResponse).toList());
        // precoSugerido (RN-042) não é preenchido aqui — calculado e setado pelo Service, mesmo padrão do custoTotalLote do Produto
        response.setPermitirEstoqueNegativo(item.getProduto().getPermitirEstoqueNegativo());
        response.setEstoqueAtual(item.getProduto().getEstoqueAtual());
        response.setAlgumInsumoNaoFracionavel(algumInsumoNaoFracionavel(fichaTecnicaProduto));
        return response;
    }

    public ItemCatalogo toEntity(ItemCatalogoRequest request, Catalogo catalogo, Produto produto) {
        return ItemCatalogo.builder()
                .catalogo(catalogo)
                .produto(produto)
                .quantidadePacote(request.getQuantidadePacote())
                .build();
        // precoVenda/override dependem do cálculo de precoSugerido (RN-042) — o Service resolve depois
    }

    public CustomizacaoAnexadaResponse toCustomizacaoAnexadaResponse(ItemCatalogoCustomizacao entidade) {
        CustomizacaoAnexadaResponse response = new CustomizacaoAnexadaResponse();
        response.setId(entidade.getId());
        response.setProdutoId(entidade.getProduto().getId());
        response.setProdutoNome(entidade.getProduto().getNome());
        response.setQuantidade(entidade.getQuantidade());
        return response;
    }

    public ItemCatalogoBuscaResponse toBuscaResponse(ItemCatalogo item, List<FichaTecnicaItem> fichaTecnicaProduto) {
        ItemCatalogoBuscaResponse response = new ItemCatalogoBuscaResponse();
        response.setId(item.getId());
        response.setProdutoId(item.getProduto().getId());
        response.setNomeProduto(item.getProduto().getNome());
        response.setPrecoVenda(item.getPrecoVenda());
        response.setCatalogoNome(item.getCatalogo().getNome());
        response.setCatalogoNumero(item.getCatalogo().getNumero());
        response.setPermitirEstoqueNegativo(item.getProduto().getPermitirEstoqueNegativo());
        response.setEstoqueAtual(item.getProduto().getEstoqueAtual());
        response.setAlgumInsumoNaoFracionavel(algumInsumoNaoFracionavel(fichaTecnicaProduto));
        return response;
    }

    /** #238 — mesmo cálculo de ProdutoMapper (agregado por produto, sem regra de negócio nova). */
    private boolean algumInsumoNaoFracionavel(List<FichaTecnicaItem> fichaTecnicaProduto) {
        return fichaTecnicaProduto.stream()
                .anyMatch(item -> item.getInsumo() != null && Boolean.FALSE.equals(item.getInsumo().getFracionavel()));
    }

    public ItemCatalogoCustomizacao toCustomizacaoEntity(CustomizacaoAnexadaRequest request, ItemCatalogo item, Produto produtoCustomizacao) {
        return ItemCatalogoCustomizacao.builder()
                .itemCatalogo(item)
                .produto(produtoCustomizacao)
                .quantidade(request.getQuantidade())
                .build();
    }
}
