package com.penseprecifique.api.empresa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.request.EmpresaRequestDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * #142 — UNIQUE(usuario_id) em empresas (índice parcial, respeita soft delete) e
 * configuracoes_precificacao (constraint direta, sem soft delete nesta tabela). upsertEmpresa()/
 * upsertConfiguracao() sempre fazem find-antes-de-inserir, então só colidem sob concorrência real
 * (duas chamadas simultâneas do mesmo usuário, nenhuma encontra a linha da outra antes de inserir) —
 * mesmo padrão de teste de NumeroSequencialConcorrenciaIT (CyclicBarrier força a corrida real).
 * A perdedora da corrida deve receber BusinessException (mensagem clara), não a exception genérica
 * de constraint do banco vazando pro cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EmpresaConfiguracaoUnicidadeUsuarioIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EmpresaService empresaService;
    @Autowired ConfiguracaoService configuracaoService;

    private Usuario novoUsuario(String prefixo) {
        return usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
    }

    @Test
    void concorrenciaNaCriacaoDeEmpresaGeraErroDeNegocioClaro() throws Exception {
        Usuario usuario = novoUsuario("empresa-conc");

        CyclicBarrier barreira = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            futures.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
                EmpresaRequestDTO req = new EmpresaRequestDTO("Empresa Concorrente", null, null, null, null);
                barreira.await();
                try {
                    return empresaService.upsertEmpresa(req);
                } catch (BusinessException e) {
                    return e;
                }
            }));
        }
        List<Object> resultados = new ArrayList<>();
        for (Future<Object> f : futures) {
            resultados.add(f.get());
        }
        pool.shutdown();

        long sucessos = resultados.stream().filter(r -> !(r instanceof Exception)).count();
        long erros = resultados.stream().filter(r -> r instanceof BusinessException).count();
        assertEquals(1, sucessos, "exatamente uma das duas chamadas concorrentes deve ter criado a empresa: " + resultados);
        assertEquals(1, erros, "a perdedora da corrida deve receber BusinessException, não exception genérica: " + resultados);
        assertInstanceOf(BusinessException.class, resultados.stream().filter(r -> r instanceof Exception).findFirst().orElseThrow());
    }

    @Test
    void concorrenciaNaCriacaoDeConfiguracaoGeraErroDeNegocioClaro() throws Exception {
        Usuario usuario = novoUsuario("config-conc");

        CyclicBarrier barreira = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> futures = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            futures.add(pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
                ConfiguracaoRequestDTO req = new ConfiguracaoRequestDTO(new BigDecimal("50.00"), new BigDecimal("100.00"));
                barreira.await();
                try {
                    return configuracaoService.upsertConfiguracao(req);
                } catch (BusinessException e) {
                    return e;
                }
            }));
        }
        List<Object> resultados = new ArrayList<>();
        for (Future<Object> f : futures) {
            resultados.add(f.get());
        }
        pool.shutdown();

        long sucessos = resultados.stream().filter(r -> !(r instanceof Exception)).count();
        long erros = resultados.stream().filter(r -> r instanceof BusinessException).count();
        assertEquals(1, sucessos, "exatamente uma das duas chamadas concorrentes deve ter criado a configuração: " + resultados);
        assertEquals(1, erros, "a perdedora da corrida deve receber BusinessException, não exception genérica: " + resultados);
    }
}
