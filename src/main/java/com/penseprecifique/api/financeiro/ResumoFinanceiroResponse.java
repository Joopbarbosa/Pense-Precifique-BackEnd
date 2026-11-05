package com.penseprecifique.api.financeiro;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * V0.19.0 (#1176, RN-NOVA-17 a 23) — resposta única do resumo: indicadores, gráficos e tabelas alternativas saem dos
 * mesmos números. Dinheiro sempre com 2 casas; percentual com 1.
 */
public record ResumoFinanceiroResponse(
        String mes, LocalDate de, LocalDate ate, LocalDate hoje,
        BigDecimal saldoInicial, boolean saldoInicialInformado, String avisoSaldoInicial,
        BigDecimal entradasRealizadas, BigDecimal saidasRealizadas, BigDecimal movimentoPeriodo, BigDecimal saldoFinal,
        Pendencia aReceber, Pendencia aPagar, PrevistoRealizado previstoRealizado,
        boolean semMovimentos, String mensagemSemMovimentos,
        ProximosVencimentos proximosVencimentos,
        List<Semana> entrouSaiu, Projecao projecao, List<CategoriaDespesa> maioresDespesas) {

    public record Pendencia(BigDecimal emAberto, BigDecimal vencido) {}

    public record PrevistoRealizado(BigDecimal previstoEntradas, BigDecimal realizadoEntradas,
                                    BigDecimal previstoSaidas, BigDecimal realizadoSaidas) {}

    public record ProximosVencimentos(List<ParcelaFinanceiraResponse> itens, long total, boolean temMais, String mensagemVazio) {}

    /** Semana do mês em faixas fixas de dias (1 a 7, 8 a 14, 15 a 21, 22 a 28, 29 ao fim). */
    public record Semana(int numero, String rotulo, LocalDate de, LocalDate ate,
                         BigDecimal entradas, BigDecimal saidas, BigDecimal saldoAcumulado) {}

    public record Projecao(BigDecimal saldoAtual, List<Marco> marcos, List<Ponto> serie, boolean semDados, String mensagemSemDados) {}

    public record Marco(int dias, LocalDate data, BigDecimal saldo) {}

    public record Ponto(LocalDate data, BigDecimal saldo) {}

    /** {@code outras} = linha «Outras categorias» (soma das demais além das cinco maiores). */
    public record CategoriaDespesa(UUID categoriaId, String nome, BigDecimal valor, BigDecimal percentual, boolean outras) {}
}
