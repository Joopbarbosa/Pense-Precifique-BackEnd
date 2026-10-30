package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.compra.CompraRequest;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CompraClockIT.RelogioFixoConfig.class)
class CompraClockIT {

    private static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @Autowired CompraService compraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DashboardCompraService dashboardCompraService;

    @BeforeEach
    void autenticar() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("compra-clock-" + UUID.randomUUID() + "@test.com").senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void previsaoAntesDeHojeEhRecusada() {
        assertThrows(BusinessException.class, () -> compraService.criarRascunho(request(HOJE.minusDays(1), HOJE.minusDays(1))));
    }

    @Test
    void previsaoIgualHojeEhAceita() {
        compraService.criarRascunho(request(HOJE, HOJE));
    }

    @Test
    void dataDeCompraFuturaEhRecusada() {
        assertThrows(BusinessException.class, () -> compraService.criarRascunho(request(HOJE.plusDays(1), null)));
    }

    /** #1209 (D2) — o dashboard usa o relógio injetado: com hoje = 15/06/2026, julho/2026 ainda não é um mês exibido. */
    @Test
    void dashboardUsaORelogioInjetado() {
        org.springframework.data.domain.Pageable pagina = org.springframework.data.domain.PageRequest.of(0, 10);
        dashboardCompraService.vendasCmv(null, null, java.time.YearMonth.of(2026, 6), pagina);
        assertThrows(BusinessException.class,
                () -> dashboardCompraService.vendasCmv(null, null, java.time.YearMonth.of(2026, 7), pagina));
    }

    private CompraRequest request(LocalDate dataCompra, LocalDate previsao) {
        return new CompraRequest(dataCompra, false, null, false, null, null,
                List.of(), null, null, null, false, previsao);
    }

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),
                    ZoneId.of("America/Sao_Paulo"));
        }
    }
}
