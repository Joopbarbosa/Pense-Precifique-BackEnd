package com.penseprecifique.api.financeiro;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * V0.19.0 (#1177, RN-NOVA-23, DT-NOVA-7) — categorias de despesa da usuária. Semeada (padrão) não se renomeia, não se
 * inativa nem se exclui; criada só se inativa (e reativa). Nome único por usuária sem diferenciar maiúsculas, acentos
 * nem espaços nas pontas. Categoria de outra usuária responde exatamente como id inexistente (404).
 */
@Service
@RequiredArgsConstructor
public class CategoriaFinanceiraService {

    static final String MSG_DUPLICADA = "Já existe uma categoria com esse nome.";
    static final String MSG_NAO_ENCONTRADA = "Categoria não encontrada.";

    private final CategoriaFinanceiraRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SementeCategoriasService semente;

    /** Lista as categorias da usuária (semeando as padrão na primeira vez). Inativas só quando pedido. */
    public List<CategoriaFinanceiraResponse> listar(boolean incluirInativas) {
        UUID usuarioId = usuarioAutenticado().getId();
        semente.garantir(usuarioId);
        return (incluirInativas ? repository.findByUsuarioIdOrderBySemeadaDescNomeAsc(usuarioId)
                : repository.findByUsuarioIdAndAtivaTrueOrderBySemeadaDescNomeAsc(usuarioId))
                .stream().map(CategoriaFinanceiraResponse::de).toList();
    }

    @Transactional
    public CategoriaFinanceiraResponse criar(String nome) {
        Usuario usuario = usuarioAutenticado();
        String limpo = NomeCategoria.exigir(nome);
        semente.garantir(usuario.getId());
        exigirNomeLivre(usuario.getId(), limpo, null);
        CategoriaFinanceira c = CategoriaFinanceira.builder().usuario(usuario).nome(limpo)
                .nomeNormalizado(NomeCategoria.normalizar(limpo)).semeada(false).build();
        return CategoriaFinanceiraResponse.de(salvar(c));
    }

    @Transactional
    public CategoriaFinanceiraResponse renomear(UUID id, String nome) {
        Usuario usuario = usuarioAutenticado();
        CategoriaFinanceira c = daUsuaria(id, usuario.getId());
        if (c.isSemeada()) {
            throw new BusinessException("Categoria padrão não pode ser renomeada.");
        }
        String limpo = NomeCategoria.exigir(nome);
        exigirNomeLivre(usuario.getId(), limpo, c.getId());
        c.setNome(limpo);
        c.setNomeNormalizado(NomeCategoria.normalizar(limpo));
        return CategoriaFinanceiraResponse.de(salvar(c));
    }

    @Transactional
    public CategoriaFinanceiraResponse inativar(UUID id) {
        CategoriaFinanceira c = daUsuaria(id, usuarioAutenticado().getId());
        if (c.isSemeada()) {
            throw new BusinessException("Categoria padrão não pode ser inativada.");
        }
        c.setAtiva(false);
        return CategoriaFinanceiraResponse.de(repository.save(c));
    }

    @Transactional
    public CategoriaFinanceiraResponse reativar(UUID id) {
        CategoriaFinanceira c = daUsuaria(id, usuarioAutenticado().getId());
        c.setAtiva(true);
        return CategoriaFinanceiraResponse.de(repository.save(c));
    }

    /** Categoria não se exclui: a padrão é fixa e a criada só se inativa (preserva o histórico dos lançamentos). */
    @Transactional(readOnly = true)
    public void excluir(UUID id) {
        CategoriaFinanceira c = daUsuaria(id, usuarioAutenticado().getId());
        throw new BusinessException(c.isSemeada() ? "Categoria padrão não pode ser excluída."
                : "Categoria não pode ser excluída. Inative-a para parar de usá-la.");
    }

    /**
     * RN-NOVA-23 — categoria do lançamento. Receita não tem categoria. Despesa de compra usa "Insumos e materiais" (fixa) e
     * a avulsa usa "Outros" quando nada é informado; despesa de outra origem exige categoria. Usada só por quem escreve.
     */
    @Transactional
    public CategoriaFinanceira resolverParaLancamento(UUID usuarioId, TipoLancamento tipo, OrigemLancamento origem, UUID categoriaId) {
        if (tipo != TipoLancamento.A_PAGAR) {
            if (categoriaId != null) {
                throw BusinessException.noCampo("categoriaId", "Receita não tem categoria.");
            }
            return null;
        }
        semente.garantir(usuarioId);
        String padrao = origem == OrigemLancamento.COMPRA ? SementeCategoriasService.INSUMOS
                : origem == OrigemLancamento.AVULSO ? SementeCategoriasService.OUTROS : null;
        if (categoriaId == null) {
            if (padrao == null) {
                throw BusinessException.noCampo("categoriaId", "Informe a categoria da despesa.");
            }
            return repository.findByUsuarioIdAndNomeNormalizado(usuarioId, NomeCategoria.normalizar(padrao)).orElseThrow();
        }
        CategoriaFinanceira c = daUsuaria(categoriaId, usuarioId);
        if (origem == OrigemLancamento.COMPRA && !c.getNomeNormalizado().equals(NomeCategoria.normalizar(SementeCategoriasService.INSUMOS))) {
            throw BusinessException.noCampo("categoriaId", "Despesa de compra usa a categoria Insumos e materiais.");
        }
        if (!c.isAtiva()) {
            throw BusinessException.noCampo("categoriaId", "Esta categoria está inativa. Escolha uma categoria ativa.");
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------ internos

    private CategoriaFinanceira daUsuaria(UUID id, UUID usuarioId) {
        if (id == null) {
            throw new ResourceNotFoundException(MSG_NAO_ENCONTRADA);
        }
        return repository.findByIdAndUsuarioId(id, usuarioId).orElseThrow(() -> new ResourceNotFoundException(MSG_NAO_ENCONTRADA));
    }

    private void exigirNomeLivre(UUID usuarioId, String nome, UUID ignorarId) {
        repository.findByUsuarioIdAndNomeNormalizado(usuarioId, NomeCategoria.normalizar(nome)).ifPresent(existente -> {
            if (!existente.getId().equals(ignorarId)) {
                throw BusinessException.noCampo("nome", MSG_DUPLICADA);
            }
        });
    }

    private CategoriaFinanceira salvar(CategoriaFinanceira c) {
        try {
            return repository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.noCampo("nome", MSG_DUPLICADA);
        }
    }

    private Usuario usuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
