package com.penseprecifique.api.producao;

import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.HistoricoStatusProducao;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Producao;
import com.penseprecifique.api.shared.domain.entity.ProducaoInsumoConsumido;
import com.penseprecifique.api.shared.domain.entity.ProducaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.EstadoProducao;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.MotivoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.OrigemHistoricoStatus;
import com.penseprecifique.api.shared.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.shared.domain.enums.SituacaoAlertaInsumo;
import com.penseprecifique.api.shared.domain.enums.StatusProducao;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.response.AlertaInsumoResponse;
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
import java.time.LocalDate;
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
    private final ProducaoProdutoRepository producaoProdutoRepository;
    private final HistoricoStatusProducaoRepository historicoStatusProducaoRepository;
    private final ProducaoInsumoConsumidoRepository producaoInsumoConsumidoRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProducaoMapper producaoMapper;

    @Transactional(readOnly = true)
    public Page<ProducaoResponse> listar(String busca, EstadoProducao estado, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Integer buscaNumero = null;
        String buscaNome = null;
        if (busca != null && !busca.isBlank()) {
            String normalizado = busca.trim();
            String semPrefixo = normalizado.toUpperCase().startsWith("PRD-") ? normalizado.substring(4) : normalizado;
            if (semPrefixo.matches("\\d+")) {
                buscaNumero = Integer.valueOf(semPrefixo);
            } else {
                buscaNome = normalizado;
            }
        }

        return producaoRepository.buscar(usuarioId, estado, buscaNumero, buscaNome, pageable)
                .map(producaoMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ProducaoDetalheResponse buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));
        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos, produtos, List.of());
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

    /** RN-061/062/064/077 — cria produção com N produtos, sem movimentação de estoque. Nasce AGUARDANDO_INICIO. */
    public ProducaoDetalheResponse criarProducao(CriarProducaoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        LocalDate dataInicio = request.getDataInicio() != null ? request.getDataInicio() : LocalDate.now();
        validarDatas(dataInicio, request.getDataTerminoPrevista());

        ProdutosValidados validados = validarEResolverProdutos(request.getProdutos(), usuarioId);

        Producao producao = Producao.builder()
                .usuario(usuario)
                .numero(proximoNumero(usuarioId))
                .estado(EstadoProducao.AGUARDANDO_INICIO)
                .dataInicio(dataInicio)
                .dataTerminoPrevista(request.getDataTerminoPrevista())
                .observacoes(request.getObservacoes())
                .build();
        producao = producaoRepository.save(producao);

        List<ProducaoProduto> produtosGravados = gravarProducaoProdutos(producao, validados);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(null)
                .statusNovo(EstadoProducao.AGUARDANDO_INICIO)
                .origem(OrigemHistoricoStatus.USUARIO)
                .build());

        List<AlertaInsumoResponse> alertas = calcularAlertas(validados);
        return producaoMapper.toDetalheResponse(producao, List.of(), produtosGravados, alertas);
    }

    /** RN-063 — edição restrita a AGUARDANDO_INICIO; substitui a lista de produtos por completo. */
    public ProducaoDetalheResponse editarProducao(UUID id, CriarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Apenas produções aguardando início podem ser editadas");
        }

        LocalDate dataInicio = request.getDataInicio() != null ? request.getDataInicio() : producao.getDataInicio();
        validarDatas(dataInicio, request.getDataTerminoPrevista());

        ProdutosValidados validados = validarEResolverProdutos(request.getProdutos(), usuarioId);

        producaoProdutoRepository.deleteAll(producaoProdutoRepository.findByProducaoId(producao.getId()));

        producao.setDataInicio(dataInicio);
        producao.setDataTerminoPrevista(request.getDataTerminoPrevista());
        producao.setObservacoes(request.getObservacoes());
        producao = producaoRepository.save(producao);

        List<ProducaoProduto> produtosGravados = gravarProducaoProdutos(producao, validados);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(EstadoProducao.AGUARDANDO_INICIO)
                .statusNovo(EstadoProducao.AGUARDANDO_INICIO)
                .origem(OrigemHistoricoStatus.USUARIO)
                .justificativa("Edição")
                .build());

        List<AlertaInsumoResponse> alertas = calcularAlertas(validados);
        return producaoMapper.toDetalheResponse(producao, List.of(), produtosGravados, alertas);
    }

    public ProducaoDetalheResponse cancelar(UUID id, CancelarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        // Produção do fluxo novo (N produtos, sem coluna legada produto_id) — nenhum estoque foi
        // movimentado na criação, então a reversão incondicional abaixo (fluxo legado) não se aplica.
        if (producao.getProduto() == null) {
            throw new BusinessException(
                    "Use o endpoint /producoes/{id}/cancelar do novo ciclo de vida — será disponibilizado em breve.");
        }

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
        producao.setEstado(EstadoProducao.CANCELADA);
        producao.setObservacaoCancelamento(request.getObservacao());
        producao.setDataCancelamento(LocalDateTime.now());
        producao = producaoRepository.save(producao);

        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos, produtos, List.of());
    }

    /** Produtos já validados (existem, ativos, com ficha técnica + rendimento) e quantidades agregadas por RN-061. */
    private record ProdutosValidados(List<Produto> produtos, Map<UUID, BigDecimal> quantidades,
                                      Map<UUID, List<FichaTecnicaItem>> fichas) {
    }

    private void validarDatas(LocalDate dataInicio, LocalDate dataTerminoPrevista) {
        if (dataTerminoPrevista.isBefore(dataInicio)) {
            throw new BusinessException("Data de término prevista deve ser igual ou posterior à data de início");
        }
    }

    /** RN-061 — existência/ativo, duplicatas somadas; RN-077 — ficha técnica + rendimento obrigatórios. */
    private ProdutosValidados validarEResolverProdutos(List<ProducaoProdutoRequest> itens, UUID usuarioId) {
        Map<UUID, BigDecimal> quantidades = new LinkedHashMap<>();
        for (ProducaoProdutoRequest item : itens) {
            quantidades.merge(item.getProdutoId(), item.getQuantidade(), BigDecimal::add);
        }

        List<Produto> produtos = new ArrayList<>();
        Map<UUID, List<FichaTecnicaItem>> fichas = new LinkedHashMap<>();
        for (UUID produtoId : quantidades.keySet()) {
            Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

            if (!Boolean.TRUE.equals(produto.getAtivo())) {
                throw new BusinessException("Produto " + produto.getNome() + " está inativo");
            }

            List<FichaTecnicaItem> ficha = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
            if (ficha.isEmpty() || produto.getRendimento() == null || produto.getRendimento().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Produto " + produto.getNome()
                        + " não tem ficha técnica completa ou rendimento válido — complete o cadastro antes de incluir em uma produção");
            }

            produtos.add(produto);
            fichas.put(produtoId, ficha);
        }

        return new ProdutosValidados(produtos, quantidades, fichas);
    }

    private List<ProducaoProduto> gravarProducaoProdutos(Producao producao, ProdutosValidados validados) {
        List<ProducaoProduto> gravados = new ArrayList<>();
        for (Produto produto : validados.produtos()) {
            gravados.add(producaoProdutoRepository.save(ProducaoProduto.builder()
                    .producao(producao)
                    .produto(produto)
                    .quantidade(validados.quantidades().get(produto.getId()))
                    .build()));
        }
        return gravados;
    }

    /**
     * RN-064 — alertas informativos de estoque, nunca bloqueiam a criação/edição. Consumo de cada
     * insumo é somado entre todos os produtos da produção antes de comparar com o estoque atual.
     */
    private List<AlertaInsumoResponse> calcularAlertas(ProdutosValidados validados) {
        Map<UUID, BigDecimal> necessidadePorComponente = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, Produto> produtosBasePorId = new LinkedHashMap<>();

        for (Produto produto : validados.produtos()) {
            BigDecimal quantidade = validados.quantidades().get(produto.getId());
            BigDecimal ratioLote = quantidade.divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            for (FichaTecnicaItem item : validados.fichas().get(produto.getId())) {
                BigDecimal necessaria = item.getQuantidade().multiply(ratioLote);
                if (item.getInsumo() != null) {
                    necessidadePorComponente.merge(item.getInsumo().getId(), necessaria, BigDecimal::add);
                    insumosPorId.putIfAbsent(item.getInsumo().getId(), item.getInsumo());
                } else if (item.getProdutoBase() != null) {
                    necessidadePorComponente.merge(item.getProdutoBase().getId(), necessaria, BigDecimal::add);
                    produtosBasePorId.putIfAbsent(item.getProdutoBase().getId(), item.getProdutoBase());
                }
            }
        }

        List<AlertaInsumoResponse> alertas = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorComponente.entrySet()) {
            UUID componenteId = entry.getKey();
            BigDecimal necessaria = entry.getValue();

            String nome;
            BigDecimal estoqueAtual;
            Boolean permitirEstoqueNegativo;
            if (insumosPorId.containsKey(componenteId)) {
                Insumo insumo = insumosPorId.get(componenteId);
                nome = insumo.getNome();
                estoqueAtual = insumo.getEstoqueAtual();
                permitirEstoqueNegativo = insumo.getPermitirEstoqueNegativo();
            } else {
                Produto base = produtosBasePorId.get(componenteId);
                nome = base.getNome();
                estoqueAtual = base.getEstoqueAtual();
                permitirEstoqueNegativo = base.getPermitirEstoqueNegativo();
            }

            SituacaoAlertaInsumo situacao;
            if (estoqueAtual.compareTo(necessaria) >= 0) {
                situacao = SituacaoAlertaInsumo.SUFICIENTE;
            } else if (Boolean.FALSE.equals(permitirEstoqueNegativo)) {
                situacao = SituacaoAlertaInsumo.BLOQUEIO_FUTURO;
            } else {
                situacao = SituacaoAlertaInsumo.AVISO;
            }

            AlertaInsumoResponse alerta = new AlertaInsumoResponse();
            alerta.setNomeInsumo(nome);
            alerta.setEstoqueAtual(estoqueAtual);
            alerta.setQuantidadeNecessaria(necessaria);
            alerta.setSituacao(situacao);
            alertas.add(alerta);
        }
        return alertas;
    }

    private InsumoConsumidoResponse montarPreview(FichaTecnicaItem item, BigDecimal quantidadeFinal) {
        // RN-051 — mesma fórmula proporcional do fluxo antigo; dado legado anterior à RN-039 pode ter rendimento nulo.
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
