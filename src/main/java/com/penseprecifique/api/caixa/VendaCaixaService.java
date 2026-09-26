package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoComponenteRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoTaxaParcelaRepository;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.insumo.MovimentacaoInsumoRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoTaxaParcela;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import com.penseprecifique.api.shared.dto.request.caixa.CancelarVendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaItemCustomizacaoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaItemRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaPagamentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaItemCustomizacaoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaItemResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaPagamentoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * #487 — RN-NOVA-1 a 11. RN-NOVA-1 reaberta no teste manual do V0.12.0: item passa a aceitar
 * origem XOR Produto direto/ItemCatalogo (com customizações fixas + ad-hoc), mesmo padrão de
 * {@code OrcamentoService#criarItem} — ver modulos/CAIXA/decisoes-caixa.md. Serviço concreto, sem
 * interface (padrão vigente desde o Épico 6).
 */
@Service
@RequiredArgsConstructor
public class VendaCaixaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final VendaCaixaRepository vendaCaixaRepository;
    private final VendaCaixaItemRepository vendaCaixaItemRepository;
    private final VendaCaixaItemCustomizacaoRepository vendaCaixaItemCustomizacaoRepository;
    private final VendaCaixaItemComponenteRepository vendaCaixaItemComponenteRepository;
    private final VendaCaixaPagamentoRepository vendaCaixaPagamentoRepository;
    private final CaixaTurnoRepository caixaTurnoRepository;
    private final ProdutoRepository produtoRepository;
    private final InsumoRepository insumoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final ItemCatalogoComponenteRepository itemCatalogoComponenteRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final ClienteRepository clienteRepository;
    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final MetodoPagamentoTaxaParcelaRepository metodoPagamentoTaxaParcelaRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;

    /**
     * RN-NOVA-1/2/3/5/7 — retorna {@link VendaCaixaResponseDTO} no sucesso, ou
     * {@link ConfirmacaoEstoqueNegativoResponse} quando há produto que ficaria negativo e ainda
     * não foi confirmado (mesmo padrão de OrcamentoService#avancarStatus/RN-052).
     */
    @Transactional
    public Object registrarVenda(VendaCaixaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        // RN-NOVA-5 — exige turno aberto
        CaixaTurno turno = caixaTurnoRepository.findByUsuarioIdAndStatus(usuarioId, StatusCaixaTurno.ABERTO)
                .orElseThrow(() -> new BusinessException("Não há caixa aberto. Abra o caixa antes de registrar a venda."));

        Cliente cliente = null;
        if (request.clienteId() != null) {
            cliente = clienteRepository.findByIdAndUsuarioId(request.clienteId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));
        }

        // RN-NOVA-1 (reaberta)/RN-NOVA-9 (V0.13.0, #516) — origem XOR ItemCatalogo/Produto direto;
        // item de Catálogo agora tem N componentes (Insumo XOR Produto-base, sem preço próprio —
        // RN-NOVA-2/3, já embutido no precoUnitario do item) mais customizações ad-hoc (escolhidas
        // na hora, para as duas origens). Acumula quantidade por produto/insumo para o estoque ser
        // checado/baixado uma vez por componente, não por linha.
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        Map<UUID, BigDecimal> quantidadeAcumuladaPorProduto = new LinkedHashMap<>();
        Map<UUID, Insumo> insumosPorId = new LinkedHashMap<>();
        Map<UUID, BigDecimal> quantidadeAcumuladaPorInsumo = new LinkedHashMap<>();
        List<ItemPreparado> itensPreparados = new ArrayList<>();
        for (VendaCaixaItemRequestDTO itemRequest : request.itens()) {
            if ((itemRequest.itemCatalogoId() == null) == (itemRequest.produtoId() == null)) {
                throw new BusinessException(
                        "Cada item da venda deve referenciar exatamente um produto ou um item de catálogo.");
            }

            ItemCatalogo itemCatalogo = null;
            Produto produtoDireto = null;
            BigDecimal precoUnitario;
            if (itemRequest.itemCatalogoId() != null) {
                itemCatalogo = buscarItemCatalogoParaVenda(itemRequest.itemCatalogoId(), usuarioId);
                precoUnitario = itemCatalogo.getPrecoVenda() != null ? itemCatalogo.getPrecoVenda() : BigDecimal.ZERO;
            } else {
                produtoDireto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(itemRequest.produtoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemRequest.produtoId()));
                precoUnitario = produtoDireto.getPrecoVenda() != null ? produtoDireto.getPrecoVenda() : BigDecimal.ZERO;
            }
            if (produtoDireto != null) {
                produtosPorId.putIfAbsent(produtoDireto.getId(), produtoDireto);
                quantidadeAcumuladaPorProduto.merge(produtoDireto.getId(), itemRequest.quantidade(), BigDecimal::add);
            }

            BigDecimal subtotalItem = precoUnitario.multiply(itemRequest.quantidade()).setScale(2, RoundingMode.HALF_UP);

            // RN-NOVA-1/9 (V0.13.0, #516) — snapshot dos N componentes do catálogo (Insumo XOR
            // Produto-base); nunca soma no subtotalItem (sem preço próprio, RN-NOVA-2/3), só
            // acumula quantidade a debitar.
            List<ComponentePreparado> componentesPreparados = new ArrayList<>();
            if (itemCatalogo != null) {
                for (ItemCatalogoComponente componente : itemCatalogoComponenteRepository.findByItemCatalogoId(itemCatalogo.getId())) {
                    BigDecimal quantidadeComponente = componente.getQuantidade().multiply(itemRequest.quantidade());
                    componentesPreparados.add(new ComponentePreparado(
                            componente.getInsumo(), componente.getProdutoBase(), quantidadeComponente));
                    if (componente.getInsumo() != null) {
                        Insumo insumo = componente.getInsumo();
                        insumosPorId.putIfAbsent(insumo.getId(), insumo);
                        quantidadeAcumuladaPorInsumo.merge(insumo.getId(), quantidadeComponente, BigDecimal::add);
                    } else {
                        Produto produtoBase = componente.getProdutoBase();
                        produtosPorId.putIfAbsent(produtoBase.getId(), produtoBase);
                        quantidadeAcumuladaPorProduto.merge(produtoBase.getId(), quantidadeComponente, BigDecimal::add);
                    }
                }
            }

            List<CustomizacaoPreparada> customizacoes = new ArrayList<>();
            // Achado do teste manual (V0.12.0) — customização ad-hoc vale para as duas origens
            // (Produto direto ou ItemCatalogo), reabrindo RN-NOVA-1.
            for (VendaCaixaItemCustomizacaoRequestDTO custRequest : itemRequest.customizacoesOuVazio()) {
                Produto custProduto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(custRequest.produtoId(), usuarioId)
                        .orElseThrow(() -> new ResourceNotFoundException("Customização não encontrada"));
                if (custProduto.getTipo() != TipoProduto.CUSTOMIZACAO) {
                    throw new BusinessException("O item '" + custProduto.getNome() + "' não é uma customização válida");
                }
                CustomizacaoPreparada preparada = prepararCustomizacao(custProduto, custRequest.quantidade());
                customizacoes.add(preparada);
                subtotalItem = subtotalItem.add(preparada.subtotal());
                produtosPorId.putIfAbsent(preparada.produto().getId(), preparada.produto());
                quantidadeAcumuladaPorProduto.merge(preparada.produto().getId(),
                        BigDecimal.valueOf(custRequest.quantidade()), BigDecimal::add);
            }

            itensPreparados.add(new ItemPreparado(itemCatalogo, produtoDireto,
                    itemRequest.quantidade(), precoUnitario, subtotalItem, customizacoes, componentesPreparados));
        }

        // RN-NOVA-2 (PDT-007/PDT-011) — mesma regra do resto do sistema, sem rigor próprio para
        // Caixa. Cobre também customizações, já mescladas no mapa acima.
        List<UUID> confirmados = request.confirmarEstoqueNegativoProdutoIds() != null
                ? request.confirmarEstoqueNegativoProdutoIds() : List.of();
        List<String> bloqueados = new ArrayList<>();
        List<AvisoEstoqueNegativoResponse> avisosPendentes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumuladaPorProduto.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            avaliarEstoqueNegativo(produto.getId(), produto.getNome(), produto.getEstoqueAtual(), entry.getValue(),
                    Boolean.TRUE.equals(produto.getPermitirEstoqueNegativo()), confirmados, bloqueados, avisosPendentes);
        }
        // RN-NOVA-1/9 (V0.13.0, #516) — componentes Insumo de itens de Catálogo entram no mesmo
        // bloqueio/aviso que já existia só para Produto.
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumuladaPorInsumo.entrySet()) {
            Insumo insumo = insumosPorId.get(entry.getKey());
            avaliarEstoqueNegativo(insumo.getId(), insumo.getNome(), insumo.getEstoqueAtual(), entry.getValue(),
                    Boolean.TRUE.equals(insumo.getPermitirEstoqueNegativo()), confirmados, bloqueados, avisosPendentes);
        }
        if (!bloqueados.isEmpty()) {
            throw new BusinessException("Estoque insuficiente para " + String.join(", ", bloqueados)
                    + ". Este(s) produto(s) não permite(m) estoque negativo.");
        }
        if (!avisosPendentes.isEmpty()) {
            ConfirmacaoEstoqueNegativoResponse resposta = new ConfirmacaoEstoqueNegativoResponse();
            resposta.setAvisos(avisosPendentes);
            return resposta;
        }

        BigDecimal subtotal = itensPreparados.stream()
                .map(ItemPreparado::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validarDesconto(subtotal, request.descontoTipo(), request.descontoValor());
        BigDecimal total = calcularTotal(subtotal, request.descontoTipo(), request.descontoValor());

        // RN-NOVA-7 — soma dos pagamentos deve ser >= total; excedente só é aceito como troco se
        // pelo menos uma linha for DINHEIRO.
        List<PagamentoPreparado> pagamentosPreparados = new ArrayList<>();
        BigDecimal somaPagamentos = BigDecimal.ZERO;
        boolean temLinhaDinheiro = false;
        for (VendaCaixaPagamentoRequestDTO pagamentoRequest : request.pagamentos()) {
            MetodoPagamentoConfiguravel metodo = metodoPagamentoRepository
                    .findByIdAndUsuarioId(pagamentoRequest.metodoPagamentoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não encontrado"));
            if (metodo.getTipo() == TipoMetodoPagamento.DINHEIRO) {
                temLinhaDinheiro = true;
            }
            somaPagamentos = somaPagamentos.add(pagamentoRequest.valor());
            pagamentosPreparados.add(new PagamentoPreparado(
                    metodo, pagamentoRequest.valor(), pagamentoRequest.parcelas(),
                    resolverTaxaAplicada(metodo, pagamentoRequest.parcelas())));
        }
        if (somaPagamentos.compareTo(total) < 0) {
            throw new BusinessException("A soma dos pagamentos é menor que o total da venda.");
        }
        BigDecimal troco = somaPagamentos.subtract(total);
        if (troco.compareTo(BigDecimal.ZERO) > 0 && !temLinhaDinheiro) {
            throw new BusinessException("Não é possível dar troco fora de dinheiro.");
        }

        Usuario usuario = usuarioRepository.findByIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));

        // RN-053 — mesmo mecanismo de PRO-N/CLI-N/CTG-N/ORC-N/PRD-N (DT-NOVA-5).
        usuarioRepository.lockPorId(usuarioId);
        Integer numero = NumeroSequencialUtil.proximoNumero(
                vendaCaixaRepository.findTopByUsuarioIdOrderByNumeroDesc(usuarioId).map(VendaCaixa::getNumero));

        VendaCaixa venda = VendaCaixa.builder()
                .usuario(usuario)
                .numero(numero)
                .cliente(cliente)
                .dataVenda(LocalDateTime.now())
                .caixaTurno(turno)
                .status(StatusVendaCaixa.CONCLUIDA)
                .subtotal(subtotal)
                .descontoTipo(request.descontoTipo())
                .descontoValor(request.descontoValor())
                .total(total)
                .troco(troco.compareTo(BigDecimal.ZERO) > 0 ? troco : null)
                .build();
        venda = vendaCaixaRepository.save(venda);

        List<VendaCaixaItemResponseDTO> itensResponse = new ArrayList<>();
        for (ItemPreparado item : itensPreparados) {
            VendaCaixaItem itemSalvo = vendaCaixaItemRepository.save(VendaCaixaItem.builder()
                    .vendaCaixa(venda)
                    .itemCatalogo(item.itemCatalogo())
                    .produto(item.produtoDireto())
                    .quantidade(item.quantidade())
                    .precoUnitario(item.precoUnitario())
                    .subtotal(item.subtotal())
                    .build());

            // RN-NOVA-1/9 (V0.13.0, #516) — snapshot dos N componentes do catálogo (Insumo XOR
            // Produto-base), pra a baixa/reversão de estoque sobreviver a uma edição posterior da
            // composição do catálogo. Sem preço próprio (RN-NOVA-2/3) — não vira linha de
            // VendaCaixaItemCustomizacao.
            for (ComponentePreparado cp : item.componentes()) {
                vendaCaixaItemComponenteRepository.save(VendaCaixaItemComponente.builder()
                        .vendaCaixaItem(itemSalvo)
                        .insumo(cp.insumo())
                        .produtoBase(cp.produtoBase())
                        .quantidade(cp.quantidade())
                        .build());
            }

            List<VendaCaixaItemCustomizacaoResponseDTO> customizacoesResponse = new ArrayList<>();
            for (CustomizacaoPreparada cp : item.customizacoes()) {
                VendaCaixaItemCustomizacao custSalva = vendaCaixaItemCustomizacaoRepository.save(VendaCaixaItemCustomizacao.builder()
                        .vendaCaixaItem(itemSalvo)
                        .produto(cp.produto())
                        .quantidade(cp.quantidade())
                        .precoUnitario(cp.precoUnitario())
                        .subtotal(cp.subtotal())
                        .build());
                customizacoesResponse.add(new VendaCaixaItemCustomizacaoResponseDTO(
                        custSalva.getId(), cp.produto().getId(), cp.produto().getNome(),
                        cp.quantidade(), cp.precoUnitario(), cp.subtotal()));
            }

            // V0.13.0 (#516, RN-NOVA-1) — item de Catálogo tem nome próprio, sem "o produto
            // vendido" único; produtoId fica nulo pra origem Catálogo.
            UUID produtoIdResposta = item.itemCatalogo() == null ? item.produtoDireto().getId() : null;
            String nomeResposta = item.itemCatalogo() != null
                    ? item.itemCatalogo().getNome() : item.produtoDireto().getNome();
            itensResponse.add(new VendaCaixaItemResponseDTO(
                    itemSalvo.getId(), produtoIdResposta, nomeResposta,
                    item.itemCatalogo() != null ? item.itemCatalogo().getId() : null,
                    item.quantidade(), item.precoUnitario(), item.subtotal(), customizacoesResponse));
        }

        // RN-NOVA-14 — baixa de estoque (SAIDA, motivo/referencia_tipo CAIXA), acumulada por
        // produto (item direto + componentes de catálogo + customizações, já mescladas no mesmo mapa).
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumuladaPorProduto.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            produto.setEstoqueAtual(produto.getEstoqueAtual().subtract(entry.getValue()));
            produtoRepository.save(produto);
            movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                    .produto(produto)
                    .tipo(TipoMovimentacaoProduto.SAIDA)
                    .motivo(MotivoMovimentacaoProduto.CAIXA)
                    .quantidade(entry.getValue())
                    .referenciaId(venda.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                    .build());
        }
        // RN-NOVA-1/9 (V0.13.0, #516) — baixa dos componentes Insumo dos itens de Catálogo (Insumo
        // nunca tinha vínculo de catálogo, nem movimentação por Caixa, antes desta versão).
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumuladaPorInsumo.entrySet()) {
            Insumo insumo = insumosPorId.get(entry.getKey());
            insumo.setEstoqueAtual(insumo.getEstoqueAtual().subtract(entry.getValue()));
            insumoRepository.save(insumo);
            movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                    .insumo(insumo)
                    .tipo(TipoMovimentacaoInsumo.SAIDA)
                    .motivo(MotivoMovimentacaoInsumo.CAIXA)
                    .quantidade(entry.getValue())
                    .custoUnitario(insumo.getCustoUnitario())
                    .referenciaId(venda.getId())
                    .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA)
                    .build());
        }

        List<VendaCaixaPagamentoResponseDTO> pagamentosResponse = new ArrayList<>();
        for (PagamentoPreparado pagamento : pagamentosPreparados) {
            VendaCaixaPagamento pagamentoSalvo = vendaCaixaPagamentoRepository.save(VendaCaixaPagamento.builder()
                    .vendaCaixa(venda)
                    .metodoPagamento(pagamento.metodo())
                    .valor(pagamento.valor())
                    .parcelas(pagamento.parcelas())
                    .taxaPercentualAplicada(pagamento.taxaAplicada())
                    .build());
            pagamentosResponse.add(new VendaCaixaPagamentoResponseDTO(
                    pagamentoSalvo.getId(), pagamento.metodo().getId(), pagamento.valor(),
                    pagamento.parcelas(), pagamento.taxaAplicada()));
        }

        return toResponse(venda, itensResponse, pagamentosResponse);
    }

    @Transactional(readOnly = true)
    public VendaCaixaResponseDTO buscarPorId(UUID id) {
        UUID usuarioId = getUsuarioIdAutenticado();
        VendaCaixa venda = vendaCaixaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda de Caixa não encontrada"));
        return toResponse(venda, itensDe(venda), pagamentosDe(venda));
    }

    @Transactional(readOnly = true)
    public List<VendaCaixaResponseDTO> listarPorTurno(UUID turnoId) {
        UUID usuarioId = getUsuarioIdAutenticado();
        CaixaTurno turno = caixaTurnoRepository.findByIdAndUsuarioId(turnoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Turno de caixa não encontrado"));
        return vendaCaixaRepository.findByCaixaTurnoIdOrderByDataVendaDesc(turno.getId()).stream()
                .map(venda -> toResponse(venda, itensDe(venda), pagamentosDe(venda)))
                .toList();
    }

    /** RN-NOVA-4 — só permitido enquanto o turno em que a venda ocorreu ainda está ABERTO.
     *
     * <p>#487 (V0.12.0): exige reautenticação por senha da usuária logada, e a devolução de estoque
     * virou escolha explícita ({@code retornarEstoque}) em vez de automática — produto danificado
     * ou perdido não deve voltar para o estoque. Quando devolve, reverte item principal (via
     * {@code getProdutoVendido()}, cobre Produto direto ou ItemCatalogo) e customizações, sempre
     * como ENTRADA com motivo/referencia_tipo CAIXA. */
    @Transactional
    public VendaCaixaResponseDTO cancelarVenda(UUID id, CancelarVendaCaixaRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        VendaCaixa venda = vendaCaixaRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda de Caixa não encontrada"));

        if (venda.getStatus() == StatusVendaCaixa.CANCELADA) {
            throw new BusinessException("Esta venda já está cancelada.");
        }
        if (venda.getCaixaTurno().getStatus() != StatusCaixaTurno.ABERTO) {
            throw new BusinessException("O turno em que esta venda ocorreu já foi encerrado.");
        }

        Usuario usuarioAutenticado = usuarioRepository.findByIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
        if (!passwordEncoder.matches(request.senha(), usuarioAutenticado.getSenhaHash())) {
            throw new BusinessException("Senha incorreta.");
        }

        boolean retornarEstoque = Boolean.TRUE.equals(request.retornarEstoque());
        if (retornarEstoque) {
            List<VendaCaixaItem> itensDaVenda = vendaCaixaItemRepository.findByVendaCaixaId(venda.getId());
            for (VendaCaixaItem item : itensDaVenda) {
                if (item.getProduto() == null) {
                    continue;
                }
                Produto produto = item.getProduto();
                produto.setEstoqueAtual(produto.getEstoqueAtual().add(item.getQuantidade()));
                produtoRepository.save(produto);
                movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                        .produto(produto)
                        .tipo(TipoMovimentacaoProduto.ENTRADA)
                        .motivo(MotivoMovimentacaoProduto.CAIXA)
                        .quantidade(item.getQuantidade())
                        .referenciaId(venda.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                        .build());
            }
            List<UUID> itemIds = itensDaVenda.stream().map(VendaCaixaItem::getId).toList();
            // RN-NOVA-1/9 (V0.13.0, #516) — devolve TODOS os componentes (Insumo XOR Produto-base)
            // dos itens de Catálogo, não só o antigo "produto principal".
            for (VendaCaixaItemComponente c : vendaCaixaItemComponenteRepository.findByVendaCaixaItemIdIn(itemIds)) {
                if (c.getInsumo() != null) {
                    Insumo insumo = c.getInsumo();
                    insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(c.getQuantidade()));
                    insumoRepository.save(insumo);
                    movimentacaoInsumoRepository.save(MovimentacaoInsumo.builder()
                            .insumo(insumo)
                            .tipo(TipoMovimentacaoInsumo.ENTRADA)
                            .motivo(MotivoMovimentacaoInsumo.CAIXA)
                            .quantidade(c.getQuantidade())
                            .custoUnitario(insumo.getCustoUnitario())
                            .referenciaId(venda.getId())
                            .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA)
                            .build());
                } else {
                    Produto produtoBase = c.getProdutoBase();
                    produtoBase.setEstoqueAtual(produtoBase.getEstoqueAtual().add(c.getQuantidade()));
                    produtoRepository.save(produtoBase);
                    movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                            .produto(produtoBase)
                            .tipo(TipoMovimentacaoProduto.ENTRADA)
                            .motivo(MotivoMovimentacaoProduto.CAIXA)
                            .quantidade(c.getQuantidade())
                            .referenciaId(venda.getId())
                            .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                            .build());
                }
            }
            for (VendaCaixaItemCustomizacao c : vendaCaixaItemCustomizacaoRepository.findByVendaCaixaItemIdIn(itemIds)) {
                Produto produto = c.getProduto();
                BigDecimal quantidade = BigDecimal.valueOf(c.getQuantidade());
                produto.setEstoqueAtual(produto.getEstoqueAtual().add(quantidade));
                produtoRepository.save(produto);
                movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                        .produto(produto)
                        .tipo(TipoMovimentacaoProduto.ENTRADA)
                        .motivo(MotivoMovimentacaoProduto.CAIXA)
                        .quantidade(quantidade)
                        .referenciaId(venda.getId())
                        .referenciaTipo(ReferenciaMovimentacaoTipo.CAIXA.name())
                        .build());
            }
        }

        venda.setStatus(StatusVendaCaixa.CANCELADA);
        venda.setCancelamentoMotivo(request.cancelamentoMotivo());
        venda.setEstoqueRetornado(retornarEstoque);
        venda = vendaCaixaRepository.save(venda);

        return toResponse(venda, itensDe(venda), pagamentosDe(venda));
    }

    /** Mesma validação de {@code OrcamentoService#buscarItemCatalogoParaVenda} (RN-045/RN-046),
     *  mensagem adaptada ao contexto do Caixa em vez de Orçamento. */
    private ItemCatalogo buscarItemCatalogoParaVenda(UUID itemCatalogoId, UUID usuarioId) {
        ItemCatalogo item = itemCatalogoRepository.findByIdAndDeletedAtIsNull(itemCatalogoId)
                .filter(i -> i.getCatalogo().getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new BusinessException("Item de catálogo não encontrado"));

        if (!Boolean.TRUE.equals(item.getCatalogo().getAtivo())) {
            throw new BusinessException("O catálogo '" + item.getCatalogo().getNome()
                    + "' está desativado. Reative o catálogo antes de vender este item no Caixa.");
        }

        // RN-045/RN-NOVA-4 (V0.13.0, #516) — qualquer componente inativado/excluído bloqueia a
        // venda do item inteiro (generaliza para os N componentes de RN-NOVA-1).
        for (ItemCatalogoComponente componente : itemCatalogoComponenteRepository.findByItemCatalogoId(item.getId())) {
            if (componente.getInsumo() != null) {
                Insumo insumo = componente.getInsumo();
                if (!Boolean.TRUE.equals(insumo.getAtivo()) || insumo.getDeletedAt() != null) {
                    throw new BusinessException("O insumo '" + insumo.getNome()
                            + "' deste item de catálogo foi inativado. Reative o insumo ou troque o"
                            + " componente do item antes de vendê-lo no Caixa.");
                }
            } else {
                Produto produtoBase = componente.getProdutoBase();
                if (!Boolean.TRUE.equals(produtoBase.getAtivo()) || produtoBase.getDeletedAt() != null) {
                    throw new BusinessException("O produto '" + produtoBase.getNome()
                            + "' deste item de catálogo foi inativado. Reative o produto ou troque o"
                            + " componente do item antes de vendê-lo no Caixa.");
                }
            }
        }
        return item;
    }

    /** V0.13.0 (#516, RN-NOVA-9) — avaliação de bloqueio/aviso de estoque negativo genérica
     * (Produto ou Insumo), mesmo critério de {@code OrcamentoService#avaliarEstoqueParaFinalizar}. */
    private void avaliarEstoqueNegativo(UUID id, String nome, BigDecimal estoqueAtual, BigDecimal necessaria,
                                         boolean permitirEstoqueNegativo, List<UUID> confirmados,
                                         List<String> bloqueados, List<AvisoEstoqueNegativoResponse> avisosPendentes) {
        BigDecimal resultante = estoqueAtual.subtract(necessaria);
        if (resultante.compareTo(BigDecimal.ZERO) >= 0) {
            return;
        }
        if (!permitirEstoqueNegativo) {
            bloqueados.add(nome);
        } else if (!confirmados.contains(id)) {
            AvisoEstoqueNegativoResponse aviso = new AvisoEstoqueNegativoResponse();
            aviso.setComponenteId(id);
            aviso.setNome(nome);
            aviso.setEstoqueAtual(estoqueAtual);
            aviso.setQuantidadeNecessaria(necessaria);
            aviso.setMensagem("A baixa de " + necessaria.stripTrailingZeros().toPlainString()
                    + " de " + nome + " deixará o estoque negativo (atual: "
                    + estoqueAtual.stripTrailingZeros().toPlainString() + "). Confirme para prosseguir.");
            avisosPendentes.add(aviso);
        }
    }

    private CustomizacaoPreparada prepararCustomizacao(Produto produto, int quantidade) {
        BigDecimal precoUnitario = produto.getPrecoVenda() != null ? produto.getPrecoVenda() : BigDecimal.ZERO;
        BigDecimal subtotal = precoUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
        return new CustomizacaoPreparada(produto, quantidade, precoUnitario, subtotal);
    }

    private List<VendaCaixaItemResponseDTO> itensDe(VendaCaixa venda) {
        List<VendaCaixaItem> itens = vendaCaixaItemRepository.findByVendaCaixaId(venda.getId());
        List<UUID> itemIds = itens.stream().map(VendaCaixaItem::getId).toList();
        Map<UUID, List<VendaCaixaItemCustomizacaoResponseDTO>> customizacoesPorItem = new HashMap<>();
        for (VendaCaixaItemCustomizacao c : vendaCaixaItemCustomizacaoRepository.findByVendaCaixaItemIdIn(itemIds)) {
            customizacoesPorItem.computeIfAbsent(c.getVendaCaixaItem().getId(), k -> new ArrayList<>())
                    .add(new VendaCaixaItemCustomizacaoResponseDTO(
                            c.getId(), c.getProduto().getId(), c.getProduto().getNome(),
                            c.getQuantidade(), c.getPrecoUnitario(), c.getSubtotal()));
        }
        // V0.13.0 (#516, RN-NOVA-1) — item de Catálogo tem nome próprio, sem "o produto vendido"
        // único; produtoId fica nulo pra origem Catálogo.
        return itens.stream()
                .map(item -> new VendaCaixaItemResponseDTO(
                        item.getId(),
                        item.getItemCatalogo() == null ? item.getProduto().getId() : null,
                        item.getItemCatalogo() != null ? item.getItemCatalogo().getNome() : item.getProduto().getNome(),
                        item.getItemCatalogo() != null ? item.getItemCatalogo().getId() : null,
                        item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal(),
                        customizacoesPorItem.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    private List<VendaCaixaPagamentoResponseDTO> pagamentosDe(VendaCaixa venda) {
        return vendaCaixaPagamentoRepository.findByVendaCaixaId(venda.getId()).stream()
                .map(p -> new VendaCaixaPagamentoResponseDTO(p.getId(), p.getMetodoPagamento().getId(),
                        p.getValor(), p.getParcelas(), p.getTaxaPercentualAplicada()))
                .toList();
    }

    /** RN-NOVA-7 (V0.8.2/ORC-002, mesmo princípio) — bloqueia desconto negativo ou maior que o subtotal. */
    private void validarDesconto(BigDecimal subtotal, TipoDesconto tipoDesconto, BigDecimal descontoValor) {
        if (tipoDesconto == null) {
            return;
        }
        BigDecimal desconto = descontoValor != null ? descontoValor : BigDecimal.ZERO;
        if (desconto.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("O desconto não pode ser negativo");
        }
        BigDecimal descontoEmValor = tipoDesconto == TipoDesconto.PERCENTUAL
                ? subtotal.multiply(desconto).divide(CEM, 6, RoundingMode.HALF_UP)
                : desconto;
        if (descontoEmValor.compareTo(subtotal) > 0) {
            throw new BusinessException("O desconto não pode ser maior que o subtotal da venda");
        }
    }

    private BigDecimal calcularTotal(BigDecimal subtotal, TipoDesconto tipoDesconto, BigDecimal descontoValor) {
        if (tipoDesconto == null || descontoValor == null) {
            return subtotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = tipoDesconto == TipoDesconto.PERCENTUAL
                ? subtotal.multiply(BigDecimal.ONE.subtract(descontoValor.divide(CEM, 6, RoundingMode.HALF_UP)))
                : subtotal.subtract(descontoValor);
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private VendaCaixaResponseDTO toResponse(
            VendaCaixa venda, List<VendaCaixaItemResponseDTO> itens, List<VendaCaixaPagamentoResponseDTO> pagamentos) {
        return new VendaCaixaResponseDTO(
                venda.getId(),
                venda.getNumero(),
                IdentificadorFormatter.formatar("CX", venda.getNumero()),
                venda.getCliente() != null ? venda.getCliente().getId() : null,
                venda.getDataVenda(),
                venda.getCaixaTurno().getId(),
                venda.getStatus(),
                venda.getSubtotal(),
                venda.getDescontoTipo(),
                venda.getDescontoValor(),
                venda.getTotal(),
                venda.getTroco(),
                venda.getCancelamentoMotivo(),
                venda.getEstoqueRetornado(),
                itens,
                pagamentos
        );
    }

    private record ItemPreparado(ItemCatalogo itemCatalogo, Produto produtoDireto,
                                  BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal subtotal,
                                  List<CustomizacaoPreparada> customizacoes,
                                  List<ComponentePreparado> componentes) {}

    private record CustomizacaoPreparada(Produto produto, int quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {}

    /** V0.13.0 (#516, RN-NOVA-1) — componente de Item de Catálogo (Insumo XOR Produto-base) já
     * resolvido pra a quantidade total do item na venda, sem preço próprio (RN-NOVA-2/3). */
    private record ComponentePreparado(Insumo insumo, Produto produtoBase, BigDecimal quantidade) {}

    private record PagamentoPreparado(MetodoPagamentoConfiguravel metodo, BigDecimal valor,
                                      Integer parcelas, BigDecimal taxaAplicada) {}

    /**
     * #487 (V0.12.0) — valida o parcelamento da linha de pagamento e devolve a taxa a congelar.
     *
     * <p>Parcelar só existe em Cartão de Crédito e nunca passa da parcela máxima configurada. A taxa
     * gravada é snapshot: com taxa uniforme vem da {@code taxaMaquininha} do método, com taxa por
     * parcela vem da linha daquela parcela específica.
     */
    private BigDecimal resolverTaxaAplicada(MetodoPagamentoConfiguravel metodo, Integer parcelas) {
        if (parcelas == null || parcelas == 1) {
            return metodo.getTaxaMaquininha();
        }
        if (metodo.getTipo() != TipoMetodoPagamento.CARTAO_CREDITO) {
            throw new BusinessException("Só é possível parcelar em Cartão Crédito.");
        }
        if (metodo.getMaxParcelas() == null) {
            throw new BusinessException("Parcelamento não está configurado para este método de pagamento.");
        }
        if (parcelas > metodo.getMaxParcelas()) {
            throw new BusinessException("O máximo configurado é " + metodo.getMaxParcelas() + " parcelas.");
        }
        if (Boolean.FALSE.equals(metodo.getTaxaParcelaUniforme())) {
            return metodoPagamentoTaxaParcelaRepository
                    .findByMetodoPagamentoIdOrderByParcelaAsc(metodo.getId()).stream()
                    .filter(t -> t.getParcela().equals(parcelas))
                    .findFirst()
                    .map(MetodoPagamentoTaxaParcela::getTaxa)
                    .orElseThrow(() -> new BusinessException(
                            "Não há taxa configurada para " + parcelas + " parcelas."));
        }
        return metodo.getTaxaMaquininha();
    }
}
