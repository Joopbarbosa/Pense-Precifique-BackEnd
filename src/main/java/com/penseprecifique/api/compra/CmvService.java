package com.penseprecifique.api.compra;

import com.penseprecifique.api.dashboard.VendasService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * #577/RN-NOVA-27 (V0.15.0, DT-NOVA-18) — CMV do dashboard de Compras. #615 (V0.17.0, DT-NOVA-8): as vendas e o custo de
 * cada uma passaram a vir do serviço único {@link VendasService}; este serviço só delega, mantendo o resultado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CmvService {

    private final VendasService vendasService;

    public List<VendasService.Venda> vendas(UUID usuarioId, LocalDate de, LocalDate ate) {
        return vendasService.vendas(usuarioId, de, ate);
    }
}
