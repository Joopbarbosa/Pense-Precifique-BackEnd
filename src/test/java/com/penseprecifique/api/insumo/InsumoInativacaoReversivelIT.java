package com.penseprecifique.api.insumo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.produto.FichaTecnicaService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.insumo.ItemLoteCompraRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #228/INS-010/INS-011 — inativação reversível de Insumo (ativo=false/true), distinta do soft-delete
 * existente (DELETE, deletedAt). Diferente do padrão de Produto: aqui a trava acontece na tentativa
 * de inativação em si (INS-011) quando o insumo está em ficha técnica de produto não excluído, não
 * depois na venda.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InsumoInativacaoReversivelIT {

    @Autowired InsumoService insumoService;
    @Autowired FichaTecnicaService fichaTecnicaService;
    @Autowired LoteCompraService loteCompraService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired FichaTecnicaItemRepository fichaTecnicaItemRepository;

    private Usuario usuario;

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("insumo-inativacao-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo novoInsumo(String nome, int numero) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(numero).nome(nome).marca("X").unidadeMedida("un")
                .estoqueAtual(new BigDecimal("100")).build());
    }

    private Produto novoProduto(String nome, int numero) {
        return produtoRepository.save(Produto.builder()
                .usuario(usuario).numero(numero).nome(nome).tipo(TipoProduto.PRODUTO)
                .tempoProducao(30).ativo(true)
                .precoVenda(new BigDecimal("10.00"))
                .build());
    }

    @Test
    void inativarSemVinculoSetaAtivoFalseSemExcluir() {
        // INS-CEN-028
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);

        insumoService.inativar(insumo.getId());

        Insumo atualizado = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertFalse(atualizado.getAtivo());
        assertTrue(atualizado.getDeletedAt() == null, "inativar não deve tocar deletedAt");
    }

    @Test
    void inativarComVinculoEmFichaTecnicaBloqueiaComListaDeProdutos() {
        // INS-CEN-029
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);
        Produto caixa = novoProduto("Caixa de bombom", 1);
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(caixa).insumo(insumo).quantidade(new BigDecimal("2")).build());

        BusinessException ex = assertThrows(BusinessException.class, () -> insumoService.inativar(insumo.getId()));

        assertTrue(ex.getMessage().contains("Caixa de bombom"), "mensagem deveria citar o produto vinculado: " + ex.getMessage());

        Insumo inalterado = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertTrue(inalterado.getAtivo(), "não deve inativar quando bloqueado");
    }

    @Test
    void inativarAposRemoverDaFichaTecnicaPassaAFuncionar() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);
        Produto caixa = novoProduto("Caixa de bombom", 1);
        fichaTecnicaItemRepository.save(FichaTecnicaItem.builder()
                .produto(caixa).insumo(insumo).quantidade(new BigDecimal("2")).build());

        assertThrows(BusinessException.class, () -> insumoService.inativar(insumo.getId()));

        fichaTecnicaService.salvarFichaTecnica(caixa, List.of(), usuario.getId());

        insumoService.inativar(insumo.getId());

        Insumo atualizado = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertFalse(atualizado.getAtivo());
    }

    @Test
    void reativarEInativarSaoIdempotentes() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);

        insumoService.inativar(insumo.getId());
        insumoService.inativar(insumo.getId());
        assertFalse(insumoRepository.findById(insumo.getId()).orElseThrow().getAtivo());

        insumoService.reativar(insumo.getId());
        insumoService.reativar(insumo.getId());
        assertTrue(insumoRepository.findById(insumo.getId()).orElseThrow().getAtivo());
    }

    @Test
    void excluirContinuaFazendoSoftDeleteTotalDistintoDeInativar() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);

        insumoService.excluir(insumo.getId());

        Insumo excluido = insumoRepository.findById(insumo.getId()).orElseThrow();
        assertTrue(excluido.getDeletedAt() != null, "excluir deve setar deletedAt (remoção lógica total)");
        assertTrue(excluido.getAtivo(), "excluir não deve mexer em ativo — são mecanismos distintos");

        assertThrows(ResourceNotFoundException.class, () -> insumoService.inativar(insumo.getId()),
                "insumo excluído não é encontrado pelo fluxo de inativação reversível");
    }

    @Test
    void fichaTecnicaBloqueiaAdicaoDeInsumoInativo() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);
        insumoService.inativar(insumo.getId());
        Produto caixa = novoProduto("Caixa de bombom", 1);

        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(insumo.getId());
        item.setQuantidade(new BigDecimal("2"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fichaTecnicaService.salvarFichaTecnica(caixa, List.of(item), usuario.getId()));
        assertTrue(ex.getMessage().contains("inativo"));
    }

    @Test
    void loteCompraBloqueiaCompraDeInsumoInativo() {
        seedUsuario();
        Insumo insumo = novoInsumo("Papelão", 1);
        insumoService.inativar(insumo.getId());

        RegistrarLoteCompraRequestDTO request = new RegistrarLoteCompraRequestDTO(
                null,
                List.of(new ItemLoteCompraRequestDTO(insumo.getId(), new BigDecimal("10"), new BigDecimal("50.00"))));

        BusinessException ex = assertThrows(BusinessException.class, () -> loteCompraService.registrarLote(request));
        assertTrue(ex.getMessage().contains("inativo"));
    }
}
