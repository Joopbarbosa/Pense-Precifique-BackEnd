package com.penseprecifique.api.agenda;

import com.penseprecifique.api.shared.domain.entity.CompromissoUsuaria;
import com.penseprecifique.api.shared.domain.entity.CompromissoUsuariaExcecao;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.18.0 (#1039, DT-NOVA-1 e DT-NOVA-5) — compromissos criados pela usuária. As ocorrências são geradas por cálculo a partir
 * do mestre da série, sem limite de horizonte; as exceções (concluída, cancelada, movida ou editada só nela) vêm da tabela e
 * entram pela data em que a ocorrência está, mesmo que a data original fique fora do período (a movida aparece só na nova data).
 * A concluída e a cancelada continuam no dia, riscadas (RN-NOVA-5); a pendente com data passada é atrasada (RN-NOVA-4).
 */
@Component
@RequiredArgsConstructor
public class CompromissosUsuaria implements FonteCompromissos {

    private final CompromissoUsuariaRepository compromissoRepository;
    private final CompromissoUsuariaExcecaoRepository excecaoRepository;
    private final OcorrenciasCalculadora calculadora;

    @Override
    public List<CompromissoBase> listar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<CompromissoUsuaria> mestres = compromissoRepository.findDaContaComVinculos(usuarioId);
        if (mestres.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CompromissoUsuariaExcecao>> excecoes = excecaoRepository
                .findDosCompromissosComVinculos(mestres.stream().map(CompromissoUsuaria::getId).toList()).stream()
                .collect(Collectors.groupingBy(e -> e.getCompromisso().getId()));
        // as originais anteriores a hoje também interessam: se estão pendentes, são atrasadas
        LocalDate limite = ate.isAfter(hoje.minusDays(1)) ? ate : hoje.minusDays(1);
        List<CompromissoBase> resultado = new ArrayList<>();
        for (CompromissoUsuaria m : mestres) {
            Map<LocalDate, CompromissoUsuariaExcecao> porData = excecoes.getOrDefault(m.getId(), List.of()).stream()
                    .collect(Collectors.toMap(CompromissoUsuariaExcecao::getDataOriginal, e -> e));
            Set<LocalDate> originais = new HashSet<>(calculadora.datas(m, m.getDataInicio(), limite));
            originais.addAll(porData.keySet());
            for (LocalDate original : originais) {
                OcorrenciaResolvida o = OcorrenciaResolvida.de(m, original, porData.get(original));
                CompromissoBase base = paraBase(o);
                if (relevante(base, de, ate, hoje)) {
                    resultado.add(base);
                }
            }
        }
        return resultado;
    }

    static CompromissoBase paraBase(OcorrenciaResolvida o) {
        CompromissoUsuaria m = o.mestre();
        return new CompromissoBase(TipoCompromisso.AVULSO.name() + ":" + m.getId() + ":" + o.dataOriginal(), TipoCompromisso.AVULSO,
                o.situacao(), o.titulo(), o.data(), o.horario(), List.of(), List.of(), m.getId(), null, 0, null,
                m.getCreatedAt() == null ? null : m.getCreatedAt().toInstant(ZoneOffset.UTC), o.dataOriginal(), false, o.horarioFim());
    }
}
