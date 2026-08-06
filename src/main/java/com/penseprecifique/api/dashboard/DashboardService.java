package com.penseprecifique.api.dashboard;

import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.response.dashboard.DashboardResponse;
import com.penseprecifique.api.shared.dto.response.dashboard.OrcamentoResumoDTO;
import com.penseprecifique.api.shared.dto.response.dashboard.ProdutoVendidoDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
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
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final UsuarioRepository usuarioRepository;

    public DashboardResponse getDashboard() {
        UUID uid = getUsuarioIdAutenticado();

        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioMes = hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime inicioProximoMes = inicioMes.plusMonths(1);

        long totalOrcamentos = orcamentoRepository.countByUsuarioIdAndDeletedAtIsNull(uid);
        long pendentes = orcamentoRepository.countByUsuarioIdAndStatusInAndDeletedAtIsNull(uid, PENDENTES);
        BigDecimal receitaTotal = orcamentoRepository.sumTotalByStatus(uid, StatusOrcamento.PAGO);
        BigDecimal receitaMes = orcamentoRepository.sumTotalByStatusAndPeriodo(uid, StatusOrcamento.PAGO, inicioMes, inicioProximoMes);

        List<ProdutoVendidoDTO> produtosMaisVendidos = orcamentoItemRepository
                .findTopProdutosMaisVendidos(uid, PageRequest.of(0, 5))
                .stream()
                .map(row -> ProdutoVendidoDTO.builder()
                        .nomeProduto((String) row[0])
                        .quantidade(((Number) row[1]).longValue())
                        .build())
                .toList();

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
}
