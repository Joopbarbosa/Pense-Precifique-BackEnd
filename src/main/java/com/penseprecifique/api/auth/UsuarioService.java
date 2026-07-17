package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.dto.request.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.UsuarioResponseDTO;

public interface UsuarioService {

    UsuarioResponseDTO getUsuarioAutenticado();

    void alterarSenha(AlterarSenhaRequestDTO request);
}
