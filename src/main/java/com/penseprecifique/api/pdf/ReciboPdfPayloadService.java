package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoCancelamento;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoPdfMultaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboEstornoPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboSinalPayload;
import com.penseprecifique.api.shared.dto.pdf.ReciboPdfData;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
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
 * Bean colaborador de {@link PdfService} para os 3 documentos migrados na Frente A de #248
 * (recibo-sinal, pdf-multa, recibo-estorno) — mesmo motivo estrutural de
 * {@link OrcamentoPdfPayloadService} (#262): a leitura de banco precisa passar pelo proxy AOP do
 * Spring via injeção (não {@code this.}) para a chamada HTTP subsequente ficar de verdade fora do
 * escopo {@code @Transactional}. Um bean só, não 3, porque a leitura dos 3 tipos é essencialmente
 * idêntica (buscar orçamento + validar guard de negócio + buscar empresa), diferindo só no guard
 * e no mapeamento final.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReciboPdfPayloadService {

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdfMapper pdfMapper;

    /**
     * P-F007b — busca itens/customizações do orçamento, mesmo padrão de
     * {@link OrcamentoPdfPayloadService#montarPayloadOrcamento}, para restaurar "Detalhes do
     * pedido" no recibo-sinal (só este documento precisa, por isso a leitura não entra em
     * {@link #buscarOrcamento}, reaproveitado por multa/estorno que não usam itens).
     */
    public PdfMicroservicoReciboSinalPayload montarPayloadReciboSinal(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = buscarOrcamento(orcamentoId, usuario);
        if (orcamento.getStatus().ordinal() < StatusOrcamento.SINAL_PAGO.ordinal()) {
            throw new BusinessException("Recibo do sinal só disponível a partir do status SINAL_PAGO");
        }
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId,
                        item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));
        ReciboPdfData dados = pdfMapper.toReciboPdfData(orcamento, buscarEmpresa(usuario), itens, customizacoesPorItem);
        return pdfMapper.toReciboSinalMicroservicoPayload(dados);
    }

    public PdfMicroservicoPdfMultaPayload montarPayloadPdfMulta(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = buscarOrcamento(orcamentoId, usuario);
        if (orcamento.getCancelamentoTipo() != TipoCancelamento.MULTA) {
            throw new BusinessException("PDF de multa só disponível para cancelamentos com multa");
        }
        ReciboPdfData dados = pdfMapper.toReciboPdfDataMulta(orcamento, buscarEmpresa(usuario));
        return pdfMapper.toPdfMultaMicroservicoPayload(dados);
    }

    public PdfMicroservicoReciboEstornoPayload montarPayloadReciboEstorno(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = buscarOrcamento(orcamentoId, usuario);
        if (!Boolean.TRUE.equals(orcamento.getEstornoSinal())) {
            throw new BusinessException("Recibo de estorno só disponível para cancelamentos com estorno de sinal");
        }
        ReciboPdfData dados = pdfMapper.toReciboPdfDataEstorno(orcamento, buscarEmpresa(usuario));
        return pdfMapper.toReciboEstornoMicroservicoPayload(dados);
    }

    private Orcamento buscarOrcamento(UUID orcamentoId, Usuario usuario) {
        return orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
    }

    private Empresa buscarEmpresa(Usuario usuario) {
        return empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
