package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * V0.16.0 (#723, DT-NOVA-9) — o backend revalida, antes de guardar, que o link do comprovante é de um portal
 * fiscal permitido: esquema https, host da lista, sem credencial na URL e sem porta diferente da padrão. A
 * lista vem da configuração e hoje tem só o portal de São Paulo, o mesmo do leitor-fiscal; os demais estados
 * entram quando o leitor os suportar.
 */
@Component
public class PortalFiscalPermitido {

    static final String MSG_LINK_NAO_PERMITIDO = "O link do comprovante não é de um portal fiscal permitido.";

    private final List<String> hosts;

    public PortalFiscalPermitido(@Value("${nota.portais-permitidos:www.nfce.fazenda.sp.gov.br}") List<String> hosts) {
        this.hosts = hosts.stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
    }

    public void validar(String link) {
        if (!permitido(link)) {
            throw BusinessException.explicado("Link do comprovante não permitido", MSG_LINK_NAO_PERMITIDO,
                    "O comprovante por link só pode apontar para o portal da Fazenda do estado da nota.",
                    "Leia a nota de novo pelo link do QR code da nota.");
        }
    }

    boolean permitido(String link) {
        try {
            // O QR de NFC-e separa os campos por "|", que o URI não aceita cru.
            URI uri = URI.create(link.trim().replace("|", "%7C"));
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && uri.getHost() != null
                    && hosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
