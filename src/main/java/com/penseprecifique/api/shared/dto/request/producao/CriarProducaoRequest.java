package com.penseprecifique.api.shared.dto.request.producao;

import com.penseprecifique.api.shared.validation.LimitesTexto;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CriarProducaoRequest {

    private LocalDate dataInicio;

    @NotNull(message = "A data de término prevista é obrigatória")
    private LocalDate dataTerminoPrevista;

    @Size(max = LimitesTexto.DESCRICAO_MAX, message = LimitesTexto.DESCRICAO_MAX_MENSAGEM) // #559/RN-NOVA-18
    private String observacoes;

    @NotEmpty(message = "Informe ao menos um produto")
    @Valid
    private List<ProducaoProdutoRequest> produtos;
}
