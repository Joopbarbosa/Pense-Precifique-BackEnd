package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.TipoProduto;
import com.penseprecifique.api.dto.request.ProdutoRequest;
import com.penseprecifique.api.dto.response.ProdutoDetalheResponse;
import com.penseprecifique.api.dto.response.ProdutoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.ProdutoMapper;
import com.penseprecifique.api.repository.FichaTecnicaItemRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final FichaTecnicaService fichaTecnicaService;
    private final ProdutoMapper produtoMapper;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(TipoProduto tipo, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        if (tipo != null) {
            return produtoRepository.findByUsuarioIdAndTipoAndDeletedAtIsNull(usuarioId, tipo, pageable)
                    .map(produtoMapper::toResponse);
        }
        return produtoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable)
                .map(produtoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProdutoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        return produtoMapper.toDetalheResponse(produto, itens);
    }

    public ProdutoDetalheResponse cadastrar(ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        validarPrecoVenda(request);

        Usuario usuario = getUsuarioAutenticado();
        Produto produto = produtoMapper.toEntity(request, usuario);
        produto = produtoRepository.save(produto);

        BigDecimal precoCusto = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        produto.setPrecoCusto(precoCusto);
        produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        return produtoMapper.toDetalheResponse(produto, itens);
    }

    public ProdutoDetalheResponse editar(UUID id, ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        validarPrecoVenda(request);
        produtoMapper.updateEntity(request, produto);

        BigDecimal precoCusto = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        produto.setPrecoCusto(precoCusto);
        produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        return produtoMapper.toDetalheResponse(produto, itens);
    }

    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setDeletedAt(LocalDateTime.now());
        produtoRepository.save(produto);
    }

    private void validarPrecoVenda(ProdutoRequest request) {
        if (request.getTipo() == TipoProduto.PRODUTO_BASE) {
            if (request.getPrecoVenda() != null) {
                throw new BusinessException("Produto Base não pode ter preço de venda.");
            }
        } else {
            if (request.getPrecoVenda() == null || request.getPrecoVenda().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Preço de venda é obrigatório para Produto e Customização.");
            }
        }
    }

    private UUID getUsuarioIdAutenticado() {
        return getUsuarioAutenticado().getId();
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
