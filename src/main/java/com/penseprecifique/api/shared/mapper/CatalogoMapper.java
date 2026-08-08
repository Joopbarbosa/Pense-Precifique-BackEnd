package com.penseprecifique.api.shared.mapper;

import com.penseprecifique.api.shared.domain.entity.Catalogo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.dto.request.catalogo.CatalogoRequest;
import com.penseprecifique.api.shared.dto.response.catalogo.CatalogoResponse;
import com.penseprecifique.api.util.IdentificadorFormatter;
import org.springframework.stereotype.Component;

@Component
public class CatalogoMapper {

    public CatalogoResponse toResponse(Catalogo catalogo, int quantidadeItens) {
        CatalogoResponse response = new CatalogoResponse();
        response.setId(catalogo.getId());
        response.setNumero(catalogo.getNumero());
        response.setIdentificador(IdentificadorFormatter.formatar("CTG", catalogo.getNumero()));
        response.setNome(catalogo.getNome());
        response.setAtivo(catalogo.getAtivo());
        response.setQuantidadeItens(quantidadeItens);
        return response;
    }

    public Catalogo toEntity(CatalogoRequest request, Usuario usuario) {
        return Catalogo.builder()
                .usuario(usuario)
                .nome(request.getNome())
                .ativo(true)
                .build();
    }

    public void updateEntity(CatalogoRequest request, Catalogo catalogo) {
        catalogo.setNome(request.getNome());
        // numero e ativo não mudam por essa via (numero é gerado no Service via RN-053, ativo tem endpoint próprio)
    }
}
