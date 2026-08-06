package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.dto.request.auth.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.auth.UsuarioResponseDTO;

public interface UsuarioService {

    UsuarioResponseDTO getUsuarioAutenticado();

    void alterarSenha(AlterarSenhaRequestDTO request);
}
