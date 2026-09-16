package com.penseprecifique.api.shared.dto.request.produto;

import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProdutoRequest {

    @NotBlank(message = "O nome do produto é obrigatório")
    private String nome;

    @NotNull(message = "O tipo é obrigatório")
    private TipoProduto tipo;

    private String descricao;

    @NotNull(message = "O tempo de produção é obrigatório")
    @Min(value = 1, message = "O tempo de produção deve ser pelo menos 1 minuto")
    private Integer tempoProducao;

    private BigDecimal precoVenda;

    private BigDecimal margemLucro;

    private BigDecimal rendimento;

    private BigDecimal estoqueAtual;

    private BigDecimal estoqueMinimo;

    private Boolean permitirEstoqueNegativo;

    /**
     * RN-NOVA-2 (V0.10.0, #299, altera PDT-016) — valor exibido/editável de "produto fracionável".
     * Ausente ou igual ao valor derivado da ficha técnica = sem override (segue derivando ao vivo).
     * Diferente do derivado = override manual, congela até nova edição explícita.
     */
    private Boolean fracionavel;

    @NotNull
    @Valid
    private List<FichaTecnicaItemRequest> fichaTecnica = new ArrayList<>();

    /**
     * #489 — campos fiscais mínimos (RN-NOVA-12/13), todos opcionais. Preparação para emissão
     * futura de NFC-e/NF-e — sem cálculo de imposto nesta versão.
     */
    @Pattern(regexp = "\\d{8}|\\d{12}|\\d{13}|\\d{14}", message = "Código de barras deve ter 8, 12, 13 ou 14 dígitos")
    private String codigoBarras;

    @Pattern(regexp = "\\d{8}", message = "NCM deve ter 8 dígitos")
    private String ncm;

    @Pattern(regexp = "\\d{4}", message = "CFOP deve ter 4 dígitos")
    private String cfop;

    private String cest;

    private String unidadeComercial;

    /** RN-NOVA-13 — validado contra a lista fechada em ProdutoService, não aqui (mensagem de
     * negócio própria, ver CEN-NOVO-13). */
    private String csosn;
}
