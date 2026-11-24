package com.penseprecifique.api.infra.storage;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** #733 — R2_HABILITADO=false: sobe sem credenciais, upload recusa com mensagem clara, remoção é ignorada. */
class R2StorageClientDesabilitadoTest {

    @Test
    void desabilitadoSobeSemCredenciaisEUploadDevolveMensagemClara() {
        R2StorageClient cliente = new R2StorageClient(false, "", "", "", "", "");
        BusinessException e = assertThrows(BusinessException.class, () -> cliente.upload("k", new byte[] {1}, "image/png"));
        assertEquals("Envio de foto desabilitado neste ambiente.", e.getMessage());
    }

    @Test
    void desabilitadoIgnoraRemocao() {
        R2StorageClient cliente = new R2StorageClient(false, "", "", "", "", "");
        assertDoesNotThrow(() -> cliente.deletarPorUrl("https://qualquer/foto.png"));
        assertDoesNotThrow(() -> cliente.deletarPorUrl(null));
    }

    @Test
    void habilitadoSemCredenciaisFalhaNaInicializacao() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new R2StorageClient(true, "", "", "", "", ""));
        assertEquals(true, e.getMessage().contains("R2_HABILITADO=false"));
    }

    @Test
    void habilitadoComCredenciaisConstroi() {
        assertDoesNotThrow(() -> new R2StorageClient(true, "a", "b", "http://r2.local", "bucket", "http://pub.local/"));
    }
}
