package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.response.dashboard.DashboardResponse;
import com.penseprecifique.api.shared.dto.response.dashboard.OrcamentoResumoDTO;
import com.penseprecifique.api.shared.dto.response.dashboard.ProdutoVendidoDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private static final List<StatusOrcamento> PENDENTES = List.of(
            StatusOrcamento.ENVIADO,
            StatusOrcamento.APROVADO,
            StatusOrcamento.AGUARDANDO_SINAL,
            StatusOrcamento.SINAL_PAGO,
            StatusOrcamento.EM_PRODUCAO
    );

    private final OrcamentoRepository orcamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final VendasService vendasService;
    private final java.time.Clock clock;

    public DashboardResponse getDashboard() {
        UUID uid = getUsuarioIdAutenticado();

        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime inicioProximoMes = inicioMes.plusMonths(1);

        long totalOrcamentos = orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(uid);
        long pendentes = orcamentoRepository.countByUsuarioIdAndStatusInAndDeletedAtIsNull(uid, PENDENTES);
        // DT-NOVA-4 (V0.10.0, #466) — dataPagamento (não status = PAGO) já reflete "foi pago
        // alguma vez", correto mesmo depois do orçamento avançar pra ENTREGUE (RN-NOVA-10).
        BigDecimal receitaTotal = orcamentoRepository.sumTotalPago(uid);
        BigDecimal receitaMes = orcamentoRepository.sumTotalPagoNoPeriodo(uid, inicioMes, inicioProximoMes);

        List<ProdutoVendidoDTO> produtosMaisVendidos = maisVendidosDoMes(uid);

        List<OrcamentoResumoDTO> orcamentosRecentes = orcamentoRepository
                .findTop5ByUsuarioIdAndDeletedAtIsNullOrderByCreatedAtDesc(uid)
                .stream()
                .map(this::toResumo)
                .toList();

        return DashboardResponse.builder()
                .totalOrcamentos(totalOrcamentos)
                .orcamentosPendentes(pendentes)
                .receitaMes(receitaMes)
                .receitaTotal(receitaTotal)
                .produtosMaisVendidos(produtosMaisVendidos)
                .orcamentosRecentes(orcamentosRecentes)
                .build();
    }

    private OrcamentoResumoDTO toResumo(Orcamento o) {
        return OrcamentoResumoDTO.builder()
                .id(o.getId())
                .numero(o.getNumero())
                .nomeCliente(o.getCliente().getNome())
                .total(o.getTotal())
                .status(o.getStatus().name())
                .build();
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
        return usuario.getId();
    }

    /**
     * #999 (RN-NOVA-35) — os 5 produtos mais vendidos no mês atual de Brasília (do dia 1 às 00:00 ao dia 1 seguinte às 00:00, limite
     * final excluído), só com vendas da RN-NOVA-12 (orçamento ENTREGUE pela data de entrega e venda do Caixa CONCLUÍDA), agrupados por
     * tipo e cadastro. Ordem: quantidade, nome sem maiúsculas e acentos, caracteres originais do nome, tipo (item de catálogo antes
     * de produto) e identificador. O comparador do Dashboard de Vendas não é alterado.
     */
    private List<ProdutoVendidoDTO> maisVendidosDoMes(UUID uid) {
        LocalDate primeiro = LocalDate.now(clock).withDayOfMonth(1);
        LocalDate ultimo = primeiro.plusMonths(1).minusDays(1);
        java.util.Map<String, ProdutoVendidoDTO> soma = new java.util.LinkedHashMap<>();
        for (VendasService.Venda v : vendasService.vendas(uid, primeiro, ultimo)) {
            for (VendasService.LinhaProduto l : v.linhas()) {
                String chave = l.tipo() + "|" + (l.id() != null ? l.id() : l.nome());
                soma.merge(chave, ProdutoVendidoDTO.builder().nomeProduto(l.nome()).quantidade(l.quantidade()).tipo(l.tipo()).id(l.id()).build(),
                        (a, b) -> ProdutoVendidoDTO.builder().nomeProduto(a.getNomeProduto()).quantidade(a.getQuantidade().add(b.getQuantidade()))
                                .tipo(a.getTipo()).id(a.getId()).build());
            }
        }
        return soma.values().stream()
                .sorted(java.util.Comparator.comparing(ProdutoVendidoDTO::getQuantidade).reversed()
                        .thenComparing(p -> semAcento(p.getNomeProduto()))
                        .thenComparing(ProdutoVendidoDTO::getNomeProduto)
                        .thenComparing(ProdutoVendidoDTO::getTipo)
                        .thenComparing(p -> p.getId() == null ? "" : p.getId().toString()))
                .limit(5)
                .toList();
    }

    private static String semAcento(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
    }
}
