package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #489 — campos fiscais mínimos no cadastro de produto (RN-NOVA-12/13, V0.12.0). Todos opcionais
 * (nenhum bloqueia salvar/vender), exceto CSOSN quando preenchido — lista fechada (CEN-NOVO-13).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoCamposFiscaisIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;

    private Usuario usuario;
    private Insumo insumo;

    private void seedUsuarioEInsumo() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-fiscal-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Papel").marca("X").unidadeMedida("un")
                .custoUnitario(new BigDecimal("2.00")).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).fracionavel(true).build());
    }

    private ProdutoRequest requestBase(String nome) {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome(nome);
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(10);
        request.setRendimento(BigDecimal.ONE);
        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(insumo.getId());
        item.setQuantidade(BigDecimal.ONE);
        request.setFichaTecnica(List.of(item));
        return request;
    }

    @Test
    void cadastroSemCamposFiscaisFuncionaNormalmente() {
        seedUsuarioEInsumo();
        ProdutoRequest request = requestBase("Caderno Simples");

        ProdutoDetalheResponse response = produtoService.cadastrar(request);

        assertNull(response.getCodigoBarras());
        assertNull(response.getNcm());
        assertNull(response.getCsosn());
    }

    @Test
    void cadastroComTodosOsCamposFiscaisValidosPersisteCorretamente() {
        seedUsuarioEInsumo();
        ProdutoRequest request = requestBase("Caderno Fiscal");
        request.setCodigoBarras("7891234567895");
        request.setNcm("48202000");
        request.setCfop("5102");
        request.setCest("2103200");
        request.setUnidadeComercial("UN");
        request.setCsosn("102");

        ProdutoDetalheResponse response = produtoService.cadastrar(request);

        assertEquals("7891234567895", response.getCodigoBarras());
        assertEquals("48202000", response.getNcm());
        assertEquals("5102", response.getCfop());
        assertEquals("2103200", response.getCest());
        assertEquals("UN", response.getUnidadeComercial());
        assertEquals("102", response.getCsosn());
    }

    @Test
    void csosnForaDaListaFechadaEBloqueado() {
        seedUsuarioEInsumo();
        ProdutoRequest request = requestBase("Caderno CSOSN Inválido");
        request.setCsosn("999");

        BusinessException ex = assertThrows(BusinessException.class, () -> produtoService.cadastrar(request));
        assertEquals("O código CSOSN informado não é válido.", ex.getMessage());
    }

    @Test
    void edicaoAtualizaCamposFiscais() {
        seedUsuarioEInsumo();
        ProdutoDetalheResponse criado = produtoService.cadastrar(requestBase("Caderno Editável"));

        ProdutoRequest edicao = requestBase("Caderno Editável");
        edicao.setCsosn("500");
        edicao.setNcm("48201000");

        ProdutoDetalheResponse editado = produtoService.editar(criado.getId(), edicao);

        assertEquals("500", editado.getCsosn());
        assertEquals("48201000", editado.getNcm());
    }
}
