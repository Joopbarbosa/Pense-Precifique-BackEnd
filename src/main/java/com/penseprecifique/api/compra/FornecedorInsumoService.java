package com.penseprecifique.api.compra;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            throw new BusinessException("Este insumo está inativo e não pode ser vinculado. Reative-o para continuar.");
        }
        if (fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId()).isPresent()) {
            throw new BusinessException("Este insumo já está vinculado a este fornecedor.");
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
     * RN-NOVA-6 — chamado pela confirmação da compra, na mesma transação: cria o par se não existe e
     * sobrescreve o preço de referência com o preço unitário pago na linha. Não valida ativo/papel
     * (vínculo existente da compra, RN-NOVA-2).
     */
    public void registrarPrecoDaCompra(Usuario usuario, Cliente fornecedor, Insumo insumo, BigDecimal precoUnitarioPago) {
        FornecedorInsumo vinculo = fornecedorInsumoRepository.findByFornecedorIdAndInsumoId(fornecedor.getId(), insumo.getId())
                .orElseGet(() -> FornecedorInsumo.builder().usuario(usuario).fornecedor(fornecedor).insumo(insumo).build());
        vinculo.setPrecoReferencia(precoUnitarioPago);
        fornecedorInsumoRepository.save(vinculo);
    }

    private FornecedorInsumo buscar(UUID id) {
        return fornecedorInsumoRepository.findByIdAndUsuarioId(id, getUsuarioAutenticado().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado: " + id));
    }

    private FornecedorInsumoResponse toResponse(FornecedorInsumo v) {
        return new FornecedorInsumoResponse(v.getId(), compraMapper.toRef(v.getFornecedor()),
                compraMapper.toRef(v.getInsumo()), v.getPrecoReferencia(), v.getUpdatedAt());
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
