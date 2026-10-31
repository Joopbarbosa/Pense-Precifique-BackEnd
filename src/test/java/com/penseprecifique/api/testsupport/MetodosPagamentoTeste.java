package com.penseprecifique.api.testsupport;

import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;

/** Apoio de testes (PP-528): grava a forma de pagamento configurável que substitui o enum antigo. */
public final class MetodosPagamentoTeste {

    private MetodosPagamentoTeste() {}

    public static MetodoPagamentoConfiguravel pix(MetodoPagamentoConfiguravelRepository repo, Usuario usuario) {
        return criar(repo, usuario, TipoMetodoPagamento.PIX, true);
    }

    public static MetodoPagamentoConfiguravel criar(MetodoPagamentoConfiguravelRepository repo, Usuario usuario,
                                                    TipoMetodoPagamento tipo, boolean ativo) {
        if (tipo != TipoMetodoPagamento.OUTRO) {
            var existente = repo.findByUsuarioIdOrderByOrdemAscTipoAsc(usuario.getId()).stream()
                    .filter(m -> m.getTipo() == tipo).findFirst();
            if (existente.isPresent()) return existente.get();
        }
        return repo.save(MetodoPagamentoConfiguravel.builder()
                .usuario(usuario).tipo(tipo).ativo(ativo).ordem(1).build());
    }
}
