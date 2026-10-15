package com.penseprecifique.api.produto;

import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Produto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

/**
 * #672/RN-NOVA-10, DT-NOVA-6 (V0.17.0) — grava a mudança do preço sugerido de um produto, síncrona e na mesma transação do
 * evento (se o evento falha, a linha não existe). Só grava quando o valor em centavos muda. Nunca lê nem altera o preço de
 * venda. Quem calcula antes/depois é o chamador, com a mesma conta do detalhe do produto (custo ao vivo × margem).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PrecoSugeridoHistoricoService {

    private final MudancaPrecoSugeridoRepository mudancaRepository;
    private final MudancaPrecoSugeridoInsumoRepository insumoRepository;
    private final InsumoRepository insumos;

    /** @return true se gravou (o preço sugerido mudou em centavos). */
    public boolean registrarSeMudou(UUID usuarioId, Produto produto, BigDecimal antes, BigDecimal depois,
                                    CausaPrecoSugerido causa, UUID referenciaId, String referenciaTexto,
                                    Collection<UUID> insumosOrigem) {
        if (antes == null || depois == null || antes.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(depois.setScale(2, java.math.RoundingMode.HALF_UP)) == 0) {
            return false;
        }
        MudancaPrecoSugerido mudanca = mudancaRepository.save(MudancaPrecoSugerido.builder()
                .usuarioId(usuarioId).produto(produto)
                .valorAnterior(antes.setScale(2, java.math.RoundingMode.HALF_UP))
                .valorNovo(depois.setScale(2, java.math.RoundingMode.HALF_UP))
                .causa(causa).referenciaId(referenciaId).referenciaTexto(referenciaTexto).build());
        if (insumosOrigem != null) {
            for (UUID insumoId : insumosOrigem) {
                insumoRepository.save(MudancaPrecoSugeridoInsumo.builder()
                        .mudanca(mudanca).insumo(insumos.getReferenceById(insumoId)).build());
            }
        }
        return true;
    }
}
