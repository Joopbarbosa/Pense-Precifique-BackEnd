package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemCustomizacaoRepository;
import com.penseprecifique.api.caixa.VendaCaixaItemRepository;
import com.penseprecifique.api.caixa.VendaCaixaPagamentoRepository;
import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixa;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItem;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaItemCustomizacao;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaPagamento;
import com.penseprecifique.api.shared.domain.enums.StatusVendaCaixa;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoEmpresaPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoItemPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoReciboCaixaPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.19.0 #1170 (RN-NOVA-33, DT-NOVA-9) — monta o payload do recibo da venda do Caixa. Mesmo molde de
 * {@link ReciboPagamentoPdfPayloadService}: leitura do banco aqui, chamada HTTP ao microsserviço fora da
 * transação. Só vende CONCLUIDA; venda de outra conta é "não encontrada". Nunca altera a venda.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReciboCaixaPdfPayloadService {

    private static final DecimalFormat MOEDA = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.of("pt", "BR")));
    private static final DecimalFormat QUANTIDADE = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.of("pt", "BR")));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final VendaCaixaRepository vendaCaixaRepository;
    private final VendaCaixaItemRepository vendaCaixaItemRepository;
    private final VendaCaixaItemCustomizacaoRepository customizacaoRepository;
    private final VendaCaixaPagamentoRepository pagamentoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    public PdfMicroservicoReciboCaixaPayload montarPayload(UUID vendaId) {
        UUID usuarioId = getUsuarioId();
        VendaCaixa venda = vendaCaixaRepository.findByIdAndUsuarioId(vendaId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda de Caixa não encontrada"));
        if (venda.getStatus() != StatusVendaCaixa.CONCLUIDA) {
            throw new BusinessException("Esta venda foi cancelada.");
        }
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId).orElse(null);

        List<VendaCaixaItem> itens = vendaCaixaItemRepository.findByVendaCaixaId(venda.getId());
        List<UUID> itemIds = itens.stream().map(VendaCaixaItem::getId).toList();
        Map<UUID, List<VendaCaixaItemCustomizacao>> customizacoes = itemIds.isEmpty() ? Map.of()
                : customizacaoRepository.findByVendaCaixaItemIdIn(itemIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getVendaCaixaItem().getId()));
        List<VendaCaixaPagamento> pagamentos = pagamentoRepository.findByVendaCaixaId(venda.getId());

        return PdfMicroservicoReciboCaixaPayload.builder()
                .empresa(PdfMicroservicoEmpresaPayload.builder()
                        .nome(empresa != null ? empresa.getNome() : "Studio")
                        .email(empresa != null ? empresa.getEmail() : null)
                        .whatsapp(empresa != null ? empresa.getWhatsapp() : null)
                        .logoUrl(empresa != null ? empresa.getLogoUrl() : null)
                        .build())
                .documento(PdfMicroservicoReciboCaixaPayload.Documento.builder()
                        .numeroFormatado(IdentificadorFormatter.formatar("CX", venda.getNumero()))
                        .dataVenda(venda.getDataVenda().format(DATA))
                        .nomeCliente(venda.getCliente() != null ? venda.getCliente().getNome() : null)
                        .telefoneCliente(venda.getCliente() != null ? venda.getCliente().getWhatsapp() : null)
                        .itens(itens.stream().map(i -> item(i, customizacoes.getOrDefault(i.getId(), List.of()))).toList())
                        .subtotal(moeda(venda.getSubtotal()))
                        .desconto(desconto(venda))
                        .total(moeda(venda.getTotal()))
                        .pagamentos(pagamentos.stream().map(this::pagamento).toList())
                        .troco(venda.getTroco() != null && venda.getTroco().signum() > 0 ? moeda(venda.getTroco()) : null)
                        .build())
                .build();
    }

    private PdfMicroservicoItemPayload item(VendaCaixaItem i, List<VendaCaixaItemCustomizacao> customizacoes) {
        String nome = i.getItemCatalogo() != null
                ? NomeMarca.rotulo(i.getItemCatalogo().getNome(), i.getItemCatalogo().getMarca())
                : NomeMarca.rotulo(i.getProduto().getNome(), i.getProduto().getMarca());
        String custom = customizacoes.isEmpty() ? null : customizacoes.stream()
                .map(c -> c.getProduto().getNome()).collect(Collectors.joining(", "));
        return PdfMicroservicoItemPayload.builder()
                .nomeProduto(nome)
                .customizacoes(custom)
                .quantidade(QUANTIDADE.format(i.getQuantidade()))
                .precoUnitario(moeda(i.getPrecoUnitario()))
                .subtotal(moeda(i.getSubtotal()))
                .build();
    }

    private PdfMicroservicoReciboCaixaPayload.Pagamento pagamento(VendaCaixaPagamento p) {
        String metodo = CompraMapper.rotulo(p.getMetodoPagamento());
        if (p.getParcelas() != null && p.getParcelas() > 1) {
            metodo += " em " + p.getParcelas() + "x";
        }
        return PdfMicroservicoReciboCaixaPayload.Pagamento.builder().metodo(metodo).valor(moeda(p.getValor())).build();
    }

    private String desconto(VendaCaixa v) {
        if (v.getDescontoTipo() == null || v.getDescontoValor() == null || v.getDescontoValor().signum() <= 0) {
            return null;
        }
        BigDecimal valor = v.getDescontoTipo() == TipoDesconto.PERCENTUAL
                ? v.getSubtotal().multiply(v.getDescontoValor()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : v.getDescontoValor();
        return "- " + moeda(valor);
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
