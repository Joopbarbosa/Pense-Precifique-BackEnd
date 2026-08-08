package com.penseprecifique.api.insumo;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.LoteCompra;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.dto.request.insumo.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoCreateRequestDTO;
import com.penseprecifique.api.shared.dto.request.insumo.InsumoRequestDTO;
import com.penseprecifique.api.shared.dto.response.insumo.InsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.shared.dto.response.insumo.ProdutoRelacionadoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.InsumoMapper;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.orcamento.OrcamentoRepository;
import com.penseprecifique.api.producao.ProducaoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InsumoService {

    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final UsuarioRepository usuarioRepository;
    private final InsumoMapper insumoMapper;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final LoteCompraService loteCompraService;
    private final ProducaoRepository producaoRepository;
    private final OrcamentoRepository orcamentoRepository;

    @Transactional(readOnly = true)
    public Page<InsumoResponseDTO> listar(String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        if (busca != null && !busca.isBlank()) {
            return insumoRepository.findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, busca, pageable)
                    .map(insumoMapper::toResponse);
        }
        return insumoRepository.findByUsuarioIdAndDeletedAtIsNull(usuarioId, pageable)
                .map(insumoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public InsumoResponseDTO buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        return insumoMapper.toResponse(insumo);
    }

    public InsumoResponseDTO cadastrar(InsumoCreateRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        if (insumoRepository.existsByNomeAndMarcaAndUsuarioIdAndDeletedAtIsNull(
                request.nome(), request.marca(), usuarioId)) {
            throw new BusinessException("Já existe um insumo com este nome e marca.");
        }

        Usuario usuario = getUsuarioAutenticado();
        Insumo insumo = insumoMapper.toEntity(request, usuario);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuarioId);
        insumo.setNumero(NumeroSequencialUtil.proximoNumero(
                insumoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Insumo::getNumero)));
        insumo = insumoRepository.save(insumo);

        LoteCompra loteCompra = loteCompraService.criarLote(usuario, LocalDateTime.now());
        loteCompraService.registrarCompraIndividual(
                insumo, request.quantidadeCompradaInicial(), request.precoTotalCompraInicial(), loteCompra.getId());

        return insumoMapper.toResponse(insumo);
    }

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

    public void excluir(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        insumo.setDeletedAt(LocalDateTime.now());
        insumoRepository.save(insumo);
    }

    /**
     * INS-010 — inativação reversível: {@code ativo=false}, insumo continua existindo (deletedAt
     * permanece null). INS-011 — só é permitida se o insumo não estiver em ficha técnica de nenhum
     * produto não excluído — produto inativado ainda pode voltar a vender, então continua contando
     * como uso.
     */
    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        List<Produto> produtosVinculados = fichaTecnicaItemRepository.findProdutosByInsumoId(id);
        if (!produtosVinculados.isEmpty()) {
            String nomes = produtosVinculados.stream().map(Produto::getNome).collect(Collectors.joining(", "));
            throw new BusinessException("Insumo " + insumo.getNome()
                    + " está vinculado à ficha técnica de: " + nomes
                    + ". Remova o insumo dessas fichas técnicas antes de inativá-lo.");
        }

        insumo.setAtivo(false);
        insumoRepository.save(insumo);
    }

    /** INS-010 — reverte a inativação: {@code ativo=true}. Idempotente, sem validação adicional. */
    public void reativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        insumo.setAtivo(true);
        insumoRepository.save(insumo);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoInsumoResponseDTO> listarMovimentacoes(UUID insumoId, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        Page<MovimentacaoInsumo> movimentacoes =
                movimentacaoInsumoRepository.findByInsumoIdOrderByCreatedAtDesc(insumoId, pageable);

        Set<UUID> producaoIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.PRODUCAO);
        Map<UUID, Integer> numeroProducaoPorId = producaoIds.isEmpty() ? Map.of()
                : producaoRepository.findAllById(producaoIds).stream()
                        .collect(Collectors.toMap(Producao::getId, Producao::getNumero));

        Set<UUID> orcamentoIds = referenciaIdsDoTipo(movimentacoes, ReferenciaMovimentacaoTipo.ORCAMENTO);
        Map<UUID, Integer> numeroOrcamentoPorId = orcamentoIds.isEmpty() ? Map.of()
                : orcamentoRepository.findAllById(orcamentoIds).stream()
                        .collect(Collectors.toMap(Orcamento::getId, Orcamento::getNumero));

        return movimentacoes.map(mov -> insumoMapper.toMovimentacaoResponse(
                mov, resolverReferencia(mov, numeroProducaoPorId, numeroOrcamentoPorId)));
    }

    private Set<UUID> referenciaIdsDoTipo(Page<MovimentacaoInsumo> movimentacoes, ReferenciaMovimentacaoTipo tipo) {
        return movimentacoes.getContent().stream()
                .filter(m -> m.getReferenciaTipo() == tipo)
                .map(MovimentacaoInsumo::getReferenciaId)
                .collect(Collectors.toSet());
    }

    private String resolverReferencia(
            MovimentacaoInsumo mov, Map<UUID, Integer> numeroProducaoPorId, Map<UUID, Integer> numeroOrcamentoPorId) {
        if (mov.getReferenciaTipo() == null) {
            return null;
        }
        return switch (mov.getReferenciaTipo()) {
            case LOTE_COMPRA -> "Compra em lote";
            case PRODUCAO -> {
                Integer numero = numeroProducaoPorId.get(mov.getReferenciaId());
                yield numero != null ? IdentificadorFormatter.formatar("PRD", numero) : "PRD-?";
            }
            case ORCAMENTO -> {
                Integer numero = numeroOrcamentoPorId.get(mov.getReferenciaId());
                yield numero != null ? IdentificadorFormatter.formatar("ORC", numero) : "ORC-?";
            }
        };
    }

    public MovimentacaoInsumoResponseDTO baixaManual(UUID insumoId, BaixaManualInsumoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));

        BigDecimal estoqueResultante = insumo.getEstoqueAtual().subtract(request.quantidade());
        if (estoqueResultante.compareTo(BigDecimal.ZERO) < 0 && !insumo.getPermitirEstoqueNegativo()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + insumo.getNome() + ". Este insumo não permite estoque negativo.");
        }

        insumo.setEstoqueAtual(estoqueResultante);
        insumoRepository.save(insumo);

        MovimentacaoInsumo movimentacao = MovimentacaoInsumo.builder()
                .insumo(insumo)
                .tipo(TipoMovimentacaoInsumo.SAIDA)
                .motivo(request.motivo())
                .quantidade(request.quantidade())
                .custoUnitario(insumo.getCustoUnitario())
                .observacao(request.observacao())
                .estornada(false)
                .build();

        return insumoMapper.toMovimentacaoResponse(movimentacaoInsumoRepository.save(movimentacao), null);
    }

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
