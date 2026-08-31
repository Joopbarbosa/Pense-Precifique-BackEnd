package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #354 — GET /insumos?sort= com campo inexistente na entidade derrubava a request com 500
 * (UnknownPathException/InvalidDataAccessApiUsageException); passou a validar contra allowlist
 * (PageableOrdenacaoResolver) e responder 400. Cobre os campos ordenáveis (nome, numero,
 * custoUnitario, estoqueAtual, createdAt) e a rejeição de campo fora da allowlist.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoOrdenacaoIT {

    @Autowired InsumoService insumoService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private UUID iAId, iBId, iCId;

    private void seed() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("ins-ord-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        iAId = novoInsumo("Alfa", 1, new BigDecimal("5.00"), new BigDecimal("50"));
        iBId = novoInsumo("Bravo", 2, new BigDecimal("15.00"), new BigDecimal("150"));
        iCId = novoInsumo("Charlie", 3, new BigDecimal("10.00"), new BigDecimal("100"));
    }

    private UUID novoInsumo(String nome, int numero, BigDecimal custoUnitario, BigDecimal estoqueAtual) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome(nome).unidadeMedida("un")
                .custoUnitario(custoUnitario).estoqueAtual(estoqueAtual)
                .build()).getId();
    }

    @Test
    void ordenaPorNomeAlfabetico() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "nome"));
        assertEquals(List.of(iAId, iBId, iCId), asc); // Alfa, Bravo, Charlie
    }

    @Test
    void ordenaPorNumeroAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "numero"));
        assertEquals(List.of(iAId, iBId, iCId), asc);

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "numero"));
        assertEquals(List.of(iCId, iBId, iAId), desc);
    }

    @Test
    void ordenaPorCustoUnitarioAscEDesc() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "custoUnitario"));
        assertEquals(List.of(iAId, iCId, iBId), asc); // 5, 10, 15

        List<UUID> desc = idsNaOrdem(Sort.by(Sort.Direction.DESC, "custoUnitario"));
        assertEquals(List.of(iBId, iCId, iAId), desc);
    }

    @Test
    void ordenaPorEstoqueAtual() {
        seed();
        List<UUID> asc = idsNaOrdem(Sort.by(Sort.Direction.ASC, "estoqueAtual"));
        assertEquals(List.of(iAId, iCId, iBId), asc); // 50, 100, 150
    }

    @Test
    void campoForaDaAllowlistLancaBusinessException() {
        seed();
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "usuario.email"));
        assertThrows(BusinessException.class, () -> insumoService.listar(null, pageable));
    }

    private List<UUID> idsNaOrdem(Sort sort) {
        Page<InsumoResponseDTO> pagina = insumoService.listar(null, PageRequest.of(0, 20, sort));
        return pagina.getContent().stream().map(InsumoResponseDTO::id).toList();
    }
}
