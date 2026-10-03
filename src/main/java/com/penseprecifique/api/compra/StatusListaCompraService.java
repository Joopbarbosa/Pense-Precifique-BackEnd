package com.penseprecifique.api.compra;

import com.penseprecifique.api.shared.domain.entity.ListaCompra;
import com.penseprecifique.api.shared.domain.enums.StatusListaCompra;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #596/RN-NOVA-41 (V0.15.0, DT-NOVA-25) — status automático da lista de compras. Separado de
 * {@link ListaCompraService} porque este depende de {@link CompraService} e a compra precisa avisar a
 * lista ao confirmar/cancelar (evita dependência circular).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StatusListaCompraService {

    private final ListaCompraItemRepository listaCompraItemRepository;
    private final CompraItemRepository compraItemRepository;
    private final ListaCompraRepository listaCompraRepository;

    /**
     * Chamado depois que uma compra criada a partir da lista é confirmada ou cancelada. Todos os
     * insumos da lista em compras CONFIRMADAS dela → COMPRADA; alguns → PARCIALMENTE_COMPRADA;
     * nenhum → GERADA. RASCUNHO e CANCELADA não mudam sozinhas.
     */
    public void recalcular(ListaCompra lista) {
        if (lista == null || lista.getStatus() == StatusListaCompra.RASCUNHO
                || lista.getStatus() == StatusListaCompra.CANCELADA) {
            return;
        }
        Set<UUID> daLista = listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()).stream()
                .map(i -> i.getInsumo().getId()).collect(Collectors.toSet());
        Set<UUID> comprados = new HashSet<>(compraItemRepository.findInsumosCompradosDaLista(lista.getId()));
        comprados.retainAll(daLista);
        StatusListaCompra novo = comprados.isEmpty() ? StatusListaCompra.GERADA
                : comprados.size() == daLista.size() ? StatusListaCompra.COMPRADA
                : StatusListaCompra.PARCIALMENTE_COMPRADA;
        lista.setStatus(novo);
        listaCompraRepository.save(lista);
    }
}
