package com.penseprecifique.api.controller;

import com.penseprecifique.api.shared.dto.request.AlterarSenhaRequestDTO;
import com.penseprecifique.api.shared.dto.response.UsuarioResponseDTO;
import com.penseprecifique.api.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuário", description = "Perfil do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    @Operation(summary = "Retorna dados do usuário autenticado")
    public ResponseEntity<UsuarioResponseDTO> getMe() {
        return ResponseEntity.ok(usuarioService.getUsuarioAutenticado());
    }

    @PutMapping("/me/senha")
    @Operation(summary = "Altera a senha do usuário autenticado")
    public ResponseEntity<Void> alterarSenha(@Valid @RequestBody AlterarSenhaRequestDTO request) {
        usuarioService.alterarSenha(request);
        return ResponseEntity.ok().build();
    }
}
