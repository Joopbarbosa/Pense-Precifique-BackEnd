package com.penseprecifique.api.produto;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * #672/DT-NOVA-6 — liga a mudança do preço sugerido de um produto aos insumos cujo custo a provocou (só nas causas de
 * compra). Um produto alcançado por dois insumos da mesma compra tem uma mudança com dois vínculos.
 */
@Entity
@Table(name = "mudancas_preco_sugerido_insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MudancaPrecoSugeridoInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mudanca_id", nullable = false)
    private MudancaPrecoSugerido mudanca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;
}
