package com.penseprecifique.api.unidademedida;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.InsumoService;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.unidademedida.UnidadeMedidaRequestDTO;
import com.penseprecifique.api.shared.dto.response.unidademedida.UnidadeMedidaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-8/UC-NOVO-1/CEN-NOVO-8/CEN-NOVO-9 (V0.14.0, #298) — cadastro de unidades de medida em
 * Configurações, unicidade de nome/sigla por usuária, exclusão bloqueada se em uso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UnidadeMedidaServiceIT {

    @Autowired UnidadeMedidaService unidadeMedidaService;
    @Autowired InsumoService insumoService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private void seedUsuario() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("unidade-medida-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void cadastrarEListarFuncionaNormalmente() {
        seedUsuario();
        UnidadeMedidaResponseDTO criada = unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));

        assertEquals("Grama", criada.nome());
        assertEquals("g", criada.sigla());
        assertTrue(unidadeMedidaService.listar().stream().anyMatch(u -> u.id().equals(criada.id())));
    }

    @Test
    void cadastrarComSiglaJaExistenteBloqueiaCEN_NOVO_8() {
        seedUsuario();
        unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Gramas", "g")));
        assertEquals("Já existe uma unidade de medida com esta sigla.", ex.getMessage());
    }

    @Test
    void cadastrarComNomeJaExistenteBloqueiaCaseInsensitive() {
        seedUsuario();
        unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("GRAMA", "gr")));
        assertEquals("Já existe uma unidade de medida com este nome.", ex.getMessage());
    }

    @Test
    void editarPreservaUnicidadeConsigoMesma() {
        seedUsuario();
        UnidadeMedidaResponseDTO criada = unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));

        UnidadeMedidaResponseDTO editada = unidadeMedidaService.editar(criada.id(),
                new UnidadeMedidaRequestDTO("Grama (editado)", "g"));

        assertEquals("Grama (editado)", editada.nome());
        assertEquals("g", editada.sigla());
    }

    @Test
    void excluirSemUsoFuncionaDireto() {
        seedUsuario();
        UnidadeMedidaResponseDTO criada = unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));

        unidadeMedidaService.excluir(criada.id());

        assertThrows(ResourceNotFoundException.class, () -> unidadeMedidaService.editar(criada.id(),
                new UnidadeMedidaRequestDTO("X", "y")));
    }

    @Test
    void excluirEmUsoPorInsumoBloqueiaCEN_NOVO_9() {
        seedUsuario();
        UnidadeMedidaResponseDTO grama = unidadeMedidaService.cadastrar(new UnidadeMedidaRequestDTO("Grama", "g"));
        insumoService.cadastrar(new InsumoCreateRequestDTO(
                "Farinha " + UUID.randomUUID(), null, grama.id(), true, null, true,
                BigDecimal.ZERO, new BigDecimal("10.00"), new BigDecimal("1")));

        BusinessException ex = assertThrows(BusinessException.class, () -> unidadeMedidaService.excluir(grama.id()));
        assertEquals("Esta unidade está em uso por 1 ou mais insumos. Troque a unidade dos insumos vinculados antes de excluir.",
                ex.getMessage());
    }
}
