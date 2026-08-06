package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.dto.request.auth.CadastroRequestDTO;
import com.penseprecifique.api.shared.dto.request.auth.LoginRequestDTO;
import com.penseprecifique.api.shared.dto.response.auth.AuthResponseDTO;

public interface AuthService {

    AuthResponseDTO register(CadastroRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);
}
