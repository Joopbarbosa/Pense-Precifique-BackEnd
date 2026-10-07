package com.penseprecifique.api.infra.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * #802, #808 e #809 — quais métodos o endereço mais específico aceita. O Spring escolhe o mapeamento pelo método do
 * pedido; assim, {@code PUT /compras/confirmar} cai em {@code PUT /compras/{id}} (id = "confirmar") e o
 * {@code OPTIONS} automático soma os métodos de {@code /compras/{id}}. Aqui vale o padrão de caminho mais específico
 * entre todos que casam (literal vence variável), igual ao que o contrato OpenAPI declara.
 */
@Component
public class MapeamentoDeMetodos {

    private final RequestMappingHandlerMapping mapeamento;

    @Autowired
    public MapeamentoDeMetodos(RequestMappingHandlerMapping mapeamento) {
        this.mapeamento = mapeamento;
    }

    /** Métodos do padrão mais específico que casa com o caminho; vazio se nenhum padrão casa. */
    public Set<String> metodosDoCaminhoMaisEspecifico(String uri) {
        PathContainer caminho = PathContainer.parsePath(uri);
        List<Map.Entry<PathPattern, Set<String>>> casados = new ArrayList<>();
        mapeamento.getHandlerMethods().keySet().forEach(info -> {
            var condicao = info.getPathPatternsCondition();
            if (condicao == null) {
                return;
            }
            Set<String> metodos = new TreeSet<>();
            info.getMethodsCondition().getMethods().forEach(m -> metodos.add(m.name()));
            if (metodos.isEmpty()) {
                for (RequestMethod m : RequestMethod.values()) {
                    metodos.add(m.name());
                }
            }
            condicao.getPatterns().stream().filter(p -> p.matches(caminho)).forEach(p -> casados.add(Map.entry(p, metodos)));
        });
        if (casados.isEmpty()) {
            return Set.of();
        }
        PathPattern melhor = casados.stream().map(Map.Entry::getKey).min(PathPattern.SPECIFICITY_COMPARATOR).orElseThrow();
        Set<String> permitidos = new TreeSet<>();
        casados.stream().filter(par -> par.getKey().getPatternString().equals(melhor.getPatternString()))
                .forEach(par -> permitidos.addAll(par.getValue()));
        return permitidos;
    }
}
