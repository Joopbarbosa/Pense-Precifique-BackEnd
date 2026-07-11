package com.penseprecifique.api.service;

import com.penseprecifique.api.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.domain.entity.Producao;
import com.penseprecifique.api.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.domain.entity.Produto;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.StatusProducao;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.dto.request.LancarProducaoRequest;
import com.penseprecifique.api.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.dto.response.ProducaoResponse;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.mapper.ProducaoMapper;
import com.penseprecifique.api.repository.FichaTecnicaItemRepository;
import com.penseprecifique.api.repository.InsumoRepository;
import com.penseprecifique.api.repository.MovimentacaoInsumoRepository;
import com.penseprecifique.api.repository.MovimentacaoProdutoRepository;
import com.penseprecifique.api.repository.ProducaoInsumoConsumidoRepository;
import com.penseprecifique.api.repository.ProducaoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProducaoService {

    private final ProducaoRepository producaoRepository;
    private final ProducaoInsumoConsumidoRepository producaoInsumoConsumidoRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProducaoMapper producaoMapper;

    @Transactional(readOnly = true)
    public Page<ProducaoResponse> listar(Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        return producaoRepository.findByUsuarioIdOrderByDataProducaoDesc(usuarioId, pageable)
                .map(producaoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProducaoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));
        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos);
    }

    @Transactional(readOnly = true)
    public List<InsumoConsumidoResponse> previewInsumosConsumidos(UUID produtoId, BigDecimal quantidade) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        List<InsumoConsumidoResponse> preview = new ArrayList<>();
        for (FichaTecnicaItem item : ficha) {
            preview.add(montarPreview(item, quantidade));
        }
        return preview;
    }

    public ProducaoDetalheResponse lancar(LancarProducaoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(request.getProdutoId(), usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());

        // RN-051 — quantidade final: lotes × rendimento (insumo não-fracionável) ou quantidade livre (todos fracionáveis).
        BigDecimal quantidadeFinal = calcularQuantidadeFinal(request, produto);

        // RN-051 — FichaTecnicaItem.quantidade agora significa "por lote"; a baixa é proporcional
        // à quantidade final produzida em relação ao rendimento do lote (ficha vazia → ratio nunca usado).
        // Dado legado anterior à RN-039 (rendimento obrigatório) pode ter escapado da obrigatoriedade — só
        // importa aqui quando há ficha técnica de fato, senão o ratio nunca é usado.
        if (!ficha.isEmpty()) {
            exigirRendimentoValido(produto);
        }
        BigDecimal ratioLote = ficha.isEmpty() ? BigDecimal.ZERO
                : quantidadeFinal.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

        // Verificação de suficiência: tudo ou nada, antes de qualquer alteração
        // RN-059 — componente com permitirEstoqueNegativo=false bloqueia incondicionalmente,
        // mesmo com confirmarEstoqueNegativo=true (a flag do cadastro sempre vence a do request).
        List<String> insuficientes = new ArrayList<>();
        List<String> bloqueados = new ArrayList<>();
        for (FichaTecnicaItem item : ficha) {
            BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
            if (item.getInsumo() != null) {
                Insumo insumo = item.getInsumo();
                if (insumo.getEstoqueAtual().compareTo(necessaria) < 0) {
                    if (Boolean.FALSE.equals(insumo.getPermitirEstoqueNegativo())) {
                        bloqueados.add(insumo.getNome());
                    } else {
                        insuficientes.add(insumo.getNome());
                    }
                }
            } else if (item.getProdutoBase() != null) {
                Produto base = item.getProdutoBase();
                if (base.getEstoqueAtual().compareTo(necessaria) < 0) {
                    if (Boolean.FALSE.equals(base.getPermitirEstoqueNegativo())) {
                        bloqueados.add(base.getNome());
                    } else {
                        insuficientes.add(base.getNome());
                    }
                }
            }
        }
        if (!bloqueados.isEmpty()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + String.join(", ", bloqueados)
                            + ". Este(s) componente(s) não permite(m) estoque negativo.");
        }
        if (!insuficientes.isEmpty() && !request.isConfirmarEstoqueNegativo()) {
            throw new BusinessException("Estoque insuficiente para os insumos: " + String.join(", ", insuficientes));
        }

        // Criação da produção
        Producao producao = Producao.builder()
                .usuario(usuario)
                .produto(produto)
                .quantidade(quantidadeFinal)
                .dataProducao(request.getDataProducao() != null ? request.getDataProducao() : LocalDateTime.now())
                .status(StatusProducao.ATIVA)
                .numero(proximoNumero(usuarioId))
                .build();
        producao = producaoRepository.save(producao);

        // Baixa dos componentes da ficha técnica
        for (FichaTecnicaItem item : ficha) {
            BigDecimal consumida = item.getQuantidade().multiply(ratioLote);

            if (item.getInsumo() != null) {
                Insumo insumo = item.getInsumo();
                insumo.setEstoqueAtual(insumo.getEstoqueAtual().subtract(consumida));
                insumoRepository.save(insumo);

                movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                        .insumo(insumo)
                        .tipo(TipoMovimentacaoInsumo.SAIDA)
                        .motivo(MotivoMovimentacaoInsumo.PRODUCAO)
                        .quantidade(consumida)
                        .referenciaId(producao.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO)
                        .estornada(false)
                        .build());

                producaoInsumoConsumidoRepository.save(ProducaoInsumoConsumido.builder()
                        .producao(producao)
                        .insumo(insumo)
                        .quantidade(consumida)
                        .build());

            } else if (item.getProdutoBase() != null) {
                // Produto base é consumido do próprio estoque de produto.
                Produto base = item.getProdutoBase();
                base.setEstoqueAtual(base.getEstoqueAtual().subtract(consumida));
                produtoRepository.save(base);

                movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                        .produto(base)
                        .tipo(TipoMovimentacaoProduto.SAIDA)
                        .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                        .quantidade(consumida)
                        .referenciaId(producao.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                        .estornada(false)
                        .build());

                producaoInsumoConsumidoRepository.save(ProducaoInsumoConsumido.builder()
                        .producao(producao)
                        .produtoBase(base)
                        .quantidade(consumida)
                        .build());
            }
        }

        // Entrada do produto produzido
        produto.setEstoqueAtual(produto.getEstoqueAtual().add(quantidadeFinal));
        produtoRepository.save(produto);

        movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                .produto(produto)
                .tipo(TipoMovimentacaoProduto.ENTRADA)
                .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                .quantidade(quantidadeFinal)
                .referenciaId(producao.getId())
                .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                .estornada(false)
                .build());

        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos);
    }

    public ProducaoDetalheResponse cancelar(UUID id, CancelarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getStatus() == StatusProducao.CANCELADA) {
            throw new BusinessException("Esta produção já foi cancelada.");
        }

        List<ProducaoInsumoConsumido> consumidos =
                producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());

        // Reverter estoque do produto produzido (a entrada da produção)
        Produto produto = producao.getProduto();
        produto.setEstoqueAtual(produto.getEstoqueAtual().subtract(producao.getQuantidade()));
        produtoRepository.save(produto);

        movimentacaoProdutoRepository
                .findByProdutoIdAndMotivoAndReferenciaIdAndTipo(
                        produto.getId(), MotivoMovimentacaoProduto.PRODUCAO,
                        producao.getId(), TipoMovimentacaoProduto.ENTRADA)
                .ifPresent(original -> {
                    original.setEstornada(true);
                    movimentacaoProdutoRepository.save(original);
                });

        movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                .produto(produto)
                .tipo(TipoMovimentacaoProduto.SAIDA)
                .motivo(MotivoMovimentacaoProduto.ESTORNO_PRODUCAO)
                .quantidade(producao.getQuantidade())
                .observacao(request.getObservacao())
                .referenciaId(producao.getId())
                .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                .estornada(false)
                .build());

        // Reverter estoque de cada componente consumido
        for (ProducaoInsumoConsumido consumido : consumidos) {
            if (consumido.getInsumo() != null) {
                // BRANCH A — componente é insumo
                Insumo insumo = consumido.getInsumo();
                insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(consumido.getQuantidade()));
                insumoRepository.save(insumo);

                movimentacaoInsumoRepository
                        .findByInsumoIdAndMotivoAndReferenciaId(
                                insumo.getId(), MotivoMovimentacaoInsumo.PRODUCAO, producao.getId())
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoInsumoRepository.save(original);
                        });

                movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                        .insumo(insumo)
                        .tipo(TipoMovimentacaoInsumo.ENTRADA)
                        .motivo(MotivoMovimentacaoInsumo.ESTORNO_PRODUCAO)
                        .quantidade(consumido.getQuantidade())
                        .observacao(request.getObservacao())
                        .referenciaId(producao.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO)
                        .estornada(false)
                        .build());

            } else if (consumido.getProdutoBase() != null) {
                // BRANCH B — componente é produto-base
                Produto produtoBase = consumido.getProdutoBase();
                produtoBase.setEstoqueAtual(produtoBase.getEstoqueAtual().add(consumido.getQuantidade()));
                produtoRepository.save(produtoBase);

                movimentacaoProdutoRepository
                        .findByProdutoIdAndMotivoAndReferenciaIdAndTipo(
                                produtoBase.getId(), MotivoMovimentacaoProduto.PRODUCAO,
                                producao.getId(), TipoMovimentacaoProduto.SAIDA)
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoProdutoRepository.save(original);
                        });

                movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                        .produto(produtoBase)
                        .tipo(TipoMovimentacaoProduto.ENTRADA)
                        .motivo(MotivoMovimentacaoProduto.ESTORNO_PRODUCAO)
                        .quantidade(consumido.getQuantidade())
                        .observacao(request.getObservacao())
                        .referenciaId(producao.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                        .estornada(false)
                        .build());
            }
        }

        producao.setStatus(StatusProducao.CANCELADA);
        producao.setObservacaoCancelamento(request.getObservacao());
        producao.setDataCancelamento(LocalDateTime.now());
        producao = producaoRepository.save(producao);

        return producaoMapper.toDetalheResponse(producao, consumidos);
    }

    private InsumoConsumidoResponse montarPreview(FichaTecnicaItem item, BigDecimal quantidadeFinal) {
        // RN-051 — mesma fórmula proporcional do lancar(); dado legado anterior à RN-039 pode ter rendimento nulo.
        Produto produto = item.getProduto();
        exigirRendimentoValido(produto);
        BigDecimal ratioLote = quantidadeFinal.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);
        BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
        InsumoConsumidoResponse response = new InsumoConsumidoResponse();
        response.setQuantidade(necessaria);

        BigDecimal estoqueAtual;
        if (item.getInsumo() != null) {
            Insumo insumo = item.getInsumo();
            response.setInsumoId(insumo.getId());
            response.setNomeInsumo(insumo.getNome());
            response.setMarca(insumo.getMarca());
            response.setUnidadeMedida(insumo.getUnidadeMedida());
            estoqueAtual = insumo.getEstoqueAtual();
        } else {
            Produto base = item.getProdutoBase();
            response.setInsumoId(base.getId());
            response.setNomeInsumo(base.getNome());
            estoqueAtual = base.getEstoqueAtual();
        }

        response.setEstoqueAntes(estoqueAtual);
        response.setEstoqueInsuficiente(estoqueAtual.compareTo(necessaria) < 0);
        return response;
    }

    /**
     * RN-039/RN-051 — rendimento é obrigatório desde o EP-04 para produto com ficha técnica preenchida,
     * mas produtos cadastrados antes dessa regra podem ter escapado da obrigatoriedade (dado legado).
     * Guarda de validação, não correção de dado — bloqueia com mensagem clara em vez de divisão nula/por zero.
     */
    private void exigirRendimentoValido(Produto produto) {
        if (produto.getRendimento() == null || produto.getRendimento().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Produto sem rendimento configurado — complete o cadastro do produto antes de lançar produção.");
        }
    }

    /** RN-051 — lotes (algum insumo não-fracionável) XOR quantidade livre (todos fracionáveis), nunca os dois. */
    private BigDecimal calcularQuantidadeFinal(LancarProducaoRequest request, Produto produto) {
        boolean temQuantidade = request.getQuantidade() != null;
        boolean temLotes = request.getLotes() != null;

        if (!temQuantidade && !temLotes) {
            throw new BusinessException("Informe a quantidade produzida ou o número de lotes.");
        }
        if (temQuantidade && temLotes) {
            throw new BusinessException(
                    "Informe apenas um dos dois: quantidade produzida OU número de lotes, não os dois ao mesmo tempo.");
        }

        if (temLotes) {
            exigirRendimentoValido(produto);
            return produto.getRendimento().multiply(BigDecimal.valueOf(request.getLotes()));
        }
        return request.getQuantidade();
    }

    private Integer proximoNumero(UUID usuarioId) {
        return producaoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId)
                .map(p -> p.getNumero() != null ? p.getNumero() + 1 : 1)
                .orElse(1);
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
