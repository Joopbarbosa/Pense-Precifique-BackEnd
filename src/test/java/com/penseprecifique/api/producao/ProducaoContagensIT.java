package com.penseprecifique.api.producao;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.dto.response.producao.ProducaoContagensResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RN-NOVA-4 (V0.10.0, #336) — contadores por filtro (estado) agregados no backend, mesmo padrão
 * já usado por ProdutoContagensResponse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProducaoContagensIT {

    @Autowired ProducaoService producaoService;
    @Autowired ProducaoRepository producaoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;
    private final AtomicInteger contador = new AtomicInteger(1);

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("producao-contagens-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private void criar(EstadoProducao estado) {
        producaoRepository.save(Producao.builder()
                .usuario(usuario).numero(contador.getAndIncrement()).estado(estado)
                .dataInicio(LocalDate.now()).dataTerminoPrevista(LocalDate.now().plusDays(1)).build());
    }

    @Test
    void contagensRefletemTodosOsEstadosSemDependerDePaginacao() {
        seedUsuario();
        criar(EstadoProducao.AGUARDANDO_INICIO);
        criar(EstadoProducao.AGUARDANDO_INICIO);
        criar(EstadoProducao.EM_ANDAMENTO);
        criar(EstadoProducao.TRAVADA);
        criar(EstadoProducao.FINALIZADA);
        criar(EstadoProducao.CANCELADA);
        criar(EstadoProducao.NAO_REALIZADA);

        ProducaoContagensResponse contagens = producaoService.contagens();

        assertEquals(7, contagens.getTotal());
        assertEquals(2, contagens.getAguardandoInicio());
        assertEquals(1, contagens.getEmAndamento());
        assertEquals(1, contagens.getTravada());
        assertEquals(1, contagens.getFinalizada());
        assertEquals(1, contagens.getCancelada());
        assertEquals(1, contagens.getNaoRealizada());
    }
}
