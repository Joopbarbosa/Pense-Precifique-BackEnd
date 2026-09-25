package com.penseprecifique.api.shared.validation;

import com.penseprecifique.api.shared.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * DT-NOVA-5 (V0.14.0) — validação de formato/tamanho de upload de imagem, promovida a
 * {@code shared/} a partir de {@code ItemCatalogoService#validarArquivoFoto} (V0.13.0/#518,
 * único consumidor até então). Com Produto (#531) e Empresa (#532) precisando da mesma regra,
 * 3 módulos passam a reaproveitar esta classe única em vez de duplicar a checagem — mesma ordem
 * de validação do UC-NOVO-1 original (formato antes de tamanho).
 */
@Component
public class ValidadorArquivoImagem {

    private static final Set<String> FORMATOS_ACEITOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;

    public void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessException("Selecione um arquivo de imagem.");
        }
        if (!FORMATOS_ACEITOS.contains(arquivo.getContentType())) {
            throw new BusinessException("Só são aceitos arquivos JPG ou PNG.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new BusinessException("Arquivo muito grande. O tamanho máximo permitido é 5MB.");
        }
    }

    public String extensaoPara(MultipartFile arquivo) {
        return "image/png".equals(arquivo.getContentType()) ? "png" : "jpg";
    }
}
