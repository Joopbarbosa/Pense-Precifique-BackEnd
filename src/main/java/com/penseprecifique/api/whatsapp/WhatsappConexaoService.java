package com.penseprecifique.api.whatsapp;

import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.whatsapp.WhatsappDtos.QrResponse;
import com.penseprecifique.api.whatsapp.WhatsappDtos.StatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Conexão da usuária (DT-NOVA-13). Só usa a interface {@link WhatsappCanal}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappConexaoService {

    public static final String VERSAO_TEXTO_AVISO = "1";
    static final String MSG_DESLIGADO = "O envio por WhatsApp ainda não está disponível neste ambiente.";
    static final String MSG_SEM_ACEITE = "Confirme que entendeu os riscos para conectar.";

    private final WhatsappCanal canal;
    private final WhatsappConexaoRepository conexaoRepository;
    private final WhatsappAceiteRiscoRepository aceiteRepository;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogioBrasilia;

    @Value("${evolution.habilitado:false}")
    private boolean habilitado;

    public boolean habilitado() { return habilitado; }

    public StatusResponse status() {
        UUID usuarioId = usuarioAtual.id();
        if (!habilitado) return new StatusResponse(false, EstadoWhatsapp.NAO_CONECTADO, null);
        WhatsappConexao c = conexaoRepository.findByUsuarioId(usuarioId).orElse(null);
        if (c == null) return new StatusResponse(true, EstadoWhatsapp.NAO_CONECTADO, null);
        atualizarDoCanal(c);
        return new StatusResponse(true, c.getEstado(), c.getEstadoAlteradoEm());
    }

    /** Cada nova conexão exige a confirmação ativa do aviso de risco (RN-NOVA-24). */
    public QrResponse conectar(Boolean entendiRiscos) {
        exigirHabilitado();
        if (!Boolean.TRUE.equals(entendiRiscos)) {
            throw BusinessException.noCampo("entendiRiscos", MSG_SEM_ACEITE);
        }
        UUID usuarioId = usuarioAtual.id();
        Instant agora = Instant.now(relogioBrasilia);
        WhatsappAceiteRisco aceite = new WhatsappAceiteRisco();
        aceite.setUsuarioId(usuarioId);
        aceite.setAceitoEm(agora);
        aceite.setVersaoTexto(VERSAO_TEXTO_AVISO);
        aceiteRepository.save(aceite);

        WhatsappConexao c = conexaoRepository.findByUsuarioId(usuarioId).orElseGet(() -> {
            WhatsappConexao nova = new WhatsappConexao();
            nova.setUsuarioId(usuarioId);
            nova.setInstancia(nomeInstancia(usuarioId));
            nova.setEstadoAlteradoEm(agora);
            return nova;
        });
        try {
            if (c.getEstado() != EstadoWhatsapp.AGUARDANDO_QR && c.getEstado() != EstadoWhatsapp.CONECTADO) {
                try {
                    canal.criarInstancia(c.getInstancia());
                } catch (WhatsappCanalException ex) {
                    // instância já existente no servidor não impede reconectar; indisponibilidade volta no QR
                    log.debug("Instância não criada (tipo {}); segue para o QR", ex.getTipo());
                }
            }
            if (c.getEstado() == EstadoWhatsapp.CONECTADO && canal.consultarEstado(c.getInstancia()) == WhatsappCanal.EstadoCanal.ABERTO) {
                return new QrResponse(EstadoWhatsapp.CONECTADO, null);
            }
            String qr = canal.obterQr(c.getInstancia());
            mudarEstado(c, EstadoWhatsapp.AGUARDANDO_QR, agora);
            return new QrResponse(EstadoWhatsapp.AGUARDANDO_QR, qr);
        } catch (WhatsappCanalException ex) {
            throw new BusinessException("Não foi possível conectar: o serviço está indisponível, tente em instantes.");
        }
    }

    /** QR atualizado enquanto aguarda a leitura; devolve o estado quando já conectou. */
    public QrResponse qr() {
        exigirHabilitado();
        WhatsappConexao c = conexaoRepository.findByUsuarioId(usuarioAtual.id()).orElse(null);
        if (c == null) return new QrResponse(EstadoWhatsapp.NAO_CONECTADO, null);
        atualizarDoCanal(c);
        if (c.getEstado() != EstadoWhatsapp.AGUARDANDO_QR) return new QrResponse(c.getEstado(), null);
        try {
            return new QrResponse(c.getEstado(), canal.obterQr(c.getInstancia()));
        } catch (WhatsappCanalException ex) {
            throw new BusinessException("Não foi possível obter o QR: o serviço está indisponível, tente em instantes.");
        }
    }

    public void desconectar() {
        exigirHabilitado();
        WhatsappConexao c = conexaoRepository.findByUsuarioId(usuarioAtual.id()).orElse(null);
        if (c == null) return;
        try {
            canal.desconectar(c.getInstancia());
        } catch (WhatsappCanalException ex) {
            if (ex.getTipo() == WhatsappCanalException.Tipo.INDISPONIVEL) {
                throw new BusinessException("Não foi possível desconectar: o serviço está indisponível, tente em instantes.");
            }
        }
        mudarEstado(c, EstadoWhatsapp.NAO_CONECTADO, Instant.now(relogioBrasilia));
    }

    void exigirHabilitado() {
        if (!habilitado) throw new BusinessException(MSG_DESLIGADO);
    }

    /** Estado salvo + o que o canal diz agora; falha do canal mantém o salvo. */
    void atualizarDoCanal(WhatsappConexao c) {
        if (c.getEstado() != EstadoWhatsapp.AGUARDANDO_QR && c.getEstado() != EstadoWhatsapp.CONECTADO) return;
        try {
            WhatsappCanal.EstadoCanal real = canal.consultarEstado(c.getInstancia());
            Instant agora = Instant.now(relogioBrasilia);
            if (real == WhatsappCanal.EstadoCanal.ABERTO && c.getEstado() != EstadoWhatsapp.CONECTADO) {
                mudarEstado(c, EstadoWhatsapp.CONECTADO, agora);
            } else if (real != WhatsappCanal.EstadoCanal.ABERTO && c.getEstado() == EstadoWhatsapp.CONECTADO) {
                mudarEstado(c, EstadoWhatsapp.DESCONECTADO, agora);
            }
        } catch (WhatsappCanalException ex) {
            // mantém o estado salvo
            log.debug("Estado do canal não consultado (tipo {}); mantém o salvo", ex.getTipo());
        }
    }

    void mudarEstado(WhatsappConexao c, EstadoWhatsapp novo, Instant quando) {
        c.setEstado(novo);
        c.setEstadoAlteradoEm(quando);
        conexaoRepository.save(c);
    }

    /** Nome estável da instância, derivado do id da usuária (sem dados pessoais). */
    static String nomeInstancia(UUID usuarioId) {
        return "pp-" + usuarioId.toString().replace("-", "");
    }
}
