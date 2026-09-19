package com.penseprecifique.api.empresa;

import com.penseprecifique.api.auth.AuthService;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.auth.CadastroRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelUpdateRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.MetodoPagamentoConfiguravelResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #491 — RN-NOVA-15/16/17. DT-NOVA-6 (seed eager no registro) coberto por
 * {@code registroDeContaSemeiaOs4MetodosFixos}; demais testes cobrem as regras de negócio da
 * própria entidade ({@code MetodoPagamentoConfiguravel}, renomeada por colisão real de nome com o
 * enum {@code shared.domain.enums.MetodoPagamento} — ver decisoes-config-perfil.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MetodoPagamentoConfiguravelServiceIT {

    @Autowired AuthService authService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired MetodoPagamentoConfiguravelService metodoPagamentoService;

    private Usuario novoUsuarioAutenticado(String prefixo) {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        autenticarComo(usuario);
        return usuario;
    }

    private void autenticarComo(Usuario usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void registroDeContaSemeiaOs4MetodosFixos() {
        String email = "seed-" + UUID.randomUUID() + "@test.com";
        authService.register(new CadastroRequestDTO(email, "senha12345", "senha12345"));
        autenticarComo(usuarioRepository.findByEmail(email).orElseThrow());

        List<MetodoPagamentoConfiguravelResponseDTO> metodos = metodoPagamentoService.listar();

        assertEquals(4, metodos.size());
        assertTrue(metodos.stream().allMatch(MetodoPagamentoConfiguravelResponseDTO::ativo));
        assertEquals(
                Set.of(TipoMetodoPagamento.DINHEIRO, TipoMetodoPagamento.PIX,
                        TipoMetodoPagamento.CARTAO_CREDITO, TipoMetodoPagamento.CARTAO_DEBITO),
                metodos.stream().map(MetodoPagamentoConfiguravelResponseDTO::tipo)
                        .collect(java.util.stream.Collectors.toSet()));

        // RN-NOVA-15 — afeta_caixa_fisico deriva de tipo: só DINHEIRO é true
        assertTrue(metodos.stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.DINHEIRO)
                .allMatch(MetodoPagamentoConfiguravelResponseDTO::afetaCaixaFisico));
        assertTrue(metodos.stream()
                .filter(m -> m.tipo() != TipoMetodoPagamento.DINHEIRO)
                .noneMatch(MetodoPagamentoConfiguravelResponseDTO::afetaCaixaFisico));
    }

    @Test
    void criarMetodoOutroComNomeLivreFunciona() {
        novoUsuarioAutenticado("outro-ok");

        MetodoPagamentoConfiguravelResponseDTO criado = metodoPagamentoService.criar(
                new MetodoPagamentoConfiguravelRequestDTO(TipoMetodoPagamento.OUTRO, "Fiado"));

        assertEquals("Fiado", criado.nome());
        assertEquals(TipoMetodoPagamento.OUTRO, criado.tipo());
        assertFalse(criado.afetaCaixaFisico());
    }

    @Test
    void tentativaDeCriarTipoFixoEBloqueada() {
        novoUsuarioAutenticado("fixo-bloqueio");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                metodoPagamentoService.criar(new MetodoPagamentoConfiguravelRequestDTO(TipoMetodoPagamento.DINHEIRO, null)));
        assertEquals("Esse método de pagamento já existe.", ex.getMessage());
    }

    @Test
    void nomeDuplicadoDeMetodoOutroEBloqueadoCaseInsensitive() {
        novoUsuarioAutenticado("outro-dup");
        metodoPagamentoService.criar(new MetodoPagamentoConfiguravelRequestDTO(TipoMetodoPagamento.OUTRO, "Vale-presente"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                metodoPagamentoService.criar(new MetodoPagamentoConfiguravelRequestDTO(TipoMetodoPagamento.OUTRO, "VALE-PRESENTE")));
        assertEquals("Já existe um método de pagamento com esse nome.", ex.getMessage());
    }

    @Test
    void desativarMetodoFixoFunciona() {
        Usuario usuario = novoUsuarioAutenticado("toggle");
        metodoPagamentoService.seedMetodosPadrao(usuario);
        MetodoPagamentoConfiguravelResponseDTO pix = metodoPagamentoService.listar().stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.PIX).findFirst().orElseThrow();

        MetodoPagamentoConfiguravelResponseDTO atualizado = metodoPagamentoService.atualizar(
                pix.id(), new MetodoPagamentoConfiguravelUpdateRequestDTO(false, null, null, null, null, null));

        assertFalse(atualizado.ativo());
    }

    @Test
    void taxaMaquininhaAceitaParaCartaoCreditoERejeitaParaDinheiro() {
        Usuario usuario = novoUsuarioAutenticado("taxa");
        metodoPagamentoService.seedMetodosPadrao(usuario);
        List<MetodoPagamentoConfiguravelResponseDTO> metodos = metodoPagamentoService.listar();
        UUID idCartaoCredito = metodos.stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.CARTAO_CREDITO).findFirst().orElseThrow().id();
        UUID idDinheiro = metodos.stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.DINHEIRO).findFirst().orElseThrow().id();

        MetodoPagamentoConfiguravelResponseDTO atualizado = metodoPagamentoService.atualizar(
                idCartaoCredito, new MetodoPagamentoConfiguravelUpdateRequestDTO(null, new BigDecimal("2.50"), null, null, null, null));
        assertEquals(new BigDecimal("2.50"), atualizado.taxaMaquininha());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                metodoPagamentoService.atualizar(idDinheiro,
                        new MetodoPagamentoConfiguravelUpdateRequestDTO(null, new BigDecimal("1.00"), null, null, null, null)));
        assertEquals("Taxa da maquininha só se aplica a Cartão Crédito ou Cartão Débito.", ex.getMessage());
    }

    @Test
    void renomearMetodoFixoEBloqueadoMasRenomearOutroFunciona() {
        Usuario usuario = novoUsuarioAutenticado("renomear");
        metodoPagamentoService.seedMetodosPadrao(usuario);
        UUID idPix = metodoPagamentoService.listar().stream()
                .filter(m -> m.tipo() == TipoMetodoPagamento.PIX).findFirst().orElseThrow().id();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                metodoPagamentoService.atualizar(idPix,
                        new MetodoPagamentoConfiguravelUpdateRequestDTO(null, null, "Pix Rápido", null, null, null)));
        assertEquals("Não é possível alterar o nome de um método fixo.", ex.getMessage());

        MetodoPagamentoConfiguravelResponseDTO outro = metodoPagamentoService.criar(
                new MetodoPagamentoConfiguravelRequestDTO(TipoMetodoPagamento.OUTRO, "Fiado"));
        MetodoPagamentoConfiguravelResponseDTO renomeado = metodoPagamentoService.atualizar(
                outro.id(), new MetodoPagamentoConfiguravelUpdateRequestDTO(null, null, "Fiado da Ana", null, null, null));
        assertEquals("Fiado da Ana", renomeado.nome());
    }

    @Test
    void atualizarMetodoInexistenteLancaResourceNotFound() {
        novoUsuarioAutenticado("not-found");
        assertThrows(ResourceNotFoundException.class, () ->
                metodoPagamentoService.atualizar(UUID.randomUUID(),
                        new MetodoPagamentoConfiguravelUpdateRequestDTO(false, null, null, null, null, null)));
    }
}
