package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendaCaixaItemRepository extends JpaRepository<VendaCaixaItem, UUID> {

    List<VendaCaixaItem> findByVendaCaixaId(UUID vendaCaixaId);
}
