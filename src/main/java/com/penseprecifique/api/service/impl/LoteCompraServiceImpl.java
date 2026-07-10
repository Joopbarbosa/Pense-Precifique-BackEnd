package com.penseprecifique.api.service.impl;

import com.penseprecifique.api.domain.entity.Insumo;
import com.penseprecifique.api.domain.entity.LoteCompra;
import com.penseprecifique.api.domain.entity.MovimentacaoInsumo;
import com.penseprecifique.api.domain.entity.Usuario;
import com.penseprecifique.api.domain.enums.MotivoMovimentacaoInsumo;
import com.penseprecifique.api.domain.enums.ReferenciaMovimentacaoTipo;
import com.penseprecifique.api.domain.enums.TipoMovimentacaoInsumo;
import com.penseprecifique.api.dto.request.ItemLoteCompraRequestDTO;
import com.penseprecifique.api.dto.request.RegistrarLoteCompraRequestDTO;
import com.penseprecifique.api.dto.response.ImpactoAgregadoResponseDTO;
import com.penseprecifique.api.dto.response.InsumoImpactoResponseDTO;
import com.penseprecifique.api.exception.BusinessException;
import com.penseprecifique.api.exception.ResourceNotFoundException;
import com.penseprecifique.api.repository.InsumoRepository;
import com.penseprecifique.api.repository.LoteCompraRepository;
import com.penseprecifique.api.repository.MovimentacaoInsumoRepository;
import com.penseprecifique.api.repository.UsuarioRepository;
import com.penseprecifique.api.service.LoteCompraService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Transactional
public class LoteCompraServiceImpl implements LoteCompraService {

    private final LoteCompraRepository loteCompraRepository;
    private final InsumoRepository insumoRepository;
    private final MovimentacaoInsumoRepository movimentacaoInsumoRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public ImpactoAgregadoResponseDTO registrarLote(RegistrarLoteCompraRequestDTO request) {
        Usuario usuario = getUsuarioAutenticado();
        UUID usuarioId = usuario.getId();

        LocalDateTime dataCompra = request.dataCompra() != null ? request.dataCompra() : LocalDateTime.now();

        LoteCompra loteCompra = criarLote(usuario, dataCompra);

        List<InsumoImpactoResponseDTO> insumosAtualizados = new ArrayList<>();

        for (ItemLoteCompraRequestDTO item : request.itens()) {
            Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(item.insumoId(), usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Insumo não encontrado: " + item.insumoId()));

            insumosAtualizados.add(registrarCompraIndividual(
                    insumo, item.quantidadeComprada(), item.precoTotalPago(), loteCompra.getId()));
        }

        return new ImpactoAgregadoResponseDTO(
                loteCompra.getId(),
                loteCompra.getDataCompra(),
                insumosAtualizados,
                new ArrayList<>()
        );
    }

    @Override
    public LoteCompra criarLote(Usuario usuario, LocalDateTime dataCompra) {
        LoteCompra loteCompra = LoteCompra.builder()
                .usuario(usuario)
                .dataCompra(dataCompra != null ? dataCompra : LocalDateTime.now())
                .build();
        return loteCompraRepository.save(loteCompra);
    }

    @Override
    public InsumoImpactoResponseDTO registrarCompraIndividual(
            Insumo insumo, BigDecimal quantidade, BigDecimal precoTotal, UUID loteCompraId) {
        BigDecimal custoUnitarioAnterior = insumo.getCustoUnitario();

        BigDecimal novoCusto = precoTotal.divide(quantidade, 6, RoundingMode.HALF_UP);

        insumo.setCustoUnitario(novoCusto);
        insumo.setEstoqueAtual(insumo.getEstoqueAtual().add(quantidade));
        insumoRepository.save(insumo);

        MovimentacaoInsumo movimentacao = MovimentacaoInsumo.builder()
                .insumo(insumo)
                .tipo(TipoMovimentacaoInsumo.ENTRADA)
                .motivo(MotivoMovimentacaoInsumo.COMPRA)
                .quantidade(quantidade)
                .observacao(null)
                .referenciaId(loteCompraId)
                .referenciaTipo(ReferenciaMovimentacaoTipo.LOTE_COMPRA)
                .estornada(false)
                .build();
        movimentacaoInsumoRepository.save(movimentacao);

        return new InsumoImpactoResponseDTO(
                insumo.getId(),
                insumo.getNome(),
                insumo.getMarca(),
                insumo.getUnidadeMedida(),
                custoUnitarioAnterior,
                novoCusto,
                quantidade
        );
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
