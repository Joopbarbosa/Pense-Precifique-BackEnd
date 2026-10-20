package com.penseprecifique.api.infra.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * #801 e #806 — política geral de entrada JSON, no lugar de tratar campo a campo:
 * (a) elemento {@code null} dentro de lista é recusado com 400 (antes: NullPointerException e 500 nos serviços; #815 trocou
 * o descarte silencioso por recusa, que o contrato representa sem ambiguidade);
 * (b) {@code null} num campo primitivo vira o valor padrão (false, 0) em vez de recusar o corpo (o contrato
 * já diz que campo opcional aceita nulo);
 * (c) #1016 — campo de texto recusa número e booleano com 400 (antes: {"nome": false} gravava o texto "false", coerção silenciosa).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer politicaDeEntradaJson() {
        return builder -> {
            builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            builder.withCoercionConfig(String.class, c -> c
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
            // #1016 — campo booleano recusa texto e número (\"false\", 0, 1): antes virava false/true em silêncio
            builder.withCoercionConfig(tools.jackson.databind.type.LogicalType.Boolean, c -> c
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
            for (Class<?> tipo : new Class<?>[] {List.class, Set.class, Collection.class}) {
                builder.withConfigOverride(tipo, o -> o.setNullHandling(JsonSetter.Value.forContentNulls(Nulls.FAIL)));
            }
        };
    }
}
