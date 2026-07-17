package com.penseprecifique.api.empresa;

import com.penseprecifique.api.shared.domain.entity.ConfiguracaoPrecificacao;
import com.penseprecifique.api.shared.dto.request.ConfiguracaoRequestDTO;
import com.penseprecifique.api.shared.dto.response.ConfiguracaoResponseDTO;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConfiguracaoServiceImpl implements ConfiguracaoService {

    private final ConfiguracaoPrecificacaoRepository configuracaoRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    public ConfiguracaoResponseDTO getConfiguracao() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return configuracaoRepository.findByUsuarioId(usuarioId)
                .map(this::toResponse)
                .orElse(new ConfiguracaoResponseDTO(null, BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Override
    public ConfiguracaoResponseDTO upsertConfiguracao(ConfiguracaoRequestDTO request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        ConfiguracaoPrecificacao configuracao = configuracaoRepository.findByUsuarioId(usuarioId)
                .orElse(ConfiguracaoPrecificacao.builder().usuarioId(usuarioId).build());

        configuracao.setValorHora(request.valorHora());
        configuracao.setMargemPadrao(request.margemPadrao());

        return toResponse(configuracaoRepository.save(configuracao));
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
