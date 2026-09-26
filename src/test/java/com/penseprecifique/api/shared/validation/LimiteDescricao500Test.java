package com.penseprecifique.api.shared.validation;

import com.penseprecifique.api.shared.dto.request.caixa.CaixaMovimentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.CancelarVendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.FecharCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.CriarProducaoVinculadaRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.producao.AgruparProducoesRequest;
import com.penseprecifique.api.shared.dto.request.producao.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.producao.TravarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.produto.BaixaManualProdutoRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #559/RN-NOVA-18, CEN-NOVO-28 — os 17 campos de descrição/observação/justificativa do adendo de
 * análise aceitam até 500 caracteres; 501 é recusado com "Máximo de 500 caracteres".
 * ({@code ItemCatalogoRequest.descricao} fica fora: mantém 150.)
 */
class LimiteDescricao500Test {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static Stream<Arguments> campos() {
        return Stream.of(
                Arguments.of(ClienteRequest.class, "observacoes"),
                Arguments.of(OrcamentoRequest.class, "observacoes"),
                Arguments.of(OrcamentoRequest.class, "metodoPagamentoObs"),
                Arguments.of(AvancaStatusRequest.class, "metodoSinalRecebidoObs"),
                Arguments.of(AvancaStatusRequest.class, "motivoCancelamento"),
                Arguments.of(AvancaStatusRequest.class, "justificativa"),
                Arguments.of(CriarProducaoVinculadaRequest.class, "observacoes"),
                Arguments.of(CriarProducaoRequest.class, "observacoes"),
                Arguments.of(CancelarProducaoRequest.class, "justificativa"),
                Arguments.of(TravarProducaoRequest.class, "justificativa"),
                Arguments.of(AgruparProducoesRequest.class, "justificativa"),
                Arguments.of(BaixaManualInsumoRequestDTO.class, "observacao"),
                Arguments.of(BaixaManualProdutoRequest.class, "observacao"),
                Arguments.of(ProdutoRequest.class, "descricao"),
                Arguments.of(CaixaMovimentoRequestDTO.class, "motivo"),
                Arguments.of(FecharCaixaTurnoRequestDTO.class, "justificativa"),
                Arguments.of(CancelarVendaCaixaRequestDTO.class, "cancelamentoMotivo"));
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("campos")
    void limite500(Class<?> tipo, String campo) {
        Set<? extends ConstraintViolation<?>> com501 = VALIDATOR.validateValue(tipo, campo, "a".repeat(501));
        assertTrue(com501.stream().anyMatch(v -> "Máximo de 500 caracteres".equals(v.getMessage())),
                tipo.getSimpleName() + "." + campo + " aceitou 501 caracteres");

        Set<? extends ConstraintViolation<?>> com500 = VALIDATOR.validateValue(tipo, campo, "a".repeat(500));
        assertEquals(0, com500.size(), tipo.getSimpleName() + "." + campo + " recusou 500 caracteres");
    }
}
