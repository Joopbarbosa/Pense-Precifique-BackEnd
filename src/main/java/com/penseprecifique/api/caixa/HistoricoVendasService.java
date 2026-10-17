package com.penseprecifique.api.caixa;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.dashboard.VendasService;
import com.penseprecifique.api.shared.domain.entity.VendaCaixaPagamento;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.mapper.CompraMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V0.17.0 #973 (RN-NOVA-26, UC-NOVO-6, CEN-NOVO-76 a 78) — histórico de todas as vendas do Caixa (concluídas e canceladas), de todos os
 * turnos. Filtros: período, status, forma de pagamento e busca (identificador ou nome do cliente). Ordenação por coluna, com desempate
 * pelo número da venda do maior para o menor em qualquer coluna e sentido. Paginado em 20 por padrão.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoricoVendasService {

    static final String CONSUMIDOR_FINAL = "Consumidor final";
    static final String NAO_INFORMADA = "Não informada";
    static final int TAMANHO_MAXIMO = 100;
    private static final LocalDate INICIO_HISTORICO = LocalDate.of(2000, 1, 1);
    private static final LocalDate FIM_HISTORICO = LocalDate.of(2999, 12, 31);

    private final VendasService vendasService;
    private final VendaCaixaPagamentoRepository pagamentosRepository;
    private final UsuarioRepository usuarioRepository;

    public Page<HistoricoVendaResponse> listar(LocalDate de, LocalDate ate, Collection<String> status, String forma, String busca,
                                               String sort, int page, int size) {
        if (de != null && ate != null && de.isAfter(ate)) {
            throw new BusinessException("A data inicial não pode ser maior que a final");
        }
        if (page < 0) {
            throw new BusinessException("Página inválida.");
        }
        if (size < 1 || size > TAMANHO_MAXIMO) {
            throw new BusinessException("Use uma página de 1 a 100 vendas.");
        }
        Ordenacao ordenacao = Ordenacao.de(sort);
        UUID usuarioId = usuarioId();

        List<VendasService.LinhaLista> caixa = vendasService.todas(usuarioId, de != null ? de : INICIO_HISTORICO,
                        ate != null ? ate : FIM_HISTORICO).stream()
                .filter(l -> l.origem() == com.penseprecifique.api.shared.domain.enums.TipoVendaCmv.VENDA_CAIXA)
                .toList();
        Map<UUID, List<String>> formasPorVenda = formasPorVenda(caixa.stream().map(VendasService.LinhaLista::id).toList());

        String termo = normalizar(busca);
        List<HistoricoVendaResponse> filtradas = caixa.stream()
                .filter(l -> status == null || status.isEmpty() || status.contains(l.status()))
                .map(l -> linha(l, formasPorVenda.getOrDefault(l.id(), List.of(NAO_INFORMADA))))
                .filter(l -> forma == null || forma.isBlank() || l.formas().contains(forma))
                .filter(l -> termo.isEmpty() || normalizar(l.identificador()).contains(termo)
                        || (termo.chars().allMatch(Character::isDigit) && l.numero() == Long.parseLong(termo))
                        || (!CONSUMIDOR_FINAL.equals(l.cliente()) && normalizar(l.cliente()).contains(termo)))
                .sorted(ordenacao.comparador())
                .toList();

        int inicio = (int) Math.min((long) page * size, filtradas.size());
        int fim = Math.min(inicio + size, filtradas.size());
        return new PageImpl<>(new ArrayList<>(filtradas.subList(inicio, fim)), PageRequest.of(page, size), filtradas.size());
    }

    private HistoricoVendaResponse linha(VendasService.LinhaLista l, List<String> formas) {
        return new HistoricoVendaResponse(l.id(), l.identificador(), numeroDe(l.identificador()), l.data(),
                l.cliente() == null || l.cliente().isBlank() ? CONSUMIDOR_FINAL : l.cliente(), l.itens(), formas, l.total(), l.status());
    }

    private Map<UUID, List<String>> formasPorVenda(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return pagamentosRepository.findByVendaCaixaIdIn(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getVendaCaixa().getId(),
                        Collectors.mapping(HistoricoVendasService::formaDe, Collectors.toList())));
    }

    private static String formaDe(VendaCaixaPagamento p) {
        return p.getMetodoPagamento() == null ? NAO_INFORMADA : CompraMapper.rotulo(p.getMetodoPagamento());
    }

    static long numeroDe(String identificador) {
        String digitos = identificador == null ? "" : identificador.replaceAll("\\D", "");
        return digitos.isEmpty() ? 0L : Long.parseLong(digitos.length() > 18 ? digitos.substring(0, 18) : digitos);
    }

    /** Sem acento e sem diferença de maiúsculas; usado no texto de busca e nas colunas de texto. */
    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private UUID usuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado")).getId();
    }

    /** Campo e sentido da ordenação pedida. Campo fora da lista é BLOQUEIO. Sem pedido, data mais recente primeiro. */
    private record Ordenacao(String campo, boolean desc) {

        static Ordenacao de(String sort) {
            if (sort == null || sort.isBlank()) {
                return new Ordenacao("data", true);
            }
            String[] partes = sort.split(",");
            String campo = partes[0].trim();
            if (!List.of("numero", "identificador", "data", "cliente", "total", "status").contains(campo)) {
                throw new BusinessException("Campo de ordenação inválido.");
            }
            boolean desc = partes.length > 1 && "desc".equalsIgnoreCase(partes[1].trim());
            return new Ordenacao(campo.equals("identificador") ? "numero" : campo, desc);
        }

        Comparator<HistoricoVendaResponse> comparador() {
            Comparator<HistoricoVendaResponse> criterio = switch (campo) {
                case "numero" -> Comparator.comparingLong(HistoricoVendaResponse::numero);
                case "data" -> Comparator.comparing(HistoricoVendaResponse::data);
                case "cliente" -> Comparator.comparing(h -> normalizar(h.cliente()));
                case "total" -> Comparator.comparing(HistoricoVendaResponse::total);
                default -> Comparator.comparingInt(h -> "CONCLUIDA".equals(h.status()) ? 0 : 1);
            };
            if (desc) {
                criterio = criterio.reversed();
            }
            // Desempate: número da venda do maior para o menor, em qualquer coluna e sentido
            return criterio.thenComparing(Comparator.comparingLong(HistoricoVendaResponse::numero).reversed());
        }
    }
}
