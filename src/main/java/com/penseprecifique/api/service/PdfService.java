package com.penseprecifique.api.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.penseprecifique.api.domain.entity.Empresa;
import com.penseprecifique.api.domain.entity.Orcamento;
import com.penseprecifique.api.domain.entity.OrcamentoItem;
import com.penseprecifique.api.domain.entity.OrcamentoItemCustomizacao;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MetodoPagamento;
import com.penseprecifique.api.domain.enums.TipoDesconto;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.repository.EmpresaRepository;
import com.penseprecifique.api.repository.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.repository.OrcamentoItemRepository;
import com.penseprecifique.api.repository.OrcamentoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PdfService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DeviceRgb COR_CABECALHO = new DeviceRgb(52, 73, 94);
    private static final DeviceRgb COR_LINHA_ALT = new DeviceRgb(240, 240, 240);

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    public byte[] gerarPdfOrcamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId())
                .orElse(null);
        List<OrcamentoItem> itens = orcamentoItemRepository.findByOrcamentoId(orcamento.getId());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            adicionarCabecalho(doc, empresa, bold, regular);
            adicionarTituloOrcamento(doc, orcamento, bold, regular);
            adicionarItens(doc, itens, bold, regular);
            adicionarTotais(doc, orcamento, bold, regular);
            adicionarCondicoesPagamento(doc, orcamento, bold, regular);
            if (Boolean.TRUE.equals(orcamento.getSinalAtivo())) {
                adicionarSinal(doc, orcamento, bold, regular);
            }
            adicionarPrazos(doc, orcamento, bold, regular);
            if (orcamento.getObservacoes() != null && !orcamento.getObservacoes().isBlank()) {
                adicionarObservacoes(doc, orcamento, bold, regular);
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    private void adicionarCabecalho(Document doc, Empresa empresa, PdfFont bold, PdfFont regular) throws Exception {
        if (empresa == null) {
            doc.add(new Paragraph("Orçamento").setFont(bold).setFontSize(18));
            return;
        }

        Table tabCab = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .setWidth(UnitValue.createPercentValue(100));

        // Logo
        Cell celulaLogo = new Cell().setBorder(null);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()))
                        .setMaxHeight(60).setAutoScale(true);
                celulaLogo.add(logo);
            } catch (Exception ignored) {
                celulaLogo.add(new Paragraph("").setFont(regular));
            }
        }
        tabCab.addCell(celulaLogo);

        // Dados da empresa
        Cell celulaEmpresa = new Cell().setBorder(null).setTextAlignment(TextAlignment.RIGHT);
        celulaEmpresa.add(new Paragraph(empresa.getNome()).setFont(bold).setFontSize(14));
        if (empresa.getEmail() != null)
            celulaEmpresa.add(new Paragraph(empresa.getEmail()).setFont(regular).setFontSize(9));
        if (empresa.getWhatsapp() != null)
            celulaEmpresa.add(new Paragraph("WhatsApp: " + empresa.getWhatsapp()).setFont(regular).setFontSize(9));
        if (empresa.getEndereco() != null)
            celulaEmpresa.add(new Paragraph(empresa.getEndereco()).setFont(regular).setFontSize(9));
        tabCab.addCell(celulaEmpresa);

        doc.add(tabCab);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarTituloOrcamento(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("ORÇAMENTO Nº " + orcamento.getNumero())
                .setFont(bold).setFontSize(14)
                .setFontColor(COR_CABECALHO)
                .setTextAlignment(TextAlignment.CENTER));

        Table info = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100));

        info.addCell(celulaInfo("Cliente:", orcamento.getCliente().getNome(), bold, regular));
        String emissao = orcamento.getCreatedAt() != null
                ? orcamento.getCreatedAt().format(FMT_DATA) : "-";
        info.addCell(celulaInfo("Data de emissão:", emissao, bold, regular));

        String validade = orcamento.getDataValidade() != null
                ? orcamento.getDataValidade().format(FMT_DATA) : "Não informada";
        info.addCell(celulaInfo("Validade:", validade, bold, regular));
        info.addCell(new Cell().setBorder(null));

        doc.add(info);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarItens(Document doc, List<OrcamentoItem> itens, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("ITENS DO ORÇAMENTO").setFont(bold).setFontSize(11)
                .setFontColor(COR_CABECALHO));

        Table tabela = new Table(UnitValue.createPercentArray(new float[]{50, 10, 20, 20}))
                .setWidth(UnitValue.createPercentValue(100));

        // Cabeçalho da tabela
        for (String col : new String[]{"Produto", "Qtd", "Preço Unit.", "Subtotal"}) {
            tabela.addHeaderCell(new Cell()
                    .add(new Paragraph(col).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COR_CABECALHO)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        boolean alternar = false;
        for (OrcamentoItem item : itens) {
            DeviceRgb fundo = alternar ? COR_LINHA_ALT : null;
            alternar = !alternar;

            tabela.addCell(celulaTabela(item.getProduto().getNome(), regular, fundo, TextAlignment.LEFT));
            tabela.addCell(celulaTabela(String.valueOf(item.getQuantidade()), regular, fundo, TextAlignment.CENTER));
            tabela.addCell(celulaTabela(moeda(item.getPrecoUnitario()), regular, fundo, TextAlignment.RIGHT));
            tabela.addCell(celulaTabela(moeda(item.getSubtotal()), regular, fundo, TextAlignment.RIGHT));

            List<OrcamentoItemCustomizacao> customizacoes =
                    orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId());
            for (OrcamentoItemCustomizacao cust : customizacoes) {
                tabela.addCell(celulaTabela("  + " + cust.getProduto().getNome(),
                        regular, fundo, TextAlignment.LEFT));
                tabela.addCell(celulaTabela(String.valueOf(cust.getQuantidade()),
                        regular, fundo, TextAlignment.CENTER));
                tabela.addCell(celulaTabela(moeda(cust.getPrecoUnitario()),
                        regular, fundo, TextAlignment.RIGHT));
                tabela.addCell(celulaTabela(moeda(cust.getSubtotal()),
                        regular, fundo, TextAlignment.RIGHT));
            }
        }

        doc.add(tabela);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarTotais(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        Table totais = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .setWidth(UnitValue.createPercentValue(100));

        totais.addCell(celulaTotal("Subtotal:", TextAlignment.RIGHT, regular));
        totais.addCell(celulaTotal(moeda(orcamento.getSubtotal()), TextAlignment.RIGHT, regular));

        String descLabel = TipoDesconto.PERCENTUAL == orcamento.getDescontoTipo()
                ? "Desconto (" + orcamento.getDescontoValor().stripTrailingZeros().toPlainString() + "%):"
                : "Desconto:";
        BigDecimal desconto = orcamento.getSubtotal().subtract(orcamento.getTotal());
        totais.addCell(celulaTotal(descLabel, TextAlignment.RIGHT, regular));
        totais.addCell(celulaTotal("- " + moeda(desconto.max(BigDecimal.ZERO)), TextAlignment.RIGHT, regular));

        totais.addCell(new Cell()
                .add(new Paragraph("TOTAL:").setFont(bold).setFontSize(11).setFontColor(COR_CABECALHO))
                .setTextAlignment(TextAlignment.RIGHT).setBorder(null));
        totais.addCell(new Cell()
                .add(new Paragraph(moeda(orcamento.getTotal())).setFont(bold).setFontSize(11).setFontColor(COR_CABECALHO))
                .setTextAlignment(TextAlignment.RIGHT).setBorder(null));

        doc.add(totais);
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarCondicoesPagamento(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("CONDIÇÕES DE PAGAMENTO").setFont(bold).setFontSize(11)
                .setFontColor(COR_CABECALHO));
        String metodo = formatarMetodoPagamento(orcamento.getMetodoPagamento());
        doc.add(new Paragraph("Método: " + metodo).setFont(regular).setFontSize(10));
        if (orcamento.getMetodoPagamento() == MetodoPagamento.OUTRO
                && orcamento.getMetodoPagamentoObs() != null) {
            doc.add(new Paragraph(orcamento.getMetodoPagamentoObs()).setFont(regular).setFontSize(9));
        }
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarSinal(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("SINAL").setFont(bold).setFontSize(11).setFontColor(COR_CABECALHO));
        String pct = orcamento.getPercentualSinal() != null
                ? " (" + orcamento.getPercentualSinal().stripTrailingZeros().toPlainString() + "%)" : "";
        doc.add(new Paragraph("Valor do sinal: " + moeda(orcamento.getValorSinal()) + pct)
                .setFont(regular).setFontSize(10));
        if (orcamento.getValorSinal() != null) {
            BigDecimal restante = orcamento.getTotal().subtract(orcamento.getValorSinal());
            doc.add(new Paragraph("Restante: " + moeda(restante)).setFont(regular).setFontSize(10));
        }
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarPrazos(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("PRAZO DE PRODUÇÃO").setFont(bold).setFontSize(11).setFontColor(COR_CABECALHO));
        doc.add(new Paragraph("Prazo: " + orcamento.getPrazoProducaoDias() + " dias úteis")
                .setFont(regular).setFontSize(10));
        String inicio = Boolean.TRUE.equals(orcamento.getInicioAssimQueAprovado())
                ? "Assim que aprovado"
                : (orcamento.getDataInicioEstimada() != null
                        ? orcamento.getDataInicioEstimada().format(FMT_DATA)
                        : "-");
        doc.add(new Paragraph("Início estimado: " + inicio).setFont(regular).setFontSize(10));
        if (orcamento.getDataAprovacao() != null) {
            doc.add(new Paragraph("Data de aprovação: " + orcamento.getDataAprovacao().format(FMT_DATA))
                    .setFont(regular).setFontSize(10));
        }
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private void adicionarObservacoes(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("OBSERVAÇÕES").setFont(bold).setFontSize(11).setFontColor(COR_CABECALHO));
        doc.add(new Paragraph(orcamento.getObservacoes()).setFont(regular).setFontSize(10));
    }

    // --- helpers ---

    private Cell celulaInfo(String label, String valor, PdfFont bold, PdfFont regular) {
        Paragraph p = new Paragraph()
                .add(new com.itextpdf.layout.element.Text(label).setFont(bold))
                .add(new com.itextpdf.layout.element.Text(" " + valor).setFont(regular))
                .setFontSize(10);
        return new Cell().setBorder(null).add(p);
    }

    private Cell celulaTabela(String texto, PdfFont font, DeviceRgb fundo, TextAlignment align) {
        Cell c = new Cell()
                .add(new Paragraph(texto).setFont(font).setFontSize(9))
                .setTextAlignment(align)
                .setPadding(3);
        if (fundo != null) c.setBackgroundColor(fundo);
        return c;
    }

    private Cell celulaTotal(String texto, TextAlignment align, PdfFont font) {
        return new Cell()
                .add(new Paragraph(texto).setFont(font).setFontSize(10))
                .setTextAlignment(align)
                .setBorder(null);
    }

    private String moeda(BigDecimal valor) {
        if (valor == null) return "R$ 0,00";
        return String.format("R$ %,.2f", valor);
    }

    private String formatarMetodoPagamento(MetodoPagamento metodo) {
        return switch (metodo) {
            case PIX -> "Pix";
            case DINHEIRO -> "Dinheiro";
            case CREDITO -> "Cartão de crédito";
            case DEBITO -> "Cartão de débito";
            case TRANSFERENCIA -> "Transferência bancária";
            case BOLETO -> "Boleto";
            case OUTRO -> "Outro";
        };
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
