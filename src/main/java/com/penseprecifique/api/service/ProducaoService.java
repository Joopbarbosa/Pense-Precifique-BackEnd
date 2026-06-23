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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        // Verificação de suficiência: tudo ou nada, antes de qualquer alteração
        List<String> insuficientes = new ArrayList<>();
        for (FichaTecnicaItem item : ficha) {
            BigDecimal necessaria = item.getQuantidade().multiply(request.getQuantidade());
            if (item.getInsumo() != null) {
                if (item.getInsumo().getEstoqueAtual().compareTo(necessaria) < 0) {
                    insuficientes.add(item.getInsumo().getNome());
                }
            } else if (item.getProdutoBase() != null) {
                if (item.getProdutoBase().getEstoqueAtual().compareTo(necessaria) < 0) {
                    insuficientes.add(item.getProdutoBase().getNome());
                }
            }
        }
        if (!insuficientes.isEmpty()) {
            throw new BusinessException("Estoque insuficiente para os insumos: " + String.join(", ", insuficientes));
        }

        // Criação da produção
        Producao producao = Producao.builder()
                .usuario(usuario)
                .produto(produto)
                .quantidade(request.getQuantidade())
                .dataProducao(request.getDataProducao() != null ? request.getDataProducao() : LocalDateTime.now())
                .status(StatusProducao.ATIVA)
                .numero(proximoNumero(usuarioId))
                .build();
        producao = producaoRepository.save(producao);

        // Baixa dos componentes da ficha técnica
        for (FichaTecnicaItem item : ficha) {
            BigDecimal consumida = item.getQuantidade().multiply(request.getQuantidade());

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
                // A tabela producao_insumos_consumidos exige insumo_id NOT NULL,
                // portanto registramos apenas a movimentação de produto (SAIDA/PRODUCAO).
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
            }
        }

        // Entrada do produto produzido
        produto.setEstoqueAtual(produto.getEstoqueAtual().add(request.getQuantidade()));
        produtoRepository.save(produto);

        movimentacaoProdutoRepository.save(MovimentacaoProduto.builder()
                .produto(produto)
                .tipo(TipoMovimentacaoProduto.ENTRADA)
                .motivo(MotivoMovimentacaoProduto.PRODUCAO)
                .quantidade(request.getQuantidade())
                .referenciaId(producao.getId())
                .referenciaTipo(ReferenciaMovimentacaoTipo.PRODUCAO.name())
                .estornada(false)
                .build());

        List<ProducaoInsumoConsumido> consumidos = producaoInsumoConsumidoRepository.findByProducaoId(producao.getId());
        return producaoMapper.toDetalheResponse(producao, consumidos);
    }

    private InsumoConsumidoResponse montarPreview(FichaTecnicaItem item, BigDecimal quantidade) {
        BigDecimal necessaria = item.getQuantidade().multiply(quantidade);
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

    private Integer proximoNumero(UUID usuarioId) {
        Integer maxNumero = producaoRepository
                .findByUsuarioIdOrderByDataProducaoDesc(usuarioId, PageRequest.of(0, 1))
                .stream()
                .mapToInt(p -> p.getNumero() != null ? p.getNumero() : 0)
                .max()
                .orElse(0);
        return maxNumero + 1;
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
