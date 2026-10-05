package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.Escolha;
import com.penseprecifique.api.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * V0.16.0 (#683, RN-NOVA-11 e RN-NOVA-15) — monta as linhas do rascunho a partir da nota lida e das
 * escolhas da artesã. Função pura, sem banco: valores de cada desconto entram uma única vez nos campos
 * de COM-003 (preço cheio e desconto da linha; desconto geral como desconto da nota).
 *
 * <ul>
 *   <li>Linha com desconto do item: cheio = valor bruto, desconto da linha = desconto do item.</li>
 *   <li>Linha sem desconto do item: cheio = valor final do item.</li>
 *   <li>Quantidade da compra = quantidade da nota × fator, em 4 casas.</li>
 *   <li>Itens do mesmo insumo viram uma linha (soma de quantidade, cheio e desconto de linha).</li>
 *   <li>Desconto da nota lançado = desconto geral × (soma dos valores finais mantidos ÷ soma de todos).</li>
 *   <li>Conferência: soma de todos os valores finais − desconto geral + acréscimos contra o total pago.</li>
 * </ul>
 */
final class MontagemRascunhoNota {

    private static final BigDecimal CENTAVO = new BigDecimal("0.01");

    private MontagemRascunhoNota() {
    }

    record Linha(UUID insumoId, BigDecimal quantidade, BigDecimal precoCheio, BigDecimal descontoLinha, List<Integer> posicoes) {}

    /** {@code diferenca} = total pago − valor calculado; zero (ou menos de 1 centavo) quando fecha. */
    record Resultado(List<Linha> linhas, BigDecimal descontoNota, BigDecimal acrescimos, BigDecimal diferenca) {}

    static Resultado montar(NotaLida nota, List<Escolha> escolhas) {
        List<NotaLida.Item> itens = nota.itensOuVazio();
        Map<Integer, Escolha> porPosicao = indexar(itens.size(), escolhas);

        BigDecimal somaTodos = BigDecimal.ZERO;
        BigDecimal somaMantidos = BigDecimal.ZERO;
        Map<UUID, Linha> linhas = new LinkedHashMap<>();
        for (int i = 0; i < itens.size(); i++) {
            NotaLida.Item item = itens.get(i);
            BigDecimal valorFinal = nz(item.valorFinal());
            somaTodos = somaTodos.add(valorFinal);
            Escolha escolha = porPosicao.get(i);
            if (escolha.ignorado()) {
                continue;
            }
            somaMantidos = somaMantidos.add(valorFinal);
            exigirLigacao(escolha, i);
            BigDecimal quantidade = nz(item.quantidade()).multiply(escolha.fator()).setScale(4, RoundingMode.HALF_UP);
            boolean comDesconto = item.desconto() != null && item.desconto().signum() > 0 && item.valorBruto() != null;
            BigDecimal cheio = comDesconto ? item.valorBruto() : valorFinal;
            BigDecimal descontoLinha = comDesconto ? item.desconto() : BigDecimal.ZERO;
            Linha atual = linhas.get(escolha.insumoId());
            if (atual == null) {
                linhas.put(escolha.insumoId(), new Linha(escolha.insumoId(), quantidade, cheio, descontoLinha, List.of(i)));
            } else {
                List<Integer> posicoes = new ArrayList<>(atual.posicoes());
                posicoes.add(i);
                linhas.put(escolha.insumoId(), new Linha(escolha.insumoId(), atual.quantidade().add(quantidade),
                        atual.precoCheio().add(cheio), atual.descontoLinha().add(descontoLinha), List.copyOf(posicoes)));
            }
        }
        if (linhas.isEmpty()) {
            throw BusinessException.explicado("Todos os itens ignorados", "Todos os itens da nota foram ignorados, então não há compra a gerar.",
                    "A compra precisa de pelo menos um item ligado a um insumo.",
                    "Ligue ao menos um item a um insumo, ou registre a compra à mão.");
        }

        BigDecimal descontoGeral = nz(nota.descontoGeral());
        BigDecimal descontoNota = BigDecimal.ZERO;
        if (descontoGeral.signum() > 0 && somaTodos.signum() > 0) {
            descontoNota = descontoGeral.multiply(somaMantidos).divide(somaTodos, 2, RoundingMode.HALF_UP);
        }
        BigDecimal acrescimos = nz(nota.acrescimos());
        BigDecimal calculado = somaTodos.subtract(descontoGeral).add(acrescimos);
        BigDecimal diferenca = nz(nota.totalPago()).subtract(calculado).setScale(2, RoundingMode.HALF_UP);
        return new Resultado(List.copyOf(linhas.values()), descontoNota, acrescimos, diferenca);
    }

    static boolean temDiferenca(Resultado resultado) {
        return resultado.diferenca().abs().compareTo(CENTAVO) >= 0;
    }

    /** Toda posição da nota precisa de uma escolha, sem repetir nem apontar para item que não existe. */
    private static Map<Integer, Escolha> indexar(int quantidadeItens, List<Escolha> escolhas) {
        Map<Integer, Escolha> porPosicao = new HashMap<>();
        Set<Integer> repetidas = new HashSet<>();
        for (Escolha e : escolhas) {
            if (e.posicao() >= quantidadeItens) {
                throw BusinessException.explicado("Item inexistente", "A escolha aponta para um item que não existe na nota.",
                        "A posição informada é maior que a quantidade de itens da nota.",
                        "Leia a nota de novo e refaça a conciliação.");
            }
            if (porPosicao.put(e.posicao(), e) != null) {
                repetidas.add(e.posicao());
            }
        }
        if (!repetidas.isEmpty()) {
            throw BusinessException.explicado("Item repetido", "Há mais de uma escolha para o mesmo item da nota.",
                    "Cada item da nota recebe uma única escolha.", "Revise a conciliação e tente de novo.");
        }
        List<String> faltando = new ArrayList<>();
        for (int i = 0; i < quantidadeItens; i++) {
            if (!porPosicao.containsKey(i)) {
                faltando.add("Item " + (i + 1) + " da nota");
            }
        }
        if (!faltando.isEmpty()) {
            throw BusinessException.explicado("Itens sem escolha", "Todo item da nota precisa estar ligado a um insumo ou marcado como ignorado.",
                    "O rascunho só nasce quando nenhum item fica sem destino.",
                    "Ligue cada item a um insumo ou marque como ignorado.").comItens(faltando);
        }
        return porPosicao;
    }

    private static void exigirLigacao(Escolha escolha, int posicao) {
        if (escolha.insumoId() == null) {
            throw BusinessException.explicado("Item sem insumo", "O item " + (posicao + 1) + " da nota não está ligado a um insumo.",
                    "Item não ignorado precisa de um insumo para entrar na compra.",
                    "Escolha o insumo do item ou marque como ignorado.");
        }
        if (escolha.fator() == null || escolha.fator().signum() <= 0) {
            throw BusinessException.explicado("Fator de conversão inválido", "O fator de conversão do item " + (posicao + 1) + " precisa ser maior que zero.",
                    "A quantidade da compra é a quantidade da nota vezes o fator; sem ele o custo ficaria errado.",
                    "Informe quantas unidades do insumo vêm em uma unidade da nota (ex.: pacote com 100 folhas, fator 100).");
        }
    }

    private static BigDecimal nz(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
