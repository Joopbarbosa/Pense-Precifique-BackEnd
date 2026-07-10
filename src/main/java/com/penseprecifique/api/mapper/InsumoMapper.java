package com.penseprecifique.api.mapper;

import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.dto.request.InsumoCreateRequestDTO;
import com.penseprecifique.api.dto.request.InsumoRequestDTO;
import com.penseprecifique.api.dto.response.InsumoResponseDTO;
import com.penseprecifique.api.dto.response.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.dto.response.ProdutoRelacionadoResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InsumoMapper {

    public InsumoResponseDTO toResponse(Insumo insumo) {
        return new InsumoResponseDTO(
                insumo.getId(),
                insumo.getNumero(),
                IdentificadorFormatter.formatar("INS", insumo.getNumero()),
                insumo.getNome(),
                insumo.getMarca(),
                insumo.getUnidadeMedida(),
                insumo.getFracionavel(),
                insumo.getPermitirEstoqueNegativo(),
                insumo.getCustoUnitario(),
                insumo.getEstoqueAtual(),
                insumo.getEstoqueMinimo(),
                insumo.getAtivo(),
                insumo.getCreatedAt(),
                insumo.getUpdatedAt()
        );
    }

    public Insumo toEntity(InsumoCreateRequestDTO request, Usuario usuario) {
        return Insumo.builder()
                .usuario(usuario)
                .nome(request.nome())
                .marca(request.marca())
                .unidadeMedida(request.unidadeMedida())
                .fracionavel(request.fracionavel() != null ? request.fracionavel() : true)
                .permitirEstoqueNegativo(request.permitirEstoqueNegativo() != null ? request.permitirEstoqueNegativo() : true)
                .custoUnitario(BigDecimal.ZERO)
                .estoqueAtual(BigDecimal.ZERO)
                .estoqueMinimo(request.estoqueMinimo())
                .ativo(true)
                .build();
    }

    public void updateEntity(InsumoRequestDTO request, Insumo insumo) {
        insumo.setNome(request.nome());
        insumo.setMarca(request.marca());
        insumo.setUnidadeMedida(request.unidadeMedida());
        if (request.fracionavel() != null) {
            insumo.setFracionavel(request.fracionavel());
        }
        if (request.permitirEstoqueNegativo() != null) {
            insumo.setPermitirEstoqueNegativo(request.permitirEstoqueNegativo());
        }
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
        response.setIdentificador(IdentificadorFormatter.formatar("PRO", produto.getNumero()));
        response.setNome(produto.getNome());
        response.setTipo(produto.getTipo());
        return response;
    }
}
