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
import com.penseprecifique.api.domain.entity.ReciboPagamento;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MetodoPagamento;
import com.penseprecifique.api.domain.enums.StatusOrcamento;
import com.penseprecifique.api.domain.enums.TipoCancelamento;
import com.penseprecifique.api.domain.enums.TipoDesconto;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.repository.EmpresaRepository;
import com.penseprecifique.api.repository.OrcamentoItemCustomizacaoRepository;
import com.penseprecifique.api.repository.OrcamentoItemRepository;
import com.penseprecifique.api.repository.OrcamentoRepository;
import com.penseprecifique.api.repository.ReciboPagamentoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PdfService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    // Cores exatas do preview
    private static final DeviceRgb TEAL = new DeviceRgb(42, 157, 143);       // #2A9D8F
    private static final DeviceRgb ORANGE = new DeviceRgb(249, 115, 22);     // #F97316
    private static final DeviceRgb DARK_BROWN = new DeviceRgb(58, 55, 47);   // #3A372F
    private static final DeviceRgb MED_BROWN = new DeviceRgb(92, 89, 79);    // #5C594F
    private static final DeviceRgb LIGHT_BROWN = new DeviceRgb(124, 120, 111); // #7C786F
    private static final DeviceRgb BORDER = new DeviceRgb(240, 238, 233);    // #F0EEE9
    private static final DeviceRgb LIGHT_BORDER = new DeviceRgb(176, 172, 164); // #B0ACA4

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoItemRepository orcamentoItemRepository;
    private final OrcamentoItemCustomizacaoRepository orcamentoItemCustomizacaoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReciboPagamentoRepository reciboPagamentoRepository;

    public byte[] gerarReciboSinal(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus().ordinal() < StatusOrcamento.SINAL_PAGO.ordinal()) {
            throw new BusinessException("Recibo do sinal só disponível a partir do status SINAL_PAGO");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            doc.add(new Paragraph("RECIBO DE SINAL")
                    .setFont(bold).setFontSize(18)
                    .setFontColor(TEAL));
            doc.add(new Paragraph("Orçamento Nº " + orcamento.getNumero())
                    .setFont(bold).setFontSize(14)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Cliente: " + orcamento.getCliente().getNome())
                    .setFont(regular).setFontSize(11));

            String metodoSinal = orcamento.getMetodoSinalRecebido() != null
                    ? formatarMetodoPagamento(orcamento.getMetodoSinalRecebido()) : "-";
            doc.add(new Paragraph("Método recebido: " + metodoSinal)
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(4));

            doc.add(new Paragraph("Valor do sinal recebido: " + moeda(orcamento.getValorSinal()))
                    .setFont(bold).setFontSize(13)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));


            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar recibo do sinal: " + e.getMessage());
        }
    }

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
            doc.setMargins(28, 28, 28, 28);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            // Cabeçalho com logo/nome da empresa
            adicionarCabecalho(doc, empresa, usuario, orcamento, bold, regular);

            // Meta e cliente em 3 colunas
            adicionarMetaCliente(doc, orcamento, bold, regular);

            // Método de pagamento
            adicionarMetodoPagamento(doc, orcamento, bold, regular);

            // Tabela de itens
            adicionarTabelaItens(doc, itens, bold, regular);

            // Sinal se ativo
            if (Boolean.TRUE.equals(orcamento.getSinalAtivo())) {
                adicionarSecaoSinal(doc, orcamento, bold, regular);
            }

            // Totais
            adicionarSecaoTotais(doc, orcamento, bold, regular);

            // Observações
            if (orcamento.getObservacoes() != null && !orcamento.getObservacoes().isBlank()) {
                adicionarObservacoes(doc, orcamento, bold, regular);
            }

            // Rodapé
            adicionarRodape(doc, orcamento, bold, regular);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar PDF: " + e.getMessage());
        }
    }

    private void adicionarCabecalho(Document doc, Empresa empresa, Usuario usuario, Orcamento orcamento, PdfFont bold, PdfFont regular) throws Exception {
        // Cabeçalho: Logo + dados da empresa à esquerda | Número do orçamento à direita
        Table headerTable = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        // Lado esquerdo: Logo + nome + contato
        Cell leftCell = new Cell().setBorder(null).setPadding(0);
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{15, 85}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        // Logo (simplificado como texto/ícone)
        Cell logoCell = new Cell().setBorder(null).setPadding(0);
        if (empresa != null && empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()))
                        .setMaxHeight(40).setAutoScale(true);
                logoCell.add(logo);
            } catch (Exception ignored) {
                logoCell.add(new Paragraph("💡").setFontSize(28));
            }
        } else {
            logoCell.add(new Paragraph("💡").setFontSize(28));
        }
        infoTable.addCell(logoCell);

        // Nome, email, telefone
        Cell companyInfoCell = new Cell().setBorder(null).setPadding(0);
        String nomeDaEmpresa = empresa != null ? empresa.getNome() : "Studio";
        companyInfoCell.add(new Paragraph(nomeDaEmpresa).setFont(bold).setFontSize(13).setFontColor(DARK_BROWN));

        String email = empresa != null && empresa.getEmail() != null ? empresa.getEmail() : usuario.getEmail();
        String phone = empresa != null && empresa.getWhatsapp() != null ? empresa.getWhatsapp() : "";

        Paragraph contato = new Paragraph();
        contato.add(new com.itextpdf.layout.element.Text("📧 " + email).setFontSize(9).setFontColor(LIGHT_BROWN));
        if (!phone.isBlank()) {
            contato.add(new com.itextpdf.layout.element.Text("\n📱 " + phone).setFontSize(9).setFontColor(LIGHT_BROWN));
        }
        companyInfoCell.add(contato);
        infoTable.addCell(companyInfoCell);

        leftCell.add(infoTable);
        headerTable.addCell(leftCell);

        // Lado direito: número do orçamento
        Cell rightCell = new Cell().setBorder(null).setTextAlignment(TextAlignment.RIGHT).setPadding(0);
        rightCell.add(new Paragraph("Orçamento").setFont(regular).setFontSize(8).setFontColor(LIGHT_BROWN));
        String numPadded = String.format("%04d", orcamento.getNumero());
        rightCell.add(new Paragraph("#" + numPadded).setFont(bold).setFontSize(20).setFontColor(ORANGE));
        headerTable.addCell(rightCell);

        doc.add(headerTable);

        // Linha de separação
        Table divider = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100));
        Cell dividerCell = new Cell().setHeight(1).setBackgroundColor(TEAL).setBorder(null)
                .setMargins(0, 0, 0, 0).setPadding(0);
        divider.addCell(dividerCell);
        doc.add(divider);
        doc.add(new Paragraph("\n").setFontSize(8));
    }

    private void adicionarMetaCliente(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        String dataEmissao = orcamento.getCreatedAt() != null
                ? orcamento.getCreatedAt().format(FMT_DATA) : "-";
        String dataValidade = orcamento.getDataValidade() != null
                ? orcamento.getDataValidade().format(FMT_DATA) : "Não definida";

        // 3 colunas: Datas, Prazo de Produção, Cliente
        Table metaTable = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        // Coluna 1: Datas
        Cell datasCell = new Cell().setBorder(null).setPadding(8);
        datasCell.add(new Paragraph("Datas").setFont(regular).setFontSize(7.5f).setFontColor(LIGHT_BORDER));
        Paragraph dataPara = new Paragraph();
        dataPara.add(new com.itextpdf.layout.element.Text("Emissão: ").setFont(regular).setFontSize(10)).
                add(new com.itextpdf.layout.element.Text(dataEmissao).setFont(bold).setFontSize(10));
        dataPara.setFontColor(DARK_BROWN).setMarginBottom(5);
        datasCell.add(dataPara);
        Paragraph validadePara = new Paragraph();
        validadePara.add(new com.itextpdf.layout.element.Text("Validade: ").setFont(regular).setFontSize(10)).
                add(new com.itextpdf.layout.element.Text(dataValidade).setFont(bold).setFontSize(10));
        validadePara.setFontColor(DARK_BROWN);
        datasCell.add(validadePara);
        metaTable.addCell(datasCell);

        // Coluna 2: Prazo de Produção
        Cell prazoCell = new Cell().setBorder(null).setPadding(8);
        prazoCell.add(new Paragraph("Prazo de produção").setFont(regular).setFontSize(7.5f).setFontColor(LIGHT_BORDER));
        String prazoDias = orcamento.getPrazoProducaoDias() != null ? orcamento.getPrazoProducaoDias() + " dias úteis" : "-";
        prazoCell.add(new Paragraph(prazoDias).setFont(bold).setFontSize(11).setFontColor(TEAL));
        String inicio = Boolean.TRUE.equals(orcamento.getInicioAssimQueAprovado())
                ? "Assim que aprovado"
                : (orcamento.getDataInicioEstimada() != null
                        ? orcamento.getDataInicioEstimada().format(FMT_DATA)
                        : "—");
        prazoCell.add(new Paragraph("Início: " + inicio).setFont(regular).setFontSize(9).setFontColor(MED_BROWN).setMarginTop(3));
        metaTable.addCell(prazoCell);

        // Coluna 3: Cliente
        Cell clienteCell = new Cell().setBorder(null).setPadding(8);
        clienteCell.add(new Paragraph("Cliente").setFont(regular).setFontSize(7.5f).setFontColor(LIGHT_BORDER));
        clienteCell.add(new Paragraph(orcamento.getCliente().getNome()).setFont(bold).setFontSize(10).setFontColor(DARK_BROWN));
        metaTable.addCell(clienteCell);

        doc.add(metaTable);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private void adicionarMetodoPagamento(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        String metodo = formatarMetodoPagamento(orcamento.getMetodoPagamento());

        Table pagtoTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell pagtoCell = new Cell().setBorder(null).setPadding(8);
        pagtoCell.add(new Paragraph("Método de pagamento").setFont(regular).setFontSize(7.5f).setFontColor(LIGHT_BORDER));

        Paragraph metodoPara = new Paragraph();
        metodoPara.add(new com.itextpdf.layout.element.Text("💳 ").setFontSize(11)).
                add(new com.itextpdf.layout.element.Text(metodo).setFont(bold).setFontSize(10).setFontColor(DARK_BROWN));
        pagtoCell.add(metodoPara);

        pagtoTable.addCell(pagtoCell);
        doc.add(pagtoTable);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private void adicionarTabelaItens(Document doc, List<OrcamentoItem> itens, PdfFont bold, PdfFont regular) {
        Table tabela = new Table(UnitValue.createPercentArray(new float[]{35, 25, 10, 15, 15}))
                .setWidth(UnitValue.createPercentValue(100));

        // Cabeçalho
        String[] headers = {"Produto", "Customizações", "Qtd", "Valor unit.", "Total"};
        for (String header : headers) {
            tabela.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(bold).setFontSize(8.5f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(TEAL)
                    .setTextAlignment(header.equals("Produto") || header.equals("Customizações") ? TextAlignment.LEFT : TextAlignment.RIGHT)
                    .setPadding(7));
        }

        boolean alternar = false;
        for (OrcamentoItem item : itens) {
            DeviceRgb fundo = alternar ? new DeviceRgb(251, 250, 248) : null; // #FBFAF8
            alternar = !alternar;

            // Produto
            tabela.addCell(new Cell()
                    .add(new Paragraph(item.getProduto().getNome()).setFont(bold).setFontSize(9.5f).setFontColor(DARK_BROWN))
                    .setBackgroundColor(fundo).setBorder(null).setPadding(6));

            // Customizações
            List<OrcamentoItemCustomizacao> customizacoes = orcamentoItemCustomizacaoRepository.findByOrcamentoItemId(item.getId());
            String custStr = customizacoes.isEmpty() ? "—" : customizacoes.stream()
                    .map(c -> c.getProduto().getNome()).reduce((a, b) -> a + ", " + b).orElse("—");
            DeviceRgb custColor = customizacoes.isEmpty() ? new DeviceRgb(192, 188, 180) : MED_BROWN; // #C0BCB4
            tabela.addCell(new Cell()
                    .add(new Paragraph(custStr).setFont(regular).setFontSize(9).setFontColor(custColor))
                    .setBackgroundColor(fundo).setBorder(null).setPadding(6).setTextAlignment(TextAlignment.LEFT));

            // Qtd
            tabela.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(item.getQuantidade())).setFont(regular).setFontSize(9).setFontColor(DARK_BROWN))
                    .setBackgroundColor(fundo).setBorder(null).setPadding(6).setTextAlignment(TextAlignment.CENTER));

            // Valor unitário
            tabela.addCell(new Cell()
                    .add(new Paragraph(moeda(item.getPrecoUnitario())).setFont(regular).setFontSize(9).setFontColor(DARK_BROWN))
                    .setBackgroundColor(fundo).setBorder(null).setPadding(6).setTextAlignment(TextAlignment.RIGHT));

            // Subtotal
            tabela.addCell(new Cell()
                    .add(new Paragraph(moeda(item.getSubtotal())).setFont(bold).setFontSize(9).setFontColor(DARK_BROWN))
                    .setBackgroundColor(fundo).setBorder(null).setPadding(6).setTextAlignment(TextAlignment.RIGHT));
        }

        doc.add(tabela);
        doc.add(new Paragraph("\n").setFontSize(8));
    }

    private void adicionarSecaoSinal(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        // Box de entrada solicitada
        Table sinalBox = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell sinalCell = new Cell().setBorder(null).setPadding(12);
        sinalCell.setBackgroundColor(new DeviceRgb(251, 250, 248)); // #FBFAF8

        // Título
        Paragraph sinalTitle = new Paragraph("Entrada solicitada").setFont(bold).setFontSize(10.5f).setFontColor(DARK_BROWN);
        sinalCell.add(sinalTitle);

        // Descrição
        String descSinal = orcamento.getPercentualSinal() != null
                ? orcamento.getPercentualSinal().stripTrailingZeros().toPlainString() + "%"
                : moeda(orcamento.getValorSinal());
        String descricao = "Para iniciar a produção, solicitamos o pagamento de " + descSinal + " do valor total.";
        sinalCell.add(new Paragraph(descricao).setFont(regular).setFontSize(8.5f).setFontColor(MED_BROWN).setMarginTop(6).setMarginBottom(6));

        // Valores
        Table valoresTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell sinalValCell = new Cell().setBorder(null).setPadding(6);
        sinalValCell.add(new Paragraph("Valor do sinal").setFont(bold).setFontSize(7).setFontColor(TEAL));
        sinalValCell.add(new Paragraph(moeda(orcamento.getValorSinal())).setFont(bold).setFontSize(12).setFontColor(TEAL).setMarginTop(2));
        valoresTable.addCell(sinalValCell);

        Cell restanteCell = new Cell().setBorder(null).setPadding(6);
        restanteCell.add(new Paragraph("Restante na entrega").setFont(bold).setFontSize(7).setFontColor(LIGHT_BROWN));
        BigDecimal restante = orcamento.getTotal().subtract(orcamento.getValorSinal() != null ? orcamento.getValorSinal() : BigDecimal.ZERO);
        restanteCell.add(new Paragraph(moeda(restante)).setFont(bold).setFontSize(12).setFontColor(DARK_BROWN).setMarginTop(2));
        valoresTable.addCell(restanteCell);

        sinalCell.add(valoresTable);
        sinalBox.addCell(sinalCell);

        doc.add(sinalBox);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private void adicionarSecaoTotais(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        // Alinha à direita e com largura limitada
        Table wrapper = new Table(UnitValue.createPercentArray(new float[]{70, 30}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell spacerCell = new Cell().setBorder(null);
        wrapper.addCell(spacerCell);

        // Tabela de totais
        Table totaisTable = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        // Subtotal
        Cell subtotalLabel = new Cell().setBorder(null).setPadding(5);
        subtotalLabel.add(new Paragraph("Subtotal").setFont(regular).setFontSize(9).setFontColor(MED_BROWN));
        totaisTable.addCell(subtotalLabel);

        Cell subtotalValue = new Cell().setBorder(null).setPadding(5);
        subtotalValue.add(new Paragraph(moeda(orcamento.getSubtotal())).setFont(regular).setFontSize(9).setFontColor(MED_BROWN));
        subtotalValue.setTextAlignment(TextAlignment.RIGHT);
        totaisTable.addCell(subtotalValue);

        // Desconto
        BigDecimal desconto = orcamento.getSubtotal().subtract(orcamento.getTotal());
        if (orcamento.getDescontoValor() != null && orcamento.getDescontoValor().compareTo(BigDecimal.ZERO) > 0) {
            Cell descontoLabel = new Cell().setBorder(null).setPadding(5);
            String descLabel = TipoDesconto.PERCENTUAL == orcamento.getDescontoTipo()
                    ? "Desconto (" + orcamento.getDescontoValor().stripTrailingZeros().toPlainString() + "%)"
                    : "Desconto";
            descontoLabel.add(new Paragraph(descLabel).setFont(regular).setFontSize(9).setFontColor(new DeviceRgb(192, 73, 43))); // #C0492B
            totaisTable.addCell(descontoLabel);

            Cell descontoValue = new Cell().setBorder(null).setPadding(5);
            descontoValue.add(new Paragraph("− " + moeda(desconto.max(BigDecimal.ZERO))).setFont(regular).setFontSize(9).setFontColor(new DeviceRgb(192, 73, 43)));
            descontoValue.setTextAlignment(TextAlignment.RIGHT);
            totaisTable.addCell(descontoValue);
        }

        // Sinal solicitado
        if (Boolean.TRUE.equals(orcamento.getSinalAtivo())) {
            Cell sinalLabel = new Cell().setBorder(null).setPadding(5).setMarginTop(4).setMarginBottom(4);
            sinalLabel.add(new Paragraph("💳 Sinal solicitado").setFont(bold).setFontSize(8.5f).setFontColor(TEAL));
            sinalLabel.setBackgroundColor(new DeviceRgb(251, 247, 238)); // subtle teal bg
            totaisTable.addCell(sinalLabel);

            Cell sinalValue = new Cell().setBorder(null).setPadding(5).setMarginTop(4).setMarginBottom(4);
            sinalValue.add(new Paragraph(moeda(orcamento.getValorSinal())).setFont(bold).setFontSize(9).setFontColor(TEAL));
            sinalValue.setTextAlignment(TextAlignment.RIGHT);
            sinalValue.setBackgroundColor(new DeviceRgb(251, 247, 238));
            totaisTable.addCell(sinalValue);
        }

        // Total em destaque
        Cell totalLabel = new Cell().setBorder(null).setPadding(7).setMarginTop(6);
        totalLabel.add(new Paragraph("Total").setFont(bold).setFontSize(11).setFontColor(DARK_BROWN));
        totalLabel.setBackgroundColor(new DeviceRgb(255, 237, 213)); // laranja muito claro
        totaisTable.addCell(totalLabel);

        Cell totalValue = new Cell().setBorder(null).setPadding(7).setMarginTop(6);
        totalValue.add(new Paragraph(moeda(orcamento.getTotal())).setFont(bold).setFontSize(15).setFontColor(ORANGE));
        totalValue.setTextAlignment(TextAlignment.RIGHT);
        totalValue.setBackgroundColor(new DeviceRgb(255, 237, 213));
        totaisTable.addCell(totalValue);

        wrapper.addCell(totaisTable);
        doc.add(wrapper);
        doc.add(new Paragraph("\n").setFontSize(6));
    }

    private void adicionarRodape(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("\n").setFontSize(12));

        String dataValidade = orcamento.getDataValidade() != null
                ? orcamento.getDataValidade().format(FMT_DATA)
                : "Não definida";

        Table rodapeTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell rodapeCell = new Cell().setBorder(null).setPadding(0);
        Paragraph rodapePara = new Paragraph();
        rodapePara.add(new com.itextpdf.layout.element.Text("📄 ").setFontSize(9)).
                add(new com.itextpdf.layout.element.Text("Este orçamento é válido até ").setFont(regular).setFontSize(8.5f).setFontColor(LIGHT_BROWN)).
                add(new com.itextpdf.layout.element.Text(dataValidade).setFont(bold).setFontSize(8.5f).setFontColor(MED_BROWN));

        rodapePara.setMarginBottom(6);
        rodapeCell.add(rodapePara);

        Paragraph rodapeLinha2 = new Paragraph();
        rodapeLinha2.add(new com.itextpdf.layout.element.Text("Gerado por ").setFont(regular).setFontSize(8).setFontColor(LIGHT_BROWN)).
                add(new com.itextpdf.layout.element.Text("Pense & Precifique").setFont(bold).setFontSize(8).setFontColor(MED_BROWN));
        rodapeLinha2.setMarginBottom(8);
        rodapeCell.add(rodapeLinha2);

        Paragraph aviso = new Paragraph("Em caso de cancelamento após aprovação, poderá ser cobrado uma taxa referente aos materiais e tempo já investidos na produção.")
                .setFont(regular).setFontSize(7.5f).setFontColor(new DeviceRgb(176, 172, 164));
        rodapeCell.add(aviso);

        rodapeTable.addCell(rodapeCell);
        doc.add(rodapeTable);
    }

    private void adicionarObservacoes(Document doc, Orcamento orcamento, PdfFont bold, PdfFont regular) {
        Table obsBox = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(null);

        Cell obsCell = new Cell().setBorder(null).setPadding(12);
        obsCell.setBackgroundColor(new DeviceRgb(251, 250, 248)); // #FBFAF8

        obsCell.add(new Paragraph("Observações").setFont(bold).setFontSize(9).setFontColor(LIGHT_BORDER).setMarginBottom(6));
        obsCell.add(new Paragraph(orcamento.getObservacoes()).setFont(regular).setFontSize(9).setFontColor(DARK_BROWN));

        obsBox.addCell(obsCell);
        doc.add(obsBox);
    }

    // --- helpers ---


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

    public byte[] gerarPdfMulta(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getCancelamentoTipo() != TipoCancelamento.MULTA) {
            throw new BusinessException("PDF de multa só disponível para cancelamentos com multa");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            doc.add(new Paragraph("NOTIFICAÇÃO DE CANCELAMENTO")
                    .setFont(bold).setFontSize(18)
                    .setFontColor(TEAL));
            doc.add(new Paragraph("Orçamento Nº " + orcamento.getNumero())
                    .setFont(bold).setFontSize(14)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Cliente: " + orcamento.getCliente().getNome())
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(4));

            String dataCancelamento = LocalDateTime.now().format(FMT_DATA);
            doc.add(new Paragraph("Data de cancelamento: " + dataCancelamento)
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Valor total do orçamento: " + moeda(orcamento.getTotal()))
                    .setFont(regular).setFontSize(11));

            String pctMulta = orcamento.getPercentualMulta() != null
                    ? orcamento.getPercentualMulta().stripTrailingZeros().toPlainString() : "0";
            doc.add(new Paragraph("Percentual de multa: " + pctMulta + "%")
                    .setFont(regular).setFontSize(11));

            BigDecimal valorMulta = orcamento.getTotal()
                    .multiply(orcamento.getPercentualMulta() != null ? orcamento.getPercentualMulta() : BigDecimal.ZERO)
                    .divide(new BigDecimal(100), 2, java.math.RoundingMode.HALF_UP);
            doc.add(new Paragraph("Valor da multa: " + moeda(valorMulta))
                    .setFont(bold).setFontSize(13)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));


            doc.close();
            return baos.toByteArray();
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar PDF de multa: " + e.getMessage());
        }
    }

    public byte[] gerarReciboEstornoSinal(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (!Boolean.TRUE.equals(orcamento.getEstornoSinal())) {
            throw new BusinessException("Recibo de estorno só disponível para cancelamentos com estorno de sinal");
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            doc.add(new Paragraph("RECIBO DE ESTORNO")
                    .setFont(bold).setFontSize(18)
                    .setFontColor(TEAL));
            doc.add(new Paragraph("Orçamento Nº " + orcamento.getNumero())
                    .setFont(bold).setFontSize(14)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Cliente: " + orcamento.getCliente().getNome())
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(4));

            doc.add(new Paragraph("Valor estornado: " + moeda(orcamento.getValorSinal()))
                    .setFont(bold).setFontSize(13)
                    .setFontColor(ORANGE));

            String dataEstorno = orcamento.getDataEstornoSinal() != null
                    ? orcamento.getDataEstornoSinal().format(FMT_DATA) : "-";
            doc.add(new Paragraph("Data do estorno: " + dataEstorno)
                    .setFont(regular).setFontSize(11));

            doc.close();
            return baos.toByteArray();
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar recibo de estorno: " + e.getMessage());
        }
    }

    public byte[] gerarReciboPagamento(UUID orcamentoId) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        if (orcamento.getStatus() != StatusOrcamento.PAGO) {
            throw new BusinessException("Recibo de pagamento só disponível para orçamentos com status PAGO");
        }

        ReciboPagamento recibo = reciboPagamentoRepository.findByOrcamentoId(orcamentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de pagamento não encontrado"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 36, 36, 36);

            PdfFont bold = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont regular = PdfFontFactory.createFont("Helvetica");

            doc.add(new Paragraph("RECIBO DE PAGAMENTO")
                    .setFont(bold).setFontSize(18)
                    .setFontColor(TEAL));
            doc.add(new Paragraph("Orçamento Nº " + orcamento.getNumero())
                    .setFont(bold).setFontSize(14)
                    .setFontColor(ORANGE));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Cliente: " + orcamento.getCliente().getNome())
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(4));

            String metodo = formatarMetodoPagamento(orcamento.getMetodoPagamento());
            doc.add(new Paragraph("Método de pagamento: " + metodo)
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("\n").setFontSize(6));

            doc.add(new Paragraph("Valor total: " + moeda(recibo.getValorTotal()))
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("Sinal pago: " + moeda(recibo.getValorSinalPago()))
                    .setFont(regular).setFontSize(11));
            doc.add(new Paragraph("Restante pago: " + moeda(recibo.getValorRestantePago()))
                    .setFont(regular).setFontSize(11));

            Table totalTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                    .setWidth(UnitValue.createPercentValue(100));
            Cell totalCell = new Cell()
                    .add(new Paragraph("TOTAL QUITADO: " + moeda(recibo.getTotalQuitado()))
                            .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(ORANGE).setTextAlignment(TextAlignment.CENTER)
                    .setPadding(8);
            totalTable.addCell(totalCell);
            doc.add(totalTable);
            doc.add(new Paragraph("\n").setFontSize(6));


            String dataPagamento = recibo.getDataPagamento() != null
                    ? recibo.getDataPagamento().format(FMT_DATA) : "-";
            doc.add(new Paragraph("Data de pagamento: " + dataPagamento)
                    .setFont(regular).setFontSize(10));

            doc.close();
            return baos.toByteArray();
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Erro ao gerar recibo de pagamento: " + e.getMessage());
        }
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
