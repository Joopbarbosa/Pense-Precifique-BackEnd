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
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.dto.request.CancelarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ConsumoRealRequest;
import com.penseprecifique.api.shared.dto.request.CriarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.IniciarProducaoRequest;
import com.penseprecifique.api.shared.dto.request.ProducaoProdutoRequest;
import com.penseprecifique.api.shared.dto.request.TravarProducaoRequest;
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
        return montarDetalhe(producao, List.of());
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
        return montarDetalhe(producao, alertas);
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
        return montarDetalhe(producao, alertas);
    }

    /**
     * RN-071/072 — cancela produção do ciclo de vida novo. AGUARDANDO_INICIO nunca moveu estoque
     * (Fluxo A, simples). EM_ANDAMENTO/TRAVADA já baixaram insumos no iniciar() (Fluxo B) — exige
     * declaração de quanto foi realmente consumido e estorna a diferença por componente.
     */
    public ProducaoDetalheResponse cancelar(UUID id, CancelarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        EstadoProducao estadoAtual = producao.getEstado();
        if (estadoAtual == EstadoProducao.FINALIZADA) {
            throw new BusinessException("Produções finalizadas não podem ser canceladas");
        }
        if (estadoAtual == EstadoProducao.CANCELADA) {
            throw new BusinessException("Esta produção já está cancelada");
        }
        if (estadoAtual == EstadoProducao.NAO_REALIZADA) {
            throw new BusinessException("Produções não realizadas não podem ser canceladas");
        }

        if (estadoAtual == EstadoProducao.AGUARDANDO_INICIO) {
            producao.setJustificativaCancelamento(request.getJustificativa());
            transicionar(producao, EstadoProducao.CANCELADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
            return montarDetalhe(producao, List.of());
        }

        // EM_ANDAMENTO ou TRAVADA — insumos já baixados no iniciar(), exige declaração de consumo real.
        Map<UUID, ConsumoRealRequest> declarado = new LinkedHashMap<>();
        if (request.getConsumoReal() != null) {
            for (ConsumoRealRequest item : request.getConsumoReal()) {
                UUID componenteId = item.getInsumoId() != null ? item.getInsumoId() : item.getProdutoBaseId();
                declarado.put(componenteId, item);
            }
        }

        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        for (ProducaoInsumoConsumido consumido : consumidos) {
            boolean isInsumo = consumido.getInsumo() != null;
            UUID componenteId = isInsumo ? consumido.getInsumo().getId() : consumido.getProdutoBase().getId();
            String nome = isInsumo ? consumido.getInsumo().getNome() : consumido.getProdutoBase().getNome();
            BigDecimal original = consumido.getQuantidade();

            ConsumoRealRequest item = declarado.get(componenteId);
            // Sem declaração para este componente — assume consumo total, nenhum estorno (comportamento
            // do fluxo antigo, que sempre estornava tudo quando não havia como saber o consumo real).
            BigDecimal consumidoReal = item != null ? item.getQuantidadeConsumida() : original;

            if (consumidoReal.compareTo(original) > 0) {
                throw new BusinessException("Quantidade consumida de " + nome
                        + " não pode ser maior que a quantidade baixada originalmente (" + original + ")");
            }

            BigDecimal diferenca = original.subtract(consumidoReal);
            if (diferenca.compareTo(BigDecimal.ZERO) > 0) {
                boolean totalmenteEstornado = consumidoReal.compareTo(BigDecimal.ZERO) == 0;
                estornarComponente(producao, consumido, diferenca, totalmenteEstornado, request.getJustificativa());
            }
        }

        producao.setJustificativaCancelamento(request.getJustificativa());
        transicionar(producao, EstadoProducao.CANCELADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        return montarDetalhe(producao, List.of());
    }

    /**
     * Estorna a diferença não consumida de um componente. `estornada` na MovimentacaoInsumo/Produto
     * original só é marcada quando o estorno é total — o campo é booleano simples, sem noção de
     * "parcialmente estornada", então marcar em estorno parcial ficaria um dado incorreto.
     */
    private void estornarComponente(Producao producao, ProducaoInsumoConsumido consumido, BigDecimal diferenca,
                                      boolean totalmenteEstornado, String justificativa) {
        if (consumido.getInsumo() != null) {
            Insumo insumo = consumido.getInsumo();
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(diferenca));
            insumoRepository.save(insumo);

            if (totalmenteEstornado) {
                movimentacaoInsumoRepository
                        .findByInsumoIdAndMotivoAndReferenciaId(insumo.getId(), MotivoMovimentacaoInsumo.PRODUCAO, producao.getId())
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoInsumoRepository.save(original);
                        });
            }

            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.ENTRADA)
                    .motivo(MotivoMovimentacaoInsumo.ESTORNO_PRODUCAO)
                    .quantidade(diferenca)
                    .observacao(justificativa)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO)
                    .estornada(false)
                    .build());

        } else if (consumido.getProdutoBase() != null) {
            Produto base = consumido.getProdutoBase();
            base.setEstoqueAtual(base.getEstoqueAtual().add(diferenca));
            produtoRepository.save(base);

            if (totalmenteEstornado) {
                movimentacaoProdutoRepository
                        .findByProdutoIdAndMotivoAndReferenciaIdAndTipo(
                                base.getId(), MotivoMovimentacaoProduto.PRODUCAO, producao.getId(), TipoMovimentacaoProduto.SAIDA)
                        .ifPresent(original -> {
                            original.setEstornada(true);
                            movimentacaoProdutoRepository.save(original);
                        });
            }

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(base)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.ESTORNO_PRODUCAO)
                    .quantidade(diferenca)
                    .observacao(justificativa)
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                    .estornada(false)
                    .build());
        }
    }

    /** RN-065/066/067 — inicia produção: sem bloqueante, baixa insumos e vai para EM_ANDAMENTO;
     *  com bloqueante, trava automaticamente (SISTEMA) sem baixar nada. */
    public ProducaoDetalheResponse iniciar(UUID id, IniciarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.AGUARDANDO_INICIO) {
            throw new BusinessException("Apenas produções aguardando início podem ser iniciadas");
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        VerificacaoInsumos verificacao = verificarComponentes(produtosDaProducao);

        if (!verificacao.bloqueantes().isEmpty()) {
            transicionar(producao, EstadoProducao.TRAVADA, OrigemHistoricoStatus.SISTEMA,
                    "Insumo(s) bloqueante(s): " + String.join(", ", verificacao.bloqueantes()));
            return montarDetalhe(producao, List.of());
        }

        for (ComponenteNecessidade componente : verificacao.componentes()) {
            baixarComponente(producao, componente);
        }

        transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    /** Trava manual — só a partir de EM_ANDAMENTO, sem tocar estoque (insumos já baixados permanecem). */
    public ProducaoDetalheResponse travar(UUID id, TravarProducaoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.EM_ANDAMENTO) {
            throw new BusinessException("Apenas produções em andamento podem ser travadas manualmente");
        }

        transicionar(producao, EstadoProducao.TRAVADA, OrigemHistoricoStatus.USUARIO, request.getJustificativa());
        return montarDetalhe(producao, List.of());
    }

    /**
     * RN-069 — retoma produção travada. Duas origens possíveis de TRAVADA exigem tratamento diferente:
     * (a) trava veio do próprio iniciar() bloqueando — nenhum insumo foi baixado ainda, então reverifica
     *     e, se liberado, baixa pela primeira vez; (b) trava veio de travar() manual após iniciar() já ter
     *     baixado — não há o que reverificar nem baixar de novo (dobraria o consumo), só volta o estado.
     */
    public ProducaoDetalheResponse retomar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.TRAVADA) {
            throw new BusinessException("Apenas produções travadas podem ser retomadas");
        }

        List<ProducaoInsumoConsumido> jaConsumido = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        if (!jaConsumido.isEmpty()) {
            transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
            return montarDetalhe(producao, List.of());
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        VerificacaoInsumos verificacao = verificarComponentes(produtosDaProducao);

        if (!verificacao.bloqueantes().isEmpty()) {
            // Permanece TRAVADA — tentativa sem sucesso não é uma transição de estado, sem histórico novo.
            return montarDetalhe(producao, List.of());
        }

        for (ComponenteNecessidade componente : verificacao.componentes()) {
            baixarComponente(producao, componente);
        }

        transicionar(producao, EstadoProducao.EM_ANDAMENTO, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    /** RN-070 — finaliza produção: incrementa estoque de cada produto pela quantidade produzida,
     *  registra a entrada, preenche dataTerminoReal e vai para FINALIZADA (imutável, sem saída). */
    public ProducaoDetalheResponse finalizar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Producao producao = producaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produção não encontrada"));

        if (producao.getEstado() != EstadoProducao.EM_ANDAMENTO) {
            throw new BusinessException("Apenas produções em andamento podem ser finalizadas");
        }

        List<ProducaoProduto> produtosDaProducao = producaoProdutoRepository.findByProducaoId(producao.getId());
        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            Produto produto = producaoProduto.getProduto();
            produto.setEstoqueAtual(produto.getEstoqueAtual().add(producaoProduto.getQuantidade()));
            produtoRepository.save(produto);

            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacaoProduto.ENTRADA)
                    .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                    .quantidade(producaoProduto.getQuantidade())
                    .referenciaId(producao.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                    .estornada(false)
                    .build());
        }

        producao.setDataTerminoReal(LocalDate.now());
        transicionar(producao, EstadoProducao.FINALIZADA, OrigemHistoricoStatus.USUARIO, null);
        return montarDetalhe(producao, List.of());
    }

    private void transicionar(Producao producao, EstadoProducao novoEstado, OrigemHistoricoStatus origem, String justificativa) {
        EstadoProducao estadoAnterior = producao.getEstado();
        producao.setEstado(novoEstado);
        producaoRepository.save(producao);

        historicoStatusProducaoRepository.save(HistoricoStatusProducao.builder()
                .producao(producao)
                .statusAnterior(estadoAnterior)
                .statusNovo(novoEstado)
                .origem(origem)
                .justificativa(justificativa)
                .build());
    }

    private ProducaoDetalheResponse montarDetalhe(Producao producao, List<AlertaInsumoResponse> alertasInsumos) {
        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        List<ProducaoProduto> produtos = producaoProdutoRepository.findByProducaoId(producao.getId());
        List<HistoricoStatusProducao> historico = historicoStatusProducaoRepository.findByProducaoIdOrderByDataTransicaoAsc(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos, produtos, alertasInsumos, historico);
    }

    /** Componente (insumo ou produto-base) com a necessidade já somada entre todos os produtos da produção. */
    private record ComponenteNecessidade(String nome, BigDecimal necessaria, BigDecimal estoqueAtual,
                                          Boolean permitirEstoqueNegativo, Insumo insumo, Produto produtoBase) {
    }

    private record VerificacaoInsumos(List<ComponenteNecessidade> componentes, List<String> bloqueantes) {
    }

    /**
     * RN-065 — necessidade de cada componente (insumo ou produto-base) da ficha técnica de cada produto
     * da produção, somada quando o mesmo componente aparece em mais de um produto. RN-059 — componente
     * com permitirEstoqueNegativo=false bloqueia incondicionalmente, nunca contornável por confirmação
     * do request (mesma regra do fluxo antigo removido no P002).
     */
    private VerificacaoInsumos verificarComponentes(List<ProducaoProduto> produtosDaProducao) {
        Map<UUID, BigDecimal> necessidadePorComponente = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, Produto> produtosBasePorId = new LinkedHashMap<>();

        for (ProducaoProduto producaoProduto : produtosDaProducao) {
            Produto produto = producaoProduto.getProduto();
            exigirRendimentoValido(produto);
            BigDecimal ratioLote = producaoProduto.getQuantidade().divide(produto.getRendimento(), 4, RoundingMode.HALF_UP);

            for (FichaTecnicaItem item : fichaTecnicaItemRepository.findByProdutoId(produto.getId())) {
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

        List<ComponenteNecessidade> componentes = new ArrayList<>();
        List<String> bloqueantes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : necessidadePorComponente.entrySet()) {
            Insumo insumo = insumosPorId.get(entry.getKey());
            Produto produtoBase = produtosBasePorId.get(entry.getKey());

            String nome = insumo != null ? insumo.getNome() : produtoBase.getNome();
            BigDecimal estoqueAtual = insumo != null ? insumo.getEstoqueAtual() : produtoBase.getEstoqueAtual();
            Boolean permitirEstoqueNegativo = insumo != null
                    ? insumo.getPermitirEstoqueNegativo() : produtoBase.getPermitirEstoqueNegativo();
            BigDecimal necessaria = entry.getValue();

            componentes.add(new ComponenteNecessidade(nome, necessaria, estoqueAtual, permitirEstoqueNegativo, insumo, produtoBase));

            if (estoqueAtual.subtract(necessaria).compareTo(BigDecimal.ZERO) < 0
                    && Boolean.FALSE.equals(permitirEstoqueNegativo)) {
                bloqueantes.add(nome);
            }
        }

        return new VerificacaoInsumos(componentes, bloqueantes);
    }

    /** Baixa efetiva de um componente — mesmo padrão de movimentação usado no fluxo legado (registrarProducao). */
    private void baixarComponente(Producao producao, ComponenteNecessidade componente) {
        BigDecimal consumida = componente.necessaria();

        if (componente.insumo() != null) {
            Insumo insumo = componente.insumo();
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

        } else if (componente.produtoBase() != null) {
            Produto base = componente.produtoBase();
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
