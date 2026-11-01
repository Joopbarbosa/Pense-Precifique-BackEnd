package com.penseprecifique.api.pdf;

import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItem;
import com.penseprecifique.api.shared.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.financeiro.ParcelaOrcamentoResponse;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.dto.pdf.OrcamentoPdfData;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCondicoesPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.orcamento.OrcamentoItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.financeiro.LancamentoFinanceiroService;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bean colaborador separado de {@link PdfService} — existe só para que a leitura de banco de
 * {@link #montarPayloadOrcamento} passe pelo proxy AOP do Spring como uma chamada de verdade
 * (via injeção), não uma auto-invocação {@code this.} dentro da mesma classe. Auto-invocação não
 * é interceptada pelo proxy que aplica {@code @Transactional}, então extrair esse método pra
 * dentro de {@code PdfService} sem anotação de classe (ou anotado, mas chamado via {@code this.})
 * faria o Spring ignorar a anotação em silêncio — a leitura funcionaria igual, mas sem nenhuma
 * fronteira transacional de verdade. Ver decisoes-pdf.md (#262) para o raciocínio completo.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrcamentoPdfPayloadService {

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final PdfMapper pdfMapper;
    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final LancamentoFinanceiroService lancamentoFinanceiroService;

    public PdfMicroservicoOrcamentoPayload montarPayloadOrcamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());
        Map<UUID, List<OrcamentoItemCustomizacao>> customizacoesPorItem = itens.stream()
                .collect(Collectors.toMap(OrcamentoItem::getId,
                        item -> orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId())));

        // V0.19.0 #445 (RN-NOVA-45): página 2 de condições. Antes da aprovação lê os métodos ativos na hora da
        // geração, na ordem do cadastro; depois da aprovação lê as parcelas do lançamento. Só o necessário é lido.
        boolean antesDaAprovacao = orcamento.getStatus() == StatusOrcamento.RASCUNHO
                || orcamento.getStatus() == StatusOrcamento.ENVIADO;
        List<MetodoPagamentoConfiguravel> metodosAtivos = metodoPagamentoRepository
                .findByUsuarioIdOrderByOrdemAscTipoAsc(usuario.getId()).stream()
                .filter(m -> Boolean.TRUE.equals(m.getAtivo())).toList();
        List<ParcelaOrcamentoResponse> parcelas = antesDaAprovacao ? List.of()
                : lancamentoFinanceiroService.parcelasDoOrcamento(orcamento.getId());
        PdfMicroservicoCondicoesPayload condicoes = pdfMapper.toCondicoesPayload(orcamento, metodosAtivos, parcelas);

        OrcamentoPdfData dados = pdfMapper.toOrcamentoPdfData(orcamento, empresa, itens, customizacoesPorItem, condicoes);
        return pdfMapper.toMicroservicoPayload(dados);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
