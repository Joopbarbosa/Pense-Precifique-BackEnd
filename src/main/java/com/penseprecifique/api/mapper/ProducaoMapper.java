package com.penseprecifique.api.mapper;

import com.penseprecifique.api.domain.entity.Producao;
import com.penseprecifique.api.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.dto.response.ProducaoResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProducaoMapper {

    public ProducaoResponse toResponse(Producao producao) {
        ProducaoResponse response = new ProducaoResponse();
        response.setId(producao.getId());
        response.setNumero(producao.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("PRD", producao.getNumero()));
        response.setProdutoId(producao.getProduto().getId());
        response.setNomeProduto(producao.getProduto().getNome());
        response.setTipoProduto(producao.getProduto().getTipo());
        response.setQuantidade(producao.getQuantidade());
        response.setDataProducao(producao.getDataProducao());
        response.setStatus(producao.getStatus());
        return response;
    }

    public ProducaoDetalheResponse toDetalheResponse(Producao producao, List<ProducaoInsumoConsumido> insumosConsumidos) {
        ProducaoDetalheResponse response = new ProducaoDetalheResponse();
        response.setId(producao.getId());
        response.setNumero(producao.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("PRD", producao.getNumero()));
        response.setProdutoId(producao.getProduto().getId());
        response.setNomeProduto(producao.getProduto().getNome());
        response.setTipoProduto(producao.getProduto().getTipo());
        response.setQuantidade(producao.getQuantidade());
        response.setDataProducao(producao.getDataProducao());
        response.setStatus(producao.getStatus());
        response.setObservacaoCancelamento(producao.getObservacaoCancelamento());
        response.setDataCancelamento(producao.getDataCancelamento());
        response.setInsumosConsumidos(insumosConsumidos.stream().map(this::toInsumoConsumidoResponse).toList());
        return response;
    }

    public InsumoConsumidoResponse toInsumoConsumidoResponse(ProducaoInsumoConsumido consumido) {
        InsumoConsumidoResponse response = new InsumoConsumidoResponse();
        if (consumido.getInsumo() != null) {
            response.setInsumoId(consumido.getInsumo().getId());
            response.setNomeInsumo(consumido.getInsumo().getNome());
            response.setMarca(consumido.getInsumo().getMarca());
            response.setUnidadeMedida(consumido.getInsumo().getUnidadeMedida());
        } else if (consumido.getProdutoBase() != null) {
            response.setInsumoId(consumido.getProdutoBase().getId());
            response.setNomeInsumo(consumido.getProdutoBase().getNome());
        }
        response.setQuantidade(consumido.getQuantidade());
        response.setEstoqueAntes(null);
        response.setEstoqueInsuficiente(false);
        return response;
    }
}
