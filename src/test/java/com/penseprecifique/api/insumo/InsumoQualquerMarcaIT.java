package com.penseprecifique.api.insumo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.UnidadeMedida;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.unidademedida.UnidadeMedidaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class InsumoQualquerMarcaIT {
    @Autowired InsumoService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired UnidadeMedidaRepository unidades;
    @Autowired ObjectMapper json;
    private UUID unidade;

    @BeforeEach
    void preparar() {
        Usuario usuario = usuarios.save(Usuario.builder().email("qualquer-marca-" + UUID.randomUUID() + "@test.com")
                .senhaHash("teste").ativo(true).build());
        unidade = unidades.save(UnidadeMedida.builder().usuario(usuario).nome("Unidade").sigla("un").build()).getId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @AfterEach
    void limparContexto() { SecurityContextHolder.clearContext(); }

    private InsumoCreateRequestDTO criar(String nome, String marca, Boolean qualquerMarca) {
        return new InsumoCreateRequestDTO(nome, marca, unidade, true, null, true, BigDecimal.ZERO,
                new BigDecimal("17.43"), new BigDecimal("3"), null, qualquerMarca);
    }

    private InsumoRequestDTO editar(InsumoResponseDTO atual, String marca, Boolean qualquerMarca) {
        return new InsumoRequestDTO(atual.nome(), marca, unidade, true, null, true,
                BigDecimal.ZERO, BigDecimal.ZERO, null, qualquerMarca);
    }

    @Test
    void cen25MarcaVaziaEFlagVerdadeiraPersistemEmCadastroConsultaEJson() {
        InsumoResponseDTO salvo = service.cadastrar(criar("Folha A4", "", true));
        assertNull(salvo.marca());
        assertTrue(salvo.qualquerMarca());
        assertTrue(service.buscarPorId(salvo.id()).qualquerMarca());
        assertTrue(json.valueToTree(salvo).get("qualquerMarca").booleanValue());
        assertEquals(0, salvo.custoUnitario().compareTo(new BigDecimal("5.810000")));
    }

    @Test
    void cen15FlagDisponivelParaConciliacaoDaTarefa681() {
        InsumoResponseDTO salvo = service.cadastrar(criar("Caneta gel azul", null, true));
        assertTrue(service.buscarPorId(salvo.id()).qualquerMarca());
        assertEquals("Caneta gel azul", salvo.nome());
    }

    @Test
    void cen26QualquerMarcaEChamexCoexistem() {
        service.cadastrar(criar("Folha A4", null, true));
        InsumoResponseDTO chamex = service.cadastrar(criar("Folha A4", "Chamex", false));
        assertFalse(chamex.qualquerMarca());
        assertEquals("Chamex", chamex.marca());
    }

    @Test
    void cen48DuplicidadeSemMarcaIndependeDaFlagENuloEquivaleAVazio() {
        service.cadastrar(criar("Folha A4", null, true));
        assertThrows(BusinessException.class, () -> service.cadastrar(criar("Folha A4", "", false)));
        assertThrows(BusinessException.class, () -> service.cadastrar(criar("Folha A4", null, true)));
    }

    @Test
    void cen49EdicaoConfirmadaLimpaMarcaECancelamentoNaoEnviaMudanca() {
        InsumoResponseDTO original = service.cadastrar(criar("Fita de cetim", "Progresso", false));
        assertEquals("Progresso", service.buscarPorId(original.id()).marca());
        assertFalse(service.buscarPorId(original.id()).qualquerMarca());
        InsumoResponseDTO editado = service.editar(original.id(), editar(original, "", true));
        assertNull(editado.marca());
        assertTrue(service.buscarPorId(original.id()).qualquerMarca());
    }

    @Test
    void marcaPreenchidaComOpcaoMarcadaERejeitadaSemModificarInsumo() {
        InsumoResponseDTO original = service.cadastrar(criar("Fita de cetim", "Progresso", false));
        assertThrows(BusinessException.class, () -> service.editar(original.id(), editar(original, "Progresso", true)));
        assertEquals("Progresso", service.buscarPorId(original.id()).marca());
        assertFalse(service.buscarPorId(original.id()).qualquerMarca());
        assertThrows(BusinessException.class, () -> service.cadastrar(criar("Caneta", "BIC", true)));
    }

    @Test
    void desmarcarPermiteMarcaEFlagAusentePreservaCadastroLegado() {
        InsumoResponseDTO original = service.cadastrar(criar("Folha A4", null, true));
        assertTrue(service.editar(original.id(), editar(original, null, null)).qualquerMarca());
        InsumoResponseDTO editado = service.editar(original.id(), editar(original, "Chamex", false));
        assertFalse(editado.qualquerMarca());
        assertEquals("Chamex", editado.marca());
        assertFalse(service.cadastrar(criar("Sem flag", null, null)).qualquerMarca());
    }

    @Test
    void editarTambemBloqueiaDuplicidadeContraMarcaVazia() {
        service.cadastrar(criar("Folha A4", null, true));
        InsumoResponseDTO chamex = service.cadastrar(criar("Folha A4", "Chamex", false));
        assertThrows(BusinessException.class, () -> service.editar(chamex.id(), editar(chamex, "", true)));
        assertEquals("Chamex", service.buscarPorId(chamex.id()).marca());
    }
}
