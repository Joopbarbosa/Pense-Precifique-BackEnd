package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.StatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.LancarProducaoLoteRequest;
import com.penseprecifique.api.shared.dto.request.LancarProducaoRequest;
import com.penseprecifique.api.shared.dto.response.InsumoConsumidoResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.ProducaoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ProducaoMapper;
import com.penseprecifique.api.produto.FichaTecnicaItemRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.auth.UsuarioRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        return producaoRepository.findByUsuarioIdOrderByNumeroDesc(usuarioId, pageable)
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

        List<ComponenteConsumo> consumo = calcularConsumo(ficha, ratioLote);

        // Verificação de suficiência: tudo ou nada, antes de qualquer alteração
        // RN-059 — componente com permitirEstoqueNegativo=false bloqueia incondicionalmente,
        // mesmo com confirmarEstoqueNegativo=true (a flag do cadastro sempre vence a do request).
        validarEstoque(consumo, request.isConfirmarEstoqueNegativo());

        Producao producao = registrarProducao(usuario, produto, quantidadeFinal, request.getDataProducao(),
                consumo, proximoNumero(usuarioId));

        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos);
    }

    /**
     * RN-060 — lançamento de múltiplas produções na mesma sessão: o consumo de cada componente
     * é somado entre todos os itens da lista antes de checar estoque (tudo ou nada), depois cada
     * produção é gravada individualmente com seu próprio número sequencial (PRD-N).
     */
    public List<ProducaoDetalheResponse> lancarLote(LancarProducaoLoteRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        List<ItemPreparado> preparados = new ArrayList<>();
        Map<UUID, BigDecimal> consumoAcumulado = new LinkedHashMap<>();
        Map<UUID, ComponenteConsumo> componentesPorId = new LinkedHashMap<>();

        for (LancarProducaoRequest item : request.getProducoes()) {
            Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(item.getProdutoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

            List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
            BigDecimal quantidadeFinal = calcularQuantidadeFinal(item, produto);
            if (!ficha.isEmpty()) {
                exigirRendimentoValido(produto);
            }
            BigDecimal ratioLote = ficha.isEmpty() ? BigDecimal.ZERO
                    : quantidadeFinal.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            List<ComponenteConsumo> consumo = calcularConsumo(ficha, ratioLote);
            preparados.add(new ItemPreparado(item, produto, quantidadeFinal, consumo));

            for (ComponenteConsumo c : consumo) {
                UUID componenteId = c.getComponenteId();
                consumoAcumulado.merge(componenteId, c.quantidadeNecessaria(), BigDecimal::add);
                componentesPorId.putIfAbsent(componenteId, c);
            }
        }

        // Checagem combinada de estoque (tudo ou nada) — consumo somado por componente entre
        // todas as produções da sessão, RN-059 e RN-052 aplicadas ao lote como um todo.
        List<String> bloqueados = new ArrayList<>();
        List<String> insuficientes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : consumoAcumulado.entrySet()) {
            ComponenteConsumo componente = componentesPorId.get(entry.getKey());
            BigDecimal resultante = componente.getEstoqueAtual().subtract(entry.getValue());
            if (resultante.compareTo(BigDecimal.ZERO) < 0) {
                if (Boolean.FALSE.equals(componente.permitirEstoqueNegativo())) {
                    bloqueados.add(componente.getNome());
                } else {
                    insuficientes.add(componente.getNome());
                }
            }
        }
        if (!bloqueados.isEmpty()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + String.join(", ", bloqueados)
                            + ". Este(s) componente(s) não permite(m) estoque negativo.");
        }
        boolean todasConfirmam = request.getProducoes().stream().allMatch(LancarProducaoRequest::isConfirmarEstoqueNegativo);
        if (!insuficientes.isEmpty() && !todasConfirmam) {
            throw new BusinessException("Estoque insuficiente para os insumos: " + String.join(", ", insuficientes));
        }

        // Gravação — validação combinada já passou, cada produção é gravada individualmente
        // com seu próprio número sequencial (a sessão não é uma entidade nova, só agrupador de request).
        List<ProducaoDetalheResponse> respostas = new ArrayList<>();
        int numero = proximoNumero(usuarioId);
        for (ItemPreparado preparado : preparados) {
            Producao producao = registrarProducao(usuario, preparado.produto(), preparado.quantidadeFinal(),
                    preparado.request().getDataProducao(), preparado.consumo(), numero++);

            List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
            respostas.add(producaoMapper.toDetalheResponse(producao, consumidos));
        }
        return respostas;
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

    /** Representa o consumo necessário de um componente da ficha técnica — insumo XOR produto-base. */
    private record ComponenteConsumo(Insumo insumo, Produto produtoBase, BigDecimal quantidadeNecessaria) {
        UUID getComponenteId() {
            return insumo != null ? insumo.getId() : produtoBase.getId();
        }

        BigDecimal getEstoqueAtual() {
            return insumo != null ? insumo.getEstoqueAtual() : produtoBase.getEstoqueAtual();
        }

        Boolean permitirEstoqueNegativo() {
            return insumo != null ? insumo.getPermitirEstoqueNegativo() : produtoBase.getPermitirEstoqueNegativo();
        }

        String getNome() {
            return insumo != null ? insumo.getNome() : produtoBase.getNome();
        }
    }

    /** Item de `lancarLote()` já com produto, ficha resolvidos e consumo calculado, aguardando a checagem combinada. */
    private record ItemPreparado(LancarProducaoRequest request, Produto produto, BigDecimal quantidadeFinal,
                                  List<ComponenteConsumo> consumo) {
    }

    /** RN-051 — consumo de cada componente da ficha técnica, proporcional ao ratio do lote produzido. */
    private List<ComponenteConsumo> calcularConsumo(List<FichaTecnicaItem> ficha, BigDecimal ratioLote) {
        List<ComponenteConsumo> consumo = new ArrayList<>();
        for (FichaTecnicaItem item : ficha) {
            BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
            if (item.getInsumo() != null) {
                consumo.add(new ComponenteConsumo(item.getInsumo(), null, necessaria));
            } else if (item.getProdutoBase() != null) {
                consumo.add(new ComponenteConsumo(null, item.getProdutoBase(), necessaria));
            }
        }
        return consumo;
    }

    /**
     * Verificação de suficiência de estoque, tudo ou nada, antes de qualquer alteração — RN-059
     * (permitirEstoqueNegativo=false bloqueia incondicionalmente) + RN-052 (permitirEstoqueNegativo=true
     * só bloqueia se o usuário não confirmou estoque negativo).
     */
    private void validarEstoque(List<ComponenteConsumo> consumo, boolean confirmarEstoqueNegativo) {
        List<String> insuficientes = new ArrayList<>();
        List<String> bloqueados = new ArrayList<>();
        for (ComponenteConsumo c : consumo) {
            if (c.getEstoqueAtual().compareTo(c.quantidadeNecessaria()) < 0) {
                if (Boolean.FALSE.equals(c.permitirEstoqueNegativo())) {
                    bloqueados.add(c.getNome());
                } else {
                    insuficientes.add(c.getNome());
                }
            }
        }
        if (!bloqueados.isEmpty()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + String.join(", ", bloqueados)
                            + ". Este(s) componente(s) não permite(m) estoque negativo.");
        }
        if (!insuficientes.isEmpty() && !confirmarEstoqueNegativo) {
            throw new BusinessException("Estoque insuficiente para os insumos: " + String.join(", ", insuficientes));
        }
    }

    /** Grava a Producao, a baixa de cada componente consumido e a entrada do produto produzido. */
    private Producao registrarProducao(Usuario usuario, Produto produto, BigDecimal quantidadeFinal,
                                        LocalDateTime dataProducao, List<ComponenteConsumo> consumo, Integer numero) {
        Producao producao = Producao.builder()
                .usuario(usuario)
                .produto(produto)
                .quantidade(quantidadeFinal)
                .dataProducao(dataProducao != null ? dataProducao : LocalDateTime.now())
                .status(StatusProducao.ATIVA)
                .numero(numero)
                .build();
        producao = producaoRepository.save(producao);

        for (ComponenteConsumo c : consumo) {
            BigDecimal consumida = c.quantidadeNecessaria();

            if (c.insumo() != null) {
                Insumo insumo = c.insumo();
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

            } else if (c.produtoBase() != null) {
                // Produto base é consumido do próprio estoque de produto.
                Produto base = c.produtoBase();
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

        return producao;
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
