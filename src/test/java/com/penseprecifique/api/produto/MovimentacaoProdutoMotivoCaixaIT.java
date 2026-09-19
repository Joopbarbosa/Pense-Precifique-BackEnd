package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #490 — MovimentacaoProduto.motivo/referencia_tipo ganham o valor CAIXA (RN-NOVA-14, V0.12.0).
 * Sem código de serviço novo nesta tarefa — só enum + CHECK de banco (migration V42), consumido de
 * verdade pela venda de Caixa em #487. Este teste garante que o valor é aceito de ponta a ponta
 * (Java → constraint do banco), tanto na baixa (SAIDA) quanto na reversão de cancelamento (ENTRADA).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MovimentacaoProdutoMotivoCaixaIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired MovimentacaoProdutoRepository movimentacaoProdutoRepository;

    @Test
    void movimentacaoDeSaidaComMotivoCaixaEPersistidaComSucesso() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("mov-caixa-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        Produto produto = produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(1).nome("Caneta").tipo(TipoProduto.PRODUTO)
                .tempoProducao(1).rendimento(BigDecimal.ONE).precoVenda(new BigDecimal("5.00"))
                .estoqueAtual(new BigDecimal("10")).ativo(true).build());
        UUID referenciaId = UUID.randomUUID(); // simula VendaCaixa.id — entidade real chega em #487

        MovimentacaoProduto saida = movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                .produto(produto).tipo(TipoMovimentacaoProduto.SAIDA)
                .motivo(MotivoMovimentacaoProduto.CAIXA)
                .quantidade(new BigDecimal("2"))
                .referenciaId(referenciaId)
                .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                .build());

        assertEquals(MotivoMovimentacaoProduto.CAIXA, saida.getMotivo());
        assertEquals("CAIXA", saida.getReferenciaTipo());

        MovimentacaoProduto entradaReversao = movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                .produto(produto).tipo(TipoMovimentacaoProduto.ENTRADA)
                .motivo(MotivoMovimentacaoProduto.CAIXA)
                .quantidade(new BigDecimal("2"))
                .referenciaId(referenciaId)
                .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                .build());

        assertEquals(TipoMovimentacaoProduto.ENTRADA, entradaReversao.getTipo());
    }
}
