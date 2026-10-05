package com.penseprecifique.api.shared.dto.response.compra;

import com.penseprecifique.api.shared.domain.enums.TipoVendaCmv;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendaCmvResponse(UUID id, TipoVendaCmv tipo, String identificador, LocalDate data,
                               String cliente, String itens, BigDecimal faturamento, BigDecimal custoMaterial,
                               BigDecimal cmvPercentual, boolean estimado, boolean semCusto) {}
