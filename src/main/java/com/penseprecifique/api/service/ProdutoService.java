package com.penseprecifique.api.service;

import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import com.penseprecifique.api.shared.domain.entity.FichaTecnicaItem;
import com.penseprecifique.api.shared.domain.entity.MovimentacaoProduto;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMovimentacaoProduto;
import com.penseprecifique.api.shared.domain.enums.TipoProduto;
import com.penseprecifique.api.shared.dto.request.BaixaManualProdutoRequest;
import com.penseprecifique.api.shared.dto.request.ProdutoRequest;
import com.penseprecifique.api.shared.dto.response.MovimentacaoProdutoResponse;
import com.penseprecifique.api.shared.dto.response.PrecoSugeridoResponse;
import com.penseprecifique.api.shared.dto.response.ProdutoDetalheResponse;
import com.penseprecifique.api.shared.dto.response.ProdutoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ProdutoMapper;
import com.penseprecifique.api.repository.ConfiguracaoPrecificacaoRepository;
import com.penseprecifique.api.repository.FichaTecnicaItemRepository;
import com.penseprecifique.api.repository.MovimentacaoProdutoRepository;
import com.penseprecifique.api.repository.ProdutoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ProdutoService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal SESSENTA = new BigDecimal("60");

    private final ProdutoRepository produtoRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final FichaTecnicaService fichaTecnicaService;
    private final ConfiguracaoPrecificacaoRepository configuracaoPrecificacaoRepository;
    private final ProdutoMapper produtoMapper;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public Page<ProdutoResponse> listar(TipoProduto tipo, String busca, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        boolean temBusca = busca != null && !busca.isBlank();
        if (tipo != null && temBusca) {
            return produtoRepository.findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, tipo, busca, pageable)
                    .map(produtoMapper::toResponse);
        }
        if (tipo != null) {
            return produtoRepository.findByUsuarioIdAndTipoAndDeletedAtIsNull(usuarioId, tipo, pageable)
                    .map(produtoMapper::toResponse);
        }
        if (temBusca) {
            return produtoRepository.findByUsuarioIdAndNomeContainingIgnoreCaseAndDeletedAtIsNull(usuarioId, busca, pageable)
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
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);

        BigDecimal somaComponentes = fichaTecnicaService.recalcularPrecoCusto(produto.getId());
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        response.setCustoTotalLote(custoTotalLote);

        // custoUnitario recalculado ao vivo (não usa produto.getPrecoCusto(), que só é atualizado no save
        // e fica stale se um insumo/produto base/valorHora mudar depois — bug RN-039 investigado).
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        response.setCustoUnitario(custoUnitario);

        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO) {
            response.setPrecoSugerido(calcularPrecoSugerido(custoUnitario, produto.getMargemLucro()));
        }
        return response;
    }

    public ProdutoDetalheResponse cadastrar(ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        validarCamposPorTipo(request);
        validarRendimento(request);

        Usuario usuario = getUsuarioAutenticado();
        Produto produto = produtoMapper.toEntity(request, usuario);
        produto.setNumero(NumeroSequencialUtil.proximoNumero(
                produtoRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(Produto::getNumero)));
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO && produto.getPrecoVenda() == null) {
            // placeholder só para satisfazer chk_preco_venda_tipo no INSERT inicial (precisa do id do
            // produto para persistir a ficha técnica antes de calcular o precoSugerido real) — sobrescrito abaixo
            produto.setPrecoVenda(BigDecimal.ZERO);
        }
        produto = produtoRepository.save(produto);

        BigDecimal somaComponentes = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        produto.setPrecoCusto(custoUnitario);

        BigDecimal precoSugerido = null;
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO) {
            if (produto.getMargemLucro() == null) {
                produto.setMargemLucro(buscarMargemPadrao(usuarioId));
            }
            precoSugerido = calcularPrecoSugerido(custoUnitario, produto.getMargemLucro());
            aplicarPrecoVendaCriacao(produto, request.getPrecoVenda(), precoSugerido);
            validarPrecoVendaObrigatorio(produto);
        }

        produto = produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);
        response.setCustoTotalLote(custoTotalLote);
        response.setPrecoSugerido(precoSugerido);
        return response;
    }

    public ProdutoDetalheResponse editar(UUID id, ProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        validarCamposPorTipo(request);
        validarRendimento(request);

        BigDecimal precoVendaAntigo = produto.getPrecoVenda();
        BigDecimal margemLucroAntigo = produto.getMargemLucro();
        boolean overrideAntigo = Boolean.TRUE.equals(produto.getOverride());

        produtoMapper.updateEntity(request, produto);
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO && produto.getMargemLucro() == null) {
            // margemLucro não veio no request: preserva o valor anterior (não tem conceito de override próprio)
            produto.setMargemLucro(margemLucroAntigo);
        }

        BigDecimal somaComponentes = fichaTecnicaService.salvarFichaTecnica(produto, request.getFichaTecnica(), usuarioId);
        BigDecimal valorHora = buscarValorHora(usuarioId);
        BigDecimal custoTotalLote = somaComponentes.add(calcularCustoMaoDeObra(produto.getTempoProducao(), valorHora));
        BigDecimal custoUnitario = calcularCustoUnitario(custoTotalLote, produto.getRendimento());
        produto.setPrecoCusto(custoUnitario);

        BigDecimal precoSugerido = null;
        if (produto.getTipo() == TipoProduto.CUSTOMIZACAO) {
            precoSugerido = calcularPrecoSugerido(custoUnitario, produto.getMargemLucro());
            boolean margemMudou = !valoresIguais(margemLucroAntigo, produto.getMargemLucro());
            aplicarPrecoVendaEdicao(produto, request.getPrecoVenda(), precoSugerido, precoVendaAntigo, overrideAntigo, margemMudou);
            validarPrecoVendaObrigatorio(produto);
        }

        produtoRepository.save(produto);

        List<FichaTecnicaItem> itens = fichaTecnicaItemRepository.findByProdutoId(produto.getId());
        ProdutoDetalheResponse response = produtoMapper.toDetalheResponse(produto, itens);
        response.setCustoTotalLote(custoTotalLote);
        response.setPrecoSugerido(precoSugerido);
        return response;
    }

    public void inativar(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setDeletedAt(LocalDateTime.now());
        produtoRepository.save(produto);
    }

    /**
     * RN-054 — preço sugerido de um produto avulso (sem Catálogo) dada uma margem informada na hora.
     * Reaproveita o custo_unitario já calculado e persistido no produto (RN-039) — não recalcula ficha técnica.
     */
    @Transactional(readOnly = true)
    public PrecoSugeridoResponse calcularPrecoSugeridoAvulso(UUID produtoId, BigDecimal margem) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        BigDecimal precoSugerido = calcularPrecoSugerido(produto.getPrecoCusto(), margem);
        return new PrecoSugeridoResponse(produto.getPrecoCusto(), margem, precoSugerido);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoProdutoResponse> listarMovimentacoes(UUID produtoId, Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return movimentacaoProdutoRepository.findByProdutoIdOrderByCreatedAtDesc(produtoId, pageable)
                .map(produtoMapper::toMovimentacaoResponse);
    }

    public MovimentacaoProdutoResponse baixaManual(UUID produtoId, BaixaManualProdutoRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(produtoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        BigDecimal estoqueResultante = produto.getEstoqueAtual().subtract(request.getQuantidade());
        if (estoqueResultante.compareTo(BigDecimal.ZERO) < 0 && !produto.getPermitirEstoqueNegativo()) {
            throw new BusinessException(
                    "Estoque insuficiente para " + produto.getNome() + ". Este produto não permite estoque negativo.");
        }

        produto.setEstoqueAtual(estoqueResultante);
        produtoRepository.save(produto);

        MovimentacaoProduto movimentacao = MovimentacaoProduto.builder()
                .produto(produto)
                .tipo(TipoMovimentacaoProduto.SAIDA)
                .motivo(request.getMotivo())
                .quantidade(request.getQuantidade())
                .observacao(request.getObservacao())
                .estornada(false)
                .build();

        return produtoMapper.toMovimentacaoResponse(movimentacaoProdutoRepository.save(movimentacao));
    }

    // ---------------------------------------------------------------
    // RN-039 — Custo Total do lote / Custo Unitário
    // ---------------------------------------------------------------

    private void validarRendimento(ProdutoRequest request) {
        boolean temFichaTecnica = request.getFichaTecnica() != null && !request.getFichaTecnica().isEmpty();
        if (temFichaTecnica && (request.getRendimento() == null || request.getRendimento().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("Rendimento é obrigatório e deve ser maior que zero quando a ficha técnica está preenchida.");
        }
    }

    private BigDecimal calcularCustoMaoDeObra(Integer tempoProducaoMinutos, BigDecimal valorHora) {
        return BigDecimal.valueOf(tempoProducaoMinutos)
                .divide(SESSENTA, 6, RoundingMode.HALF_UP)
                .multiply(valorHora);
    }

    private BigDecimal calcularCustoUnitario(BigDecimal custoTotalLote, BigDecimal rendimento) {
        // rendimento nulo/zero só é possível quando a ficha técnica está vazia (validarRendimento não bloqueou);
        // nesse caso não há divisão por lote a fazer — custo unitário = custo total.
        BigDecimal divisor = (rendimento != null && rendimento.compareTo(BigDecimal.ZERO) > 0) ? rendimento : BigDecimal.ONE;
        return custoTotalLote.divide(divisor, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal buscarValorHora(UUID usuarioId) {
        return configuracaoPrecificacaoRepository.findByUsuarioId(usuarioId)
                .map(ConfiguracaoPrecificacao::getValorHora)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal buscarMargemPadrao(UUID usuarioId) {
        return configuracaoPrecificacaoRepository.findByUsuarioId(usuarioId)
                .map(ConfiguracaoPrecificacao::getMargemPadrao)
                .orElse(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------
    // RN-038a — Preço de venda com override (só CUSTOMIZACAO)
    // ---------------------------------------------------------------

    private BigDecimal calcularPrecoSugerido(BigDecimal custoUnitario, BigDecimal margemLucro) {
        BigDecimal margem = margemLucro != null ? margemLucro : BigDecimal.ZERO;
        BigDecimal fator = BigDecimal.ONE.add(margem.divide(CEM, 6, RoundingMode.HALF_UP));
        return custoUnitario.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }

    private void aplicarPrecoVendaCriacao(Produto produto, BigDecimal precoVendaInformado, BigDecimal precoSugerido) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            produto.setPrecoVenda(precoVendaInformado);
            produto.setOverride(true);
        } else {
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
        }
    }

    private void aplicarPrecoVendaEdicao(Produto produto, BigDecimal precoVendaInformado, BigDecimal precoSugerido,
                                          BigDecimal precoVendaAntigo, boolean overrideAntigo, boolean margemMudou) {
        if (precoVendaInformado != null && precoVendaInformado.compareTo(precoSugerido) != 0) {
            // artesã editou o preço manualmente para um valor diferente do sugerido
            produto.setPrecoVenda(precoVendaInformado);
            produto.setOverride(true);
            return;
        }
        if (precoVendaInformado != null) {
            // veio igual ao sugerido: trata como se não fosse override
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
            return;
        }
        // precoVenda não veio no request
        if (margemMudou && !overrideAntigo) {
            // sem override: acompanha a nova margem
            produto.setPrecoVenda(precoSugerido);
            produto.setOverride(false);
            return;
        }
        // com override, ou mudança apenas de custo (ficha técnica/insumo): preço persistido nunca muda sozinho
        produto.setPrecoVenda(precoVendaAntigo);
        produto.setOverride(overrideAntigo);
    }

    private void validarPrecoVendaObrigatorio(Produto produto) {
        if (produto.getPrecoVenda() == null || produto.getPrecoVenda().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Preço de venda é obrigatório para produtos do tipo Customização.");
        }
    }

    private boolean valoresIguais(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    // ---------------------------------------------------------------
    // Validação de campos por tipo
    // ---------------------------------------------------------------

    private void validarCamposPorTipo(ProdutoRequest request) {
        if (request.getTipo() == TipoProduto.CUSTOMIZACAO) {
            return;
        }
        if (request.getPrecoVenda() != null || request.getMargemLucro() != null) {
            throw new BusinessException("Preço de venda e margem de lucro só se aplicam a produtos do tipo Customização.");
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
