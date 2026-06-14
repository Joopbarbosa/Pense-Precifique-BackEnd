package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.CadastroRequestDTO;
import com.penseprecifique.api.dto.request.LoginRequestDTO;
import com.penseprecifique.api.dto.response.AuthResponseDTO;

public interface AuthService {

    AuthResponseDTO register(CadastroRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);
}
