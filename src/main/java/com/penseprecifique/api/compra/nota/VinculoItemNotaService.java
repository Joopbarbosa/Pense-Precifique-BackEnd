package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import com.penseprecifique.api.shared.dto.leitorfiscal.NotaLida;
import com.penseprecifique.api.shared.dto.request.compra.NotaRascunhoRequest.Escolha;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.OrigemLigacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * V0.16.0 (#681, RN-NOVA-15, DT-NOVA-11) — cada confirmação da conciliação cria ou atualiza o vínculo do
 * item (usuária + CNPJ do emitente + nome normalizado): insumo e fator, ou "ignorar". Vínculo salvo aceito
 * sem mudança fica como está; trocado à mão vira MANUAL. Nota sem CNPJ do emitente não grava vínculo.
 */
@Service
@RequiredArgsConstructor
public class VinculoItemNotaService {

    private final VinculoItemNotaRepository repository;

    @Transactional
    public void gravar(Usuario usuario, NotaLida nota, String cnpjEmitente, List<Escolha> escolhas, Map<UUID, Insumo> insumos) {
        if (cnpjEmitente == null) {
            return;
        }
        List<NotaLida.Item> itens = nota.itensOuVazio();
        String emitenteNome = nota.emitente() != null ? truncar(nota.emitente().nome()) : null;
        for (Escolha escolha : escolhas) {
            NotaLida.Item item = itens.get(escolha.posicao());
            String chave = truncar(CasamentoPorNome.normalizarChave(item.nome()));
            VinculoItemNota vinculo = repository.findByUsuarioIdAndEmitenteCnpjAndNomeItemNormalizado(usuario.getId(), cnpjEmitente, chave)
                    .orElse(null);
            Insumo insumo = escolha.ignorado() ? null : insumos.get(escolha.insumoId());
            if (vinculo != null && escolha.origem() == OrigemLigacao.VINCULO_SALVO && igual(vinculo, escolha, insumo)) {
                continue;
            }
            if (vinculo == null) {
                vinculo = VinculoItemNota.builder().usuario(usuario).emitenteCnpj(cnpjEmitente).nomeItemNormalizado(chave).build();
            }
            vinculo.setEmitenteNome(emitenteNome);
            vinculo.setNomeItem(truncar(item.nome().trim()));
            vinculo.setIgnorar(escolha.ignorado());
            vinculo.setInsumo(insumo);
            vinculo.setFator(escolha.ignorado() ? null : escolha.fator());
            vinculo.setOrigem(origem(escolha));
            repository.save(vinculo);
        }
    }

    private static boolean igual(VinculoItemNota vinculo, Escolha escolha, Insumo insumo) {
        if (escolha.ignorado() || Boolean.TRUE.equals(vinculo.getIgnorar())) {
            return escolha.ignorado() && Boolean.TRUE.equals(vinculo.getIgnorar());
        }
        return vinculo.getInsumo() != null && insumo != null && Objects.equals(vinculo.getInsumo().getId(), insumo.getId())
                && vinculo.getFator() != null && escolha.fator() != null && vinculo.getFator().compareTo(escolha.fator()) == 0;
    }

    private static OrigemVinculoItemNota origem(Escolha escolha) {
        if (escolha.ignorado() || escolha.origem() == null) {
            return OrigemVinculoItemNota.MANUAL;
        }
        return switch (escolha.origem()) {
            case CASAMENTO_NOME -> OrigemVinculoItemNota.CASAMENTO_NOME;
            case SUGESTAO_IA -> OrigemVinculoItemNota.SUGESTAO_IA;
            case VINCULO_SALVO, SEM_LIGACAO -> OrigemVinculoItemNota.MANUAL;
        };
    }

    private static String truncar(String texto) {
        return texto == null ? null : texto.length() <= 500 ? texto : texto.substring(0, 500);
    }
}
