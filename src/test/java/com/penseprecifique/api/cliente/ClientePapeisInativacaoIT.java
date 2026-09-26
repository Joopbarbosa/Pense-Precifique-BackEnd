package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteContagensResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V0.15.0 — #536 (papéis, tipo de pessoa, documento) e #538 (inativar/reativar reversível).
 * CEN-NOVO-1, 2, 3, 26, 27 e a trava de vínculo novo da RN-NOVA-3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ClientePapeisInativacaoIT {

    @Autowired ClienteService clienteService;
    @Autowired ClienteRepository clienteRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Usuario usuario;

    @BeforeEach
    void autenticar() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("cli-papeis-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private ClienteRequest req(String nome, boolean cliente, boolean fornecedor) {
        ClienteRequest r = new ClienteRequest();
        r.setNome(nome);
        r.setEhCliente(cliente);
        r.setEhFornecedor(fornecedor);
        return r;
    }

    private List<String> nomes(Boolean ativo, PapelCadastro papel) {
        return clienteService.listar(null, ativo, papel, PageRequest.of(0, 50, org.springframework.data.domain.Sort.by("nome"))).getContent().stream()
                .map(ClienteResponse::getNome).toList();
    }

    @Test
    void cen1_registroClienteEFornecedorApareceNosDoisFiltros() {
        ClienteResponse r = clienteService.cadastrar(req("Papelaria Central", true, true));
        clienteService.cadastrar(req("Mariana Costa", true, false));
        clienteService.cadastrar(req("Atacado Arte", false, true));

        assertEquals("CLI-1", r.getIdentificador());
        assertEquals(List.of("Mariana Costa", "Papelaria Central"), nomes(null, PapelCadastro.CLIENTE));
        assertEquals(List.of("Atacado Arte", "Papelaria Central"), nomes(null, PapelCadastro.FORNECEDOR));
        assertEquals(3, nomes(null, null).size());

        ClienteContagensResponse c = clienteService.contagens();
        assertEquals(3, c.ativos());
        assertEquals(2, c.clientes());
        assertEquals(2, c.fornecedores());
        assertEquals(0, c.inativos());
    }

    @Test
    void cen2_semNenhumPapelBloqueia() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> clienteService.cadastrar(req("Sem papel", false, false)));
        assertEquals("Marque se é Cliente, Fornecedor ou os dois.", ex.getMessage());

        ClienteRequest nulos = new ClienteRequest();
        nulos.setNome("Papéis nulos");
        assertThrows(BusinessException.class, () -> clienteService.cadastrar(nulos));
    }

    @Test
    void cen3_inativarEReativarFornecedor() {
        UUID id = clienteService.cadastrar(req("Papelaria Central", false, true)).getId();

        clienteService.inativar(id);
        assertFalse(nomes(null, PapelCadastro.FORNECEDOR).contains("Papelaria Central"));
        assertEquals(List.of("Papelaria Central"), nomes(false, null));
        // vínculo existente: o detalhe continua abrindo o registro inativo (antes: 404)
        assertFalse(clienteService.buscarPorId(id).isAtiva());
        assertEquals(1, clienteService.contagens().inativos());

        clienteService.reativar(id);
        assertEquals(List.of("Papelaria Central"), nomes(null, PapelCadastro.FORNECEDOR));
        assertTrue(clienteService.buscarPorId(id).isAtiva());
    }

    @Test
    void cen26_cnpjAlfanumericoMinusculoSalvaNormalizado() {
        ClienteRequest r = req("Fornecedor PJ", false, true);
        r.setTipoPessoa(TipoPessoa.JURIDICA);
        r.setDocumento("12.abc.345/01de-35");

        ClienteResponse salvo = clienteService.cadastrar(r);
        assertEquals("12ABC34501DE35", salvo.getDocumento());
        assertEquals(TipoPessoa.JURIDICA, salvo.getTipoPessoa());
    }

    @Test
    void documentoInvalidoBloqueia() {
        ClienteRequest pf = req("Pessoa", true, false);
        pf.setDocumento("529.982.247-24");
        assertEquals("CPF inválido",
                assertThrows(BusinessException.class, () -> clienteService.cadastrar(pf)).getMessage());

        ClienteRequest pj = req("Empresa", true, false);
        pj.setTipoPessoa(TipoPessoa.JURIDICA);
        pj.setDocumento("12.ABC.345/01DE-36");
        assertEquals("CNPJ inválido",
                assertThrows(BusinessException.class, () -> clienteService.cadastrar(pj)).getMessage());
    }

    @Test
    void cen27_documentoDuplicadoBloqueiaMesmoInativo() {
        ClienteRequest primeiro = req("Papelaria Central", false, true);
        primeiro.setTipoPessoa(TipoPessoa.JURIDICA);
        primeiro.setDocumento("11.222.333/0001-81");
        UUID id = clienteService.cadastrar(primeiro).getId();
        clienteService.inativar(id);

        ClienteRequest outro = req("Outra", true, false);
        outro.setTipoPessoa(TipoPessoa.JURIDICA);
        outro.setDocumento("11222333000181");
        BusinessException ex = assertThrows(BusinessException.class, () -> clienteService.cadastrar(outro));
        assertEquals("Já existe um cadastro com este CPF/CNPJ: CLI-1 — Papelaria Central", ex.getMessage());

        // editar o próprio registro mantendo o documento não é duplicidade
        primeiro.setObservacoes("Entrega às terças");
        assertEquals("Entrega às terças", clienteService.editar(id, primeiro).getObservacoes());
    }

    @Test
    void estrangeiroAceitaDocumentoLivreSemValidarDv() {
        ClienteRequest r = req("John Smith", true, false);
        r.setTipoPessoa(TipoPessoa.ESTRANGEIRO);
        r.setDocumento(" p1234567 ");
        assertEquals("P1234567", clienteService.cadastrar(r).getDocumento());
    }

    @Test
    void desmarcarPapelComHistoricoEPermitido() {
        UUID id = clienteService.cadastrar(req("Mariana Costa", true, true)).getId();
        ClienteResponse editado = clienteService.editar(id, req("Mariana Costa", true, false));
        assertFalse(editado.isEhFornecedor());
        assertTrue(editado.isEhCliente());
    }

    @Test
    void rn3_vinculoNovoExigeAtivoComPapel_vinculoExistenteNao() {
        UUID fornecedorPuro = clienteService.cadastrar(req("Atacado Arte", false, true)).getId();
        UUID inativo = clienteService.cadastrar(req("Mariana Costa", true, false)).getId();
        clienteService.inativar(inativo);
        UUID uid = usuario.getId();

        assertEquals("Este cadastro não está ativo como Cliente.", assertThrows(BusinessException.class,
                () -> clienteService.resolverParaVinculo(fornecedorPuro, uid, PapelCadastro.CLIENTE, null)).getMessage());
        assertEquals("Este cadastro não está ativo como Cliente.", assertThrows(BusinessException.class,
                () -> clienteService.resolverParaVinculo(inativo, uid, PapelCadastro.CLIENTE, null)).getMessage());
        assertEquals("Este cadastro não está ativo como Fornecedor.", assertThrows(BusinessException.class,
                () -> clienteService.resolverParaVinculo(inativo, uid, PapelCadastro.FORNECEDOR, null)).getMessage());

        // mesmo id já salvo no vínculo: aceito sem trava (CEN-NOVO-4)
        assertEquals(inativo, clienteService.resolverParaVinculo(inativo, uid, PapelCadastro.CLIENTE, inativo).getId());
        assertEquals(fornecedorPuro, clienteService.resolverParaVinculo(fornecedorPuro, uid, PapelCadastro.FORNECEDOR, null).getId());
    }

    @Test
    void buscaPorDocumentoSemMascara() {
        ClienteRequest r = req("Empresa X", false, true);
        r.setTipoPessoa(TipoPessoa.JURIDICA);
        r.setDocumento("11222333000181");
        clienteService.cadastrar(r);
        clienteService.cadastrar(req("Outra", true, false));

        List<String> achados = clienteService.listar("11.222.333", null, null, PageRequest.of(0, 20))
                .getContent().stream().map(ClienteResponse::getNome).toList();
        assertEquals(List.of("Empresa X"), achados);
    }
}
