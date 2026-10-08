package com.penseprecifique.api.infra.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.util.Iterator;

/**
 * #813, #816 e #817 — o gerador do OpenAPI não lê {@code @Digits}, {@code @Positive} nem {@code @PositiveOrZero}. Este
 * conversor traduz as três para o contrato, uma vez, em vez de repetir {@code @Schema} em cada campo:
 * {@code @Digits(integer = I, fraction = F)} vira {@code multipleOf} 10^-F e limites de valor ±(10^I − 10^-F);
 * {@code @Positive} vira mínimo exclusivo 0; {@code @PositiveOrZero} vira mínimo 0.
 */
@Component
public class RestricoesNumericasConverter implements ModelConverter {

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType tipo, ModelConverterContext contexto, Iterator<ModelConverter> cadeia) {
        Schema esquema = cadeia.hasNext() ? cadeia.next().resolve(tipo, contexto, cadeia) : null;
        if (esquema == null || tipo.getCtxAnnotations() == null || esquema.get$ref() != null) {
            return esquema;
        }
        for (Annotation a : tipo.getCtxAnnotations()) {
            if (a instanceof Digits d) {
                BigDecimal passo = BigDecimal.ONE.movePointLeft(d.fraction());
                BigDecimal maximo = BigDecimal.TEN.pow(d.integer()).subtract(passo);
                esquema.setMultipleOf(passo);
                esquema.setMaximum(maximo);
                if (esquema.getMinimum() == null && esquema.getExclusiveMinimumValue() == null) {
                    esquema.setMinimum(maximo.negate());
                }
            }
        }
        for (Annotation a : tipo.getCtxAnnotations()) {
            if (a instanceof Positive) {
                esquema.setMinimum(null);
                esquema.setExclusiveMinimumValue(BigDecimal.ZERO);
            } else if (a instanceof PositiveOrZero) {
                esquema.setExclusiveMinimumValue(null);
                esquema.setMinimum(BigDecimal.ZERO);
            }
        }
        return esquema;
    }
}
