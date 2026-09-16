package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.StatusCaixaTurno;
import com.penseprecifique.api.shared.domain.enums.TipoCaixaMovimento;
import com.penseprecifique.api.shared.dto.request.caixa.AbrirCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.CaixaMovimentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.FecharCaixaTurnoRequestDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaMovimentoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.CaixaTurnoResponseDTO;
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

import static org.junit.jupiter.api.Assertions.*;

/** #488 — RN-NOVA-6/8/9. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CaixaTurnoServiceIT {

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CaixaTurnoService caixaTurnoService;

    private void seedUsuario(String prefixo) {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email(prefixo + "-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    @Test
    void abrirTurnoFuncionaEBuscarTurnoAbertoRetornaOMesmo() {
        seedUsuario("abrir");

        CaixaTurnoResponseDTO aberto = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));

        assertEquals(StatusCaixaTurno.ABERTO, aberto.status());
        assertEquals(new BigDecimal("100.00"), aberto.valorAbertura());
        assertNull(aberto.dataFechamento());

        CaixaTurnoResponseDTO atual = caixaTurnoService.buscarTurnoAberto();
        assertEquals(aberto.id(), atual.id());
    }

    @Test
    void buscarTurnoAbertoSemTurnoLancaResourceNotFound() {
        seedUsuario("sem-turno");
        assertThrows(ResourceNotFoundException.class, caixaTurnoService::buscarTurnoAberto);
    }

    @Test
    void abrirSegundoTurnoEBloqueado() {
        seedUsuario("segundo-turno");
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("50.00")));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("50.00"))));
        assertEquals("Já existe um caixa aberto.", ex.getMessage());
    }

    @Test
    void registrarSangriaESuprimentoComMotivoValidoFunciona() {
        seedUsuario("movimento-ok");
        caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        String motivo30 = "Retirada para pagamento de fornecedor";
        assertTrue(motivo30.length() >= 30);

        CaixaMovimentoResponseDTO sangria = caixaTurnoService.registrarMovimento(
                new CaixaMovimentoRequestDTO(TipoCaixaMovimento.SANGRIA, new BigDecimal("30.00"), motivo30));

        assertEquals(TipoCaixaMovimento.SANGRIA, sangria.tipo());
        assertEquals(new BigDecimal("30.00"), sangria.valor());
    }

    @Test
    void registrarMovimentoSemTurnoAbertoEBloqueado() {
        seedUsuario("movimento-sem-turno");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                caixaTurnoService.registrarMovimento(new CaixaMovimentoRequestDTO(
                        TipoCaixaMovimento.SUPRIMENTO, new BigDecimal("20.00"),
                        "Motivo com trinta caracteres ou mais para passar")));
        assertEquals("Não há caixa aberto.", ex.getMessage());
    }

    @Test
    void listarMovimentosRetornaOsRegistradosNoTurno() {
        seedUsuario("listar-movimentos");
        CaixaTurnoResponseDTO turno = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        caixaTurnoService.registrarMovimento(new CaixaMovimentoRequestDTO(
                TipoCaixaMovimento.SUPRIMENTO, new BigDecimal("40.00"), "Suprimento de troco extra para o dia de hoje"));
        caixaTurnoService.registrarMovimento(new CaixaMovimentoRequestDTO(
                TipoCaixaMovimento.SANGRIA, new BigDecimal("15.00"), "Sangria para compra de material de limpeza"));

        List<CaixaMovimentoResponseDTO> movimentos = caixaTurnoService.listarMovimentos(turno.id());

        assertEquals(2, movimentos.size());
    }

    @Test
    void fecharTurnoCalculaDiferencaSemBloquear() {
        seedUsuario("fechar");
        CaixaTurnoResponseDTO turno = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        caixaTurnoService.registrarMovimento(new CaixaMovimentoRequestDTO(
                TipoCaixaMovimento.SUPRIMENTO, new BigDecimal("50.00"), "Suprimento para troco adicional no caixa hoje"));
        caixaTurnoService.registrarMovimento(new CaixaMovimentoRequestDTO(
                TipoCaixaMovimento.SANGRIA, new BigDecimal("20.00"), "Sangria para pagamento de despesa urgente hoje"));

        // esperado = 100 + 50 - 20 = 130.00 (sem vendas — VendaCaixaPagamento ainda não existe, #487)
        CaixaTurnoResponseDTO fechado = caixaTurnoService.fecharTurno(
                turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("125.00")));

        assertEquals(StatusCaixaTurno.FECHADO, fechado.status());
        assertEquals(0, new BigDecimal("130.00").compareTo(fechado.valorFechamentoEsperado()));
        assertEquals(0, new BigDecimal("-5.00").compareTo(fechado.diferenca()));
        assertNotNull(fechado.dataFechamento());
    }

    @Test
    void fecharTurnoJaFechadoLancaResourceNotFound() {
        seedUsuario("fechar-2x");
        CaixaTurnoResponseDTO turno = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        caixaTurnoService.fecharTurno(turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("100.00")));

        assertThrows(ResourceNotFoundException.class, () ->
                caixaTurnoService.fecharTurno(turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("100.00"))));
    }

    @Test
    void depoisDeFecharEPossivelAbrirNovoTurno() {
        seedUsuario("reabrir");
        CaixaTurnoResponseDTO turno = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("100.00")));
        caixaTurnoService.fecharTurno(turno.id(), new FecharCaixaTurnoRequestDTO(new BigDecimal("100.00")));

        CaixaTurnoResponseDTO novo = caixaTurnoService.abrirTurno(new AbrirCaixaTurnoRequestDTO(new BigDecimal("80.00")));
        assertEquals(StatusCaixaTurno.ABERTO, novo.status());
    }
}
