package com.penseprecifique.api.caixa;

import com.penseprecifique.api.shared.domain.entity.VendaCaixaItemCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendaCaixaItemCustomizacaoRepository extends JpaRepository<VendaCaixaItemCustomizacao, UUID> {

    List<VendaCaixaItemCustomizacao> findByVendaCaixaItemId(UUID vendaCaixaItemId);

    List<VendaCaixaItemCustomizacao> findByVendaCaixaItemIdIn(List<UUID> vendaCaixaItemIds);
}
