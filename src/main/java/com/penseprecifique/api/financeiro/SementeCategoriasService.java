package com.penseprecifique.api.financeiro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * V0.19.0 (RN-NOVA-23, DT-NOVA-7) — semeia as categorias padrão da usuária em transação própria (REQUIRES_NEW), para que
 * a semente não dependa nem desfaça com a transação de quem chamou. Idempotente: duas chamadas simultâneas não duplicam.
 * O resumo financeiro nunca chama este serviço (só leitura).
 */
@Service
@RequiredArgsConstructor
public class SementeCategoriasService {

    public static final String INSUMOS = "Insumos e materiais";
    public static final String OUTROS = "Outros";
    static final List<String> PADRAO = List.of(INSUMOS, "Embalagem", "Frete", "Aluguel", "Contas (água, luz, internet)", OUTROS);

    private final CategoriaFinanceiraRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void garantir(UUID usuarioId) {
        for (String nome : PADRAO) {
            repository.semear(UUID.randomUUID(), usuarioId, nome, NomeCategoria.normalizar(nome));
        }
    }
}
