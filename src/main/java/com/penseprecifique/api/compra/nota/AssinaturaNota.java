package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * V0.16.0 (#683, DT-NOVA-9) — vínculo verificável entre a leitura da nota e a criação do rascunho. A
 * leitura devolve uma assinatura (HMAC-SHA256, segredo só do backend) sobre usuária, nota lida,
 * resumo do comprovante e prazo; o rascunho só é criado com a nota lida exatamente como foi devolvida.
 * A forma canônica não depende da escala dos decimais nem da ordem dos campos do JSON.
 */
@Component
public class AssinaturaNota {

    static final String MSG_INVALIDA = "A leitura expirou ou foi alterada. Leia a nota de novo.";

    private final byte[] segredo;
    private final long validadeSegundos;
    private final Clock clock;

    @Autowired
    public AssinaturaNota(@Value("${nota.assinatura.segredo:}") String segredo,
                          @Value("${nota.assinatura.validade-minutos:120}") long validadeMinutos) {
        this(segredo, validadeMinutos, Clock.systemUTC());
    }

    AssinaturaNota(String segredo, long validadeMinutos, Clock clock) {
        this.segredo = segredo == null ? new byte[0] : segredo.getBytes(StandardCharsets.UTF_8);
        this.validadeSegundos = validadeMinutos * 60;
        this.clock = clock;
    }

    /** Assina e devolve {@code "<expira em segundos>.<hmac base64url>"}. */
    public String assinar(UUID usuarioId, NotaLida nota, String resumoComprovante) {
        exigirSegredo();
        long expira = clock.instant().plusSeconds(validadeSegundos).getEpochSecond();
        return expira + "." + hmac(usuarioId, nota, resumoComprovante, expira);
    }

    public Instant expiraEm(String assinatura) {
        return Instant.ofEpochSecond(Long.parseLong(assinatura.substring(0, assinatura.indexOf('.'))));
    }

    /** Recusa assinatura ausente, mal formada, expirada ou que não bate com usuária, nota ou comprovante. */
    public void verificar(String assinatura, UUID usuarioId, NotaLida nota, String resumoComprovante) {
        exigirSegredo();
        if (!StringUtils.hasText(assinatura) || assinatura.indexOf('.') < 1) {
            throw invalida();
        }
        long expira;
        try {
            expira = Long.parseLong(assinatura.substring(0, assinatura.indexOf('.')));
        } catch (NumberFormatException e) {
            throw invalida();
        }
        if (clock.instant().getEpochSecond() > expira) {
            throw invalida();
        }
        byte[] esperado = (expira + "." + hmac(usuarioId, nota, resumoComprovante, expira)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperado, assinatura.getBytes(StandardCharsets.UTF_8))) {
            throw invalida();
        }
    }

    private void exigirSegredo() {
        if (segredo.length == 0) {
            throw BusinessException.explicado("Leitura da nota indisponível", LeitorFiscalClient.MSG_INDISPONIVEL,
                    LeitorFiscalClient.MOTIVO_FALHA, LeitorFiscalClient.COMO_RESOLVER_FALHA);
        }
    }

    private static BusinessException invalida() {
        return BusinessException.explicado("Leitura expirada", MSG_INVALIDA,
                "A leitura vale por um tempo curto e precisa ser a mesma que o sistema devolveu.",
                "Leia o QR code, a foto ou o arquivo de novo.");
    }

    private String hmac(UUID usuarioId, NotaLida nota, String resumoComprovante, long expira) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            String texto = usuarioId + "|" + expira + "|" + (resumoComprovante == null ? "" : resumoComprovante) + "|" + canonico(nota);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }

    /** Forma canônica da nota lida: campos em ordem fixa, decimais sem zeros à direita, texto como veio. */
    static String canonico(NotaLida n) {
        StringBuilder s = new StringBuilder();
        if (n.emitente() != null) {
            campo(s, n.emitente().cnpj()).append(';');
            campo(s, n.emitente().nome()).append(';');
            campo(s, n.emitente().uf()).append(';');
        }
        campo(s, n.chaveAcesso()).append(';');
        campo(s, n.numero()).append(';');
        campo(s, n.serie()).append(';');
        campo(s, n.dataEmissao()).append(';');
        decimal(s, n.totalPago()).append(';');
        decimal(s, n.descontoGeral()).append(';');
        decimal(s, n.acrescimos()).append(';');
        campo(s, n.origem()).append(';');
        campo(s, n.metodo()).append(';');
        campo(s, n.uf()).append(';');
        campo(s, n.leiaute()).append(';');
        for (NotaLida.Item i : n.itensOuVazio()) {
            s.append('[');
            campo(s, i.nome()).append(',');
            decimal(s, i.quantidade()).append(',');
            decimal(s, i.valorFinal()).append(',');
            decimal(s, i.valorBruto()).append(',');
            decimal(s, i.desconto()).append(',');
            campo(s, i.unidade()).append(',');
            campo(s, i.codigo()).append(',');
            campo(s, i.ean()).append(']');
        }
        return s.toString();
    }

    private static StringBuilder campo(StringBuilder s, String valor) {
        // prefixo de tamanho evita que "ab","c" e "a","bc" gerem o mesmo texto
        if (valor == null) {
            return s.append('-');
        }
        return s.append(valor.length()).append(':').append(valor);
    }

    private static StringBuilder decimal(StringBuilder s, BigDecimal valor) {
        if (valor == null) {
            return s.append('-');
        }
        BigDecimal normal = valor.signum() == 0 ? BigDecimal.ZERO : valor.stripTrailingZeros();
        return s.append(normal.toPlainString());
    }
}
