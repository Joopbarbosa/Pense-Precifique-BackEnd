package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixaItemComponente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendaCaixaItemComponenteRepository extends JpaRepository<VendaCaixaItemComponente, UUID> {

    List<VendaCaixaItemComponente> findByVendaCaixaItemId(UUID vendaCaixaItemId);

    List<VendaCaixaItemComponente> findByVendaCaixaItemIdIn(List<UUID> vendaCaixaItemIds);
}
