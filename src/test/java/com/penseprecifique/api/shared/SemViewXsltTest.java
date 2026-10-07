package com.penseprecifique.api.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * #756 (V0.16.0) — contenção do GHSA-pc63-qcmh-9cmg (spring-webmvc até 6.2.19, XsltView com mapeamento "/**").
 * A linha 6.2 não tem versão corrigida (a correção só existe no Spring Framework 7.0.9). A API só devolve JSON
 * ({@code @RestController}), sem renderização de view. Este teste trava isso: se alguém introduzir um
 * {@code @Controller} que renderiza view, ou usar {@code XsltView}/{@code XsltViewResolver}, a exposição deixa de
 * ser teórica e o teste falha, obrigando a rever a correção antes de seguir.
 */
class SemViewXsltTest {

    private static final String PACOTE = "com.penseprecifique.api";

    @Test
    void todoControllerEhRestController() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        scanner.addExcludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> comView = scanner.findCandidateComponents(PACOTE).stream().map(BeanDefinition::getBeanClassName).toList();
        assertEquals(List.of(), comView, "@Controller que não é @RestController pode renderizar view (GHSA-pc63-qcmh-9cmg)");
        ClassPathScanningCandidateComponentProvider rest = new ClassPathScanningCandidateComponentProvider(false);
        rest.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        assertFalse(rest.findCandidateComponents(PACOTE).isEmpty(), "o scanner deveria achar os @RestController (teste sem efeito)");
    }

    @Test
    void codigoDaAplicacaoNaoUsaXsltView() throws IOException {
        Path classes = Paths.get("target", "classes");
        assertTrue(Files.isDirectory(classes), "target/classes ausente: o teste exige o código compilado");
        List<Path> usam;
        try (Stream<Path> arquivos = Files.walk(classes)) {
            usam = arquivos.filter(p -> p.toString().endsWith(".class")).filter(SemViewXsltTest::citaXslt).toList();
        }
        assertTrue(usam.isEmpty(), "classes da aplicação que citam XsltView/XsltViewResolver: " + usam);
    }

    private static boolean citaXslt(Path classe) {
        try {
            String texto = new String(Files.readAllBytes(classe), StandardCharsets.ISO_8859_1);
            return texto.contains("view/xslt/XsltView");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
