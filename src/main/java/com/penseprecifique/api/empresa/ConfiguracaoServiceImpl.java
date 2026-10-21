package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import com.penseprecifique.api.shared.dto.request.config.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.config.ConfiguracaoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.produto.CausaPrecoSugerido;
import com.penseprecifique.api.produto.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfiguracaoServiceImpl implements ConfiguracaoService {

    private final ConfiguracaoPrecificacaoRepository configuracaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProdutoService produtoService;

    @Override
    public ConfiguracaoResponseDTO getConfiguracao() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return configuracaoRepository.findByUsuarioId(usuarioId)
                .map(this::toResponse)
                .orElse(new ConfiguracaoResponseDTO(null, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Override
    @Transactional
    public ConfiguracaoResponseDTO upsertConfiguracao(ConfiguracaoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        ConfiguracaoPrecificacao configuracao = configuracaoRepository.findByUsuarioId(usuarioId)
                .orElse(ConfiguracaoPrecificacao.builder().usuarioId(usuarioId).build());

        BigDecimal valorHoraAntigo = configuracao.getValorHora();
        boolean valorHoraMudou = request.valorHora() != null
                && (valorHoraAntigo == null || valorHoraAntigo.compareTo(request.valorHora()) != 0);
        // #672/DT-NOVA-6 — preço sugerido de antes (ao vivo, com o valor da hora anterior) de todos os produtos da conta
        java.util.Map<UUID, BigDecimal> precoSugeridoAntes = valorHoraMudou
                ? produtoService.capturarPrecosSugeridos(produtoService.idsDosProdutosDaConta(usuarioId)) : java.util.Map.of();
        configuracao.setValorHora(request.valorHora());
        configuracao.setMargemPadrao(request.margemPadrao());

        try {
            ConfiguracaoResponseDTO salvo = toResponse(configuracaoRepository.saveAndFlush(configuracao));
            // #580/RN-NOVA-31 (V0.15.0) — mão de obra mudou: custo gravado dos produtos acompanha.
            if (valorHoraMudou) {
                produtoService.recalcularCustoDeTodos(usuarioId);
                produtoService.registrarMudancasPrecoSugerido(usuarioId, precoSugeridoAntes, CausaPrecoSugerido.VALOR_HORA,
                        null, null, java.util.Map.of());
            }
            return salvo;
        } catch (DataIntegrityViolationException e) {
            // #142 — mesma race condition documentada em EmpresaServiceImpl.upsertEmpresa.
            throw new BusinessException("Este usuário já possui uma configuração de precificação cadastrada.");
        }
    }

    private UUID getUsuarioIdAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"))
                .getId();
    }

    private ConfiguracaoResponseDTO toResponse(ConfiguracaoPrecificacao c) {
        return new ConfiguracaoResponseDTO(c.getId(), c.getValorHora(), c.getMargemPadrao(), c.getUpdatedAt());
    }
}
