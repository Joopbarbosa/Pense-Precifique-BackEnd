package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.caixa.AbrirCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.FecharCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PP-662 (DT-NOVA-17, RN-NOVA-3) — datas do turno do Caixa gravadas pelo relógio injetado (Brasília). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CaixaTurnoRelogioIT.RelogioFixoConfig.class)
class CaixaTurnoRelogioIT {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    // 01/07 02:30 UTC = 30/06 23:30 em Brasília
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 6, 30, 23, 30);

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CaixaTurnoService caixaTurnoService;

    private void autenticar() {
        Usuario u = usuarioRepository.save(Usuario.builder()
                .email("caixa-relogio-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    @Test
    void aberturaEFechamentoUsamOHorarioDeBrasiliaDoRelogioInjetado() {
        autenticar();
        CaixaTurnoResponseDTO aberto = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("10.00")));
        assertEquals(AGORA, aberto.dataAbertura());

        CaixaTurnoResponseDTO fechado = caixaTurnoService.fecharTurno(aberto.id(),
                new FecharCaixaTurnoRequestDTO(new BigDecimal("10.00"), null));
        assertEquals(AGORA, fechado.dataFechamento());
    }

    @Test
    void fecharTurnoDeOutraContaOuInexistenteContinuaNaoEncontrado() {
        autenticar();
        assertThrows(ResourceNotFoundException.class, () -> caixaTurnoService.fecharTurno(UUID.randomUUID(),
                new FecharCaixaTurnoRequestDTO(new BigDecimal("1.00"), null)));
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(Instant.parse("2026-07-01T02:30:00Z"), BRASILIA);
        }
    }
}
