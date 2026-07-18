package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.dto.response.AlertaInsumoResponse;
import com.penseprecifique.api.shared.dto.response.HistoricoStatusResponse;
import com.penseprecifique.api.shared.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoResponse;
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
        response.setEstado(producao.getEstado());
        response.setDataInicio(producao.getDataInicio());
        response.setDataTerminoPrevista(producao.getDataTerminoPrevista());
        response.setObservacoes(producao.getObservacoes());
        return response;
    }

    public ProducaoDetalheResponse toDetalheResponse(Producao producao,
                                                       List<ProducaoInsumoConsumido> insumosConsumidos,
                                                       List<ProducaoProduto> produtos,
                                                       List<AlertaInsumoResponse> alertasInsumos,
                                                       List<HistoricoStatusProducao> historicoStatus) {
        ProducaoDetalheResponse response = new ProducaoDetalheResponse();
        response.setId(producao.getId());
        response.setNumero(producao.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("PRD", producao.getNumero()));
        response.setEstado(producao.getEstado());
        response.setDataInicio(producao.getDataInicio());
        response.setDataTerminoPrevista(producao.getDataTerminoPrevista());
        response.setDataTerminoReal(producao.getDataTerminoReal());
        response.setObservacoes(producao.getObservacoes());
        response.setJustificativaCancelamento(producao.getJustificativaCancelamento());
        response.setJustificativaNaoRealizada(producao.getJustificativaNaoRealizada());
        response.setProducaoOrigemId(producao.getProducaoOrigem() != null ? producao.getProducaoOrigem().getId() : null);
        response.setTipoOrigem(producao.getTipoOrigem());
        response.setProdutos(produtos.stream().map(this::toProducaoProdutoResponse).toList());
        response.setAlertasInsumos(alertasInsumos);
        response.setInsumosConsumidos(insumosConsumidos.stream().map(this::toInsumoConsumidoResponse).toList());
        response.setHistoricoStatus(historicoStatus.stream().map(this::toHistoricoStatusResponse).toList());
        return response;
    }

    public HistoricoStatusResponse toHistoricoStatusResponse(HistoricoStatusProducao historico) {
        HistoricoStatusResponse response = new HistoricoStatusResponse();
        response.setStatusAnterior(historico.getStatusAnterior());
        response.setStatusNovo(historico.getStatusNovo());
        response.setDataTransicao(historico.getDataTransicao());
        response.setJustificativa(historico.getJustificativa());
        response.setOrigem(historico.getOrigem());
        return response;
    }

    public ProducaoProdutoResponse toProducaoProdutoResponse(ProducaoProduto producaoProduto) {
        ProducaoProdutoResponse response = new ProducaoProdutoResponse();
        response.setProdutoId(producaoProduto.getProduto().getId());
        response.setNomeProduto(producaoProduto.getProduto().getNome());
        response.setTipoProduto(producaoProduto.getProduto().getTipo());
        response.setQuantidade(producaoProduto.getQuantidade());
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
