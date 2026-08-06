package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.FichaTecnicaItemResponse;
import com.penseprecifique.api.shared.dto.response.produto.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ProdutoMapper {

    public ProdutoResponse toResponse(Produto produto, List<FichaTecnicaItem> itens) {
        ProdutoResponse response = new ProdutoResponse();
        response.setId(produto.getId());
        response.setNumero(produto.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("PRO", produto.getNumero()));
        response.setNome(produto.getNome());
        response.setTipo(produto.getTipo());
        response.setPrecoVenda(produto.getPrecoVenda());
        response.setPrecoCusto(produto.getPrecoCusto());
        response.setMargemLucro(produto.getMargemLucro());
        response.setOverride(produto.getOverride());
        response.setRendimento(produto.getRendimento());
        response.setCustoUnitario(produto.getPrecoCusto());
        response.setEstoqueAtual(produto.getEstoqueAtual());
        response.setEstoqueMinimo(produto.getEstoqueMinimo());
        response.setPermitirEstoqueNegativo(produto.getPermitirEstoqueNegativo());
        response.setAtivo(produto.getAtivo());
        // RN-051 — mesmo cálculo de toDetalheResponse, para a listagem não precisar de dois nomes de campo.
        response.setAlgumInsumoNaoFracionavel(itens.stream()
                .anyMatch(item -> item.getInsumo() != null && Boolean.FALSE.equals(item.getInsumo().getFracionavel())));
        response.setCreatedAt(produto.getCreatedAt());
        response.setUpdatedAt(produto.getUpdatedAt());
        return response;
    }

    public ProdutoDetalheResponse toDetalheResponse(Produto produto, List<FichaTecnicaItem> itens) {
        ProdutoDetalheResponse response = new ProdutoDetalheResponse();
        response.setId(produto.getId());
        response.setNumero(produto.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("PRO", produto.getNumero()));
        response.setNome(produto.getNome());
        response.setTipo(produto.getTipo());
        response.setDescricao(produto.getDescricao());
        response.setTempoProducao(produto.getTempoProducao());
        response.setPrecoVenda(produto.getPrecoVenda());
        response.setPrecoCusto(produto.getPrecoCusto());
        response.setMargemLucro(produto.getMargemLucro());
        response.setOverride(produto.getOverride());
        response.setRendimento(produto.getRendimento());
        response.setCustoUnitario(produto.getPrecoCusto());
        response.setEstoqueAtual(produto.getEstoqueAtual());
        response.setEstoqueMinimo(produto.getEstoqueMinimo());
        response.setPermitirEstoqueNegativo(produto.getPermitirEstoqueNegativo());
        response.setAtivo(produto.getAtivo());
        // RN-051 — cálculo trivial de agregação sobre dado já mapeado por item, sem ramificação de regra de negócio.
        response.setAlgumInsumoNaoFracionavel(itens.stream()
                .anyMatch(item -> item.getInsumo() != null && Boolean.FALSE.equals(item.getInsumo().getFracionavel())));
        response.setFichaTecnica(itens.stream().map(this::toFichaTecnicaItemResponse).toList());
        response.setCreatedAt(produto.getCreatedAt());
        response.setUpdatedAt(produto.getUpdatedAt());
        return response;
    }

    public Produto toEntity(ProdutoRequest request, Usuario usuario) {
        return Produto.builder()
                .usuario(usuario)
                .nome(request.getNome())
                .tipo(request.getTipo())
                .descricao(request.getDescricao())
                .tempoProducao(request.getTempoProducao())
                .precoVenda(request.getPrecoVenda())
                .margemLucro(request.getMargemLucro())
                .rendimento(request.getRendimento())
                .precoCusto(BigDecimal.ZERO)
                .estoqueAtual(request.getEstoqueAtual() != null ? request.getEstoqueAtual() : BigDecimal.ZERO)
                .estoqueMinimo(request.getEstoqueMinimo())
                .permitirEstoqueNegativo(request.getPermitirEstoqueNegativo() != null ? request.getPermitirEstoqueNegativo() : true)
                .ativo(true)
                .build();
    }

    public void updateEntity(ProdutoRequest request, Produto produto) {
        produto.setNome(request.getNome());
        produto.setTipo(request.getTipo());
        produto.setDescricao(request.getDescricao());
        produto.setTempoProducao(request.getTempoProducao());
        produto.setPrecoVenda(request.getPrecoVenda());
        produto.setMargemLucro(request.getMargemLucro());
        produto.setRendimento(request.getRendimento());
        produto.setEstoqueMinimo(request.getEstoqueMinimo());
        if (request.getPermitirEstoqueNegativo() != null) {
            produto.setPermitirEstoqueNegativo(request.getPermitirEstoqueNegativo());
        }
        // precoCusto e estoqueAtual só mudam via movimentação/recálculo de ficha
        // precoVenda/margemLucro/override (RN-038a) são recalculados/ajustados pelo ProdutoService logo em seguida
    }

    public FichaTecnicaItemResponse toFichaTecnicaItemResponse(FichaTecnicaItem item) {
        FichaTecnicaItemResponse response = new FichaTecnicaItemResponse();
        response.setId(item.getId());
        response.setQuantidade(item.getQuantidade());

        BigDecimal custoUnitario;
        if (item.getInsumo() != null) {
            custoUnitario = item.getInsumo().getCustoUnitario();
            response.setInsumoId(item.getInsumo().getId());
            response.setNomeInsumo(item.getInsumo().getNome());
            response.setMarcaInsumo(item.getInsumo().getMarca());
            response.setUnidadeMedida(item.getInsumo().getUnidadeMedida());
            response.setFracionavelInsumo(item.getInsumo().getFracionavel());
            response.setTipoExibicaoQuantidade(item.getInsumo().getTipoExibicaoQuantidade());
        } else {
            custoUnitario = item.getProdutoBase().getPrecoCusto();
            response.setProdutoBaseId(item.getProdutoBase().getId());
            response.setNomeProdutoBase(item.getProdutoBase().getNome());
        }

        response.setCustoUnitario(custoUnitario);
        response.setCustoTotal(item.getQuantidade().multiply(custoUnitario));
        return response;
    }

    public MovimentacaoProdutoResponse toMovimentacaoResponse(MovimentacaoProduto mov) {
        MovimentacaoProdutoResponse response = new MovimentacaoProdutoResponse();
        response.setId(mov.getId());
        response.setTipo(mov.getTipo());
        response.setMotivo(mov.getMotivo());
        response.setQuantidade(mov.getQuantidade());
        response.setObservacao(mov.getObservacao());
        response.setReferenciaId(mov.getReferenciaId());
        response.setReferenciaTipo(mov.getReferenciaTipo());
        response.setCatalogoReferencia(mov.getCatalogoReferencia());
        response.setPrecoVendido(mov.getPrecoVendido());
        response.setEstornada(mov.getEstornada());
        response.setCreatedAt(mov.getCreatedAt());
        return response;
    }
}
