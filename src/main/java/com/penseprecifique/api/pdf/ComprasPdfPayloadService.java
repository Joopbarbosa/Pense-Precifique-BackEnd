package com.penseprecifique.api.pdf;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.compra.CompraItemRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.compra.ListaCompraItemRepository;
import com.penseprecifique.api.compra.ListaCompraRepository;
import com.penseprecifique.api.empresa.EmpresaRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.Empresa;
import com.penseprecifique.api.shared.domain.entity.ListaCompra;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoCompraPayload;
import com.penseprecifique.api.shared.dto.pdf.PdfMicroservicoListaComprasPayload;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * #545/#547 (V0.15.0, DT-NOVA-12) — leitura de banco dos PDFs de compra e de lista de compras.
 * Bean separado de {@link PdfService} pelo mesmo motivo de {@link CatalogoPdfPayloadService}: a
 * transação curta fecha antes da chamada HTTP ao microsserviço (#262).
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ComprasPdfPayloadService {

    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;
    private final ListaCompraRepository listaCompraRepository;
    private final ListaCompraItemRepository listaCompraItemRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdfMapper pdfMapper;

    /** RN-NOVA-11 — qualquer compra com COM-N (rascunho, confirmada ou cancelada). */
    public PdfMicroservicoCompraPayload montarPayloadCompra(UUID compraId) {
        Usuario usuario = getUsuarioAutenticado();
        Compra compra = compraRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(compraId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Compra não encontrada: " + compraId));
        return pdfMapper.toCompraMicroservicoPayload(compra,
                compraItemRepository.findByCompraIdOrderByOrdemAsc(compra.getId()), empresa(usuario));
    }

    /** RN-NOVA-14 — sempre do retrato LST-N, nunca dos dados atuais. */
    public PdfMicroservicoListaComprasPayload montarPayloadListaCompras(UUID listaId) {
        Usuario usuario = getUsuarioAutenticado();
        ListaCompra lista = listaCompraRepository.findByIdAndUsuarioId(listaId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Lista de compras não encontrada: " + listaId));
        return pdfMapper.toListaComprasMicroservicoPayload(lista,
                listaCompraItemRepository.findByListaIdOrderByOrdemAsc(lista.getId()), empresa(usuario));
    }

    private Empresa empresa(Usuario usuario) {
        return empresaRepository.findByUsuarioIdAndDeletedAtIsNull(usuario.getId()).orElse(null);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
