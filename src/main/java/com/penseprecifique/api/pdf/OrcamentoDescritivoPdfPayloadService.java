package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.orcamento.descritivo.OrcamentoDescritivoOpcaoRepository;
import com.penseprecifique.api.orcamento.descritivo.OrcamentoDescritivoRepository;
import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoDescritivoOpcao;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoEmpresaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoOrcamentoDescritivoPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.NomeMarca;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * V0.19.0 #382 (DT-NOVA-10) — monta o payload do PDF do Descritivo, separado do {@link OrcamentoPdfPayloadService}
 * (que lê {@code OrcamentoItem}). Só a página de opções: preço de referência gravado na opção (nunca o atual), sem
 * total, subtotal, sinal, forma de pagamento nem página de condições. Descritivo de outra conta é "não encontrado".
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrcamentoDescritivoPdfPayloadService {

    private static final DecimalFormat MOEDA = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.of("pt", "BR")));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrcamentoDescritivoRepository repository;
    private final OrcamentoDescritivoOpcaoRepository opcaoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    /** O orçamento (do id dado, da conta logada) é um Descritivo? Usado pelo {@link PdfService} para escolher o payload. */
    public boolean ehDescritivo(UUID orcamentoId) {
        return repository.existeDescritivo(orcamentoId, getUsuarioId());
    }

    public PdfMicroservicoOrcamentoDescritivoPayload montarPayload(UUID orcamentoId) {
        UUID usuarioId = getUsuarioId();
        Orcamento orcamento = repository.buscarDescritivo(orcamentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId).orElse(null);
        List<OrcamentoDescritivoOpcao> opcoes = opcaoRepository.findByOrcamentoIdOrderByOrdemAsc(orcamento.getId());

        return PdfMicroservicoOrcamentoDescritivoPayload.builder()
                .empresa(PdfMicroservicoEmpresaPayload.builder()
                        .nome(empresa != null ? empresa.getNome() : "Studio")
                        .email(empresa != null ? empresa.getEmail() : null)
                        .whatsapp(empresa != null ? empresa.getWhatsapp() : null)
                        .logoUrl(empresa != null ? empresa.getLogoUrl() : null)
                        .build())
                .documento(PdfMicroservicoOrcamentoDescritivoPayload.Documento.builder()
                        // RN-053: o prefixo "ORC-" nunca aparece no PDF; só o número.
                        .numeroFormatado(String.valueOf(orcamento.getNumero()))
                        .titulo(orcamento.getTitulo())
                        .nomeCliente(orcamento.getCliente() != null ? orcamento.getCliente().getNome() : null)
                        .telefoneCliente(orcamento.getCliente() != null ? orcamento.getCliente().getWhatsapp() : null)
                        .dataEmissao(orcamento.getCreatedAt().format(DATA))
                        .observacoes(orcamento.getObservacoes())
                        .opcoes(opcoes.stream().map(this::opcao).toList())
                        .build())
                .build();
    }

    private PdfMicroservicoOrcamentoDescritivoPayload.Opcao opcao(OrcamentoDescritivoOpcao o) {
        String nome = o.getProduto() != null
                ? NomeMarca.rotulo(o.getProduto().getNome(), o.getProduto().getMarca())
                : NomeMarca.rotulo(o.getItemCatalogo().getNome(), o.getItemCatalogo().getMarca());
        return PdfMicroservicoOrcamentoDescritivoPayload.Opcao.builder()
                .nome(nome)
                .quantidadeUnidade(o.getQuantidadeUnidade())
                .precoReferencia(moeda(o.getPrecoReferencia()))
                .build();
    }

    private static String moeda(BigDecimal valor) {
        return "R$ " + MOEDA.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    private UUID getUsuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }
}
