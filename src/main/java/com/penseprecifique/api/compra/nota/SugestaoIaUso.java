package com.penseprecifique.api.compra.nota;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * V0.16.0 (#681, DT-NOVA-11) — limite técnico de sugestões de insumo por IA por conta e mês
 * ({@code SUGESTAO_IA_LIMITE_MENSAL_POR_CONTA}, 200 por padrão), fora do limite de leituras do
 * leitor-fiscal. Reserva atômica: trava a linha do mês e concede só o que cabe; o mês seguinte começa
 * do zero (linha nova).
 */
@Component
public class SugestaoIaUso {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final NamedParameterJdbcTemplate jdbc;
    private final int limiteMensal;

    public SugestaoIaUso(NamedParameterJdbcTemplate jdbc,
                         @Value("${sugestao-ia.limite-mensal-por-conta:200}") int limiteMensal) {
        this.jdbc = jdbc;
        this.limiteMensal = limiteMensal;
    }

    /** Reserva até {@code pedidas} sugestões no mês atual; devolve quantas cabem (0 quando o limite acabou). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reservar(UUID usuarioId, int pedidas) {
        return reservar(usuarioId, pedidas, LocalDate.now(FUSO));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reservar(UUID usuarioId, int pedidas, LocalDate hoje) {
        if (pedidas <= 0) {
            return 0;
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("usuario", usuarioId)
                .addValue("mes", hoje.withDayOfMonth(1));
        jdbc.update("INSERT INTO sugestoes_ia_uso_mensal (usuario_id, mes, quantidade) VALUES (:usuario, :mes, 0) "
                + "ON CONFLICT (usuario_id, mes) DO NOTHING", p);
        Integer usadas = jdbc.queryForObject("SELECT quantidade FROM sugestoes_ia_uso_mensal "
                + "WHERE usuario_id = :usuario AND mes = :mes FOR UPDATE", p, Integer.class);
        int concedidas = Math.max(0, Math.min(pedidas, limiteMensal - (usadas == null ? 0 : usadas)));
        if (concedidas > 0) {
            jdbc.update("UPDATE sugestoes_ia_uso_mensal SET quantidade = quantidade + :q "
                    + "WHERE usuario_id = :usuario AND mes = :mes", p.addValue("q", concedidas));
        }
        return concedidas;
    }
}
