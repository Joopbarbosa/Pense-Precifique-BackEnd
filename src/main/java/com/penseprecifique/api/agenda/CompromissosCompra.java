package com.penseprecifique.api.agenda;

import com.penseprecifique.api.compra.CompraItemRepository;
import com.penseprecifique.api.compra.CompraRepository;
import com.penseprecifique.api.shared.domain.entity.Compra;
import com.penseprecifique.api.shared.domain.entity.CompraItem;
import com.penseprecifique.api.shared.domain.enums.SituacaoCompromisso;
import com.penseprecifique.api.shared.domain.enums.StatusCompra;
import com.penseprecifique.api.shared.domain.enums.TipoCompromisso;
import com.penseprecifique.api.util.IdentificadorFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.18.0 (#549, DT-NOVA-1) — compromisso da compra pendente de recebimento (RN-NOVA-18): "Entrega de compra — COM-N
 * (fornecedor)" no dia da previsão de entrega. Sai ao dar entrada, cancelar ou excluir a compra; atrasado, aparece em
 * hoje com o texto "Atrasada há N dias" (COM-045).
 */
@Component
@RequiredArgsConstructor
public class CompromissosCompra implements FonteCompromissos {

    private final CompraRepository compraRepository;
    private final CompraItemRepository compraItemRepository;

    @Override
    public List<CompromissoBase> listar(UUID usuarioId, LocalDate de, LocalDate ate, LocalDate hoje) {
        List<Compra> pendentes = new ArrayList<>(compraRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, StatusCompra.PENDENTE_RECEBIMENTO));
        // Concluído não some: a compra que estava a receber e já deu entrada continua no dia como concluída.
        compraRepository.findByUsuarioIdAndStatusAndDeletedAtIsNull(usuarioId, StatusCompra.CONFIRMADA).stream()
                .filter(c -> c.getPrevisaoEntrega() != null).forEach(pendentes::add);
        if (pendentes.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<CompraItem>> linhas = compraItemRepository.findByCompraIdIn(pendentes.stream().map(Compra::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getCompra().getId()));
        List<CompromissoBase> resultado = new ArrayList<>();
        for (Compra c : pendentes) {
            if (c.getPrevisaoEntrega() == null) {
                continue;
            }
            String fornecedores = nomesDosFornecedores(c, linhas.getOrDefault(c.getId(), List.of()));
            String identificador = IdentificadorFormatter.formatar("COM", c.getNumero());
            CompromissoBase base = new CompromissoBase(TipoCompromisso.RECEBIMENTO_COMPRA.name() + ":" + c.getId(),
                    TipoCompromisso.RECEBIMENTO_COMPRA,
                    c.getStatus() == StatusCompra.CONFIRMADA ? SituacaoCompromisso.CONCLUIDO : SituacaoCompromisso.PENDENTE,
                    "Entrega de compra — " + identificador + (fornecedores.isEmpty() ? "" : " (" + fornecedores + ")"),
                    c.getPrevisaoEntrega(), null, List.of(), List.of(), c.getId(), identificador,
                    c.getNumero() == null ? 0 : c.getNumero(), null,
                    c.getCreatedAt() == null ? null : c.getCreatedAt().toInstant(ZoneOffset.UTC), null, true);
            if (relevante(base, de, ate, hoje)) {
                resultado.add(base);
            }
        }
        return resultado;
    }

    /** Mesma regra da listagem de compras: fornecedores das linhas (ou o do cabeçalho), distintos e em ordem alfabética. */
    private static String nomesDosFornecedores(Compra compra, List<CompraItem> itens) {
        TreeSet<String> nomes = new TreeSet<>();
        itens.stream().map(CompraItem::getFornecedor).filter(Objects::nonNull).forEach(f -> nomes.add(f.getNome()));
        if (nomes.isEmpty() && compra.getFornecedor() != null) {
            nomes.add(compra.getFornecedor().getNome());
        }
        return String.join(", ", nomes);
    }
}
