package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.dto.request.auth.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.auth.AuthResponseDTO;
import com.penseprecifique.api.shared.dto.response.auth.UsuarioResponseDTO;

public interface UsuarioService {

    UsuarioResponseDTO getUsuarioAutenticado();

    void alterarSenha(AlterarSenhaRequestDTO request);

    /** #334 (DT-NOVA-13) — troca o e-mail de acesso e devolve token novo (o antigo deixa de valer). */
    AuthResponseDTO alterarEmail(AlterarEmailRequestDTO request);
}
