package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.dto.response.compra.DashboardComprasResponse;
import com.penseprecifique.api.shared.dto.response.compra.EvolucaoPrecoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #548/RN-NOVA-15 (V0.15.0, DT-NOVA-9) — dashboard de compras e evolução do preço pago. Agregação
 * síncrona ao vivo sobre as linhas de compras CONFIRMADAS (rascunhos e canceladas nunca entram).
 * Valores usam o preço PAGO da linha, nunca o custo médio do insumo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardCompraService {

    static final int MAX_INSUMOS_GRAFICO = 5;
    static final int JANELA_AUMENTO_DIAS = 90;
    private static final BigDecimal CEM = new BigDecimal("100");

    private final CompraItemRepository compraItemRepository;
    private final InsumoRepository insumoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CompraMapper compraMapper;

    private static final Comparator<CompraItem> CRONOLOGICA = Comparator
            .comparing((CompraItem i) -> i.getCompra().getDataCompra())
            .thenComparing(i -> i.getCompra().getNumero())
            .thenComparing(CompraItem::getOrdem);

    public DashboardComprasResponse dashboard() {
        UUID usuarioId = usuarioId();
        List<CompraItem> linhas = compraItemRepository.findPorStatus(usuarioId, StatusCompra.CONFIRMADA);
        LocalDate hoje = LocalDate.now();

        BigDecimal mes = somar(linhas.stream().filter(i -> {
            LocalDate d = i.getCompra().getDataCompra();
            return d.getYear() == hoje.getYear() && d.getMonth() == hoje.getMonth();
        }).toList());
        BigDecimal ano = somar(linhas.stream().filter(i -> i.getCompra().getDataCompra().getYear() == hoje.getYear()).toList());
        return new DashboardComprasResponse(mes, ano, fornecedorMaisUsado(linhas), maiorAumento(linhas, hoje));
    }

    /**
     * Até 5 insumos; período default = últimos 3 meses. Um ponto por linha confirmada com preço pago.
     */
    public EvolucaoPrecoResponse evolucaoPreco(List<UUID> insumoIds, LocalDate de, LocalDate ate) {
        UUID usuarioId = usuarioId();
        Set<UUID> ids = new LinkedHashSet<>(insumoIds != null ? insumoIds : List.of());
        if (ids.isEmpty()) {
            throw new BusinessException("Escolha pelo menos um insumo.");
        }
        if (ids.size() > MAX_INSUMOS_GRAFICO) {
            throw new BusinessException("Escolha no máximo 5 insumos.");
        }
        LocalDate fim = ate != null ? ate : LocalDate.now();
        LocalDate inicio = de != null ? de : fim.minusMonths(3);
        if (inicio.isAfter(fim)) {
            throw new BusinessException("A data inicial não pode ser depois da data final.");
        }

        Map<UUID, Insumo> insumos = new HashMap<>();
        for (UUID id : ids) {
            insumos.put(id, insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + id)));
        }
        Map<UUID, List<CompraItem>> porInsumo = compraItemRepository.findPorInsumosEStatus(usuarioId, ids, StatusCompra.CONFIRMADA)
                .stream()
                .filter(i -> i.getPrecoUnitarioPago() != null)
                .filter(i -> !i.getCompra().getDataCompra().isBefore(inicio) && !i.getCompra().getDataCompra().isAfter(fim))
                .sorted(CRONOLOGICA)
                .collect(Collectors.groupingBy(i -> i.getInsumo().getId()));

        List<EvolucaoPrecoResponse.Serie> series = new ArrayList<>();
        for (UUID id : ids) {
            List<CompraItem> pontos = porInsumo.getOrDefault(id, List.of());
            BigDecimal base = pontos.isEmpty() ? null : pontos.get(0).getPrecoUnitarioPago();
            series.add(new EvolucaoPrecoResponse.Serie(compraMapper.toRef(insumos.get(id)), pontos.stream()
                    .map(i -> new EvolucaoPrecoResponse.Ponto(i.getCompra().getDataCompra(), i.getCompra().getId(),
                            IdentificadorFormatter.formatar("COM", i.getCompra().getNumero()), i.getPrecoUnitarioPago(),
                            i.getQuantidade(), i.getFornecedor() != null ? i.getFornecedor().getNome() : null,
                            variacao(base, i.getPrecoUnitarioPago())))
                    .toList()));
        }
        return new EvolucaoPrecoResponse(inicio, fim, series);
    }

    // ---------------------------------------------------------------------------------------------

    private static BigDecimal somar(List<CompraItem> linhas) {
        return linhas.stream().map(CompraItem::getPrecoTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Fornecedor em mais compras confirmadas (conta uma vez por compra). Empate: compra mais recente. */
    private DashboardComprasResponse.FornecedorMaisUsado fornecedorMaisUsado(List<CompraItem> linhas) {
        Map<UUID, Set<UUID>> comprasPorFornecedor = new HashMap<>();
        Map<UUID, Compra> maisRecente = new HashMap<>();
        Map<UUID, Cliente> fornecedores = new HashMap<>();
        Comparator<Compra> recencia = Comparator.comparing(Compra::getDataCompra).thenComparing(Compra::getNumero);
        for (CompraItem i : linhas) {
            if (i.getFornecedor() == null) {
                continue;
            }
            UUID f = i.getFornecedor().getId();
            fornecedores.putIfAbsent(f, i.getFornecedor());
            comprasPorFornecedor.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(i.getCompra().getId());
            maisRecente.merge(f, i.getCompra(), (a, b) -> recencia.compare(a, b) >= 0 ? a : b);
        }
        return comprasPorFornecedor.keySet().stream()
                .max(Comparator.comparing((UUID f) -> comprasPorFornecedor.get(f).size())
                        .thenComparing(f -> maisRecente.get(f), recencia))
                .map(f -> new DashboardComprasResponse.FornecedorMaisUsado(compraMapper.toRef(fornecedores.get(f)),
                        comprasPorFornecedor.get(f).size()))
                .orElse(null);
    }

    /**
     * Maior variação entre o primeiro e o último preço pago do insumo nos últimos 90 dias; exige pelo
     * menos 2 compras com preço. Sem candidato → null ("Sem dados suficientes"). A variação vem mesmo se
     * for ≤ 0 (nenhum insumo subiu): o card mostra o valor como está.
     */
    private DashboardComprasResponse.InsumoMaiorAumento maiorAumento(List<CompraItem> linhas, LocalDate hoje) {
        LocalDate inicio = hoje.minusDays(JANELA_AUMENTO_DIAS);
        Map<UUID, List<CompraItem>> porInsumo = linhas.stream()
                .filter(i -> i.getPrecoUnitarioPago() != null && !i.getCompra().getDataCompra().isBefore(inicio))
                .sorted(CRONOLOGICA)
                .collect(Collectors.groupingBy(i -> i.getInsumo().getId()));

        DashboardComprasResponse.InsumoMaiorAumento melhor = null;
        for (List<CompraItem> serie : porInsumo.values()) {
            if (serie.size() < 2) {
                continue;
            }
            CompraItem primeiro = serie.get(0);
            CompraItem ultimo = serie.get(serie.size() - 1);
            BigDecimal v = variacao(primeiro.getPrecoUnitarioPago(), ultimo.getPrecoUnitarioPago());
            if (melhor == null || v.compareTo(melhor.variacaoPercentual()) > 0) {
                melhor = new DashboardComprasResponse.InsumoMaiorAumento(compraMapper.toRef(primeiro.getInsumo()),
                        primeiro.getPrecoUnitarioPago(), primeiro.getCompra().getDataCompra(),
                        ultimo.getPrecoUnitarioPago(), ultimo.getCompra().getDataCompra(), v);
            }
        }
        return melhor;
    }

    /** (atual − base) ÷ base × 100, 2 casas. */
    static BigDecimal variacao(BigDecimal base, BigDecimal atual) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return atual.subtract(base).multiply(CEM).divide(base, 2, RoundingMode.HALF_UP);
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }
}
