package com.penseprecifique.api.service;

import com.penseprecifique.api.shared.dto.request.CadastroRequestDTO;
import com.penseprecifique.api.shared.dto.request.LoginRequestDTO;
import com.penseprecifique.api.shared.dto.response.AuthResponseDTO;

public interface AuthService {

    AuthResponseDTO register(CadastroRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);
}
