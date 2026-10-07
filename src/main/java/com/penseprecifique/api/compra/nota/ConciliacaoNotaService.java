package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.InsumoProposto;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.ItemConciliacao;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V0.16.0 (#681, RN-NOVA-12, RN-NOVA-13, RN-NOVA-26, DT-NOVA-11) — proposta de ligação de cada item da nota,
 * nesta ordem, parando no que resolver: vínculo salvo (usuária + CNPJ do emitente + nome normalizado),
 * vínculo de outro fornecedor com o mesmo nome de item (#718), casamento
 * por nome com insumos ATIVOS, sugestão por IA só para o que sobrou. Nada é gravado: a
 * artesã vê e confirma todos os itens (RN-NOVA-14); os vínculos são gravados na criação do rascunho.
 */
@Service
@RequiredArgsConstructor
public class ConciliacaoNotaService {

    static final String AVISO_INSUMO_INATIVO = "o insumo vinculado está inativo";
    static final String AVISO_LIMITE_IA = "O limite mensal de sugestões da IA foi atingido; os itens sem ligação ficam para você escolher.";

    private final VinculoItemNotaRepository vinculoRepository;
    private final InsumoRepository insumoRepository;
    private final SugestaoInsumoIaClient sugestaoIaClient;
    private final SugestaoIaUso sugestaoIaUso;

    public record Resultado(List<ItemConciliacao> itens, List<String> avisos) {}

    public Resultado conciliar(UUID usuarioId, String cnpjEmitente, List<NotaLida.Item> lidos) {
        // #778 — sem ORDER BY o banco devolve na ordem que quiser; candidatos empatados saem por nome (e id), sempre iguais.
        List<Insumo> disponiveis = new ArrayList<>(insumoRepository.findByUsuarioIdAndAtivoTrueAndDeletedAtIsNull(usuarioId));
        disponiveis.sort(java.util.Comparator.comparing((Insumo i) -> i.getNome().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(Insumo::getId));
        Map<String, VinculoItemNota> vinculos = cnpjEmitente == null ? Map.of()
                : vinculoRepository.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizadoIn(usuarioId, cnpjEmitente,
                        lidos.stream().map(i -> CasamentoPorNome.normalizarChave(i.nome())).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(VinculoItemNota::getNomeItemNormalizado, Function.identity()));

        // #718 — nomes de item sem vínculo deste fornecedor: procurados nos vínculos de outros fornecedores.
        Set<String> semVinculo = lidos.stream().map(i -> CasamentoPorNome.normalizarChave(i.nome()))
                .filter(nome -> !vinculos.containsKey(nome)).collect(Collectors.toSet());
        Map<String, List<VinculoItemNota>> deOutros = semVinculo.isEmpty() ? Map.of()
                : vinculoRepository.findDeOutrosFornecedores(usuarioId, semVinculo, cnpjEmitente == null ? "" : cnpjEmitente)
                .stream().collect(Collectors.groupingBy(VinculoItemNota::getNomeItemNormalizado));

        List<ItemConciliacao> itens = new ArrayList<>();
        for (int i = 0; i < lidos.size(); i++) {
            String chave = CasamentoPorNome.normalizarChave(lidos.get(i).nome());
            itens.add(propor(i, lidos.get(i), vinculos.get(chave), deOutros.getOrDefault(chave, List.of()), disponiveis));
        }
        List<String> avisos = new ArrayList<>();
        sugerirPorIa(usuarioId, itens, disponiveis, avisos);
        return new Resultado(itens, avisos);
    }

    private ItemConciliacao propor(int posicao, NotaLida.Item item, VinculoItemNota vinculo,
                                   List<VinculoItemNota> deOutros, List<Insumo> disponiveis) {
        String aviso = null;
        if (vinculo != null) {
            if (Boolean.TRUE.equals(vinculo.getIgnorar())) {
                return item(posicao, item, OrigemLigacao.VINCULO_SALVO, null, null, true, List.of(), null);
            }
            Insumo vinculado = vinculo.getInsumo();
            if (Boolean.TRUE.equals(vinculado.getAtivo()) && vinculado.getDeletedAt() == null) {
                return item(posicao, item, OrigemLigacao.VINCULO_SALVO, proposto(vinculado), vinculo.getFator(), false, List.of(), null);
            }
            aviso = AVISO_INSUMO_INATIVO;
        }
        // #718 (RN-NOVA-26) — vínculo de outro fornecedor: um só insumo liga (fator do mais recente); insumos
        // diferentes não ligam sozinhos e viram candidatos. A lista já vem do mais recente para o mais antigo.
        List<Insumo> vinculadosOutros = deOutros.stream().map(VinculoItemNota::getInsumo).distinct().toList();
        if (vinculo == null && vinculadosOutros.size() == 1) {
            return item(posicao, item, OrigemLigacao.VINCULO_OUTRO_FORNECEDOR, proposto(vinculadosOutros.get(0)),
                    deOutros.get(0).getFator(), false, List.of(), null);
        }
        List<Insumo> candidatos = new ArrayList<>(vinculadosOutros);
        CasamentoPorNome.candidatos(item.nome(), disponiveis, Insumo::getNome, Insumo::getMarca).stream()
                .filter(c -> candidatos.stream().noneMatch(j -> j.getId().equals(c.getId()))).forEach(candidatos::add);
        // Vínculo para insumo inativo: nunca liga sozinho, mostra os candidatos (RN-NOVA-12, CEN-NOVO-59).
        if (candidatos.size() == 1 && aviso == null) {
            return item(posicao, item, OrigemLigacao.CASAMENTO_NOME, proposto(candidatos.get(0)), BigDecimal.ONE, false, List.of(), null);
        }
        return item(posicao, item, OrigemLigacao.SEM_LIGACAO, null, null, false,
                candidatos.stream().map(ConciliacaoNotaService::proposto).toList(), aviso);
    }

    /** Uma chamada por nota, só com os itens sem ligação; respeita o limite mensal por conta. */
    private void sugerirPorIa(UUID usuarioId, List<ItemConciliacao> itens, List<Insumo> disponiveis, List<String> avisos) {
        List<ItemConciliacao> semLigacao = itens.stream()
                .filter(i -> i.origemLigacao() == OrigemLigacao.SEM_LIGACAO && !i.ignorar()).toList();
        if (semLigacao.isEmpty() || !sugestaoIaClient.habilitada()) {
            return;
        }
        int concedidas = sugestaoIaUso.reservar(usuarioId, semLigacao.size());
        if (concedidas < semLigacao.size()) {
            avisos.add(AVISO_LIMITE_IA);
        }
        if (concedidas == 0) {
            return;
        }
        List<ItemConciliacao> pedidos = semLigacao.subList(0, concedidas);
        List<SugestaoInsumoIaClient.Candidato> candidatos = disponiveis.stream()
                .filter(ins -> pedidos.stream().anyMatch(p -> CasamentoPorNome.temPalavraEmComum(p.nome(), ins.getNome())))
                .limit(SugestaoInsumoIaClient.MAX_CANDIDATOS)
                .map(ins -> new SugestaoInsumoIaClient.Candidato(ins.getId(), nomeParaIa(ins)))
                .toList();
        List<SugestaoInsumoIaClient.Sugestao> sugestoes = sugestaoIaClient.sugerir(
                pedidos.stream().map(p -> new SugestaoInsumoIaClient.ItemPedido(p.posicao(), p.nome())).toList(),
                candidatos);
        Map<UUID, Insumo> porId = disponiveis.stream().collect(Collectors.toMap(Insumo::getId, Function.identity()));
        for (SugestaoInsumoIaClient.Sugestao s : sugestoes) {
            ItemConciliacao atual = itens.get(s.posicao());
            itens.set(s.posicao(), new ItemConciliacao(atual.posicao(), atual.nome(), atual.quantidade(), atual.valorFinal(),
                    atual.unidade(), OrigemLigacao.SUGESTAO_IA, proposto(porId.get(s.insumoId())), s.fator(), false,
                    atual.candidatos(), atual.aviso()));
        }
    }

    private static ItemConciliacao item(int posicao, NotaLida.Item item, OrigemLigacao origem, InsumoProposto insumo,
                                        BigDecimal fator, boolean ignorar, List<InsumoProposto> candidatos, String aviso) {
        return new ItemConciliacao(posicao, item.nome(), item.quantidade(), item.valorFinal(), item.unidade(),
                origem, insumo, fator, ignorar, candidatos, aviso);
    }

    static InsumoProposto proposto(Insumo insumo) {
        return new InsumoProposto(insumo.getId(), IdentificadorFormatter.formatar("INS", insumo.getNumero()),
                insumo.getNome(), insumo.getMarca(), unidade(insumo));
    }

    private static String nomeParaIa(Insumo insumo) {
        return StringUtils.hasText(insumo.getMarca()) ? insumo.getNome() + " " + insumo.getMarca() : insumo.getNome();
    }

    private static String unidade(Insumo insumo) {
        return insumo.getUnidadeMedida() != null ? insumo.getUnidadeMedida().getSigla() : null;
    }
}
