package com.penseprecifique.api.mapper;

import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.dto.request.InsumoRequestDTO;
import com.penseprecifique.api.dto.response.InsumoResponseDTO;
import com.penseprecifique.api.dto.response.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.dto.response.ProdutoRelacionadoResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InsumoMapper {

    public InsumoResponseDTO toResponse(Insumo insumo) {
        return new InsumoResponseDTO(
                insumo.getId(),
                insumo.getNome(),
                insumo.getMarca(),
                insumo.getUnidadeMedida(),
                insumo.getCustoUnitario(),
                insumo.getEstoqueAtual(),
                insumo.getEstoqueMinimo(),
                insumo.getAtivo(),
                insumo.getCreatedAt(),
                insumo.getUpdatedAt()
        );
    }

    public Insumo toEntity(InsumoRequestDTO request, Usuario usuario) {
        return Insumo.builder()
                .usuario(usuario)
                .nome(request.nome())
                .marca(request.marca())
                .unidadeMedida(request.unidadeMedida())
                .custoUnitario(BigDecimal.ZERO)
                .estoqueAtual(request.estoqueAtual() != null ? request.estoqueAtual() : BigDecimal.ZERO)
                .estoqueMinimo(request.estoqueMinimo())
                .ativo(true)
                .build();
    }

    public void updateEntity(InsumoRequestDTO request, Insumo insumo) {
        insumo.setNome(request.nome());
        insumo.setMarca(request.marca());
        insumo.setUnidadeMedida(request.unidadeMedida());
        insumo.setEstoqueMinimo(request.estoqueMinimo());
        // custoUnitario e estoqueAtual só mudam via movimentação
    }

    public MovimentacaoInsumoResponseDTO toMovimentacaoResponse(MovimentacaoInsumo mov) {
        return new MovimentacaoInsumoResponseDTO(
                mov.getId(),
                mov.getTipo(),
                mov.getMotivo(),
                mov.getQuantidade(),
                mov.getObservacao(),
                mov.getReferenciaId(),
                mov.getReferenciaTipo(),
                mov.getEstornada(),
                mov.getCreatedAt()
        );
    }

    public List<InsumoResponseDTO> toResponseList(List<Insumo> insumos) {
        return insumos.stream().map(this::toResponse).toList();
    }

    public ProdutoRelacionadoResponse toProdutoRelacionadoResponse(Produto produto) {
        ProdutoRelacionadoResponse response = new ProdutoRelacionadoResponse();
        response.setId(produto.getId());
        response.setNome(produto.getNome());
        response.setTipo(produto.getTipo());
        return response;
    }
}
