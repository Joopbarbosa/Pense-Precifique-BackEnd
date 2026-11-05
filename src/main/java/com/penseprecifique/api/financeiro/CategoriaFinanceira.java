package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.shared.domain.entity.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** V0.19.0 (#1177, RN-NOVA-23) — categoria de despesa da usuária. Semeada não se renomeia nem se exclui; criada só se inativa. */
@Entity
@Table(name = "categorias_financeiras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, length = 40)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false, length = 40)
    private String nomeNormalizado;

    @Column(nullable = false, updatable = false)
    private boolean semeada;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (createdAt == null) createdAt = agora;
        if (updatedAt == null) updatedAt = agora;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
