package com.penseprecifique.api.produto;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.produto.FichaTecnicaItemRequest;
import com.penseprecifique.api.shared.dto.request.produto.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.produto.ProdutoDetalheResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-NOVA-2 (V0.10.0, #299, altera PDT-016) — campo fracionável de Produto vira persistido+
 * editável (padrão calculado+override, mesmo modelo de precoVenda/RN-038a). CEN-NOVO-3/4/5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProdutoFracionavelOverrideIT {

    @Autowired ProdutoService produtoService;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired InsumoRepository insumoRepository;

    private Usuario usuario;
    private final AtomicInteger contadorInsumo = new AtomicInteger(1);

    private void seedUsuario() {
        usuario = usuarioRepository.save(Usuario.builder()
                .email("produto-fracionavel-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));
    }

    private Insumo criarInsumo(boolean fracionavel) {
        return insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(contadorInsumo.getAndIncrement()).nome("Insumo " + UUID.randomUUID()).unidadeMedida("un")
                .custoUnitario(new BigDecimal("4.00")).estoqueAtual(new BigDecimal("100"))
                .permitirEstoqueNegativo(true).fracionavel(fracionavel).build());
    }

    private FichaTecnicaItemRequest itemFichaTecnica(UUID insumoId, BigDecimal quantidade) {
        FichaTecnicaItemRequest item = new FichaTecnicaItemRequest();
        item.setInsumoId(insumoId);
        item.setQuantidade(quantidade);
        return item;
    }

    private ProdutoRequest requestBase(UUID insumoId, Boolean fracionavelInformado) {
        ProdutoRequest request = new ProdutoRequest();
        request.setNome("Produto " + UUID.randomUUID());
        request.setTipo(TipoProduto.PRODUTO);
        request.setTempoProducao(30);
        request.setRendimento(BigDecimal.ONE);
        request.setPrecoVenda(new BigDecimal("10.00"));
        request.setFichaTecnica(List.of(itemFichaTecnica(insumoId, BigDecimal.ONE)));
        request.setFracionavel(fracionavelInformado);
        return request;
    }

    @Test
    void cadastroSemInformarFracionavelDerivaDaFichaTecnica() {
        seedUsuario();
        Insumo insumoFracionavel = criarInsumo(true);

        ProdutoDetalheResponse response = produtoService.cadastrar(requestBase(insumoFracionavel.getId(), null));

        assertTrue(response.getFracionavel());
        assertFalse(response.getFracionavelOverride());
    }

    @Test
    void editarComValorDiferenteDoDerivadoAtivaOverride() {
        seedUsuario();
        Insumo insumoFracionavel = criarInsumo(true);
        ProdutoDetalheResponse criado = produtoService.cadastrar(requestBase(insumoFracionavel.getId(), null));
        assertTrue(criado.getFracionavel()); // derivado: true (insumo é fracionável)

        ProdutoRequest edicao = requestBase(insumoFracionavel.getId(), false); // artesã sobrescreve para false
        ProdutoDetalheResponse editado = produtoService.editar(criado.getId(), edicao);

        assertFalse(editado.getFracionavel());
        assertTrue(editado.getFracionavelOverride());
    }

    @Test
    void overrideSobreviveATrocaDeInsumoNaFichaTecnica() {
        seedUsuario();
        Insumo insumoFracionavel = criarInsumo(true);
        ProdutoDetalheResponse criado = produtoService.cadastrar(requestBase(insumoFracionavel.getId(), null));

        ProdutoRequest comOverride = requestBase(insumoFracionavel.getId(), false);
        ProdutoDetalheResponse comOverrideAtivo = produtoService.editar(criado.getId(), comOverride);
        assertTrue(comOverrideAtivo.getFracionavelOverride());
        assertFalse(comOverrideAtivo.getFracionavel());

        // Troca o insumo por um não-fracionável — a derivação automática agora daria false,
        // mas o override deve manter o valor manual anterior (false), sem mudança visível aqui,
        // e sem enviar `fracionavel` no request (artesã não tocou o campo nesta edição).
        Insumo insumoNaoFracionavel = criarInsumo(false);
        ProdutoRequest trocaFicha = requestBase(insumoNaoFracionavel.getId(), null);
        ProdutoDetalheResponse depoisDaTroca = produtoService.editar(criado.getId(), trocaFicha);

        assertTrue(depoisDaTroca.getFracionavelOverride(), "override deve sobreviver à mudança de ficha técnica (CEN-NOVO-5)");
        assertFalse(depoisDaTroca.getFracionavel(), "valor override (false) não deve ser sobrescrito pela nova derivação");
    }

    @Test
    void editarComValorIgualAoDerivadoNaoAtivaOverride() {
        seedUsuario();
        Insumo insumoFracionavel = criarInsumo(true);
        ProdutoDetalheResponse criado = produtoService.cadastrar(requestBase(insumoFracionavel.getId(), null));

        // Envia explicitamente o mesmo valor que já seria derivado (true) — não conta como override.
        ProdutoRequest edicao = requestBase(insumoFracionavel.getId(), true);
        ProdutoDetalheResponse editado = produtoService.editar(criado.getId(), edicao);

        assertTrue(editado.getFracionavel());
        assertFalse(editado.getFracionavelOverride());
    }

    @Test
    void algumInsumoNaoFracionavelContinuaGroundTruthIndependenteDoOverride() {
        seedUsuario();
        Insumo insumoNaoFracionavel = criarInsumo(false);
        ProdutoDetalheResponse criado = produtoService.cadastrar(requestBase(insumoNaoFracionavel.getId(), null));

        // Artesã sobrescreve o badge para "fracionável" (true) por engano/decisão própria.
        ProdutoRequest comOverride = requestBase(insumoNaoFracionavel.getId(), true);
        ProdutoDetalheResponse editado = produtoService.editar(criado.getId(), comOverride);

        assertTrue(editado.getFracionavel(), "campo override deve refletir o valor escolhido pela artesã");
        // ⚠️ Gate de negócio (PDT-CEN-034/PDC-027) nunca lê o override — continua true (insumo real
        // não é fracionável), garantindo que Produção ainda trave a quantidade em múltiplos do rendimento.
        assertTrue(editado.isAlgumInsumoNaoFracionavel(),
                "algumInsumoNaoFracionavel (gate de Produção) não pode ser afetado pelo override de exibição");
    }
}
