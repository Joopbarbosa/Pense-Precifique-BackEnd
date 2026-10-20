package com.penseprecifique.api.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * #999 (RN-NOVA-35) — relógio de Brasília, único para gravar a data da venda (entrega do orçamento e venda do Caixa) e para
 * consultar o "mês atual" da Início. Assim a venda perto da meia-noite cai no mesmo mês de Brasília mesmo com a JVM em
 * outro fuso (ex.: CI em UTC). Os testes trocam este bean por um relógio fixo.
 */
@Configuration
public class RelogioConfig {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock relogioBrasilia() {
        return Clock.system(FUSO);
    }
}
