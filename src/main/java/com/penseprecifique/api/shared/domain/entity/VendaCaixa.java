package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * #487 — venda direta de balcão (Caixa/PDV, Epic #416). Numeração `CX-N` herda RN-053
 * integralmente (DT-NOVA-5). Diferente de {@link Orcamento} (máquina de 9 status), nasce sempre
 * `CONCLUIDA` — sem rascunho (RN-NOVA-10); só cancelável enquanto o {@link CaixaTurno} ainda está
 * `ABERTO` (RN-NOVA-4), nunca editável (RN-NOVA-11).
 */
@Entity
@Table(name = "venda_caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendaCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    /** Opcional — venda de balcão comumente não identifica cliente (achado de pesquisa de mercado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda;

    /** FK real, não referência solta — sempre 1 turno (DT-NOVA-2). RN-NOVA-5. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_turno_id", nullable = false)
    private CaixaTurno caixaTurno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVendaCaixa status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "desconto_tipo")
    private TipoDesconto descontoTipo;

    @Column(name = "desconto_valor", precision = 15, scale = 2)
    private BigDecimal descontoValor;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** RN-NOVA-7 — soma dos pagamentos − total; só existe quando houve troco em dinheiro. */
    @Column(precision = 15, scale = 2)
    private BigDecimal troco;

    /** Obrigatório só quando status = CANCELADA (RN-NOVA-4). */
    @Column(name = "cancelamento_motivo", columnDefinition = "TEXT")
    private String cancelamentoMotivo;

    /** #487 (V0.12.0) — escolha da usuária ao cancelar: o estoque da venda voltou ou não.
     * Nulo em venda não cancelada e nas canceladas antes desta versão, quando devolver era o
     * único comportamento possível. */
    @Column(name = "estoque_retornado")
    private Boolean estoqueRetornado;
}
