package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.LoteCompra;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.dto.request.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.dto.response.ImpactoAgregadoResponseDTO;
import com.penseprecifique.api.dto.response.InsumoImpactoResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public interface LoteCompraService {

    ImpactoAgregadoResponseDTO registrarLote(RegistrarLoteCompraRequestDTO request);

    LoteCompra criarLote(Usuario usuario, LocalDateTime dataCompra);

    InsumoImpactoResponseDTO registrarCompraIndividual(
            Insumo insumo, BigDecimal quantidade, BigDecimal precoTotal, UUID loteCompraId);
}
