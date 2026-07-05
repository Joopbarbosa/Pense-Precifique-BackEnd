package com.penseprecifique.api.mapper;

import com.penseprecifique.api.domain.entity.Catalogo;
import com.penseprecifique.api.domain.entity.ItemCatalogo;
import com.penseprecifique.api.domain.entity.ItemCatalogoCustomizacao;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.dto.request.CustomizacaoAnexadaRequest;
import com.penseprecifique.api.dto.request.ItemCatalogoRequest;
import com.penseprecifique.api.dto.response.CustomizacaoAnexadaResponse;
import com.penseprecifique.api.dto.response.ItemCatalogoResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ItemCatalogoMapper {

    public ItemCatalogoResponse toResponse(ItemCatalogo item, List<ItemCatalogoCustomizacao> customizacoes) {
        ItemCatalogoResponse response = new ItemCatalogoResponse();
        response.setId(item.getId());
        response.setProdutoId(item.getProduto().getId());
        response.setProdutoNome(item.getProduto().getNome());
        response.setQuantidadePacote(item.getQuantidadePacote());
        response.setPrecoVenda(item.getPrecoVenda());
        response.setOverride(item.getOverride());
        response.setCustomizacoesAnexadas(customizacoes.stream().map(this::toCustomizacaoAnexadaResponse).toList());
        // precoSugerido (RN-042) não é preenchido aqui — calculado e setado pelo Service, mesmo padrão do custoTotalLote do Produto
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
        response.setProdutoId(entidade.getProduto().getId());
        response.setProdutoNome(entidade.getProduto().getNome());
        response.setQuantidade(entidade.getQuantidade());
        return response;
    }

    public ItemCatalogoCustomizacao toCustomizacaoEntity(CustomizacaoAnexadaRequest request, ItemCatalogo item, Produto produtoCustomizacao) {
        return ItemCatalogoCustomizacao.builder()
                .itemCatalogo(item)
                .produto(produtoCustomizacao)
                .quantidade(request.getQuantidade())
                .build();
    }
}
