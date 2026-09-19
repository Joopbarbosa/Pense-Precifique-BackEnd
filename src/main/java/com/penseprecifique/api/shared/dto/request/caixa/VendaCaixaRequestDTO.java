package com.penseprecifique.api.shared.dto.request.caixa;

import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * #487 — RN-NOVA-1/3/7. {@code clienteId} opcional (venda de balcão comum sem identificar
 * cliente). {@code confirmarEstoqueNegativoProdutoIds} — mesmo padrão de confirmação de
 * Orçamento/Produção (RN-NOVA-2): reenvio confirmando os produtos listados na resposta de aviso.
 */
public record VendaCaixaRequestDTO(
        UUID clienteId,

        @NotEmpty(message = "A venda precisa de pelo menos 1 item")
        @Valid
        List<VendaCaixaItemRequestDTO> itens,

        TipoDesconto descontoTipo,

        @DecimalMin(value = "0", message = "O desconto não pode ser negativo")
        BigDecimal descontoValor,

        @NotEmpty(message = "A venda precisa de pelo menos 1 forma de pagamento")
        @Valid
        List<VendaCaixaPagamentoRequestDTO> pagamentos,

        List<UUID> confirmarEstoqueNegativoProdutoIds
) {}
