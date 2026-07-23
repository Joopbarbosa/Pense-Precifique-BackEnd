package com.penseprecifique.api.auth;

import com.penseprecifique.api.shared.domain.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmail(String email);

    Optional<Usuario> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * #161 — lock pessimista no registro do usuário, usado como ponto de serialização por usuario_id
     * antes de ler o MAX(numero) em Producao/Orcamento/Catalogo/Cliente/Produto/Insumo. Travar a linha
     * do usuário (em vez da linha de maior numero da entidade) é o que realmente serializa: a segunda
     * transação só consegue ler o MAX(numero) depois que a primeira commitou seu INSERT, porque ambas
     * disputam o mesmo lock antes de ler — travar a linha de maior numero não protegeria, já que essa
     * linha não é alterada pelo INSERT da nova (a segunda transação action reutilizaria o mesmo valor lido
     * antes do lock, sem enxergar a nova linha commitada).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> lockPorId(@Param("id") UUID id);
}
