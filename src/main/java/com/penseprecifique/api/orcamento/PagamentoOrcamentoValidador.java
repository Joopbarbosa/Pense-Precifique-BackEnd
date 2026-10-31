package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest.TipoSinal;
import com.penseprecifique.api.shared.dto.response.compra.MetodoPagamentoRefResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.PagamentoOrcamentoValidadoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.shared.validation.FaixaDataPermitida;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * V0.19.0 (#1185, RN-NOVA-38/39/42, CEN-NOVO-45/46/59) — regras do passo "Pagamento" da aprovação, sem gravar nada.
 * A #1174 reutiliza {@link #validar} na mesma transação que grava o pagamento, cria as parcelas e muda o status.
 */
@Component
@RequiredArgsConstructor
public class PagamentoOrcamentoValidador {

    public static final String MSG_SINAL = "O sinal precisa ser maior que zero e no máximo o total.";
    public static final String MSG_METODO = "Escolha uma forma de pagamento da lista.";

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal PADRAO_PERCENTUAL = new BigDecimal("50");
    private static final BigDecimal LIMITE_NUMERICO = new BigDecimal("99999999.99");

    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final Clock clock;

    public PagamentoOrcamentoValidadoResponse validar(Orcamento orcamento, PagamentoOrcamentoRequest request, UUID usuarioId) {
        MetodoPagamentoConfiguravel metodo = validarMetodo(request.getMetodoPagamentoId(), usuarioId);
        LocalDate hoje = LocalDate.now(clock);
        BigDecimal total = orcamento.getTotal() != null ? orcamento.getTotal().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        boolean temSinal = Boolean.TRUE.equals(request.getTemSinal());

        BigDecimal percentual = null;
        BigDecimal valorSinal = null;
        BigDecimal saldo = total;
        LocalDate sinalAte = null;

        if (temSinal) {
            if (total.signum() <= 0) {
                throw BusinessException.noCampo("valorSinal", MSG_SINAL);
            }
            TipoSinal tipo = request.getTipoSinal() != null ? request.getTipoSinal() : TipoSinal.PERCENTUAL;
            if (tipo == TipoSinal.PERCENTUAL) {
                BigDecimal informado = request.getPercentualSinal() != null ? request.getPercentualSinal() : PADRAO_PERCENTUAL;
                percentual = numeroComDuasCasas(informado, "percentualSinal");
                if (percentual.compareTo(BigDecimal.ONE) < 0 || percentual.compareTo(CEM) > 0) {
                    throw BusinessException.noCampo("percentualSinal", MSG_SINAL);
                }
                valorSinal = total.multiply(percentual).divide(CEM, 2, RoundingMode.DOWN);
            } else {
                if (request.getValorSinal() == null) {
                    throw BusinessException.noCampo("valorSinal", MSG_SINAL);
                }
                valorSinal = numeroComDuasCasas(request.getValorSinal(), "valorSinal");
                if (valorSinal.compareTo(total) > 0) {
                    throw BusinessException.noCampo("valorSinal", MSG_SINAL);
                }
                percentual = valorSinal.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
            }
            // CEN-NOVO-59: o arredondamento (para baixo, em centavos) não pode zerar o sinal.
            if (valorSinal.compareTo(new BigDecimal("0.01")) < 0 || valorSinal.compareTo(total) > 0) {
                throw BusinessException.noCampo(tipo == TipoSinal.PERCENTUAL ? "percentualSinal" : "valorSinal", MSG_SINAL);
            }
            saldo = total.subtract(valorSinal).setScale(2, RoundingMode.HALF_UP);
            sinalAte = validarData(request.getSinalAte(), "sinalAte", "Informe até quando o sinal deve ser pago.", hoje);
        } else if (request.getSinalAte() != null || request.getPercentualSinal() != null || request.getValorSinal() != null) {
            // sem sinal, qualquer dado de sinal é inconsistência (nunca ignorado em silêncio)
            throw BusinessException.noCampo("temSinal", "Os dados do sinal só valem quando há sinal.");
        }

        boolean geraSaldo = !temSinal || saldo.signum() > 0;
        LocalDate vencimento = null;
        if (geraSaldo) {
            vencimento = request.getVencimentoSaldo() == null ? hoje
                    : validarData(request.getVencimentoSaldo(), "vencimentoSaldo", null, hoje);
        }
        return new PagamentoOrcamentoValidadoResponse(refMetodo(metodo), temSinal, total, percentual, valorSinal,
                saldo, geraSaldo, sinalAte, vencimento);
    }

    /** RN-NOVA-42: só método ATIVO da própria empresa; inexistente e de outra empresa têm resposta idêntica. */
    MetodoPagamentoConfiguravel validarMetodo(UUID metodoId, UUID usuarioId) {
        if (metodoId == null) {
            throw BusinessException.noCampo("metodoPagamentoId", MSG_METODO);
        }
        return metodoPagamentoRepository.findByIdAndUsuarioId(metodoId, usuarioId)
                .filter(m -> Boolean.TRUE.equals(m.getAtivo()))
                .orElseThrow(() -> BusinessException.noCampo("metodoPagamentoId", MSG_METODO));
    }

    private BigDecimal numeroComDuasCasas(BigDecimal valor, String campo) {
        // comparação com o teto antes de qualquer operação de escala (entrada como 1E+999999999 nunca é expandida)
        if (valor.signum() <= 0 || valor.abs().compareTo(LIMITE_NUMERICO) > 0 || valor.stripTrailingZeros().scale() > 2) {
            throw BusinessException.noCampo(campo, MSG_SINAL);
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private LocalDate validarData(LocalDate data, String campo, String mensagemAusente, LocalDate hoje) {
        if (data == null) {
            throw BusinessException.noCampo(campo, mensagemAusente);
        }
        if (!FaixaDataPermitida.contem(data)) {
            throw BusinessException.noCampo(campo, FaixaDataPermitida.MENSAGEM);
        }
        if (data.isBefore(hoje)) {
            throw BusinessException.noCampo(campo, mensagemAnterior(campo));
        }
        return data;
    }

    private String mensagemAnterior(String campo) {
        return "sinalAte".equals(campo)
                ? "A data limite do sinal não pode ser anterior à data da aprovação."
                : "O vencimento do saldo não pode ser anterior à data da aprovação.";
    }

    private MetodoPagamentoRefResponse refMetodo(MetodoPagamentoConfiguravel metodo) {
        return new MetodoPagamentoRefResponse(metodo.getId(), metodo.getTipo(), CompraMapper.rotulo(metodo),
                Boolean.TRUE.equals(metodo.getAtivo()));
    }
}
