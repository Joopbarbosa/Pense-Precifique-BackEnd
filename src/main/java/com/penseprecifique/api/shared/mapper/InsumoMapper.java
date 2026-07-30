package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;
import com.penseprecifique.api.shared.dto.request.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.InsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.ProdutoRelacionadoResponse;
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
                insumo.getTipoExibicaoQuantidade(),
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
        boolean fracionavel = request.fracionavel() != null ? request.fracionavel() : true;
        return Insumo.builder()
                .usuario(usuario)
                .nome(request.nome())
                .marca(request.marca())
                .unidadeMedida(request.unidadeMedida())
                .fracionavel(fracionavel)
                .tipoExibicaoQuantidade(tipoExibicaoQuantidadeParaSalvar(fracionavel, request.tipoExibicaoQuantidade()))
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
        insumo.setTipoExibicaoQuantidade(
                tipoExibicaoQuantidadeParaSalvar(insumo.getFracionavel(), request.tipoExibicaoQuantidade()));
        if (request.permitirEstoqueNegativo() != null) {
            insumo.setPermitirEstoqueNegativo(request.permitirEstoqueNegativo());
        }
        insumo.setEstoqueMinimo(request.estoqueMinimo());
        // custoUnitario e estoqueAtual só mudam via movimentação
    }

    // RN-NOVA-1 — tipo de exibição só faz sentido quando fracionavel = true; nulo quando não-fracionável,
    // e default DECIMAL (comportamento atual do sistema) quando fracionável mas não informado.
    private TipoExibicaoQuantidade tipoExibicaoQuantidadeParaSalvar(boolean fracionavel, TipoExibicaoQuantidade informado) {
        if (!fracionavel) {
            return null;
        }
        return informado != null ? informado : TipoExibicaoQuantidade.DECIMAL;
    }

    public MovimentacaoInsumoResponseDTO toMovimentacaoResponse(MovimentacaoInsumo mov, String referencia) {
        return new MovimentacaoInsumoResponseDTO(
                mov.getId(),
                mov.getTipo(),
                mov.getMotivo(),
                mov.getQuantidade(),
                mov.getCustoUnitario(),
                mov.getObservacao(),
                mov.getReferenciaId(),
                mov.getReferenciaTipo(),
                referencia,
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
