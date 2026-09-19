package com.penseprecifique.api.empresa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoConfiguravel;
import com.penseprecifique.api.shared.domain.entity.MetodoPagamentoTaxaParcela;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.MetodoPagamentoConfiguravelUpdateRequestDTO;
import com.penseprecifique.api.shared.dto.request.config.TaxaParcelaRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.MetodoPagamentoConfiguravelResponseDTO;
import com.penseprecifique.api.shared.dto.response.config.TaxaParcelaResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * #491 — RN-NOVA-15/16/17. Service concreto, sem interface (padrão vigente desde o Épico 6,
 * CLAUDE.md seção 4) — a dupla Service/ServiceImpl de {@code ConfiguracaoService}/
 * {@code EmpresaService} neste mesmo package é legado anterior a essa convenção, não replicado
 * aqui.
 */
@Service
@RequiredArgsConstructor
public class MetodoPagamentoConfiguravelService {

    private static final List<TipoMetodoPagamento> TIPOS_FIXOS = List.of(
            TipoMetodoPagamento.DINHEIRO, TipoMetodoPagamento.PIX,
            TipoMetodoPagamento.CARTAO_CREDITO, TipoMetodoPagamento.CARTAO_DEBITO);

    private static final List<TipoMetodoPagamento> TIPOS_CARTAO = List.of(
            TipoMetodoPagamento.CARTAO_CREDITO, TipoMetodoPagamento.CARTAO_DEBITO);

    private final MetodoPagamentoConfiguravelRepository metodoPagamentoRepository;
    private final MetodoPagamentoTaxaParcelaRepository metodoPagamentoTaxaParcelaRepository;
    private final UsuarioRepository usuarioRepository;

    /** RN-NOVA-15 — seed eager, chamado por AuthServiceImpl#register (DT-NOVA-6). */
    @Transactional
    public void seedMetodosPadrao(Usuario usuario) {
        int ordem = 1;
        for (TipoMetodoPagamento tipo : TIPOS_FIXOS) {
            metodoPagamentoRepository.save(MetodoPagamentoConfiguravel.builder()
                    .usuario(usuario)
                    .tipo(tipo)
                    .ativo(true)
                    .ordem(ordem++)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<MetodoPagamentoConfiguravelResponseDTO> listar() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return metodoPagamentoRepository.findByUsuarioIdOrderByOrdemAscTipoAsc(usuarioId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** RN-NOVA-16/CEN-NOVO-12 — só cria tipo OUTRO; tentativa de tipo fixo é sempre bloqueada
     * porque o seed já garante que ele existe. */
    @Transactional
    public MetodoPagamentoConfiguravelResponseDTO criar(MetodoPagamentoConfiguravelRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();

        if (request.tipo() != TipoMetodoPagamento.OUTRO) {
            throw new BusinessException("Esse método de pagamento já existe.");
        }
        if (request.nome() == null || request.nome().isBlank()) {
            throw new BusinessException("Informe o nome do método de pagamento.");
        }
        String nome = request.nome().trim();
        if (metodoPagamentoRepository.existsByUsuarioIdAndTipoAndNomeIgnoreCase(
                usuarioId, TipoMetodoPagamento.OUTRO, nome)) {
            throw new BusinessException("Já existe um método de pagamento com esse nome.");
        }

        Usuario usuario = usuarioRepository.findByIdAndDeletedAtIsNull(usuarioId)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));

        MetodoPagamentoConfiguravel metodo = MetodoPagamentoConfiguravel.builder()
                .usuario(usuario)
                .tipo(TipoMetodoPagamento.OUTRO)
                .nome(nome)
                .ativo(true)
                .build();
        return toResponse(metodoPagamentoRepository.save(metodo));
    }

    /** RN-NOVA-17 — taxaMaquininha só aceita para Cartão Crédito/Débito; nome só edita OUTRO. */
    @Transactional
    public MetodoPagamentoConfiguravelResponseDTO atualizar(UUID id, MetodoPagamentoConfiguravelUpdateRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        MetodoPagamentoConfiguravel metodo = metodoPagamentoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Método de pagamento não encontrado"));

        if (request.ativo() != null) {
            metodo.setAtivo(request.ativo());
        }

        if (request.taxaMaquininha() != null) {
            if (!TIPOS_CARTAO.contains(metodo.getTipo())) {
                throw new BusinessException("Taxa da maquininha só se aplica a Cartão Crédito ou Cartão Débito.");
            }
            metodo.setTaxaMaquininha(request.taxaMaquininha());
        }

        if (request.nome() != null) {
            if (metodo.getTipo() != TipoMetodoPagamento.OUTRO) {
                throw new BusinessException("Não é possível alterar o nome de um método fixo.");
            }
            String novoNome = request.nome().trim();
            if (novoNome.isBlank()) {
                throw new BusinessException("Informe o nome do método de pagamento.");
            }
            boolean duplicado = metodoPagamentoRepository.existsByUsuarioIdAndTipoAndNomeIgnoreCase(
                    usuarioId, TipoMetodoPagamento.OUTRO, novoNome)
                    && !novoNome.equalsIgnoreCase(metodo.getNome());
            if (duplicado) {
                throw new BusinessException("Já existe um método de pagamento com esse nome.");
            }
            metodo.setNome(novoNome);
        }

        aplicarParcelamento(metodo, request);

        return toResponse(metodoPagamentoRepository.save(metodo));
    }

    /**
     * #491 (V0.12.0) — parcelamento só existe em Cartão de Crédito. Com taxa uniforme, a taxa
     * vigente continua sendo a `taxaMaquininha`; com taxa variável, exige uma entrada por parcela
     * de 1 até `maxParcelas` (sem buraco), porque a venda precisa saber a taxa de qualquer parcela
     * que a usuária escolher no Caixa.
     */
    private void aplicarParcelamento(MetodoPagamentoConfiguravel metodo,
                                     MetodoPagamentoConfiguravelUpdateRequestDTO request) {
        if (request.maxParcelas() == null && request.taxaParcelaUniforme() == null
                && request.taxasParcela() == null) {
            return;
        }
        if (metodo.getTipo() != TipoMetodoPagamento.CARTAO_CREDITO) {
            throw new BusinessException("Parcelamento só se aplica a Cartão Crédito.");
        }

        if (request.maxParcelas() != null) metodo.setMaxParcelas(request.maxParcelas());
        if (request.taxaParcelaUniforme() != null) metodo.setTaxaParcelaUniforme(request.taxaParcelaUniforme());

        boolean uniforme = !Boolean.FALSE.equals(metodo.getTaxaParcelaUniforme());
        if (uniforme) {
            metodoPagamentoTaxaParcelaRepository.deleteByMetodoPagamentoId(metodo.getId());
            return;
        }

        Integer maxParcelas = metodo.getMaxParcelas();
        if (maxParcelas == null) {
            throw new BusinessException("Informe a parcela máxima antes de definir a taxa por parcela.");
        }
        List<TaxaParcelaRequestDTO> taxas = request.taxasParcela() == null ? List.of() : request.taxasParcela();
        Set<Integer> parcelasInformadas = taxas.stream().map(TaxaParcelaRequestDTO::parcela).collect(Collectors.toSet());
        if (parcelasInformadas.size() != taxas.size()) {
            throw new BusinessException("A mesma parcela foi informada mais de uma vez.");
        }
        for (int parcela = 1; parcela <= maxParcelas; parcela++) {
            if (!parcelasInformadas.contains(parcela)) {
                throw new BusinessException("Informe a taxa da parcela " + parcela + ".");
            }
        }
        if (parcelasInformadas.stream().anyMatch(p -> p > maxParcelas)) {
            throw new BusinessException("Há taxa informada para uma parcela acima da parcela máxima.");
        }

        metodoPagamentoTaxaParcelaRepository.deleteByMetodoPagamentoId(metodo.getId());
        metodoPagamentoTaxaParcelaRepository.flush();
        metodoPagamentoTaxaParcelaRepository.saveAll(taxas.stream()
                .map(t -> MetodoPagamentoTaxaParcela.builder()
                        .metodoPagamentoId(metodo.getId())
                        .parcela(t.parcela())
                        .taxa(t.taxa())
                        .build())
                .toList());
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private MetodoPagamentoConfiguravelResponseDTO toResponse(MetodoPagamentoConfiguravel m) {
        List<TaxaParcelaResponseDTO> taxasParcela = Boolean.FALSE.equals(m.getTaxaParcelaUniforme())
                ? metodoPagamentoTaxaParcelaRepository.findByMetodoPagamentoIdOrderByParcelaAsc(m.getId()).stream()
                        .map(t -> new TaxaParcelaResponseDTO(t.getParcela(), t.getTaxa()))
                        .toList()
                : List.of();

        return new MetodoPagamentoConfiguravelResponseDTO(
                m.getId(), m.getTipo(), m.getNome(), m.isAfetaCaixaFisico(),
                m.getTaxaMaquininha(), m.getAtivo(), m.getOrdem(),
                m.getMaxParcelas(), m.getTaxaParcelaUniforme(), taxasParcela);
    }
}
