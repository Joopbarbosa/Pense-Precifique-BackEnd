package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraItemRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.produto.ProdutoService;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * #607/RN-NOVA-16 (V0.17.0, DT-NOVA-8 e 10) — Dashboard de Cadastros. Síncrono, sem cache, sobre a fonte única de vendas
 * ({@link VendasService}) e sobre as compras que já deram entrada (data de contagem = data de entrada). Desempate de todo ranking e da
 * curva ABC: nome (sem maiúscula e sem acento), depois marca (sem marca antes) e, por fim, número do cadastro — resultado sempre igual
 * para os mesmos dados.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardCadastrosService {

    /** V0.17.0 #971 (RN-NOVA-23): rankings mostram 10 itens; a lupa lista todos (campo {@code todos}). */
    private static final int TOP = 10;
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal OITENTA = new BigDecimal("80");
    private static final BigDecimal NOVENTA_E_CINCO = new BigDecimal("95");

    private final VendasService vendasService;
    private final CompraItemRepository compraItemRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final ProdutoService produtoService;
    private final UsuarioRepository usuarioRepository;

    /** Insumo/produto com os dados de desempate. */
    private record Chave(UUID id, String identificador, String nome, String marca, int numero) {}

    /** Nome sem maiúscula/acento, depois marca (sem marca antes) e número do cadastro. */
    private static final Comparator<Chave> ORDEM = Comparator
            .comparing((Chave c) -> normalizar(c.nome()))
            .thenComparing(c -> normalizar(c.marca()))
            .thenComparingInt(Chave::numero);

    public DashboardCadastrosResponse dashboard(LocalDate de, LocalDate ate, UUID insumoEvolucao) {
        UUID usuarioId = usuarioId();
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);

        List<CompraItem> compras = compraItemRepository.findPorStatus(usuarioId, StatusCompra.CONFIRMADA);
        List<CompraItem> comprasPeriodo = comprasEntre(compras, p.inicio(), p.fim());
        List<CompraItem> comprasAnterior = comprasEntre(compras, p.inicioAnterior(), p.fimAnterior());

        List<VendasService.Venda> vendas = vendasService.vendas(usuarioId, p.inicioGeral(), p.fim());
        List<VendasService.Venda> vendasPeriodo = vendas.stream().filter(v -> dentro(v.data(), p.inicio(), p.fim())).toList();
        List<VendasService.Venda> vendasAnterior = vendas.stream().filter(v -> dentro(v.data(), p.inicioAnterior(), p.fimAnterior())).toList();

        // 3 e 4 — insumo mais comprado (nº de compras) e em que mais gastamos (valor pago)
        Map<UUID, BigDecimal> compradas = contarCompras(comprasPeriodo);
        Map<UUID, BigDecimal> compradasAnt = contarCompras(comprasAnterior);
        Map<UUID, BigDecimal> gastos = somarGasto(comprasPeriodo);
        Map<UUID, BigDecimal> gastosAnt = somarGasto(comprasAnterior);
        Map<UUID, Chave> chavesInsumo = new HashMap<>();
        Stream.concat(comprasPeriodo.stream(), comprasAnterior.stream()).forEach(i -> chavesInsumo.putIfAbsent(i.getInsumo().getId(), chave(i.getInsumo())));

        // 5 e 6 — produto mais vendido (quantidade) e que dá mais lucro (faturamento − custo de material)
        Map<String, LinhaSoma> linhas = somarLinhas(vendasPeriodo);
        Map<String, LinhaSoma> linhasAnt = somarLinhas(vendasAnterior);

        Insumo escolhido = escolherInsumoDaEvolucao(usuarioId, insumoEvolucao, compradas, chavesInsumo);

        return new DashboardCadastrosResponse(p.inicio(), p.fim(), p.inicioAnterior(), p.fimAnterior(),
                evolucaoPreco(escolhido, compras, p.inicio(), p.fim()),
                insumosEmProdutos(usuarioId),
                rankingInsumos(compradas, compradasAnt, chavesInsumo, null),
                rankingInsumos(gastos, gastosAnt, chavesInsumo, null),
                rankingProdutos(linhas, linhasAnt, false),
                rankingProdutos(linhas, linhasAnt, true),
                precosAbaixoDoSugerido(usuarioId),
                margens(usuarioId),
                curvaAbc(linhas),
                variacoes(comprasPeriodo),
                numerosEstoque(usuarioId, vendasPeriodo, vendasAnterior));
    }

    /**
     * V0.17.0 #974 (RN-NOVA-25, CEN-NOVO-72 a 75): valor do estoque em material (custo médio pago) e produtos prontos (custo de produção
     * atual por unidade, 4 casas), potencial de venda, % de CMV e margem do período, e insumos abaixo do mínimo. Fotos de hoje, exceto CMV.
     */
    private DashboardCadastrosResponse.Estoque numerosEstoque(UUID usuarioId, List<VendasService.Venda> vendasPeriodo,
                                                              List<VendasService.Venda> vendasAnterior) {
        List<DashboardCadastrosResponse.LinhaEstoque> valorInsumos = new ArrayList<>();
        List<DashboardCadastrosResponse.LinhaMinimo> abaixoMinimo = new ArrayList<>();
        for (Insumo i : insumoRepository.findByUsuarioIdAndAtivoTrueAndDeletedAtIsNull(usuarioId)) {
            BigDecimal estoque = i.getEstoqueAtual() == null ? BigDecimal.ZERO : i.getEstoqueAtual();
            BigDecimal custo = i.getCustoUnitario() == null ? BigDecimal.ZERO : i.getCustoUnitario();
            if (estoque.signum() > 0) {
                valorInsumos.add(new DashboardCadastrosResponse.LinhaEstoque(i.getId(), IdentificadorFormatter.formatar("INS", i.getNumero()),
                        i.getNome(), "INSUMO", estoque, custo, estoque.multiply(custo), null));
            }
            if (i.getEstoqueMinimo() != null && estoque.compareTo(i.getEstoqueMinimo()) < 0) {
                abaixoMinimo.add(new DashboardCadastrosResponse.LinhaMinimo(i.getId(), IdentificadorFormatter.formatar("INS", i.getNumero()),
                        i.getNome(), estoque, i.getEstoqueMinimo(), i.getEstoqueMinimo().subtract(estoque)));
            }
        }
        List<DashboardCadastrosResponse.LinhaEstoque> valorProdutos = new ArrayList<>();
        List<DashboardCadastrosResponse.LinhaEstoque> potencial = new ArrayList<>();
        for (Produto p : produtosAtivos(usuarioId)) {
            BigDecimal estoque = p.getEstoqueAtual() == null ? BigDecimal.ZERO : p.getEstoqueAtual();
            if (estoque.signum() <= 0) {
                continue;
            }
            BigDecimal custoUnitario = produtoService.custoUnitarioAoVivo(p).setScale(4, RoundingMode.HALF_UP);
            String identificador = IdentificadorFormatter.formatar("PRO", p.getNumero());
            valorProdutos.add(new DashboardCadastrosResponse.LinhaEstoque(p.getId(), identificador, com.penseprecifique.api.util.NomeMarca.rotulo(p.getNome(), p.getMarca()), "PRODUTO", estoque,
                    custoUnitario, estoque.multiply(custoUnitario), p.getPrecoVenda()));
            if (p.getPrecoVenda() != null) {
                potencial.add(new DashboardCadastrosResponse.LinhaEstoque(p.getId(), identificador, com.penseprecifique.api.util.NomeMarca.rotulo(p.getNome(), p.getMarca()), "PRODUTO", estoque, null,
                        estoque.multiply(p.getPrecoVenda()), p.getPrecoVenda()));
            }
        }
        List<DashboardCadastrosResponse.LinhaEstoque> valorTodos = new ArrayList<>(valorInsumos);
        valorTodos.addAll(valorProdutos);
        valorTodos.sort(Comparator.comparing(DashboardCadastrosResponse.LinhaEstoque::valor).reversed()
                .thenComparing(l -> normalizar(l.nome())).thenComparing(DashboardCadastrosResponse.LinhaEstoque::identificador));
        potencial.sort(Comparator.comparing(DashboardCadastrosResponse.LinhaEstoque::valor).reversed()
                .thenComparing(l -> normalizar(l.nome())).thenComparing(DashboardCadastrosResponse.LinhaEstoque::identificador));
        abaixoMinimo.sort(Comparator.comparing(DashboardCadastrosResponse.LinhaMinimo::falta).reversed()
                .thenComparing(l -> normalizar(l.nome())).thenComparing(DashboardCadastrosResponse.LinhaMinimo::identificador));

        BigDecimal material = centavos(valorInsumos.stream().map(DashboardCadastrosResponse.LinhaEstoque::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal prontos = centavos(valorProdutos.stream().map(DashboardCadastrosResponse.LinhaEstoque::valor).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal potencialTotal = centavos(potencial.stream().map(DashboardCadastrosResponse.LinhaEstoque::valor).reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal[] atual = cmvEMargem(vendasPeriodo);
        BigDecimal[] anterior = cmvEMargem(vendasAnterior);
        return new DashboardCadastrosResponse.Estoque(material.add(prontos), material, prontos, valorTodos,
                potencialTotal, potencial,
                DashboardNumero.de(atual[0], anterior[0]), DashboardNumero.de(atual[1], anterior[1]),
                abaixoMinimo.size(), abaixoMinimo);
    }

    /** % de CMV e margem após custo de material do conjunto de vendas; 0% quando não há faturamento (RN-NOVA-25, itens 3 e 4). */
    private static BigDecimal[] cmvEMargem(List<VendasService.Venda> vendas) {
        BigDecimal faturamento = vendas.stream().map(VendasService.Venda::faturamento).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cmv = vendas.stream().map(VendasService.Venda::cmv).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (faturamento.signum() <= 0) {
            return new BigDecimal[]{BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)};
        }
        BigDecimal percentualCmv = cmv.multiply(CEM).divide(faturamento, 2, RoundingMode.HALF_UP);
        BigDecimal margem = faturamento.subtract(cmv).multiply(CEM).divide(faturamento, 2, RoundingMode.HALF_UP);
        return new BigDecimal[]{percentualCmv, margem};
    }

    private static BigDecimal centavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /** Evolução do preço de um insumo escolhido, mês a mês (o seletor da tela chama só este pedaço). */
    public DashboardCadastrosResponse.EvolucaoPreco evolucaoDoInsumo(UUID insumoId, LocalDate de, LocalDate ate) {
        UUID usuarioId = usuarioId();
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + insumoId));
        return evolucaoPreco(insumo, compraItemRepository.findPorStatus(usuarioId, StatusCompra.CONFIRMADA), p.inicio(), p.fim());
    }

    /** Limite de insumos ao mesmo tempo no gráfico de evolução de preço (RN-NOVA-29). */
    public static final int MAX_INSUMOS_EVOLUCAO = 5;

    /**
     * #989 (RN-NOVA-29, DT-NOVA-24): a mesma rotina mensal de {@link #evolucaoDoInsumo}, aplicada uma vez a cada insumo, na ordem pedida
     * (repetidos contam uma vez). Vazio ou mais de 5 insumos é erro de cliente; insumo de outra conta não existe para quem pergunta.
     */
    public DashboardCadastrosResponse.EvolucaoPrecoInsumos evolucaoDosInsumos(List<UUID> insumoIds, LocalDate de, LocalDate ate) {
        List<UUID> ids = insumoIds == null ? List.of() : insumoIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException("Escolha ao menos um insumo.");
        }
        if (ids.size() > MAX_INSUMOS_EVOLUCAO) {
            throw new BusinessException("Escolha no máximo " + MAX_INSUMOS_EVOLUCAO + " insumos");
        }
        UUID usuarioId = usuarioId();
        PeriodoDashboard p = PeriodoDashboard.de(de, ate);
        List<CompraItem> compras = compraItemRepository.findPorStatus(usuarioId, StatusCompra.CONFIRMADA);
        List<DashboardCadastrosResponse.EvolucaoPreco> series = new ArrayList<>();
        for (UUID id : ids) {
            Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + id));
            series.add(evolucaoPreco(insumo, compras, p.inicio(), p.fim()));
        }
        return new DashboardCadastrosResponse.EvolucaoPrecoInsumos(series);
    }

    // ---------------------------------------------------------------------------------------------

    private DashboardCadastrosResponse.EvolucaoPreco evolucaoPreco(Insumo insumo, List<CompraItem> compras, LocalDate de, LocalDate ate) {
        if (insumo == null) {
            return null;
        }
        Map<YearMonth, BigDecimal[]> porMes = new java.util.TreeMap<>();
        for (CompraItem i : comprasEntre(compras, de, ate)) {
            if (!i.getInsumo().getId().equals(insumo.getId()) || i.getQuantidade() == null || i.getPrecoTotal() == null) {
                continue;
            }
            BigDecimal[] soma = porMes.computeIfAbsent(YearMonth.from(i.getCompra().dataParaContagem()), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            soma[0] = soma[0].add(i.getPrecoTotal());
            soma[1] = soma[1].add(i.getQuantidade());
        }
        List<DashboardCadastrosResponse.PontoPreco> pontos = new ArrayList<>();
        porMes.forEach((mes, soma) -> {
            if (soma[1].signum() > 0) {
                pontos.add(new DashboardCadastrosResponse.PontoPreco(mes.atDay(1),
                        soma[0].divide(soma[1], 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP), soma[1], soma[0]));
            }
        });
        String unidade = insumo.getUnidadeMedida() != null ? insumo.getUnidadeMedida().getNome() : null;
        return new DashboardCadastrosResponse.EvolucaoPreco(insumo.getId(), insumo.getNome(), insumo.getMarca(), pontos, unidade);
    }

    /** O insumo escolhido na tela; sem escolha, o mais comprado no período; sem compras, nenhum. */
    private Insumo escolherInsumoDaEvolucao(UUID usuarioId, UUID escolhido, Map<UUID, BigDecimal> compradas, Map<UUID, Chave> chaves) {
        if (escolhido != null) {
            return insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(escolhido, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + escolhido));
        }
        return compradas.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<UUID, BigDecimal> e) -> e.getValue()).reversed()
                        .thenComparing(e -> chaves.get(e.getKey()), ORDEM))
                .map(Map.Entry::getKey).findFirst()
                .flatMap(id -> insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)).orElse(null);
    }

    private DashboardCadastrosResponse.Ranking insumosEmProdutos(UUID usuarioId) {
        Map<UUID, Long> contagem = new HashMap<>();
        for (Object[] linha : fichaTecnicaItemRepository.contarProdutosAtivosPorInsumo(usuarioId)) {
            contagem.put((UUID) linha[0], ((Number) linha[1]).longValue());
        }
        List<DashboardCadastrosResponse.Item> todos = insumoRepository.findByUsuarioIdAndAtivoTrueAndDeletedAtIsNull(usuarioId).stream()
                .map(i -> new Object[]{chave(i), contagem.getOrDefault(i.getId(), 0L)})
                .sorted(Comparator.comparing((Object[] o) -> (Long) o[1]).reversed().thenComparing(o -> (Chave) o[0], ORDEM))
                .map(o -> item((Chave) o[0], BigDecimal.valueOf((Long) o[1]), null, null))
                .toList();
        return new DashboardCadastrosResponse.Ranking(todos.stream().limit(TOP).toList(), todos);
    }

    private DashboardCadastrosResponse.Ranking rankingInsumos(Map<UUID, BigDecimal> atual, Map<UUID, BigDecimal> anterior,
                                                              Map<UUID, Chave> chaves, String detalhe) {
        List<DashboardCadastrosResponse.Item> todos = atual.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<UUID, BigDecimal> e) -> e.getValue()).reversed()
                        .thenComparing(e -> chaves.get(e.getKey()), ORDEM))
                .map(e -> item(chaves.get(e.getKey()), e.getValue(), anterior.get(e.getKey()), detalhe))
                .toList();
        return new DashboardCadastrosResponse.Ranking(todos.stream().limit(TOP).toList(), todos);
    }

    private record LinhaSoma(Chave chave, BigDecimal quantidade, BigDecimal faturamento, BigDecimal custo) {
        BigDecimal lucro() { return faturamento.subtract(custo); }
    }

    private Map<String, LinhaSoma> somarLinhas(List<VendasService.Venda> vendas) {
        Map<String, LinhaSoma> soma = new LinkedHashMap<>();
        for (VendasService.Venda v : vendas) {
            for (VendasService.LinhaProduto l : v.linhas()) {
                String k = l.tipo() + "|" + (l.id() != null ? l.id() : l.nome());
                BigDecimal custo = l.custo() != null ? l.custo() : BigDecimal.ZERO;
                soma.merge(k, new LinhaSoma(chaveDaLinha(l), l.quantidade(), l.faturamento(), custo),
                        (a, b) -> new LinhaSoma(a.chave(), a.quantidade().add(b.quantidade()), a.faturamento().add(b.faturamento()), a.custo().add(b.custo())));
            }
        }
        return soma;
    }

    private Chave chaveDaLinha(VendasService.LinhaProduto l) {
        if ("PRODUTO".equals(l.tipo()) && l.id() != null) {
            return produtoRepository.findById(l.id())
                    .map(p -> new Chave(p.getId(), IdentificadorFormatter.formatar("PRO", p.getNumero()), com.penseprecifique.api.util.NomeMarca.rotulo(p.getNome(), p.getMarca()), null, p.getNumero()))
                    .orElse(new Chave(l.id(), "", com.penseprecifique.api.util.NomeMarca.rotulo(l.nome(), l.marca()), null, 0));
        }
        return new Chave(l.id(), "", com.penseprecifique.api.util.NomeMarca.rotulo(l.nome(), l.marca()), null, 0);
    }

    private DashboardCadastrosResponse.Ranking rankingProdutos(Map<String, LinhaSoma> atual, Map<String, LinhaSoma> anterior, boolean porLucro) {
        List<DashboardCadastrosResponse.Item> todos = atual.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, LinhaSoma> e) -> valorProduto(e.getValue(), porLucro)).reversed()
                        .thenComparing(e -> e.getValue().chave(), ORDEM))
                .map(e -> new DashboardCadastrosResponse.Item(e.getValue().chave().id(), e.getValue().chave().identificador(),
                        e.getValue().chave().nome(), null, valorProduto(e.getValue(), porLucro),
                        anterior.containsKey(e.getKey()) ? valorProduto(anterior.get(e.getKey()), porLucro) : null, null))
                .toList();
        return new DashboardCadastrosResponse.Ranking(todos.stream().limit(TOP).toList(), todos);
    }

    private static BigDecimal valorProduto(LinhaSoma l, boolean porLucro) {
        return porLucro ? l.lucro().setScale(2, RoundingMode.HALF_UP) : l.quantidade();
    }

    private List<DashboardCadastrosResponse.PrecoAbaixoSugerido> precosAbaixoDoSugerido(UUID usuarioId) {
        List<DashboardCadastrosResponse.PrecoAbaixoSugerido> lista = new ArrayList<>();
        for (Produto p : produtosAtivos(usuarioId)) {
            if (p.getPrecoVenda() == null) {
                continue;
            }
            BigDecimal sugerido = produtoService.precoSugeridoAoVivo(p);
            if (p.getPrecoVenda().compareTo(sugerido) < 0) {
                lista.add(new DashboardCadastrosResponse.PrecoAbaixoSugerido(p.getId(), IdentificadorFormatter.formatar("PRO", p.getNumero()),
                        p.getNome(), p.getPrecoVenda(), sugerido, p.getPrecoVenda().subtract(sugerido)));
            }
        }
        lista.sort(Comparator.comparing(DashboardCadastrosResponse.PrecoAbaixoSugerido::diferenca)
                .thenComparing(x -> normalizar(x.nome())).thenComparing(DashboardCadastrosResponse.PrecoAbaixoSugerido::identificador));
        return lista;
    }

    private List<DashboardCadastrosResponse.MargemProduto> margens(UUID usuarioId) {
        List<DashboardCadastrosResponse.MargemProduto> lista = new ArrayList<>();
        for (Produto p : produtosAtivos(usuarioId)) {
            if (p.getPrecoVenda() == null || p.getPrecoVenda().signum() <= 0) {
                continue;
            }
            BigDecimal custo = produtoService.custoUnitarioAoVivo(p);
            BigDecimal margem = p.getPrecoVenda().subtract(custo).multiply(CEM).divide(p.getPrecoVenda(), 2, RoundingMode.HALF_UP);
            lista.add(new DashboardCadastrosResponse.MargemProduto(p.getId(), IdentificadorFormatter.formatar("PRO", p.getNumero()),
                    p.getNome(), p.getPrecoVenda(), custo.setScale(2, RoundingMode.HALF_UP), margem));
        }
        lista.sort(Comparator.comparing(DashboardCadastrosResponse.MargemProduto::margemPercentual)
                .thenComparing(x -> normalizar(x.nome())).thenComparing(DashboardCadastrosResponse.MargemProduto::identificador));
        return lista;
    }

    /** Curva ABC por lucro (RN-NOVA-16): classe pelo acumulado já incluindo o produto; o primeiro é sempre A. */
    private DashboardCadastrosResponse.CurvaAbc curvaAbc(Map<String, LinhaSoma> linhas) {
        List<LinhaSoma> ordenadas = linhas.values().stream()
                .sorted(Comparator.comparing((LinhaSoma l) -> l.lucro().setScale(2, RoundingMode.HALF_UP)).reversed()
                        .thenComparing(LinhaSoma::chave, ORDEM))
                .toList();
        List<LinhaSoma> positivos = ordenadas.stream().filter(l -> l.lucro().setScale(2, RoundingMode.HALF_UP).signum() > 0).toList();
        BigDecimal total = positivos.stream().map(l -> l.lucro().setScale(2, RoundingMode.HALF_UP)).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<DashboardCadastrosResponse.ItemAbc> itens = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (int i = 0; i < positivos.size(); i++) {
            LinhaSoma l = positivos.get(i);
            BigDecimal lucro = l.lucro().setScale(2, RoundingMode.HALF_UP);
            acumulado = acumulado.add(lucro);
            BigDecimal acumuladoPct = acumulado.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
            String classe = i == 0 || acumulado.multiply(CEM).compareTo(total.multiply(OITENTA)) <= 0 ? "A"
                    : acumulado.multiply(CEM).compareTo(total.multiply(NOVENTA_E_CINCO)) <= 0 ? "B" : "C";
            itens.add(new DashboardCadastrosResponse.ItemAbc(l.chave().id(), l.chave().identificador(), l.chave().nome(), lucro,
                    lucro.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP), acumuladoPct, classe));
        }
        List<DashboardCadastrosResponse.ItemAbc> semLucro = ordenadas.stream().filter(l -> l.lucro().setScale(2, RoundingMode.HALF_UP).signum() <= 0)
                .map(l -> new DashboardCadastrosResponse.ItemAbc(l.chave().id(), l.chave().identificador(), l.chave().nome(),
                        l.lucro().setScale(2, RoundingMode.HALF_UP), null, null, null)).toList();
        return new DashboardCadastrosResponse.CurvaAbc(total, itens, semLucro);
    }

    /** Variação % entre o primeiro e o último preço pago no período (2+ compras); ordena pela maior variação absoluta. */
    private DashboardCadastrosResponse.Ranking variacoes(List<CompraItem> compras) {
        Map<UUID, List<CompraItem>> porInsumo = compras.stream().filter(i -> i.getPrecoUnitarioPago() != null)
                .sorted(Comparator.comparing((CompraItem i) -> i.getCompra().dataParaContagem()).thenComparing(i -> i.getCompra().getNumero())
                        .thenComparing(CompraItem::getOrdem))
                .collect(Collectors.groupingBy(i -> i.getInsumo().getId(), LinkedHashMap::new, Collectors.toList()));
        List<DashboardCadastrosResponse.Item> todos = new ArrayList<>();
        List<Object[]> ordenaveis = new ArrayList<>();
        for (List<CompraItem> serie : porInsumo.values()) {
            if (serie.stream().map(i -> i.getCompra().getId()).distinct().count() < 2) {
                continue;
            }
            BigDecimal primeiro = serie.get(0).getPrecoUnitarioPago();
            BigDecimal ultimo = serie.get(serie.size() - 1).getPrecoUnitarioPago();
            BigDecimal v = DashboardNumero.variacao(primeiro, ultimo);
            if (v != null) {
                ordenaveis.add(new Object[]{chave(serie.get(0).getInsumo()), v, primeiro, ultimo});
            }
        }
        ordenaveis.sort(Comparator.comparing((Object[] o) -> ((BigDecimal) o[1]).abs()).reversed().thenComparing(o -> (Chave) o[0], ORDEM));
        for (Object[] o : ordenaveis) {
            todos.add(item((Chave) o[0], (BigDecimal) o[1], null, "R$ " + o[2] + " → R$ " + o[3]));
        }
        return new DashboardCadastrosResponse.Ranking(todos.stream().limit(TOP).toList(), todos);
    }

    // ---------------------------------------------------------------------------------------------

    private List<Produto> produtosAtivos(UUID usuarioId) {
        return produtoRepository.findByUsuarioIdAndTipoAndDeletedAtIsNull(usuarioId, TipoProduto.PRODUTO).stream()
                .filter(p -> Boolean.TRUE.equals(p.getAtivo())).toList();
    }

    private static List<CompraItem> comprasEntre(List<CompraItem> compras, LocalDate de, LocalDate ate) {
        return compras.stream().filter(i -> dentro(i.getCompra().dataParaContagem(), de, ate)).toList();
    }

    private static boolean dentro(LocalDate d, LocalDate de, LocalDate ate) {
        return !d.isBefore(de) && !d.isAfter(ate);
    }

    private static Map<UUID, BigDecimal> contarCompras(List<CompraItem> itens) {
        Map<UUID, java.util.Set<UUID>> compras = new LinkedHashMap<>();
        itens.forEach(i -> compras.computeIfAbsent(i.getInsumo().getId(), k -> new java.util.HashSet<>()).add(i.getCompra().getId()));
        Map<UUID, BigDecimal> contagem = new LinkedHashMap<>();
        compras.forEach((insumo, set) -> contagem.put(insumo, BigDecimal.valueOf(set.size())));
        return contagem;
    }

    private static Map<UUID, BigDecimal> somarGasto(List<CompraItem> itens) {
        Map<UUID, BigDecimal> gasto = new LinkedHashMap<>();
        itens.stream().filter(i -> i.getPrecoTotal() != null).forEach(i -> gasto.merge(i.getInsumo().getId(), i.getPrecoTotal(), BigDecimal::add));
        return gasto;
    }

    private static Chave chave(Insumo i) {
        return new Chave(i.getId(), IdentificadorFormatter.formatar("INS", i.getNumero()), i.getNome(), i.getMarca(), i.getNumero());
    }

    private static DashboardCadastrosResponse.Item item(Chave c, BigDecimal valor, BigDecimal anterior, String detalhe) {
        return new DashboardCadastrosResponse.Item(c.id(), c.identificador(), c.nome(), c.marca(), valor, anterior, detalhe);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
