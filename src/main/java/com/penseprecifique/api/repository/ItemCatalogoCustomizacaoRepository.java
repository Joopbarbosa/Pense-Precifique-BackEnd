package com.penseprecifique.api.repository;

import com.penseprecifique.api.domain.entity.ItemCatalogoCustomizacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemCatalogoCustomizacaoRepository extends JpaRepository<ItemCatalogoCustomizacao, UUID> {

    List<ItemCatalogoCustomizacao> findByItemCatalogoId(UUID itemCatalogoId);
}
