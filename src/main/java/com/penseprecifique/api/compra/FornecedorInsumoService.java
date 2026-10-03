package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.enums.RegraPrecoReferencia;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.entity.FornecedorInsumo;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.dto.request.compra.FornecedorInsumoRequest;
import com.penseprecifique.api.shared.dto.request.compra.PrecoReferenciaRequest;
import com.penseprecifique.api.shared.dto.response.compra.FornecedorInsumoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * #540/RN-NOVA-6 (V0.15.0, DT-NOVA-6) — vínculo Fornecedor↔Insumo. N:N, um par por
 * fornecedor+insumo, preço de referência opcional. Criado manualmente (exige fornecedor ativo com
 * papel Fornecedor e insumo ativo) ou automaticamente na confirmação da compra
 * ({@link #registrarPrecoDaCompra}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FornecedorInsumoService {

    private final FornecedorInsumoRepository fornecedorInsumoRepository;
    private final InsumoRepository insumoRepository;
    private final ClienteService clienteService;
    private final UsuarioRepository usuarioRepository;
    private final CompraMapper compraMapper;
    private final CompraItemRepository compraItemRepository;

    static final int JANELA_MESES = 12;

    @Transactional(readOnly = true)
    public List<FornecedorInsumoResponse> listar(UUID fornecedorId, UUID insumoId) {
        UUID usuarioId = getUsuarioAutenticado().getId();
        if ((fornecedorId == null) == (insumoId == null)) {
            throw new BusinessException("Informe o fornecedor ou o insumo.");
        }
        List<FornecedorInsumo> vinculos = fornecedorId != null
                ? fornecedorInsumoRepository.findByUsuarioIdAndFornecedorId(usuarioId, fornecedorId)
                : fornecedorInsumoRepository.findByUsuarioIdAndInsumoId(usuarioId, insumoId);
        return vinculos.stream()
                .sorted(Comparator.comparing((FornecedorInsumo v) -> fornecedorId != null
                        ? v.getInsumo().getNome() : v.getFornecedor().getNome(), String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse).toList();
    }

    public FornecedorInsumoResponse criar(FornecedorInsumoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Cliente fornecedor = clienteService.resolverParaVinculo(request.fornecedorId(), usuario.getId(),
                PapelCadastro.FORNECEDOR, null);
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(request.insumoId(), usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado: " + request.insumoId()));
        if (!Boolean.TRUE.equals(insumo.getAtivo())) {
            throw BusinessException.explicado("Insumo inativo", "Este insumo está inativo e não pode ser vinculado. Reative-o para continuar.",
                    "Insumo inativo não recebe vínculos novos com fornecedores.",
                    "Reative o insumo na tela de Insumos e vincule de novo.");
        }
        if (fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId()).isPresent()) {
            throw BusinessException.explicado("Vínculo já existe", "Este insumo já está vinculado a este fornecedor.",
                    "Cada par fornecedor + insumo tem um vínculo só, com um preço de referência.",
                    "Abra o vínculo existente para ver ou atualizar o preço de referência.");
        }
        FornecedorInsumo vinculo = fornecedorInsumoRepository.save(FornecedorInsumo.builder()
                .usuario(usuario).fornecedor(fornecedor).insumo(insumo)
                .precoReferencia(request.precoReferencia()).build());
        return toResponse(vinculo);
    }

    public FornecedorInsumoResponse atualizarPreco(UUID id, PrecoReferenciaRequest request) {
        FornecedorInsumo vinculo = buscar(id);
        vinculo.setPrecoReferencia(request.precoReferencia());
        return toResponse(fornecedorInsumoRepository.save(vinculo));
    }

    /**
     * Remoção física: o vínculo é só "o que o fornecedor vende e por quanto", não é referenciado por
     * nenhuma outra tabela nem tem identificador sequencial — soft delete só complicaria o upsert da
     * confirmação (par único fornecedor+insumo).
     */
    public void remover(UUID id) {
        fornecedorInsumoRepository.delete(buscar(id));
    }

    /**
     * RN-NOVA-6 + #590/RN-NOVA-39 — chamado pela confirmação da compra, na mesma transação e depois de
     * a compra já estar CONFIRMADA: cria o par se não existe (com o preço pago, em qualquer regra) e
     * recalcula o preço de referência pela regra do insumo. Não valida ativo/papel (vínculo existente
     * da compra, RN-NOVA-2).
     */
    public void registrarPrecoDaCompra(Usuario usuario, Cliente fornecedor, Insumo insumo, BigDecimal precoUnitarioPago) {
        FornecedorInsumo vinculo = fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId())
                .orElseGet(() -> fornecedorInsumoRepository.save(FornecedorInsumo.builder().usuario(usuario)
                        .fornecedor(fornecedor).insumo(insumo).precoReferencia(precoUnitarioPago).build()));
        aplicarRegra(vinculo);
    }

    /** #590 — cancelamento de compra: recalcula o par sem a compra cancelada (MANUAL não muda). */
    public void recalcular(Cliente fornecedor, Insumo insumo) {
        fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId())
                .ifPresent(this::aplicarRegra);
    }

    /** #590 — troca da regra do insumo: recalcula todos os vínculos dele. */
    public void recalcularDoInsumo(Insumo insumo) {
        fornecedorInsumoRepository.findByUsuarioIdAndInsumoId(insumo.getUsuario().getId(), insumo.getId())
                .forEach(this::aplicarRegra);
    }

    /**
     * #590/RN-NOVA-39 — MEDIA: soma do pago ÷ soma das quantidades (média ponderada pela quantidade);
     * MENOR_VALOR: menor preço unitário pago; ambas nas linhas CONFIRMADAS do par nos últimos 12 meses.
     * Sem linha na janela, mantém o valor. Ex.: 10 un a 2,565 + 20 un a 2,80 → 81,65 ÷ 30 = 2,7217.
     */
    private void aplicarRegra(FornecedorInsumo vinculo) {
        RegraPrecoReferencia regra = vinculo.getInsumo().getRegraPrecoReferencia();
        if (regra == null || regra == RegraPrecoReferencia.MANUAL) {
            return;
        }
        List<CompraItem> linhas = compraItemRepository.findConfirmadasDoPar(vinculo.getFornecedor().getId(),
                        vinculo.getInsumo().getId(), LocalDate.now().minusMonths(JANELA_MESES)).stream()
                .filter(i -> i.getQuantidade() != null && i.getQuantidade().signum() > 0 && i.getPrecoTotal() != null)
                .toList();
        if (linhas.isEmpty()) {
            return;
        }
        BigDecimal preco;
        if (regra == RegraPrecoReferencia.MEDIA) {
            BigDecimal pago = linhas.stream().map(CompraItem::getPrecoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal quantidade = linhas.stream().map(CompraItem::getQuantidade).reduce(BigDecimal.ZERO, BigDecimal::add);
            preco = pago.divide(quantidade, 4, RoundingMode.HALF_UP);
        } else {
            preco = linhas.stream().map(i -> CompraMapper.precoUnitario(i.getPrecoTotal(), i.getQuantidade()))
                    .min(Comparator.naturalOrder()).orElseThrow();
        }
        if (preco.signum() <= 0) {
            return;
        }
        vinculo.setPrecoReferencia(preco);
        fornecedorInsumoRepository.save(vinculo);
    }

    private FornecedorInsumo buscar(UUID id) {
        return fornecedorInsumoRepository.findByIdAndUsuarioId(id, getUsuarioAutenticado().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado: " + id));
    }

    private FornecedorInsumoResponse toResponse(FornecedorInsumo v) {
        FornecedorInsumoResponse.UltimaCompra ultima = compraItemRepository
                .findUltimasConfirmadasDoPar(v.getFornecedor().getId(), v.getInsumo().getId(),
                        org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst()
                .map(i -> new FornecedorInsumoResponse.UltimaCompra(i.getCompra().getId(),
                        IdentificadorFormatter.formatar("COM", i.getCompra().getNumero()),
                        i.getCompra().getDataCompra(), i.getPrecoUnitarioPago()))
                .orElse(null);
        return new FornecedorInsumoResponse(v.getId(), compraMapper.toRef(v.getFornecedor()),
                compraMapper.toRef(v.getInsumo()), v.getPrecoReferencia(), v.getUpdatedAt(),
                v.getInsumo().getRegraPrecoReferencia(), ultima);
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
