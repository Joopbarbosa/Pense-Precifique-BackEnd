package com.penseprecifique.api.shared.validation;

import com.penseprecifique.api.shared.domain.enums.ComprovanteTipo;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * V0.16.0 (#683, DT-NOVA-10) — valida o arquivo original de uma nota (comprovante). Imagem segue a
 * invariante de upload ({@link ValidadorArquivoImagem}: só JPG ou PNG, 5 MB); PDF e XML não são imagem e
 * têm validação própria (até 10 MB, conferida pelo conteúdo e não só pelo tipo informado).
 */
@Component
public class ValidadorArquivoComprovante {

    static final long TAMANHO_MAXIMO_DOCUMENTO_BYTES = 10L * 1024 * 1024;
    static final String MSG_TIPO = "Só são aceitos arquivos JPG, PNG, PDF ou XML.";
    static final String MSG_GRANDE = "Arquivo muito grande. O tamanho máximo permitido é 10MB para PDF e XML.";

    private final ValidadorArquivoImagem validadorImagem;

    public ValidadorArquivoComprovante(ValidadorArquivoImagem validadorImagem) {
        this.validadorImagem = validadorImagem;
    }

    /** Valida e devolve o tipo do comprovante; nunca devolve {@code LINK}. */
    public ComprovanteTipo validar(MultipartFile arquivo, byte[] conteudo) {
        if (arquivo == null || arquivo.isEmpty() || conteudo == null || conteudo.length == 0) {
            throw new BusinessException("Selecione um arquivo.");
        }
        String tipoInformado = arquivo.getContentType();
        if ("image/jpeg".equals(tipoInformado) || "image/png".equals(tipoInformado)) {
            validadorImagem.validar(arquivo);
            if (!assinaturaDeImagemConfere(tipoInformado, conteudo)) {
                throw new BusinessException("Só são aceitos arquivos JPG ou PNG.");
            }
            return ComprovanteTipo.IMAGEM;
        }
        if ("application/pdf".equals(tipoInformado)) {
            exigirTamanho(conteudo);
            if (!comecaCom(conteudo, "%PDF-")) {
                throw new BusinessException(MSG_TIPO);
            }
            return ComprovanteTipo.PDF;
        }
        if ("text/xml".equals(tipoInformado) || "application/xml".equals(tipoInformado)) {
            exigirTamanho(conteudo);
            if (!pareceXml(conteudo)) {
                throw new BusinessException(MSG_TIPO);
            }
            return ComprovanteTipo.XML;
        }
        throw new BusinessException(MSG_TIPO);
    }

    public String extensao(ComprovanteTipo tipo, MultipartFile arquivo) {
        return switch (tipo) {
            case PDF -> "pdf";
            case XML -> "xml";
            case IMAGEM -> validadorImagem.extensaoPara(arquivo);
            case LINK -> throw new IllegalArgumentException("Link não tem arquivo.");
        };
    }

    private static void exigirTamanho(byte[] conteudo) {
        if (conteudo.length > TAMANHO_MAXIMO_DOCUMENTO_BYTES) {
            throw new BusinessException(MSG_GRANDE);
        }
    }

    private static boolean assinaturaDeImagemConfere(String tipo, byte[] c) {
        if ("image/png".equals(tipo)) {
            return c.length > 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
        }
        return c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
    }

    private static boolean comecaCom(byte[] c, String prefixo) {
        byte[] p = prefixo.getBytes(StandardCharsets.US_ASCII);
        if (c.length < p.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (c[i] != p[i]) {
                return false;
            }
        }
        return true;
    }

    /** XML começa (depois de BOM e espaços) com '<'. */
    private static boolean pareceXml(byte[] c) {
        int i = 0;
        if (c.length >= 3 && (c[0] & 0xFF) == 0xEF && (c[1] & 0xFF) == 0xBB && (c[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < c.length && (c[i] == ' ' || c[i] == '\n' || c[i] == '\r' || c[i] == '\t')) {
            i++;
        }
        return i < c.length && c[i] == '<';
    }

    /** Lê o conteúdo do arquivo; falha de leitura vira erro de entrada. */
    public static byte[] ler(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Não foi possível ler o arquivo enviado.");
        }
    }
}
