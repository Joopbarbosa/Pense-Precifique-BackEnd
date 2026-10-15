package com.penseprecifique.api.shared.dto.response.compra;

import org.springframework.data.domain.Page;
import java.math.BigDecimal;

public record VendasCmvResponse(Page<VendaCmvResponse> vendas, BigDecimal totalCustoMaterial) {}
