package com.penseprecifique.api.infra.config;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * #801 e #806 — política geral de entrada JSON, no lugar de tratar campo a campo:
 * (a) elemento {@code null} dentro de lista é descartado (antes: NullPointerException e 500 nos serviços);
 * (b) {@code null} num campo primitivo vira o valor padrão (false, 0) em vez de recusar o corpo (o contrato
 * já diz que campo opcional aceita nulo).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer politicaDeEntradaJson() {
        return builder -> {
            builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
            for (Class<?> tipo : new Class<?>[] {List.class, Set.class, Collection.class}) {
                builder.withConfigOverride(tipo, o -> o.setNullHandling(JsonSetter.Value.forContentNulls(Nulls.SKIP)));
            }
        };
    }
}
