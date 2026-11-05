package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.caixa.VendaCaixaRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.compra.ListaCompraRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.pdf.PdfService;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.whatsapp.WhatsappDtos.EnvioRequest;
import com.penseprecifique.api.whatsapp.WhatsappDtos.EnvioResponse;
import com.penseprecifique.api.whatsapp.WhatsappDtos.HistoricoItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Envio síncrono de PDF por WhatsApp (DT-NOVA-14, RN-NOVA-24 a 36). Ordem: recurso ligado, entrada
 * hostil, documento da própria conta, conexão, destinatário/consentimento, recusa, PDF, 10 MB e, sob trava
 * por usuária, limite diário e intervalo. Envio que falha ou é bloqueado não consome o limite.
 */
@Service
@RequiredArgsConstructor
public class WhatsappEnvioService {

    static final int LEGENDA_MAX = 1024;
    static final String MSG_DOC_NAO_ENCONTRADO = "Documento não encontrado.";
    static final String MSG_NUMERO_INVALIDO = "Número inválido. Use DDD e número, ex.: 11 91234-5678.";
    static final String MSG_RECUSA = "Esta pessoa pediu para parar de receber.";
    static final String MSG_SEM_ACEITE_CLIENTE =
            "Este cliente ainda não aceitou receber pelo WhatsApp. Marque no cadastro depois de combinar com ele.";
    static final String MSG_SEM_CONFIRMACAO_NUMERO =
            "Confirme que esta pessoa aceitou receber pelo WhatsApp.";
    static final String MSG_LIMITE = "Você chegou ao limite de envios de hoje.";
    static final String MSG_ARQUIVO = "O arquivo é grande demais para enviar.";
    static final String MSG_PRECOS = "O PDF mostra os seus preços de referência. Enviar mesmo assim?";
    static final String MSG_SEM_SEGUNDA_PARTE = "Este envio não tem uma segunda parte para reenviar.";
    static final String MSG_DESCONECTADO = "Não foi possível enviar: seu WhatsApp está desconectado.";
    static final String MSG_INDISPONIVEL = "Não foi possível enviar: o serviço está indisponível, tente em instantes.";
    static final String MSG_SEM_WHATSAPP = "Não foi possível enviar: este número não parece ter WhatsApp.";

    private final WhatsappCanal canal;
    private final WhatsappConexaoService conexaoService;
    private final WhatsappConexaoRepository conexaoRepository;
    private final WhatsappEnvioRepository envioRepository;
    private final ClienteRepository clienteRepository;
    private final OrcamentoRepository orcamentoRepository;
    private final CompraRepository compraRepository;
    private final ListaCompraRepository listaCompraRepository;
    private final CatalogoRepository catalogoRepository;
    private final VendaCaixaRepository vendaCaixaRepository;
    private final PdfService pdfService;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogioBrasilia;
    private final PlatformTransactionManager transactionManager;

    @Value("${evolution.limite-diario:50}")
    private int limiteDiario;
    @Value("${evolution.intervalo-segundos:30}")
    private int intervaloSegundos;
    @Value("${evolution.tamanho-maximo-bytes:10485760}")
    private long tamanhoMaximoBytes;

    private record Documento(Integer numero,
            Function<Boolean, com.penseprecifique.api.pdf.PdfMicroservicoClient.PdfGeracaoResultado> pdf,
            String arquivo) { }

    private record Destino(String canonico, String nome, boolean fornecedor, boolean numeroDigitado) { }

    public EnvioResponse enviar(EnvioRequest req) {
        conexaoService.exigirHabilitado();
        UUID usuarioId = usuarioAtual.id();
        if (req == null || req.tipoDocumento() == null || req.documentoId() == null) {
            throw new BusinessException("Informe o documento a enviar.");
        }
        String legendaInformada = req.legenda();
        if (legendaInformada != null && legendaInformada.length() > LEGENDA_MAX) {
            throw BusinessException.noCampo("legenda", "A legenda deve ter no máximo " + LEGENDA_MAX + " caracteres.");
        }
        Documento doc = localizar(req.tipoDocumento(), req.documentoId(), usuarioId);

        WhatsappConexao conexao = conexaoRepository.findByUsuarioId(usuarioId).orElse(null);
        if (conexao != null) conexaoService.atualizarDoCanal(conexao);
        if (conexao == null || conexao.getEstado() != EstadoWhatsapp.CONECTADO) {
            registrar(usuarioId, null, req, ResultadoEnvio.BLOQUEADO, "DESCONECTADO", false);
            throw new BusinessException(MSG_DESCONECTADO);
        }

        Destino destino = resolverDestino(req, usuarioId);
        boolean confirmouNumero = destino.numeroDigitado() && Boolean.TRUE.equals(req.confirmoAceite());
        bloquearRecusa(usuarioId, destino.canonico(), req, confirmouNumero);
        if (destino.numeroDigitado() && !confirmouNumero) {
            registrar(usuarioId, destino.canonico(), req, ResultadoEnvio.BLOQUEADO, "SEM_CONFIRMACAO", false);
            throw BusinessException.noCampo("confirmoAceite", MSG_SEM_CONFIRMACAO_NUMERO);
        }
        if (req.tipoDocumento() == TipoDocumentoWhatsapp.LISTA_COMPRAS
                && (destino.fornecedor() || destino.numeroDigitado())
                && !Boolean.TRUE.equals(req.confirmaPrecosFornecedor())) {
            throw BusinessException.noCampo("confirmaPrecosFornecedor", MSG_PRECOS);
        }

        com.penseprecifique.api.pdf.PdfMicroservicoClient.PdfGeracaoResultado geracao;
        if (Boolean.TRUE.equals(req.reenviarSegundaParte())) {
            geracao = doc.pdf().apply(true);
        } else {
            try {
                geracao = doc.pdf().apply(false);
            } catch (com.penseprecifique.api.pdf.PdfMicroservicoClient.PdfControleException ex) {
                if (!"AVISO".equals(ex.getTipo())) throw ex;
                geracao = doc.pdf().apply(true);
            }
        }
        List<byte[]> arquivos = geracao.partes().isEmpty()
                ? List.of(geracao.pdf())
                : geracao.partes().stream().map(com.penseprecifique.api.pdf.PdfMicroservicoClient.PdfParte::bytes).toList();
        if (Boolean.TRUE.equals(req.reenviarSegundaParte()) && geracao.partes().size() != 2) {
            throw new BusinessException(MSG_SEM_SEGUNDA_PARTE);
        }
        if (arquivos.stream().anyMatch(pdf -> pdf == null || pdf.length > tamanhoMaximoBytes)) {
            registrar(usuarioId, destino.canonico(), req, ResultadoEnvio.BLOQUEADO, "ARQUIVO_GRANDE", confirmouNumero);
            throw new BusinessException(MSG_ARQUIVO);
        }
        if (Boolean.TRUE.equals(req.reenviarSegundaParte())) arquivos = List.of(arquivos.get(1));
        final List<byte[]> arquivosEnvio = arquivos;
        final var geracaoPdf = geracao;
        String legenda = legendaInformada != null && !legendaInformada.isBlank()
                ? legendaInformada.strip() : legendaPadrao(req.tipoDocumento(), doc.numero(), destino.nome());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Object[] saida = tx.execute(status -> {
            conexaoRepository.travarPorUsuario(usuarioId);
            Instant agora = Instant.now(relogioBrasilia);
            LocalDate hoje = LocalDate.now(relogioBrasilia);
            Instant inicio = hoje.atStartOfDay(relogioBrasilia.getZone()).toInstant();
            Instant fim = hoje.plusDays(1).atStartOfDay(relogioBrasilia.getZone()).toInstant();
            long hojeEnviados = envioRepository
                    .countByUsuarioIdAndResultadoAndEnviadoEmGreaterThanEqualAndEnviadoEmLessThan(
                            usuarioId, ResultadoEnvio.ENVIADO, inicio, fim);
            if (hojeEnviados + arquivosEnvio.size() > limiteDiario) {
                gravar(usuarioId, destino.canonico(), req, ResultadoEnvio.BLOQUEADO, "LIMITE_DIARIO", confirmouNumero, agora);
                return new Object[]{MSG_LIMITE, null, 0};
            }
            var ultimo = envioRepository.findFirstByUsuarioIdAndResultadoOrderByEnviadoEmDesc(usuarioId, ResultadoEnvio.ENVIADO);
            if (ultimo.isPresent() && !Boolean.TRUE.equals(req.reenviarSegundaParte())) {
                long decorrido = Duration.between(ultimo.get().getEnviadoEm(), agora).toSeconds();
                if (decorrido < intervaloSegundos) {
                    gravar(usuarioId, destino.canonico(), req, ResultadoEnvio.BLOQUEADO, "INTERVALO", confirmouNumero, agora);
                    return new Object[]{"Aguarde " + (intervaloSegundos - decorrido) + " segundos para enviar de novo.", null, 0};
                }
            }
            for (int indice = 0; indice < arquivosEnvio.size(); indice++) {
                int numeroParte = Boolean.TRUE.equals(req.reenviarSegundaParte()) ? 2 : indice + 1;
                String nomeArquivo = arquivosEnvio.size() == 1 && geracaoPdf.partes().isEmpty()
                        ? doc.arquivo() : doc.arquivo().replace(".pdf", "-parte-" + numeroParte + ".pdf");
                try {
                    canal.enviarDocumento(conexao.getInstancia(), destino.canonico(), arquivosEnvio.get(indice), nomeArquivo,
                            arquivosEnvio.size() > 1 || !geracaoPdf.partes().isEmpty() ? legenda + " (parte " + numeroParte + " de 2)" : legenda);
                } catch (WhatsappCanalException ex) {
                    gravar(usuarioId, destino.canonico(), req, ResultadoEnvio.FALHOU, ex.getTipo().name(), confirmouNumero, agora);
                    if (numeroParte == 2 && !Boolean.TRUE.equals(req.reenviarSegundaParte())) {
                        return new Object[]{"A primeira parte foi enviada, mas a segunda falhou. Você pode reenviar somente a segunda parte.", null, 0, true};
                    }
                    String msg = switch (ex.getTipo()) {
                        case DESCONECTADO -> MSG_DESCONECTADO;
                        case NUMERO_SEM_WHATSAPP -> MSG_SEM_WHATSAPP;
                        case INDISPONIVEL -> MSG_INDISPONIVEL;
                    };
                    return new Object[]{msg, null, 0};
                }
                gravar(usuarioId, destino.canonico(), req, ResultadoEnvio.ENVIADO, null, confirmouNumero, agora);
            }
            return new Object[]{null, agora, (int) hojeEnviados + arquivosEnvio.size()};
        });
        if (saida.length > 3) throw new SegundaParteWhatsappException((String) saida[0]);
        if (saida[0] != null) throw new BusinessException((String) saida[0]);
        return new EnvioResponse(ResultadoEnvio.ENVIADO, (Instant) saida[1], (Integer) saida[2], limiteDiario);
    }

    public static class SegundaParteWhatsappException extends RuntimeException {
        public SegundaParteWhatsappException(String message) { super(message); }
    }

    public List<HistoricoItem> historico() {
        UUID usuarioId = usuarioAtual.id();
        return envioRepository.findByUsuarioIdOrderByEnviadoEmDesc(usuarioId, PageRequest.of(0, 50)).stream()
                .map(e -> new HistoricoItem(e.getId(), e.getNumero(), e.getTipoDocumento(), e.getDocumentoId(),
                        e.getEnviadoEm(), e.getResultado(), e.getMotivo()))
                .toList();
    }

    private Destino resolverDestino(EnvioRequest req, UUID usuarioId) {
        if (req.clienteId() != null) {
            Cliente c = clienteRepository.findByIdAndUsuarioId(req.clienteId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado."));
            String canonico = NumeroWhatsapp.canonico(c.getWhatsapp());
            if (canonico == null) throw BusinessException.noCampo("clienteId", MSG_NUMERO_INVALIDO);
            if (Boolean.TRUE.equals(c.getRecusouWhatsapp())) {
                registrarSimples(usuarioId, canonico, req, "RECUSA");
                throw new BusinessException(MSG_RECUSA);
            }
            if (!Boolean.TRUE.equals(c.getAceitaWhatsapp())) {
                registrarSimples(usuarioId, canonico, req, "SEM_ACEITE");
                throw new BusinessException(MSG_SEM_ACEITE_CLIENTE);
            }
            return new Destino(canonico, c.getNome(), Boolean.TRUE.equals(c.getEhFornecedor()), false);
        }
        String canonico = NumeroWhatsapp.canonico(req.numero());
        if (canonico == null) throw BusinessException.noCampo("numero", MSG_NUMERO_INVALIDO);
        return new Destino(canonico, null, false, true);
    }

    /** RN-NOVA-27 — a recusa vale para o número em qualquer cadastro da usuária e vence tudo. */
    private void bloquearRecusa(UUID usuarioId, String canonico, EnvioRequest req, boolean confirmou) {
        boolean recusou = clienteRepository.findByUsuarioIdAndWhatsappCanonico(usuarioId, canonico).stream()
                .anyMatch(c -> Boolean.TRUE.equals(c.getRecusouWhatsapp()));
        if (recusou) {
            registrar(usuarioId, canonico, req, ResultadoEnvio.BLOQUEADO, "RECUSA", confirmou);
            throw new BusinessException(MSG_RECUSA);
        }
    }

    private Documento localizar(TipoDocumentoWhatsapp tipo, UUID id, UUID usuarioId) {
        Integer numero;
        Function<Boolean, com.penseprecifique.api.pdf.PdfMicroservicoClient.PdfGeracaoResultado> pdf;
        String arquivo;
        switch (tipo) {
            case ORCAMENTO -> { numero = orcamento(id, usuarioId); pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "orcamento"; }
            case RECIBO_SINAL -> { numero = orcamento(id, usuarioId); pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "recibo-sinal"; }
            case RECIBO_PAGAMENTO -> { numero = orcamento(id, usuarioId); pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "recibo-pagamento"; }
            case MULTA -> { numero = orcamento(id, usuarioId); pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "comprovante-multa"; }
            case ESTORNO_SINAL -> { numero = orcamento(id, usuarioId); pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "comprovante-estorno"; }
            case RECIBO_CAIXA -> {
                numero = vendaCaixaRepository.findByIdAndUsuarioId(id, usuarioId).map(v -> v.getNumero())
                        .orElseThrow(() -> new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO));
                pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "recibo-venda";
            }
            case COMPRA -> {
                numero = compraRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId).map(c -> c.getNumero())
                        .orElseThrow(() -> new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO));
                pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "compra";
            }
            case LISTA_COMPRAS -> {
                numero = listaCompraRepository.findByIdAndUsuarioId(id, usuarioId).map(l -> l.getNumero())
                        .orElseThrow(() -> new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO));
                pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "lista-compras";
            }
            case CATALOGO -> {
                numero = catalogoRepository.findByIdAndUsuarioId(id, usuarioId).map(c -> c.getNumero())
                        .orElseThrow(() -> new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO));
                pdf = dividir -> pdfService.gerarPdfWhatsapp(tipo.name(), id, dividir); arquivo = "catalogo";
            }
            default -> throw new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO);
        }
        return new Documento(numero, pdf, arquivo + (numero == null ? "" : "-" + numero) + ".pdf");
    }

    private Integer orcamento(UUID id, UUID usuarioId) {
        return orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .map(o -> o.getNumero())
                .orElseThrow(() -> new ResourceNotFoundException(MSG_DOC_NAO_ENCONTRADO));
    }

    /** RN-NOVA-30 — legenda padrão por tipo; sem nome, sem a saudação. */
    static String legendaPadrao(TipoDocumentoWhatsapp tipo, Integer numero, String nome) {
        String saudacao = nome == null || nome.isBlank() ? "" : "Olá, " + nome.strip() + "! ";
        String n = numero == null ? "" : String.valueOf(numero);
        return switch (tipo) {
            case ORCAMENTO -> saudacao + "Segue o orçamento nº " + n + ". As condições de pagamento estão na página 2.";
            case RECIBO_SINAL, RECIBO_PAGAMENTO, RECIBO_CAIXA -> saudacao + "Segue o recibo nº " + n + ".";
            case MULTA, ESTORNO_SINAL -> saudacao + "Segue o comprovante do orçamento nº " + n + ".";
            case COMPRA -> saudacao + "Segue a compra nº " + n + ".";
            case LISTA_COMPRAS -> "Segue a lista de compras " + IdentificadorFormatter.formatar("LST", numero) + ".";
            case CATALOGO -> saudacao + "Segue o nosso catálogo.";
        };
    }

    private void registrarSimples(UUID usuarioId, String canonico, EnvioRequest req, String motivo) {
        registrar(usuarioId, canonico, req, ResultadoEnvio.BLOQUEADO, motivo, false);
    }

    private void registrar(UUID usuarioId, String numero, EnvioRequest req, ResultadoEnvio resultado,
                           String motivo, boolean consentimento) {
        gravar(usuarioId, numero, req, resultado, motivo, consentimento, Instant.now(relogioBrasilia));
    }

    private void gravar(UUID usuarioId, String numero, EnvioRequest req, ResultadoEnvio resultado,
                        String motivo, boolean consentimento, Instant quando) {
        WhatsappEnvio e = new WhatsappEnvio();
        e.setUsuarioId(usuarioId);
        e.setNumero(numero);
        e.setTipoDocumento(req.tipoDocumento());
        e.setDocumentoId(req.documentoId());
        e.setEnviadoEm(quando);
        e.setResultado(resultado);
        e.setMotivo(motivo);
        e.setConsentimentoConfirmado(consentimento);
        envioRepository.save(e);
    }
}
