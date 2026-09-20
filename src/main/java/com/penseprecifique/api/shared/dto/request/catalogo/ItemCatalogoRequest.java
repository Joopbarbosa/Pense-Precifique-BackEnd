package com.penseprecifique.api.shared.dto.request.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ItemCatalogoRequest {

    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    /** RN-NOVA-1 — pelo menos 1 componente (Produto, Customização ou Insumo). */
    @NotEmpty(message = "É preciso pelo menos 1 componente")
    @Valid
    private List<ItemCatalogoComponenteRequest> componentes;

    /** RN-NOVA-2 — mesmo conceito de Produto.tempoProducao. */
    @NotNull(message = "O tempo de produção é obrigatório")
    @Min(value = 0, message = "O tempo de produção não pode ser negativo")
    private Integer tempoProducao;

    /** RN-NOVA-3 — margem própria do item. Mesmo campo opcional a nível de DTO que
     * {@code ProdutoRequest.margemLucro} — pré-preenchimento com a margem padrão de Configurações é
     * responsabilidade do Frontend, não do Backend (mesmo padrão já usado em Produto). */
    private BigDecimal margemLucro;

    /** Se nulo, o Service usa o precoSugerido calculado; se preenchido e diferente, aciona override
     * (mesmo modelo calculado+override de Produto, PDT-005). */
    private BigDecimal precoVenda;

    /** RN-NOVA-7 — texto opcional, também exibido no PDF do catálogo (#519). */
    @Size(max = 150, message = "A descrição não pode ter mais de 150 caracteres")
    private String descricao;
}
