package com.penseprecifique.api.shared.exception;

import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.producao.PerdaProducaoRequest;
import com.penseprecifique.api.shared.dto.request.produto.BaixaManualProdutoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #819 (Schemathesis, rodada final 12) — quantidade acima da coluna {@code numeric(15,4)} chegava ao banco e
 * voltava como 500. Agora a validação recusa antes de gravar, e o estouro que ainda escapar vira 400.
 */
class QuantidadeExtremaTest {

    private static final String OBSERVACAO = "Ajuste manual de teste automatizado com mais de trinta caracteres.";
    private static final BigDecimal EXTREMA = new BigDecimal("4.1174597923958803e307");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void baixaManualDeProdutoRecusaQuantidadeForaDaColuna() {
        BaixaManualProdutoRequest request = new BaixaManualProdutoRequest();
        request.setTipo(TipoMovimentacaoProduto.SAIDA);
        request.setMotivo(MotivoMovimentacaoProduto.CORRECAO);
        request.setObservacao(OBSERVACAO);

        request.setQuantidade(EXTREMA);
        assertEquals(1, validator.validate(request).size());

        request.setQuantidade(new BigDecimal("100000000000"));
        assertEquals(1, validator.validate(request).size(), "12 dígitos inteiros não cabem em numeric(15,4)");

        request.setQuantidade(new BigDecimal("99999999999.9999"));
        assertTrue(validator.validate(request).isEmpty(), "o maior valor persistível continua aceito");
    }

    @Test
    void baixaManualDeInsumoRecusaQuantidadeForaDaColuna() {
        var extrema = new BaixaManualInsumoRequestDTO(TipoMovimentacaoInsumo.SAIDA, EXTREMA,
                MotivoMovimentacaoInsumo.CORRECAO, OBSERVACAO);
        var valida = new BaixaManualInsumoRequestDTO(TipoMovimentacaoInsumo.SAIDA, new BigDecimal("99999999999.9999"),
                MotivoMovimentacaoInsumo.CORRECAO, OBSERVACAO);

        assertEquals(1, validator.validate(extrema).size());
        assertTrue(validator.validate(valida).isEmpty());
    }

    @Test
    void perdaDeProducaoRecusaQuantidadeForaDaColuna() {
        PerdaProducaoRequest request = new PerdaProducaoRequest();
        request.setProdutoId(UUID.randomUUID());

        request.setQuantidadePerdida(EXTREMA);
        assertEquals(1, validator.validate(request).size());

        request.setQuantidadePerdida(new BigDecimal("99999999999.9999"));
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void estouroDeColunaNumericaQueEscaparDaValidacaoRetorna400() {
        var estouro = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: numeric field overflow"));

        var resposta = new GlobalExceptionHandler().handleDataAccess(estouro);

        assertEquals(400, resposta.getStatusCode().value());
        assertEquals("O valor numérico enviado está fora da faixa permitida.", resposta.getBody().message());
    }
}
