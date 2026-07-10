package com.penseprecifique.api.service.impl;

import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.LoteCompra;
import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.dto.request.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.dto.request.InsumoCreateRequestDTO;
import com.penseprecifique.api.dto.request.InsumoRequestDTO;
import com.penseprecifique.api.dto.response.InsumoResponseDTO;
import com.penseprecifique.api.dto.response.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.dto.response.ProdutoRelacionadoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.InsumoMapper;
import com.penseprecifique.api.repository.FichaTecnicaItemRepository;
import com.penseprecifique.api.repository.InsumoRepository;
import com.penseprecifique.api.repository.MovimentacaoInsumoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import com.penseprecifique.api.service.InsumoService;
import com.penseprecifique.api.service.LoteCompraService;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InsumoServiceImpl implements InsumoService {

    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InsumoMapper insumoMapper;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final LoteCompraService loteCompraService;

    @Override
    @Transactional(readOnly = true)
    public Page<InsumoResponseDTO> listar(Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        return insumoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable)
                .map(insumoMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public InsumoResponseDTO buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return insumoMapper.toResponse(insumo);
    }

    @Override
    public InsumoResponseDTO cadastrar(InsumoCreateRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
                request.nome(), request.marca(), usuarioId)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        Usuario usuario = getUsuarioAutenticado();
        Insumo insumo = insumoMapper.toEntity(request, usuario);
        insumo.setNumero(NumeroSequencialUtil.proximoNumero(
                insumoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Insumo::getNumero)));
        insumo = insumoRepository.save(insumo);

        LoteCompra loteCompra = loteCompraService.criarLote(usuario, LocalDateTime.now());
        loteCompraService.registrarCompraIndividual(
                insumo, request.quantidadeCompradaInicial(), request.precoTotalCompraInicial(), loteCompra.getId());

        return insumoMapper.toResponse(insumo);
    }

    @Override
    public InsumoResponseDTO editar(UUID id, InsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndIdNotAndDeletedAtIsNull(
                request.nome(), request.marca(), usuarioId, id)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        insumoMapper.updateEntity(request, insumo);
        return insumoMapper.toResponse(insumoRepository.save(insumo));
    }

    @Override
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        insumo.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(insumo);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MovimentacaoInsumoResponseDTO> listarMovimentacoes(UUID insumoId, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return movimentacaoInsumoRepository.findByInsumoIdOrderByCreatedAtDesc(insumoId, pageable)
                .map(insumoMapper::toMovimentacaoResponse);
    }

    @Override
    public MovimentacaoInsumoResponseDTO baixaManual(UUID insumoId, BaixaManualInsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        if (insumo.getEstoqueAtual().compareTo(request.quantidade()) < 0) {
            throw new BusinessException("Estoque insuficiente para realizar a baixa.");
        }

        insumo.setEstoqueAtual(insumo.getEstoqueAtual().subtract(request.quantidade()));
        insumoRepository.save(insumo);

        MovimentacaoInsumo movimentacao = MovimentacaoInsumo.builder()
                .insumo(insumo)
                .tipo(TipoMovimentacaoInsumo.SAIDA)
                .motivo(MotivoMovimentacaoInsumo.BAIXA_MANUAL)
                .quantidade(request.quantidade())
                .observacao(request.observacao())
                .estornada(false)
                .build();

        return insumoMapper.toMovimentacaoResponse(movimentacaoInsumoRepository.save(movimentacao));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProdutoRelacionadoResponse> listarProdutosRelacionados(UUID insumoId) {
        UUID usuarioId = getUsuarioIdAutenticado();
        insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return fichaTecnicaItemRepository.findProdutosByInsumoId(insumoId)
                .stream()
                .map(insumoMapper::toProdutoRelacionadoResponse)
                .toList();
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
