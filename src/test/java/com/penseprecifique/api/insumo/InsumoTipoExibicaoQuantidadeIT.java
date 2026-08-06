package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoExibicaoQuantidade;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * RN-NOVA-1 — tipoExibicaoQuantidade só é relevante quando fracionavel = true; nulo/ignorado
 * quando fracionavel = false, sem lançar erro (InsumoMapper#tipoExibicaoQuantidadeParaSalvar).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoTipoExibicaoQuantidadeIT {

    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;

    private void seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("tipo-exibicao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private InsumoCreateRequestDTO request(Boolean fracionavel, TipoExibicaoQuantidade tipo) {
        return new InsumoCreateRequestDTO(
                "Insumo " + UUID.randomUUID(), null, "un", fracionavel, tipo, true,
                BigDecimal.ZERO, new BigDecimal("10.00"), new BigDecimal("5"));
    }

    @Test
    void fracionavelComTipoFracao() {
        seedUsuario();
        InsumoResponseDTO response = insumoService.cadastrar(request(true, TipoExibicaoQuantidade.FRACAO));
        assertEquals(TipoExibicaoQuantidade.FRACAO, response.tipoExibicaoQuantidade());
    }

    @Test
    void fracionavelComTipoDecimal() {
        seedUsuario();
        InsumoResponseDTO response = insumoService.cadastrar(request(true, TipoExibicaoQuantidade.DECIMAL));
        assertEquals(TipoExibicaoQuantidade.DECIMAL, response.tipoExibicaoQuantidade());
    }

    @Test
    void fracionavelSemTipoInformadoUsaDecimalComoPadrao() {
        seedUsuario();
        InsumoResponseDTO response = insumoService.cadastrar(request(true, null));
        assertEquals(TipoExibicaoQuantidade.DECIMAL, response.tipoExibicaoQuantidade());
    }

    @Test
    void naoFracionavelIgnoraTipoInformadoSemErro() {
        seedUsuario();
        InsumoResponseDTO response = insumoService.cadastrar(request(false, TipoExibicaoQuantidade.FRACAO));
        assertNull(response.tipoExibicaoQuantidade());
    }

    @Test
    void editarParaNaoFracionavelLimpaTipoExibicao() {
        seedUsuario();
        InsumoResponseDTO criado = insumoService.cadastrar(request(true, TipoExibicaoQuantidade.FRACAO));

        InsumoRequestDTO edicao = new InsumoRequestDTO(
                criado.nome(), null, "un", false, null, true, BigDecimal.ZERO, null);
        InsumoResponseDTO editado = insumoService.editar(criado.id(), edicao);

        assertNull(editado.tipoExibicaoQuantidade());
    }
}
