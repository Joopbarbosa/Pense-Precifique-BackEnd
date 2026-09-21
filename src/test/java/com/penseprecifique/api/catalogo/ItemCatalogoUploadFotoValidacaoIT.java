package com.penseprecifique.api.catalogo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoComponenteRequest;
import com.penseprecifique.api.shared.dto.request.catalogo.ItemCatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.shared.dto.response.catalogo.ItemCatalogoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OpenProject #518 — CEN-NOVO-6 (upload de foto acima de 5MB é bloqueado). CEN-NOVO-5 (formato
 * inválido) já tem cobertura em {@code e2e/catalogo-foto-descricao-pdf.spec.ts}; CEN-NOVO-6 não
 * tinha nenhuma cobertura automatizada (o spec E2E usa um buffer pequeno, insuficiente para
 * exercitar o limite de tamanho). {@code validarArquivoFoto} lança BusinessException antes de
 * qualquer chamada ao R2, então o teste roda 100% em memória.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ItemCatalogoUploadFotoValidacaoIT {

    @Autowired CatalogoService catalogoService;
    @Autowired ItemCatalogoService itemCatalogoService;
    @Autowired InsumoRepository insumoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private UUID novoItemCatalogo() {
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .email("upload-foto-" + UUID.randomUUID() + "@test.com")
                .senhaHash("x").ativo(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, List.of()));

        Insumo insumo = insumoRepository.save(Insumo.builder()
                .usuario(usuario).numero(1).nome("Insumo Foto").unidadeMedida("un")
                .custoUnitario(new BigDecimal("1.0000")).estoqueAtual(BigDecimal.TEN).fracionavel(true)
                .permitirEstoqueNegativo(true).build());

        CatalogoRequest catalogoRequest = new CatalogoRequest();
        catalogoRequest.setNome("Catálogo Upload Foto " + UUID.randomUUID());
        CatalogoResponse catalogo = catalogoService.cadastrar(catalogoRequest);

        ItemCatalogoComponenteRequest componente = new ItemCatalogoComponenteRequest();
        componente.setInsumoId(insumo.getId());
        componente.setQuantidade(BigDecimal.ONE);

        ItemCatalogoRequest itemRequest = new ItemCatalogoRequest();
        itemRequest.setNome("Item Upload Foto");
        itemRequest.setTempoProducao(0);
        itemRequest.setComponentes(List.of(componente));

        ItemCatalogoResponse item = itemCatalogoService.adicionar(catalogo.getId(), itemRequest);
        return item.getId();
    }

    @Test
    void uploadDeFotoAcimaDe5MbEBloqueadoComMensagemDoLimite() {
        UUID itemId = novoItemCatalogo();
        byte[] conteudo6Mb = new byte[6 * 1024 * 1024];
        MockMultipartFile arquivoGrande = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", conteudo6Mb);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itemCatalogoService.uploadFoto(itemId, arquivoGrande));

        assertEquals("Arquivo muito grande. O tamanho máximo permitido é 5MB.", ex.getMessage());
    }
}
