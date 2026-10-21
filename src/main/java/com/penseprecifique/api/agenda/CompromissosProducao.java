package com.penseprecifique.api.agenda;

import com.penseprecifique.api.orcamento.EntregaEstimadaCalculadora;
import com.penseprecifique.api.orcamento.OrcamentoProducaoRepository;
import com.penseprecifique.api.producao.HistoricoStatusProducaoRepository;
import com.penseprecifique.api.producao.ProducaoProdutoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoProducao;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.18.0 (#1036, DT-NOVA-1) — compromissos da produção: início previsto e término previsto (RN-NOVA-16), com o aviso
 * de estouro da entrega prometida dos orçamentos vinculados (RN-NOVA-17). Calculados a cada consulta; o compromisso
 * sai quando a produção começa (início) ou é finalizada, cancelada ou marcada como não realizada (término).
 */
@Component
@RequiredArgsConstructor
public class CompromissosProducao implements FonteCompromissos {

    private static final Set<EstadoProducao> EM_ABERTO =
            Set.of(EstadoProducao.AGUARDANDO_INICIO, EstadoProducao.EM_ANDAMENTO, EstadoProducao.TRAVADA);
    private static final Set<StatusOrcamento> ORCAMENTO_ENCERRADO = Set.of(StatusOrcamento.ENTREGUE, StatusOrcamento.CANCELADO);
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final ProducaoRepository producaoRepository;
    private final ProducaoProdutoRepository producaoProdutoRepository;
    private final HistoricoStatusProducaoRepository historicoRepository;
    private final OrcamentoProducaoRepository orcamentoProducaoRepository;
    private final EntregaEstimadaCalculadora entregaEstimadaCalculadora;

    @Override
    public List<CompromissoBase> listar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<Producao> producoes = producaoRepository.findEmAberto(usuarioId, EM_ABERTO);
        if (producoes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = producoes.stream().map(Producao::getId).toList();
        Map<UUID, List<ProducaoProduto>> produtos = producaoProdutoRepository.findComProdutoByProducaoIdIn(ids).stream()
                .collect(Collectors.groupingBy(pp -> pp.getProducao().getId()));
        Set<UUID> jaComecaram = Set.copyOf(historicoRepository.findIdsQueJaEstiveramEmAndamento(ids));
        Map<UUID, List<OrcamentoProducao>> vinculos = orcamentoProducaoRepository.findByProducaoIdIn(ids).stream()
                .collect(Collectors.groupingBy(v -> v.getProducao().getId()));

        List<CompromissoBase> resultado = new ArrayList<>();
        for (Producao p : producoes) {
            String nome = nomeDosProdutos(produtos.getOrDefault(p.getId(), List.of()));
            String sufixo = nome.isEmpty() ? "" : " (" + nome + ")";
            String identificador = IdentificadorFormatter.formatar("PRD", p.getNumero());
            List<String> marcacoes = p.getEstado() == EstadoProducao.TRAVADA ? List.of("Travada") : List.of();

            // RN-NOVA-16 — o início continua enquanto a produção ainda não começou de fato: aguardando início, ou travada
            // pelo sistema ao tentar iniciar sem nunca ter estado em andamento.
            boolean aindaNaoComecou = p.getEstado() == EstadoProducao.AGUARDANDO_INICIO
                    || (p.getEstado() == EstadoProducao.TRAVADA && !jaComecaram.contains(p.getId()));
            if (p.getDataInicio() != null && aindaNaoComecou) {
                incluir(resultado, base(p, TipoCompromisso.INICIO_PRODUCAO, "Início previsto — " + identificador + sufixo,
                        p.getDataInicio(), marcacoes, List.of()), de, ate, hoje);
            }
            if (p.getDataTerminoPrevista() != null) {
                incluir(resultado, base(p, TipoCompromisso.TERMINO_PRODUCAO, "Término previsto — " + identificador + sufixo,
                        p.getDataTerminoPrevista(), marcacoes, avisoDeEstouro(p, vinculos.getOrDefault(p.getId(), List.of()))), de, ate, hoje);
            }
        }
        return resultado;
    }

    private void incluir(List<CompromissoBase> destino, CompromissoBase c, LocalDate de, LocalDate ate, LocalDate hoje) {
        if (relevante(c, de, ate, hoje)) {
            destino.add(c);
        }
    }

    /**
     * RN-NOVA-16 — primeiro produto em ordem alfabética do nome (desempate: marca, depois o cadastrado primeiro) e
     * "+N produtos" para os demais; "Nome — Marca" quando o produto tem marca (PDT-026).
     */
    static String nomeDosProdutos(List<ProducaoProduto> itens) {
        if (itens.isEmpty()) {
            return "";
        }
        List<Produto> ordenados = itens.stream().map(ProducaoProduto::getProduto)
                .sorted(Comparator.comparing((Produto p) -> p.getNome().toLowerCase(java.util.Locale.ROOT))
                        .thenComparing(p -> p.getMarca() == null ? "" : p.getMarca().toLowerCase(java.util.Locale.ROOT))
                        .thenComparing(p -> p.getNumero() == null ? 0 : p.getNumero()))
                .toList();
        Produto primeiro = ordenados.get(0);
        String nome = primeiro.getMarca() == null || primeiro.getMarca().isBlank()
                ? primeiro.getNome() : primeiro.getNome() + " — " + primeiro.getMarca();
        int outros = ordenados.size() - 1;
        return outros == 0 ? nome : nome + ", +" + outros + (outros == 1 ? " produto" : " produtos");
    }

    /**
     * RN-NOVA-17 — o término previsto posterior à entrega estimada de um orçamento vinculado (ainda em aberto) é
     * estouro. Um orçamento: "Passa da entrega prometida (dd/mm)"; produção ligada a mais de um orçamento: a lista dos
     * que estouram, por data de entrega e, no empate, pelo número do orçamento.
     */
    private List<String> avisoDeEstouro(Producao p, List<OrcamentoProducao> vinculos) {
        if (p.getDataTerminoPrevista() == null) {
            return List.of();
        }
        record Estouro(String identificador, int numero, LocalDate entrega) {}
        List<Estouro> estouros = new ArrayList<>();
        for (OrcamentoProducao v : vinculos) {
            Orcamento o = v.getOrcamento();
            if (ORCAMENTO_ENCERRADO.contains(o.getStatus())) {
                continue;
            }
            LocalDate entrega = entregaEstimadaCalculadora.calcular(o);
            if (entrega != null && p.getDataTerminoPrevista().isAfter(entrega)) {
                estouros.add(new Estouro(IdentificadorFormatter.formatar("ORC", o.getNumero()), o.getNumero() == null ? 0 : o.getNumero(), entrega));
            }
        }
        if (estouros.isEmpty()) {
            return List.of();
        }
        estouros.sort(Comparator.comparing(Estouro::entrega).thenComparingInt(Estouro::numero));
        if (vinculos.size() == 1) {
            return List.of("Passa da entrega prometida (" + estouros.get(0).entrega().format(DIA_MES) + ")");
        }
        return List.of("Passa da entrega prometida: " + estouros.stream()
                .map(e -> e.identificador() + " (" + e.entrega().format(DIA_MES) + ")")
                .collect(Collectors.joining(", ")));
    }

    private CompromissoBase base(Producao p, TipoCompromisso tipo, String titulo, LocalDate data, List<String> marcacoes, List<String> avisos) {
        return new CompromissoBase(tipo.name() + ":" + p.getId(), tipo, SituacaoCompromisso.PENDENTE, titulo, data, null,
                marcacoes, avisos, p.getId(), IdentificadorFormatter.formatar("PRD", p.getNumero()),
                p.getNumero() == null ? 0 : p.getNumero(),
                null, p.getCreatedAt() == null ? null : p.getCreatedAt().toInstant(ZoneOffset.UTC), null, false);
    }
}
