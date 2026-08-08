package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.AcaoResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.OperacaoPosResolucaoVinculo;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.insumo.ResolverVinculosInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.SubstituicaoInsumoRequestDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #228/#237 — fluxo "inativar vinculados / substituir" ao inativar/excluir Insumo: excluir() ganha a
 * mesma trava de vínculo já existente em inativar() (INS-011), e POST /insumos/{id}/resolver-vinculos
 * resolve os vínculos em massa (inativando os produtos vinculados ou substituindo o insumo na ficha
 * técnica de cada um) antes de prosseguir com a operação original na mesma chamada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoResolverVinculosIT {

    @Autowired InsumoService insumoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;
    private int proximoNumero = 1;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-resolver-vinculos-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo novoInsumo(String nome, BigDecimal custoUnitario) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(proximoNumero++).nome(nome).marca("X").unidadeMedida("un")
                .custoUnitario(custoUnitario).estoqueAtual(new BigDecimal("100")).build());
    }

    private Produto novoProduto(String nome) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(proximoNumero++).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(true).precoCusto(BigDecimal.ZERO)
                .precoVenda(new BigDecimal("10.00")).build());
    }

    private void vincular(Produto produto, Insumo insumo, BigDecimal quantidade) {
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(produto).insumo(insumo).quantidade(quantidade).build());
    }

    @Test
    void excluirSemVinculoFuncionaDireto() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", new BigDecimal("1.0000"));

        insumoService.excluir(insumo.getId());

        Insumo excluido = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertTrue(excluido.getDeletedAt() != null);
    }

    @Test
    void excluirComVinculoSemResolverContinuaBloqueado() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", new BigDecimal("1.0000"));
        Produto caixa = novoProduto("Caixa de bombom");
        vincular(caixa, insumo, new BigDecimal("2"));

        BusinessException ex = assertThrows(BusinessException.class, () -> insumoService.excluir(insumo.getId()));
        assertTrue(ex.getMessage().contains("Caixa de bombom"));

        Insumo inalterado = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertNull(inalterado.getDeletedAt(), "não deve excluir quando bloqueado");
    }

    @Test
    void resolverVinculosInativarVinculadosInativaProdutosEProsseguiOperacaoOriginal() {
        seedUsuario();
        Insumo insumo = novoInsumo("INS-60", new BigDecimal("1.0000"));
        Produto pro3 = novoProduto("PRO-3");
        Produto pro4 = novoProduto("PRO-4");
        Produto pro5 = novoProduto("PRO-5");
        vincular(pro3, insumo, new BigDecimal("1"));
        vincular(pro4, insumo, new BigDecimal("2"));
        vincular(pro5, insumo, new BigDecimal("3"));

        ResolverVinculosInsumoRequestDTO request = new ResolverVinculosInsumoRequestDTO(
                AcaoResolucaoVinculo.REMOVER_VINCULOS, OperacaoPosResolucaoVinculo.INATIVAR, null);
        insumoService.resolverVinculos(insumo.getId(), request);

        assertFalse(produtoRepository.findById(pro3.getId()).orElseThrow().getAtivo());
        assertFalse(produtoRepository.findById(pro4.getId()).orElseThrow().getAtivo());
        assertFalse(produtoRepository.findById(pro5.getId()).orElseThrow().getAtivo());
        assertFalse(insumoRepository.findById(insumo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void resolverVinculosSubstituirCobrindoTudoAtualizaFichaTecnicaEProsseguiComExcluir() {
        seedUsuario();
        Insumo insumoAntigo = novoInsumo("INS-60", new BigDecimal("1.0000"));
        Insumo substitutoA = novoInsumo("Substituto A", new BigDecimal("5.0000"));
        Insumo substitutoB = novoInsumo("Substituto B", new BigDecimal("7.0000"));
        Produto pro3 = novoProduto("PRO-3");
        Produto pro4 = novoProduto("PRO-4");
        vincular(pro3, insumoAntigo, new BigDecimal("2"));
        vincular(pro4, insumoAntigo, new BigDecimal("3"));

        ResolverVinculosInsumoRequestDTO request = new ResolverVinculosInsumoRequestDTO(
                AcaoResolucaoVinculo.SUBSTITUIR, OperacaoPosResolucaoVinculo.EXCLUIR,
                List.of(new SubstituicaoInsumoRequestDTO(pro3.getId(), substitutoA.getId()),
                        new SubstituicaoInsumoRequestDTO(pro4.getId(), substitutoB.getId())));
        insumoService.resolverVinculos(insumoAntigo.getId(), request);

        List<FichaTecnicaItem> itensPro3 = fichaTecnicaItemRepository.findByProdutoId(pro3.getId());
        assertEquals(1, itensPro3.size());
        assertEquals(substitutoA.getId(), itensPro3.get(0).getInsumo().getId());
        assertEquals(0, new BigDecimal("10.0000").compareTo(produtoRepository.findById(pro3.getId()).orElseThrow().getPrecoCusto()));

        List<FichaTecnicaItem> itensPro4 = fichaTecnicaItemRepository.findByProdutoId(pro4.getId());
        assertEquals(1, itensPro4.size());
        assertEquals(substitutoB.getId(), itensPro4.get(0).getInsumo().getId());
        assertEquals(0, new BigDecimal("21.0000").compareTo(produtoRepository.findById(pro4.getId()).orElseThrow().getPrecoCusto()));

        assertTrue(insumoRepository.findById(insumoAntigo.getId()).orElseThrow().getDeletedAt() != null);
    }

    @Test
    void resolverVinculosSubstituirFaltandoCobrirLancaEnaoAplicaNada() {
        seedUsuario();
        Insumo insumoAntigo = novoInsumo("INS-60", new BigDecimal("1.0000"));
        Insumo substituto = novoInsumo("Substituto A", new BigDecimal("5.0000"));
        Produto pro3 = novoProduto("PRO-3");
        Produto pro4 = novoProduto("PRO-4");
        vincular(pro3, insumoAntigo, new BigDecimal("2"));
        vincular(pro4, insumoAntigo, new BigDecimal("3"));

        ResolverVinculosInsumoRequestDTO request = new ResolverVinculosInsumoRequestDTO(
                AcaoResolucaoVinculo.SUBSTITUIR, OperacaoPosResolucaoVinculo.EXCLUIR,
                List.of(new SubstituicaoInsumoRequestDTO(pro3.getId(), substituto.getId())));

        assertThrows(BusinessException.class, () -> insumoService.resolverVinculos(insumoAntigo.getId(), request));

        assertEquals(insumoAntigo.getId(), fichaTecnicaItemRepository.findByProdutoId(pro3.getId()).get(0).getInsumo().getId());
        assertEquals(insumoAntigo.getId(), fichaTecnicaItemRepository.findByProdutoId(pro4.getId()).get(0).getInsumo().getId());
        assertNull(insumoRepository.findById(insumoAntigo.getId()).orElseThrow().getDeletedAt());
    }
}
