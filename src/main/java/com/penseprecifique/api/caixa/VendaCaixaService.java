package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.produto.MovimentacaoProdutoRepository;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import com.penseprecifique.api.shared.dto.request.caixa.CancelarVendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaItemRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaPagamentoRequestDTO;
import com.penseprecifique.api.shared.dto.request.caixa.VendaCaixaRequestDTO;
import com.penseprecifique.api.shared.dto.response.AvisoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.ConfirmacaoEstoqueNegativoResponse;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaItemResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaPagamentoResponseDTO;
import com.penseprecifique.api.shared.dto.response.caixa.VendaCaixaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * #487 — RN-NOVA-1 a 11. Serviço concreto, sem interface (padrão vigente desde o Épico 6).
 */
@Service
@RequiredArgsConstructor
public class VendaCaixaService {

    private static final BigDecimal CEM = new BigDecimal("100");

    private final VendaCaixaRepository vendaCaixaRepository;
    private final VendaCaixaItemRepository vendaCaixaItemRepository;
    private final VendaCaixaPagamentoRepository vendaCaixaPagamentoRepository;
    private final CaixaTurnoRepository caixaTurnoRepository;
    private final ProdutoRepository produtoRepository;
    private final MovimentacaoProdutoRepository movimentacaoProdutoRepository;
    private final ClienteRepository clienteRepository;
    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
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
            cliente = clienteRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(request.clienteId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado"));
        }

        // RN-NOVA-1/3 — carrega produtos e faz snapshot do preço; acumula quantidade por produto
        // (mesmo padrão de OrcamentoService#validarEstoqueParaFinalizar) para o caso de o mesmo
        // produto aparecer em mais de uma linha da mesma venda.
        Map<UUID, Produto> produtosPorId = new LinkedHashMap<>();
        Map<UUID, BigDecimal> quantidadeAcumuladaPorProduto = new LinkedHashMap<>();
        List<ItemPreparado> itensPreparados = new ArrayList<>();
        for (VendaCaixaItemRequestDTO itemRequest : request.itens()) {
            Produto produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(itemRequest.produtoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemRequest.produtoId()));
            produtosPorId.putIfAbsent(produto.getId(), produto);
            quantidadeAcumuladaPorProduto.merge(produto.getId(), itemRequest.quantidade(), BigDecimal::add);

            BigDecimal precoUnitario = produto.getPrecoVenda() != null ? produto.getPrecoVenda() : BigDecimal.ZERO;
            BigDecimal subtotalItem = precoUnitario.multiply(itemRequest.quantidade()).setScale(2, RoundingMode.HALF_UP);
            itensPreparados.add(new ItemPreparado(produto, itemRequest.quantidade(), precoUnitario, subtotalItem));
        }

        // RN-NOVA-2 (PDT-007/PDT-011) — mesma regra do resto do sistema, sem rigor próprio para Caixa.
        List<UUID> confirmados = request.confirmarEstoqueNegativoProdutoIds() != null
                ? request.confirmarEstoqueNegativoProdutoIds() : List.of();
        List<String> bloqueados = new ArrayList<>();
        List<AvisoEstoqueNegativoResponse> avisosPendentes = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantidadeAcumuladaPorProduto.entrySet()) {
            Produto produto = produtosPorId.get(entry.getKey());
            BigDecimal resultante = produto.getEstoqueAtual().subtract(entry.getValue());
            if (resultante.compareTo(BigDecimal.ZERO) >= 0) {
                continue;
            }
            if (!produto.getPermitirEstoqueNegativo()) {
                bloqueados.add(produto.getNome());
            } else if (!confirmados.contains(produto.getId())) {
                AvisoEstoqueNegativoResponse aviso = new AvisoEstoqueNegativoResponse();
                aviso.setComponenteId(produto.getId());
                aviso.setNome(produto.getNome());
                aviso.setEstoqueAtual(produto.getEstoqueAtual());
                aviso.setQuantidadeNecessaria(entry.getValue());
                aviso.setMensagem("A baixa de " + entry.getValue().stripTrailingZeros().toPlainString()
                        + " de " + produto.getNome() + " deixará o estoque negativo (atual: "
                        + produto.getEstoqueAtual().stripTrailingZeros().toPlainString() + "). Confirme para prosseguir.");
                avisosPendentes.add(aviso);
            }
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
            pagamentosPreparados.add(new PagamentoPreparado(metodo, pagamentoRequest.valor()));
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
                    .produto(item.produto())
                    .quantidade(item.quantidade())
                    .precoUnitario(item.precoUnitario())
                    .subtotal(item.subtotal())
                    .build());
            itensResponse.add(new VendaCaixaItemResponseDTO(
                    itemSalvo.getId(), item.produto().getId(), item.produto().getNome(),
                    item.quantidade(), item.precoUnitario(), item.subtotal()));
        }

        // RN-NOVA-14 — baixa de estoque (SAIDA, motivo/referencia_tipo CAIXA), acumulada por produto.
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

        List<VendaCaixaPagamentoResponseDTO> pagamentosResponse = new ArrayList<>();
        for (PagamentoPreparado pagamento : pagamentosPreparados) {
            VendaCaixaPagamento pagamentoSalvo = vendaCaixaPagamentoRepository.save(VendaCaixaPagamento.builder()
                    .vendaCaixa(venda)
                    .metodoPagamento(pagamento.metodo())
                    .valor(pagamento.valor())
                    .build());
            pagamentosResponse.add(new VendaCaixaPagamentoResponseDTO(
                    pagamentoSalvo.getId(), pagamento.metodo().getId(), pagamento.valor()));
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

    /** RN-NOVA-4 — só permitido enquanto o turno em que a venda ocorreu ainda está ABERTO; reverte
     * a baixa de estoque automaticamente (ENTRADA, motivo/referencia_tipo CAIXA). */
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

        for (VendaCaixaItem item : vendaCaixaItemRepository.findByVendaCaixaId(venda.getId())) {
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

        venda.setStatus(StatusVendaCaixa.CANCELADA);
        venda.setCancelamentoMotivo(request.cancelamentoMotivo());
        venda = vendaCaixaRepository.save(venda);

        return toResponse(venda, itensDe(venda), pagamentosDe(venda));
    }

    private List<VendaCaixaItemResponseDTO> itensDe(VendaCaixa venda) {
        return vendaCaixaItemRepository.findByVendaCaixaId(venda.getId()).stream()
                .map(item -> new VendaCaixaItemResponseDTO(
                        item.getId(), item.getProduto().getId(), item.getProduto().getNome(),
                        item.getQuantidade(), item.getPrecoUnitario(), item.getSubtotal()))
                .toList();
    }

    private List<VendaCaixaPagamentoResponseDTO> pagamentosDe(VendaCaixa venda) {
        return vendaCaixaPagamentoRepository.findByVendaCaixaId(venda.getId()).stream()
                .map(p -> new VendaCaixaPagamentoResponseDTO(p.getId(), p.getMetodoPagamento().getId(), p.getValor()))
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
                itens,
                pagamentos
        );
    }

    private record ItemPreparado(Produto produto, BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {}

    private record PagamentoPreparado(MetodoPagamentoConfiguravel metodo, BigDecimal valor) {}
}
