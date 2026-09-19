package com.penseprecifique.api.shared.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.UUID;

/**
 * #488 (V0.12.0) — horário de funcionamento de um dia da semana da empresa. Abrir o caixa fora da
 * faixa configurada gera aviso, nunca bloqueio.
 *
 * <p>Tabela filha em vez de 14 colunas em {@link Empresa}: {@code EmpresaResponseDTO} alimenta o
 * payload do microsserviço de PDF, e uma lista aninhada mantém esse payload legível.
 */
@Entity
@Table(name = "empresa_horarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpresaHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    /** 1=segunda ... 7=domingo — mesmo valor de {@link java.time.DayOfWeek#getValue()} (ISO-8601).
     *
     * <p>Coluna é SMALLINT (faixa 1–7 não justifica 4 bytes), mas o tipo Java continua Integer para
     * não contaminar DTOs e chamadas com {@code Short}; {@code @JdbcTypeCode} concilia os dois — sem
     * ele a validação de schema do Hibernate reprova (int2 encontrado, integer esperado). */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    @Column(nullable = false)
    @Builder.Default
    private Boolean fechado = false;

    /** Nulo somente quando {@code fechado = true} (CHECK no banco garante o par). */
    @Column(name = "hora_abertura")
    private LocalTime horaAbertura;

    @Column(name = "hora_fechamento")
    private LocalTime horaFechamento;
}
