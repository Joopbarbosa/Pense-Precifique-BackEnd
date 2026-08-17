package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.ReciboPagamento;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboPagamentoPayload;
import com.penseprecifique.api.shared.dto.pdf.ReciboPagamentoPdfData;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.orcamento.ReciboPagamentoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bean colaborador de {@link PdfService} para {@code recibo-pagamento} (última migração de #248)
 * — mesmo motivo estrutural de {@link OrcamentoPdfPayloadService}/{@link ReciboPdfPayloadService}
 * (#262): leitura de banco via injeção (não auto-invocação), para a chamada HTTP subsequente
 * ficar de verdade fora do escopo {@code @Transactional}.
 *
 * <p>Bean próprio, não reaproveita {@link ReciboPdfPayloadService} — este último se declara
 * explicitamente escopado aos 3 documentos cuja leitura é idêntica (orçamento + guard + empresa);
 * recibo-pagamento lê também {@link ReciboPagamentoRepository} (entidade própria, relação 1:1 com
 * {@code Orcamento}), leitura estruturalmente diferente — mesmo critério que já justifica
 * {@link OrcamentoPdfPayloadService} ser separado (ver decisoes-pdf.md).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReciboPagamentoPdfPayloadService {

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;
    private final PdfMapper pdfMapper;

    public PdfMicroservicoReciboPagamentoPayload montarPayloadReciboPagamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus() != StatusOrcamento.PAGO) {
            throw new BusinessException("Recibo de pagamento só disponível para orçamentos com status PAGO");
        }

        ReciboPagamento recibo = reciboPagamentoRepository.findByOrcamentoId(orcamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de pagamento não encontrado"));

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);

        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId,
                        item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));

        ReciboPagamentoPdfData dados = pdfMapper.toReciboPagamentoPdfData(orcamento, recibo, empresa, itens, customizacoesPorItem);
        return pdfMapper.toReciboPagamentoMicroservicoPayload(dados);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
