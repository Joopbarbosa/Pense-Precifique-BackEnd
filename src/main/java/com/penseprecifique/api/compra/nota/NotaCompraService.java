package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.infra.storage.R2StorageClient;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.ComprovanteTipo;
import com.penseprecifique.api.shared.domain.enums.OrigemCompra;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoDesconto;
import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraItemRequest;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.AcaoFornecedor;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.CompraRef;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.FornecedorProposta;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.ItemConciliacao;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.SituacaoFornecedor;
import com.penseprecifique.api.shared.dto.response.compra.NotaRascunhoResponse;
import com.penseprecifique.api.shared.dto.response.compra.NotaRascunhoResponse.AvisoNota;
import com.penseprecifique.api.shared.dto.response.compra.NotaRascunhoResponse.LinhaPrevia;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.validation.DocumentoFiscal;
import com.penseprecifique.api.shared.validation.ValidadorArquivoComprovante;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V0.16.0 (#683, DT-NOVA-9, DT-NOVA-10) — registrar compra a partir de uma nota fiscal. Dois passos:
 * {@link #ler} chama o leitor-fiscal, confere nota já registrada e devolve a proposta com assinatura (nada
 * é gravado); {@link #criarRascunho} verifica a assinatura, monta as linhas ({@link MontagemRascunhoNota})
 * e cria a compra RASCUNHO. O serviço de leitura só lê: ligação item→insumo é do produto (esta etapa
 * devolve todos os itens sem ligação; a conciliação automática é da #681).
 */
@Service
@RequiredArgsConstructor
public class NotaCompraService {

    private static final Pattern CHAVE_NO_LINK = Pattern.compile("[?&](?:p|chNFe)=([0-9A-Za-z]{44})(?![0-9A-Za-z])");
    private static final Pattern CHAVE = Pattern.compile("[0-9A-Z]{44}");

    private final LeitorFiscalClient leitorFiscalClient;
    private final AssinaturaNota assinaturaNota;
    private final CompraService compraService;
    private final CompraRepository compraRepository;
    private final InsumoRepository insumoRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteService clienteService;
    private final ValidadorArquivoComprovante validadorComprovante;
    private final R2StorageClient r2StorageClient;

    // ------------------------------------------------------------------------------------ leitura

    /** Lê a nota. Exatamente uma entrada: {@code qrUrl}, {@code chaveAcesso} ou {@code arquivo}. */
    public NotaLeituraResponse ler(String modelo, String qrUrl, String chaveAcesso, MultipartFile arquivo,
                                   boolean confirmouEnvioIa) {
        Usuario usuario = compraService.getUsuarioAutenticado();
        String modeloNormalizado = modelo == null ? "" : modelo.trim().toUpperCase(Locale.ROOT);
        if (!modeloNormalizado.equals("NFCE") && !modeloNormalizado.equals("NFE")) {
            throw entrada("Informe se a nota é NFC-e (cupom) ou NF-e (modelo 55).");
        }
        int entradas = (StringUtils.hasText(qrUrl) ? 1 : 0) + (StringUtils.hasText(chaveAcesso) ? 1 : 0)
                + (arquivo != null && !arquivo.isEmpty() ? 1 : 0);
        if (entradas != 1) {
            throw entrada("Envie o link do QR code, a chave de acesso ou um arquivo, só um dos três.");
        }
        if (StringUtils.hasText(qrUrl) && (qrUrl.length() > 2000 || modeloNormalizado.equals("NFE"))) {
            throw entrada("O link do QR code só vale para NFC-e (cupom).");
        }
        String chave = chaveAcesso == null || chaveAcesso.isBlank() ? null : chaveAcesso.trim().toUpperCase(Locale.ROOT);
        if (chave != null && !CHAVE.matcher(chave).matches()) {
            throw entrada("A chave de acesso precisa ter 44 caracteres (números e letras).");
        }

        byte[] conteudo = null;
        LeitorFiscalClient.ArquivoLeitura envio = null;
        String resumoComprovante = null;
        if (arquivo != null && !arquivo.isEmpty()) {
            conteudo = ValidadorArquivoComprovante.ler(arquivo);
            ComprovanteTipo tipo = validadorComprovante.validar(arquivo, conteudo);
            if (tipo != ComprovanteTipo.XML && !confirmouEnvioIa) {
                throw BusinessException.explicado("Confirme o envio", "Confirme que a foto ou o PDF pode ser enviado a um serviço externo de IA.",
                        "A nota pode trazer o CPF e a imagem não é ocultada antes do envio.",
                        "Aceite o aviso de envio e tente de novo, ou envie o arquivo XML da nota.");
            }
            envio = new LeitorFiscalClient.ArquivoLeitura(nomeSeguro(arquivo.getOriginalFilename(), tipo), conteudo);
            resumoComprovante = sha256(conteudo);
        } else if (StringUtils.hasText(qrUrl)) {
            resumoComprovante = sha256(qrUrl.getBytes(StandardCharsets.UTF_8));
            String daUrl = chaveDoLink(qrUrl);
            if (daUrl != null) {
                Optional<CompraRef> existente = verificarJaRegistrada(usuario.getId(), daUrl);
                if (existente.isPresent()) {
                    return new NotaLeituraResponse(null, null, null, null, existente.get(), List.of());
                }
            }
        } else if (chave != null) {
            Optional<CompraRef> existente = verificarJaRegistrada(usuario.getId(), chave);
            if (existente.isPresent()) {
                return new NotaLeituraResponse(null, null, null, null, existente.get(), List.of());
            }
        }

        NotaLida nota = leitorFiscalClient.ler(modeloNormalizado, StringUtils.hasText(qrUrl) ? qrUrl : null, chave,
                envio, confirmouEnvioIa, usuario.getId());
        validarNotaLida(nota);

        Optional<CompraRef> existente = verificarJaRegistrada(usuario.getId(), nota.chaveAcesso().toUpperCase(Locale.ROOT));
        if (existente.isPresent()) {
            return new NotaLeituraResponse(null, null, null, null, existente.get(), List.of());
        }
        String assinatura = assinaturaNota.assinar(usuario.getId(), nota, resumoComprovante);
        List<ItemConciliacao> itens = new ArrayList<>();
        List<NotaLida.Item> lidos = nota.itensOuVazio();
        for (int i = 0; i < lidos.size(); i++) {
            NotaLida.Item item = lidos.get(i);
            itens.add(new ItemConciliacao(i, item.nome(), item.quantidade(), item.valorFinal(), item.unidade(),
                    "SEM_LIGACAO", null, null));
        }
        return new NotaLeituraResponse(nota, assinatura, assinaturaNota.expiraEm(assinatura),
                proporFornecedor(usuario.getId(), nota), null, itens);
    }

    // ------------------------------------------------------------------------------------ rascunho

    @Transactional
    public NotaRascunhoResponse criarRascunho(NotaRascunhoRequest request, MultipartFile arquivo, boolean simular) {
        Usuario usuario = compraService.getUsuarioAutenticado();
        NotaLida nota = request.notaLida();
        byte[] conteudo = arquivo != null && !arquivo.isEmpty() ? ValidadorArquivoComprovante.ler(arquivo) : null;
        String resumo = conteudo != null ? sha256(conteudo)
                : StringUtils.hasText(request.comprovanteLink()) ? sha256(request.comprovanteLink().getBytes(StandardCharsets.UTF_8))
                : null;
        assinaturaNota.verificar(request.assinatura(), usuario.getId(), nota, resumo);
        validarNotaLida(nota);

        String chave = nota.chaveAcesso().toUpperCase(Locale.ROOT);
        Optional<Compra> viva = compraRepository.findByUsuarioIdAndChaveAcessoAndDeletedAtIsNull(usuario.getId(), chave);
        if (viva.isPresent()) {
            if (viva.get().getStatus() == StatusCompra.RASCUNHO) {
                return new NotaRascunhoResponse(compraService.responder(viva.get()), List.of(), BigDecimal.ZERO, BigDecimal.ZERO, List.of());
            }
            throw jaRegistrada(viva.get());
        }

        MontagemRascunhoNota.Resultado resultado = MontagemRascunhoNota.montar(nota, request.escolhas());
        List<Insumo> insumos = new ArrayList<>();
        for (MontagemRascunhoNota.Linha linha : resultado.linhas()) {
            insumos.add(insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(linha.insumoId(), usuario.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + linha.insumoId())));
        }
        List<LinhaPrevia> previas = new ArrayList<>();
        List<AvisoNota> avisos = new ArrayList<>();
        for (int i = 0; i < resultado.linhas().size(); i++) {
            MontagemRascunhoNota.Linha linha = resultado.linhas().get(i);
            String nome = insumos.get(i).getNome();
            previas.add(new LinhaPrevia(linha.insumoId(), nome, linha.quantidade(), linha.precoCheio(), linha.descontoLinha(), linha.posicoes()));
            if (linha.posicoes().size() > 1) {
                avisos.add(new AvisoNota("AVISO", "JUNCAO_DE_ITENS",
                        linha.posicoes().size() + " itens serão somados na mesma linha do insumo " + nome, null));
            }
        }
        if (MontagemRascunhoNota.temDiferenca(resultado)) {
            avisos.add(new AvisoNota("AVISO", "DIFERENCA_NO_TOTAL",
                    "A soma dos itens, com descontos e acréscimos, difere do total pago da nota em R$ "
                            + resultado.diferenca().abs().toPlainString().replace('.', ','), resultado.diferenca()));
        }
        if (simular) {
            return new NotaRascunhoResponse(null, previas, resultado.descontoNota(), resultado.acrescimos(), avisos);
        }

        UUID fornecedorId = resolverFornecedor(usuario.getId(), nota, request.fornecedor());
        CompraRequest compraRequest = montarRequest(nota, fornecedorId, resultado);
        Compra compra;
        try {
            compra = compraService.criarRascunhoDeNota(compraRequest, origemDe(nota), chave);
            compraRepository.flush(); // a unicidade da chave (RN-NOVA-5) é conferida aqui, não só no commit
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.explicado("Nota já registrada", "Esta nota já foi registrada numa compra.",
                    "A mesma nota não gera duas compras.", "Abra a compra existente na lista de compras.");
        }
        registrarComprovante(compra, usuario, arquivo, conteudo, request.comprovanteLink());
        return new NotaRascunhoResponse(compraService.responder(compra), previas, resultado.descontoNota(), resultado.acrescimos(), avisos);
    }

    // ------------------------------------------------------------------------------------ auxiliares

    private CompraRequest montarRequest(NotaLida nota, UUID fornecedorId, MontagemRascunhoNota.Resultado resultado) {
        List<CompraItemRequest> linhas = new ArrayList<>();
        for (MontagemRascunhoNota.Linha l : resultado.linhas()) {
            boolean desconto = l.descontoLinha().signum() > 0;
            linhas.add(new CompraItemRequest(l.insumoId(), null, l.quantidade(), null, l.precoCheio(),
                    desconto ? TipoDesconto.VALOR : null, desconto ? l.descontoLinha() : null));
        }
        boolean descontoNota = resultado.descontoNota().signum() > 0;
        String observacoes = resultado.acrescimos().signum() > 0
                ? "Acréscimos da nota: R$ " + resultado.acrescimos().setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',')
                : null;
        return new CompraRequest(dataDaNota(nota), false, fornecedorId, false, null, observacoes, linhas,
                descontoNota ? TipoDesconto.VALOR : null, descontoNota ? resultado.descontoNota() : null, null);
    }

    private void registrarComprovante(Compra compra, Usuario usuario, MultipartFile arquivo, byte[] conteudo, String link) {
        if (conteudo != null) {
            ComprovanteTipo tipo = validadorComprovante.validar(arquivo, conteudo);
            String chaveArmazenamento = "compras/" + usuario.getId() + "/" + compra.getId() + "/" + UUID.randomUUID()
                    + "." + validadorComprovante.extensao(tipo, arquivo);
            String url = r2StorageClient.upload(chaveArmazenamento, conteudo, arquivo.getContentType());
            compra.setComprovanteTipo(tipo);
            compra.setComprovanteNome(nomeSeguro(arquivo.getOriginalFilename(), tipo));
            compra.setComprovanteUrl(url);
        } else if (StringUtils.hasText(link)) {
            compra.setComprovanteTipo(ComprovanteTipo.LINK);
            compra.setComprovanteUrl(link);
        } else {
            return;
        }
        compraRepository.save(compra);
    }

    /** RN-NOVA-10 — nunca cria ou altera cadastro sozinho: a artesã escolhe a ação quando o emitente não é fornecedor. */
    private UUID resolverFornecedor(UUID usuarioId, NotaLida nota, AcaoFornecedor acao) {
        String cnpj = documentoDoEmitente(nota);
        if (cnpj == null) {
            return null;
        }
        Optional<Cliente> existente = clienteRepository.findByUsuarioIdAndDocumento(usuarioId, cnpj);
        if (existente.isPresent() && Boolean.TRUE.equals(existente.get().getEhFornecedor())) {
            return existente.get().getId();
        }
        if (acao == null) {
            throw BusinessException.explicado("Escolha o fornecedor", "Escolha o que fazer com o emitente da nota.",
                    "O emitente ainda não é fornecedor cadastrado e o sistema não cria cadastro sozinho.",
                    existente.isPresent()
                            ? "Adicione o papel Fornecedor ao cadastro existente ou siga sem fornecedor."
                            : "Cadastre o emitente como fornecedor ou siga sem fornecedor.");
        }
        return switch (acao) {
            case SEM_FORNECEDOR -> null;
            case ADICIONAR_PAPEL -> {
                Cliente cliente = existente.orElseThrow(() -> entrada("O emitente não tem cadastro para receber o papel Fornecedor."));
                cliente.setEhFornecedor(true);
                clienteRepository.save(cliente);
                yield cliente.getId();
            }
            case CADASTRAR -> {
                if (existente.isPresent()) {
                    throw entrada("O emitente já tem cadastro; use a opção de adicionar o papel Fornecedor.");
                }
                ClienteRequest novo = new ClienteRequest();
                novo.setNome(truncar(nota.emitente().nome(), 255));
                novo.setEhFornecedor(true);
                novo.setEhCliente(false);
                novo.setTipoPessoa(TipoPessoa.JURIDICA);
                novo.setDocumento(cnpj);
                yield clienteService.cadastrar(novo).getId();
            }
        };
    }

    private FornecedorProposta proporFornecedor(UUID usuarioId, NotaLida nota) {
        String cnpj = documentoDoEmitente(nota);
        String nome = nota.emitente() != null ? nota.emitente().nome() : null;
        if (cnpj == null) {
            return new FornecedorProposta(SituacaoFornecedor.NAO_CADASTRADO, null, nome, null);
        }
        Optional<Cliente> existente = clienteRepository.findByUsuarioIdAndDocumento(usuarioId, cnpj);
        if (existente.isEmpty()) {
            return new FornecedorProposta(SituacaoFornecedor.NAO_CADASTRADO, null, nome, cnpj);
        }
        Cliente cliente = existente.get();
        return new FornecedorProposta(Boolean.TRUE.equals(cliente.getEhFornecedor())
                ? SituacaoFornecedor.FORNECEDOR_CADASTRADO : SituacaoFornecedor.SO_CLIENTE, cliente.getId(), cliente.getNome(), cnpj);
    }

    private static String documentoDoEmitente(NotaLida nota) {
        return nota.emitente() == null ? null : DocumentoFiscal.normalizar(nota.emitente().cnpj());
    }

    /** RN-NOVA-5 — rascunho vivo: abre esse; confirmada ou cancelada: bloqueia com o número da compra. */
    private Optional<CompraRef> verificarJaRegistrada(UUID usuarioId, String chave) {
        Optional<Compra> viva = compraRepository.findByUsuarioIdAndChaveAcessoAndDeletedAtIsNull(usuarioId, chave);
        if (viva.isEmpty()) {
            return Optional.empty();
        }
        Compra compra = viva.get();
        if (compra.getStatus() == StatusCompra.RASCUNHO) {
            return Optional.of(new CompraRef(compra.getId(), IdentificadorFormatter.formatar("COM", compra.getNumero())));
        }
        throw jaRegistrada(compra);
    }

    private static BusinessException jaRegistrada(Compra compra) {
        String identificador = IdentificadorFormatter.formatar("COM", compra.getNumero());
        return BusinessException.explicado("Nota já registrada", "Esta nota já foi registrada na compra " + identificador + ".",
                "A mesma nota não gera duas compras: dobraria estoque e custo.",
                "Abra a compra " + identificador + " na lista de compras.").comItens(List.of(identificador));
    }

    private static void validarNotaLida(NotaLida nota) {
        if (nota == null || !StringUtils.hasText(nota.chaveAcesso())
                || !CHAVE.matcher(nota.chaveAcesso().toUpperCase(Locale.ROOT)).matches()
                || nota.itensOuVazio().isEmpty()
                || nota.itensOuVazio().stream().anyMatch(i -> !StringUtils.hasText(i.nome()) || i.quantidade() == null
                        || i.quantidade().signum() <= 0 || i.valorFinal() == null || i.valorFinal().signum() < 0)) {
            throw BusinessException.explicado(LeitorFiscalClient.TITULO_FALHA, LeitorFiscalClient.MSG_INDISPONIVEL,
                    LeitorFiscalClient.MOTIVO_FALHA, LeitorFiscalClient.COMO_RESOLVER_FALHA);
        }
        origemDe(nota);
        dataDaNota(nota);
    }

    private static OrigemCompra origemDe(NotaLida nota) {
        try {
            OrigemCompra origem = OrigemCompra.valueOf(nota.origem());
            if (origem == OrigemCompra.MANUAL) {
                throw new IllegalArgumentException();
            }
            return origem;
        } catch (RuntimeException e) {
            throw BusinessException.explicado(LeitorFiscalClient.TITULO_FALHA, LeitorFiscalClient.MSG_INDISPONIVEL,
                    LeitorFiscalClient.MOTIVO_FALHA, LeitorFiscalClient.COMO_RESOLVER_FALHA);
        }
    }

    private static LocalDate dataDaNota(NotaLida nota) {
        try {
            return LocalDate.parse(nota.dataEmissao().substring(0, 10));
        } catch (RuntimeException e) {
            throw BusinessException.explicado(LeitorFiscalClient.TITULO_FALHA, LeitorFiscalClient.MSG_INDISPONIVEL,
                    LeitorFiscalClient.MOTIVO_FALHA, LeitorFiscalClient.COMO_RESOLVER_FALHA);
        }
    }

    static String chaveDoLink(String qrUrl) {
        Matcher m = CHAVE_NO_LINK.matcher(qrUrl);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private static String nomeSeguro(String original, ComprovanteTipo tipo) {
        String base = StringUtils.hasText(original) ? original.replaceAll("[^A-Za-z0-9._ -]", "_") : "nota";
        return truncar(base, 255);
    }

    private static String truncar(String texto, int max) {
        return texto == null ? null : texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static BusinessException entrada(String mensagem) {
        return new BusinessException(mensagem);
    }

    static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
