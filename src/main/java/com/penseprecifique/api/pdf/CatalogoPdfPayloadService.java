package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.CatalogoRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.pdf.CatalogoPdfData;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCatalogoPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Bean colaborador separado de {@link PdfService} — mesmo motivo de
 * {@link OrcamentoPdfPayloadService} (proxy AOP do {@code @Transactional} exige injeção, não
 * auto-invocação).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CatalogoPdfPayloadService {

    private final CatalogoRepository catalogoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdfMapper pdfMapper;

    public PdfMicroservicoCatalogoPayload montarPayloadCatalogo(UUID catalogoId) {
        Usuario usuario = getUsuarioAutenticado();
        Catalogo catalogo = catalogoRepository.findByIdAndUsuarioId(catalogoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Catálogo não encontrado"));

        // RN-NOVA-8/CEN-NOVO-8 — catálogo inativo bloqueia a geração do PDF.
        if (!Boolean.TRUE.equals(catalogo.getAtivo())) {
            throw new BusinessException("O catálogo precisa estar ativo para gerar o PDF.");
        }

        Empresa empresa = empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
        List<ItemCatalogo> itens = itemCatalogoRepository.findByCatalogoIdAndDeletedAtIsNull(catalogo.getId());

        CatalogoPdfData dados = pdfMapper.toCatalogoPdfData(catalogo, empresa, itens);
        return pdfMapper.toCatalogoMicroservicoPayload(dados);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
