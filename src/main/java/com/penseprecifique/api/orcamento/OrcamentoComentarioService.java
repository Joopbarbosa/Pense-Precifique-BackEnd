package com.penseprecifique.api.orcamento;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoComentario;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.orcamento.ComentarioOrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.ComentarioOrcamentoResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.validation.LimitesTexto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * V0.18.0 (#1038, DT-NOVA-6, RN-NOVA-22) — conversa única de comentários por orçamento, usada pelo Detalhe do Orçamento e
 * pela Agenda. Comentar vale em qualquer status; não há edição nem exclusão; duplicar o orçamento não copia a conversa.
 */
@Service
@RequiredArgsConstructor
public class OrcamentoComentarioService {

    static final int TAMANHO_PAGINA_PADRAO = 20;
    static final int TAMANHO_PAGINA_MAXIMO = 100;

    private final OrcamentoRepository orcamentoRepository;
    private final OrcamentoComentarioRepository comentarioRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * A página 0 traz os mais recentes; dentro de cada página os comentários saem em ordem cronológica, com o mais recente
     * no fim (RN-086 + RN-NOVA-22).
     */
    @Transactional(readOnly = true)
    public Page<ComentarioOrcamentoResponse> listar(UUID orcamentoId, Pageable pageable) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = buscarOrcamento(orcamentoId, usuario);
        int tamanho = Math.min(Math.max(pageable.getPageSize(), 1), TAMANHO_PAGINA_MAXIMO);
        int pagina = Math.max(pageable.getPageNumber(), 0);
        Page<OrcamentoComentario> mais = comentarioRepository.findByOrcamentoId(orcamento.getId(),
                PageRequest.of(pagina, tamanho, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<ComentarioOrcamentoResponse> conteudo = new ArrayList<>(mais.getContent().stream()
                .map(c -> new ComentarioOrcamentoResponse(c.getId(), c.getTexto(), c.getCreatedAt())).toList());
        Collections.reverse(conteudo);
        return new PageImpl<>(conteudo, mais.getPageable(), mais.getTotalElements());
    }

    @Transactional
    public ComentarioOrcamentoResponse comentar(UUID orcamentoId, ComentarioOrcamentoRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        Orcamento orcamento = buscarOrcamento(orcamentoId, usuario);
        String texto = request.texto() == null ? "" : request.texto().strip();
        if (texto.isEmpty()) {
            throw BusinessException.noCampo("texto", "Escreva o comentário");
        }
        if (texto.length() > LimitesTexto.DESCRICAO_MAX) {
            throw BusinessException.noCampo("texto", LimitesTexto.DESCRICAO_MAX_MENSAGEM);
        }
        OrcamentoComentario salvo = comentarioRepository.save(OrcamentoComentario.builder()
                .orcamento(orcamento).usuario(usuario).texto(texto).build());
        return new ComentarioOrcamentoResponse(salvo.getId(), salvo.getTexto(), salvo.getCreatedAt());
    }

    /** Orçamento de outra conta ou excluído: não encontrado, sem revelar a existência (RN-NOVA-6). */
    private Orcamento buscarOrcamento(UUID orcamentoId, Usuario usuario) {
        return orcamentoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(orcamentoId, usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
