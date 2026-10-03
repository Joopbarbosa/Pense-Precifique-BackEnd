package com.penseprecifique.api.shared.domain.entity;

import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private String nome;

    @Column
    private String email;

    @Column(length = 20)
    private String whatsapp;

    // #536 (V0.15.0)
    @Column(length = 20)
    private String telefone;

    @Column
    private String site;

    // #536/RN-NOVA-1 — papéis; CHECK chk_cliente_papel exige pelo menos um.
    @Column(name = "eh_cliente", nullable = false)
    @Builder.Default
    private Boolean ehCliente = true;

    @Column(name = "eh_fornecedor", nullable = false)
    @Builder.Default
    private Boolean ehFornecedor = false;

    // #536/RN-NOVA-17 — documento guardado normalizado (sem máscara, maiúsculo).
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 20)
    @Builder.Default
    private TipoPessoa tipoPessoa = TipoPessoa.FISICA;

    @Column(length = 30)
    private String documento;

    @Column(columnDefinition = "TEXT")
    private String endereco;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // #538/RN-NOVA-2 (V0.15.0) — inativar é reversível e só alterna este campo; a coluna
    // deleted_at foi removida (V58), não existe excluir cadastro.
    @Column(name = "ativa", nullable = false)
    @Builder.Default
    private Boolean ativa = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
