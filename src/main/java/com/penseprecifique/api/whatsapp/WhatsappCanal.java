package com.penseprecifique.api.whatsapp;

/**
 * DT-NOVA-13 — porta do canal de WhatsApp. Hoje a única implementação é {@link EvolutionClient}; trocar
 * para a API oficial depois significa uma nova implementação, sem mexer nos serviços.
 */
public interface WhatsappCanal {

    enum EstadoCanal { ABERTO, CONECTANDO, FECHADO }

    void criarInstancia(String instancia);

    EstadoCanal consultarEstado(String instancia);

    /** QR em base64 (imagem). Nunca é gravado. */
    String obterQr(String instancia);

    void desconectar(String instancia);

    void enviarDocumento(String instancia, String numeroCanonico, byte[] pdf, String nomeArquivo, String legenda);
}
