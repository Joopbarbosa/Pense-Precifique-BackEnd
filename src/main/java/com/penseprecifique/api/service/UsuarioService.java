package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.AlterarSenhaRequestDTO;
import com.penseprecifique.api.dto.response.UsuarioResponseDTO;

public interface UsuarioService {

    UsuarioResponseDTO getUsuarioAutenticado();

    void alterarSenha(AlterarSenhaRequestDTO request);
}
