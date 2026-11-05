package com.penseprecifique.api.infra;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PP-528 (RN-NOVA-37, DT-NOVA-8): a V96 apaga os orçamentos antigos e dependentes, remove só os vínculos de
 * {@code orcamento_producoes} (as produções ficam) e troca as colunas do pagamento. Roda em um esquema próprio
 * ({@code mig528}), levado até a V91 (a anterior à V96 neste ramo), populado e então migrado: nunca toca nos dados do esquema {@code public}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MigracaoV96ExcluiOrcamentosAntigosIT {

    private static final String ESQUEMA = "mig528";

    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String usuarioBanco;
    @Value("${spring.datasource.password}") String senhaBanco;

    private Flyway flyway(String alvo) {
        return Flyway.configure().dataSource(url, usuarioBanco, senhaBanco).schemas(ESQUEMA).createSchemas(true)
                .locations("classpath:db/migration").target(alvo).load();
    }

    private void apagarEsquema() throws Exception {
        try (Connection c = DriverManager.getConnection(url, usuarioBanco, senhaBanco); Statement s = c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
        }
    }

    @BeforeEach @AfterEach
    void limpar() throws Exception { apagarEsquema(); }

    private long contar(Statement s, String tabela) throws Exception {
        try (ResultSet r = s.executeQuery("SELECT count(*) FROM " + ESQUEMA + "." + tabela)) { r.next(); return r.getLong(1); }
    }

    @Test
    void apagaOrcamentosAntigosMantemProducoesETrocaColunasDePagamento() throws Exception {
        flyway("91").migrate();
        try (Connection c = DriverManager.getConnection(url, usuarioBanco, senhaBanco); Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + ESQUEMA);
            s.execute("INSERT INTO usuarios (id, email, senha_hash) VALUES ('00000000-0000-0000-0000-000000000001','mig528@x.com','h')");
            s.execute("INSERT INTO clientes (id, usuario_id, nome, numero) VALUES ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Maria',1)");
            s.execute("INSERT INTO orcamentos (id, usuario_id, cliente_id, metodo_pagamento) VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002','PIX')");
            s.execute("INSERT INTO producoes (id, usuario_id) VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000001')");
            s.execute("INSERT INTO orcamento_producoes (orcamento_id, producao_id) VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004')");
            s.execute("INSERT INTO produtos (id, usuario_id, nome, tipo, tempo_producao, numero, preco_venda) VALUES ('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000001','Caderno','PRODUTO',10,1,10)");
            s.execute("INSERT INTO orcamento_itens (id, orcamento_id, produto_id, preco_unitario, subtotal) VALUES ('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000006',10,10)");
            assertEquals(1, contar(s, "orcamentos"));
        }

        flyway("latest").migrate();

        try (Connection c = DriverManager.getConnection(url, usuarioBanco, senhaBanco); Statement s = c.createStatement()) {
            s.execute("SET search_path TO " + ESQUEMA);
            assertEquals(0, contar(s, "orcamentos"));
            assertEquals(0, contar(s, "orcamento_itens"));
            assertEquals(0, contar(s, "orcamento_producoes"));
            assertEquals(1, contar(s, "producoes"), "produções nunca são apagadas");
            assertEquals(1, contar(s, "clientes"));
            assertEquals(1, contar(s, "produtos"));
            try (ResultSet r = s.executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='" + ESQUEMA
                    + "' AND table_name='orcamentos' AND column_name IN ('metodo_pagamento','metodo_pagamento_obs','metodo_sinal_recebido','metodo_sinal_recebido_obs')")) {
                assertFalse(r.next(), "colunas do enum antigo devem ter sido removidas");
            }
            try (ResultSet r = s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='" + ESQUEMA
                    + "' AND table_name='orcamentos' AND column_name IN ('metodo_pagamento_id','metodo_sinal_recebido_id')")) {
                r.next();
                assertEquals(2, r.getLong(1));
            }
        }
    }
}
