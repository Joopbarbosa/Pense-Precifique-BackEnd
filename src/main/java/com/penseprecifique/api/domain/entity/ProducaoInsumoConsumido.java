package com.penseprecifique.api.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "producao_insumos_consumidos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProducaoInsumoConsumido {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producao_id", nullable = false)
    private Producao producao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(name = "quantidade", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantidade;
}
