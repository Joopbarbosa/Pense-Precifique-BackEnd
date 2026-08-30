-- Massa de dados para ambiente Dev/Demo — avulso, fora do pocket V0.8.2.
-- Conta alvo: penseprecifique@admin.com (usuario_id fixo abaixo, confirmado via SELECT antes de gerar).
-- Numero sequencial (RN-053) calculado manualmente por conta, pois não há trigger/sequence no banco
-- (aplicação usa MAX(numero)+1 via NumeroSequencialUtil, sem filtrar deleted_at):
--   clientes  -> já existem 5 linhas soft-deletadas (numero 1-5) para esta conta; começa em 6.
--   insumos   -> 0 linhas hoje; começa em 1.
--   produtos  -> 0 linhas hoje; começa em 1. IMPORTANTE: tipo=PRODUTO e tipo=CUSTOMIZACAO
--                dividem a MESMA sequência de numero (UNIQUE(usuario_id, numero) sem coluna de
--                tipo) — os 25 PRODUTO usam 1-25, as 25 CUSTOMIZACAO usam 26-50, sem sobrepor.
-- "Customização" não é tabela própria — é produtos.tipo='CUSTOMIZACAO' (confirmado no Passo 0).
-- Rode dentro de uma transação (BEGIN/COMMIT já incluídos) para poder abortar com ROLBACK se algo
-- colidir. Idempotência: NÃO é idempotente — rodar 2x duplica dados (nomes repetidos, numero
-- recalculado a partir do estado real a cada execução manual, não craqueado em CTE dinâmica aqui).

BEGIN;

-- ID fixo da conta de teste (confirmado via SELECT no Passo 0 desta sessão).
-- \set usuario_id '9970791d-a06c-42ea-9e91-cfe504868e21'

CREATE TEMP TABLE tmp_insumos (numero int PRIMARY KEY, id uuid, nome text, fracionavel boolean);
CREATE TEMP TABLE tmp_produtos (numero int PRIMARY KEY, id uuid, nome text, tipo text);

-- ─── Clientes (25, numero 6-30) ─────────────────────────────────────────────
INSERT INTO clientes (usuario_id, nome, email, whatsapp, endereco, observacoes, ativa, numero) VALUES
('9970791d-a06c-42ea-9e91-cfe504868e21','Mariana Ferreira Costa','mariana.costa@gmail.com','(11) 98765-4321','Rua das Acácias, 120 - São Paulo/SP',NULL,true,6),
('9970791d-a06c-42ea-9e91-cfe504868e21','João Pedro Almeida','jp.almeida@hotmail.com','(21) 99887-6655',NULL,NULL,true,7),
('9970791d-a06c-42ea-9e91-cfe504868e21','Beatriz Souza Lima','bia.lima@gmail.com','(31) 98234-1122','Av. Afonso Pena, 900 - Belo Horizonte/MG',NULL,true,8),
('9970791d-a06c-42ea-9e91-cfe504868e21','Rafael Oliveira Santos','rafa.santos@outlook.com','(41) 99123-4567',NULL,'Cliente prefere contato por WhatsApp.',true,9),
('9970791d-a06c-42ea-9e91-cfe504868e21','Camila Rodrigues Nunes','camila.nunes@gmail.com','(51) 98456-7890',NULL,NULL,true,10),
('9970791d-a06c-42ea-9e91-cfe504868e21','Lucas Gabriel Pereira','lucas.pereira@yahoo.com.br','(61) 99765-0011','SQN 210 Bloco B - Brasília/DF',NULL,true,11),
('9970791d-a06c-42ea-9e91-cfe504868e21','Fernanda Cristina Barbosa','fer.barbosa@gmail.com','(71) 98111-2233',NULL,NULL,true,12),
('9970791d-a06c-42ea-9e91-cfe504868e21','Thiago Henrique Rocha','thiago.rocha@hotmail.com','(81) 99345-6677',NULL,NULL,true,13),
('9970791d-a06c-42ea-9e91-cfe504868e21','Juliana Aparecida Martins','ju.martins@gmail.com','(85) 98999-1234','Rua do Sol, 45 - Fortaleza/CE',NULL,true,14),
('9970791d-a06c-42ea-9e91-cfe504868e21','Bruno Cesar Carvalho','bruno.carvalho@outlook.com','(11) 97654-3210',NULL,'Encomenda recorrente, mensal.',false,15),
('9970791d-a06c-42ea-9e91-cfe504868e21','Larissa Gomes Ribeiro','larissa.ribeiro@gmail.com','(21) 98876-5432',NULL,NULL,true,16),
('9970791d-a06c-42ea-9e91-cfe504868e21','Diego Fernandes Araújo','diego.araujo@yahoo.com.br','(31) 99234-5566','Rua Ceará, 78 - Belo Horizonte/MG',NULL,true,17),
('9970791d-a06c-42ea-9e91-cfe504868e21','Patrícia Alves Monteiro','patricia.monteiro@gmail.com','(41) 98567-8899',NULL,NULL,true,18),
('9970791d-a06c-42ea-9e91-cfe504868e21','Gustavo Henrique Dias','gustavo.dias@hotmail.com','(51) 99678-1122',NULL,NULL,true,19),
('9970791d-a06c-42ea-9e91-cfe504868e21','Vanessa Cristina Teixeira','vanessa.teixeira@gmail.com','(61) 98345-6789','SHIS QI 15 - Brasília/DF','Prefere retirada, não entrega.',true,20),
('9970791d-a06c-42ea-9e91-cfe504868e21','Rodrigo Augusto Correia','rodrigo.correia@outlook.com','(71) 99456-7788',NULL,NULL,true,21),
('9970791d-a06c-42ea-9e91-cfe504868e21','Amanda Beatriz Cardoso','amanda.cardoso@gmail.com','(81) 98234-9900',NULL,NULL,true,22),
('9970791d-a06c-42ea-9e91-cfe504868e21','Felipe Augusto Moreira','felipe.moreira@yahoo.com.br','(85) 99765-4321','Av. Beira Mar, 300 - Fortaleza/CE',NULL,true,23),
('9970791d-a06c-42ea-9e91-cfe504868e21','Renata Aparecida Pinto','renata.pinto@gmail.com','(11) 98123-4455',NULL,NULL,false,24),
('9970791d-a06c-42ea-9e91-cfe504868e21','Marcelo Vinícius Castro','marcelo.castro@hotmail.com','(21) 99876-5511',NULL,NULL,true,25),
('9970791d-a06c-42ea-9e91-cfe504868e21','Isabela Cristina Freitas','isabela.freitas@gmail.com','(31) 98456-1122','Rua Goiás, 55 - Belo Horizonte/MG',NULL,true,26),
('9970791d-a06c-42ea-9e91-cfe504868e21','André Luiz Barros','andre.barros@outlook.com','(41) 99234-8899',NULL,'Sempre pede nota fiscal.',true,27),
('9970791d-a06c-42ea-9e91-cfe504868e21','Priscila Fernanda Melo','priscila.melo@gmail.com','(51) 98678-3344',NULL,NULL,true,28),
('9970791d-a06c-42ea-9e91-cfe504868e21','Eduardo Henrique Lopes','eduardo.lopes@yahoo.com.br','(61) 99345-1177','SQS 308 - Brasília/DF',NULL,true,29),
('9970791d-a06c-42ea-9e91-cfe504868e21','Carolina Souza Andrade','carolina.andrade@gmail.com','(71) 98567-2299',NULL,NULL,true,30);

-- ─── Insumos (25, numero 1-25) ──────────────────────────────────────────────
-- Mix deliberado: 11 não-fracionável / 14 fracionável; permitir_estoque_negativo mix true/false;
-- estoque_atual com 3 casos = 0 (itens 8, 12, 16) e 1 caso negativo (item 18, permitir_negativo=true).
WITH ins AS (
  INSERT INTO insumos (usuario_id, nome, marca, unidade_medida, custo_unitario, estoque_atual, estoque_minimo, fracionavel, permitir_estoque_negativo, tipo_exibicao_quantidade, numero) VALUES
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Papel Cartão 180g','Scrity','Folha',0.35,350,50,false,true,NULL,1),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Papel Vergê','Filipaper','Folha',0.55,200,30,false,true,NULL,2),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Papel Fotográfico','HP','Folha',1.20,80,10,false,false,NULL,3),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Papel Manteiga',NULL,'Folha',0.15,500,100,false,true,NULL,4),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cordão de Sisal',NULL,'cm',0.02,1200.5,200,true,true,'DECIMAL',5),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Fita de Cetim','Progresso','cm',0.03,850,150,true,true,'FRACAO',6),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cola Quente (bastão)','Tekbond','g',0.04,600,100,true,false,'DECIMAL',7),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Tinta Acrílica','Acrilex','ml',0.08,0,50,true,false,'DECIMAL',8),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Glitter','Corfix','g',0.15,45.5,20,true,true,'FRACAO',9),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Botão de Madeira',NULL,'Unidade',0.25,300,50,false,true,NULL,10),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Renda de Algodão',NULL,'cm',0.10,220,40,true,true,'DECIMAL',11),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Elástico','Círculo','cm',0.015,0,100,true,true,'FRACAO',12),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Adesivo Vinil','3M','Unidade',0.60,150,25,false,false,NULL,13),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Carimbo Personalizado',NULL,'Unidade',3.50,12,2,false,false,NULL,14),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Envelope Kraft',NULL,'Unidade',0.45,400,60,false,true,NULL,15),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Lacre de Cera',NULL,'Unidade',0.30,0,30,false,true,NULL,16),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Purpurina','Corfix','g',0.18,30.25,10,true,true,'FRACAO',17),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Barbante',NULL,'cm',0.01,-15.5,200,true,true,'DECIMAL',18),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Miçangas',NULL,'Unidade',0.05,800,100,false,true,NULL,19),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Feltro','Santa Fé','cm',0.12,180,30,true,false,'DECIMAL',20),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Tecido Algodão',NULL,'cm',0.20,95.5,20,true,false,'FRACAO',21),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','MDF Cru 3mm',NULL,'Unidade',2.80,40,5,false,true,NULL,22),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Verniz','Acrilex','ml',0.09,220,40,true,true,'DECIMAL',23),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cola Branca','Tenaz','ml',0.03,500,80,true,true,'FRACAO',24),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Fio de Nylon',NULL,'cm',0.02,340.75,50,true,false,'DECIMAL',25)
  RETURNING id, numero, nome, fracionavel
)
INSERT INTO tmp_insumos SELECT numero, id, nome, fracionavel FROM ins;

-- ─── Produtos tipo=PRODUTO (25, numero 1-25) ────────────────────────────────
WITH prods AS (
  INSERT INTO produtos (usuario_id, nome, tipo, tempo_producao, preco_venda, margem_lucro, override, rendimento, estoque_atual, estoque_minimo, permitir_estoque_negativo, numero) VALUES
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Convite de Casamento Personalizado','PRODUTO',30,12.90,55,true,1,40,5,true,1),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Convite de Aniversário Infantil','PRODUTO',20,9.50,50,true,1,60,10,true,2),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Caixa de Presente Decorada','PRODUTO',25,18.00,60,true,1,25,5,false,3),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Álbum de Fotos Artesanal','PRODUTO',90,45.00,45,true,1,10,2,true,4),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Porta-Retrato em MDF','PRODUTO',35,22.00,50,true,1,18,3,true,5),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Topo de Bolo Personalizado','PRODUTO',15,15.00,65,true,1,30,5,false,6),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Painel de Festa em Feltro','PRODUTO',120,85.00,40,true,1,5,1,true,7),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Lembrancinha de Chá de Bebê','PRODUTO',10,6.50,55,true,10,100,20,true,8),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cartão de Agradecimento','PRODUTO',8,4.00,50,true,1,200,30,true,9),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Caderno Artesanal','PRODUTO',40,28.00,45,true,1,15,3,false,10),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Quadro Decorativo','PRODUTO',50,35.00,48,true,1,8,2,true,11),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Kit Festa Junina','PRODUTO',60,32.00,42,true,1,12,2,true,12),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Etiqueta Personalizada para Presente','PRODUTO',5,2.50,60,true,20,300,50,true,13),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Sacolinha Surpresa','PRODUTO',12,7.00,50,true,5,80,15,true,14),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Tag para Lembrancinha','PRODUTO',6,1.80,55,true,15,250,40,true,15),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Convite Digital Impresso','PRODUTO',15,8.90,50,true,1,50,10,true,16),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Caixinha para Doces','PRODUTO',10,3.50,55,true,10,180,30,true,17),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Envelope Personalizado','PRODUTO',8,2.90,50,true,1,220,40,true,18),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Placa de Boas-vindas','PRODUTO',45,38.00,45,true,1,6,1,false,19),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Painel para Mesa do Bolo','PRODUTO',70,55.00,42,true,1,4,1,true,20),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Marcador de Página Artesanal','PRODUTO',7,3.20,58,true,8,140,20,true,21),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Kit Chá de Panela','PRODUTO',55,40.00,44,true,1,9,2,true,22),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Convite Formatura','PRODUTO',28,13.50,52,true,1,35,5,true,23),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Porta-Guardanapo Artesanal','PRODUTO',20,11.00,48,true,1,22,4,true,24),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Vela Decorada Personalizada','PRODUTO',25,17.50,50,true,1,14,3,false,25)
  RETURNING id, numero, nome, tipo
)
INSERT INTO tmp_produtos SELECT numero, id, nome, tipo FROM prods;

-- ─── Produtos tipo=CUSTOMIZACAO (25, numero 26-50 — mesma sequência dos PRODUTO acima) ──
WITH custs AS (
  INSERT INTO produtos (usuario_id, nome, tipo, tempo_producao, preco_venda, margem_lucro, override, numero) VALUES
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Acabamento em Dourado','CUSTOMIZACAO',5,4.50,60,true,26),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Gravação a Laser Personalizada','CUSTOMIZACAO',15,12.00,55,true,27),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cor Personalizada','CUSTOMIZACAO',3,2.00,50,true,28),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Tamanho Grande','CUSTOMIZACAO',8,6.00,45,true,29),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Acabamento Fosco','CUSTOMIZACAO',4,3.50,55,true,30),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Brilho Extra (Glitter)','CUSTOMIZACAO',5,3.00,60,true,31),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Mensagem Personalizada à Mão','CUSTOMIZACAO',10,8.00,65,true,32),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Embalagem para Presente','CUSTOMIZACAO',6,5.50,50,true,33),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Fita de Cetim Colorida Extra','CUSTOMIZACAO',3,2.20,55,true,34),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Acabamento em Prata','CUSTOMIZACAO',5,4.50,60,true,35),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Aplique de Renda','CUSTOMIZACAO',7,6.50,48,true,36),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Costura Reforçada','CUSTOMIZACAO',6,4.00,45,true,37),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Verniz Localizado','CUSTOMIZACAO',4,3.20,52,true,38),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Textura em Relevo','CUSTOMIZACAO',8,7.00,50,true,39),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Cordão Extra','CUSTOMIZACAO',2,1.50,55,true,40),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Etiqueta Personalizada Extra','CUSTOMIZACAO',3,1.80,58,true,41),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Bordado Simples','CUSTOMIZACAO',20,15.00,50,true,42),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Pintura à Mão Extra','CUSTOMIZACAO',25,18.00,55,true,43),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Acabamento Emborrachado','CUSTOMIZACAO',5,4.00,48,true,44),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Camada Extra de Verniz','CUSTOMIZACAO',6,3.80,50,true,45),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Recorte Especial a Laser','CUSTOMIZACAO',12,10.00,52,true,46),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Adesivo Personalizado Extra','CUSTOMIZACAO',4,2.50,55,true,47),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Fivela Decorativa','CUSTOMIZACAO',3,3.00,50,true,48),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Borda Denteada','CUSTOMIZACAO',5,2.80,48,true,49),
  ('9970791d-a06c-42ea-9e91-cfe504868e21','Kit Fita + Lacre Especial','CUSTOMIZACAO',6,5.00,52,true,50)
  RETURNING id, numero, nome, tipo
)
INSERT INTO tmp_produtos SELECT numero, id, nome, tipo FROM custs;

-- ─── Ficha Técnica: liga cada Produto (tipo=PRODUTO) a 2-3 Insumos ──────────
-- Cobertura deliberada: maioria dos produtos usa ao menos 1 insumo não-fracionável
-- (algumInsumoNaoFracionavel=true); produtos 7 e 25 usam só insumos fracionáveis (=false),
-- para cobrir os dois estados do flag.
INSERT INTO ficha_tecnica_itens (produto_id, insumo_id, quantidade)
SELECT p.id, i.id, mapa.quantidade
FROM (VALUES
  (1,1,2),(1,6,15),(1,16,1),
  (2,2,1),(2,9,3),
  (3,22,1),(3,6,20),
  (4,22,2),(4,3,10),(4,21,40),
  (5,22,1),(5,23,5),
  (6,22,1),(6,13,1),
  (7,20,200),(7,24,30),
  (8,19,8),(8,5,12),
  (9,1,1),(9,9,1),
  (10,2,20),(10,21,25),(10,5,30),
  (11,22,1),(11,23,8),
  (12,1,3),(12,6,25),(12,19,15),
  (13,13,1),
  (14,4,2),(14,6,10),
  (15,1,1),(15,5,8),
  (16,3,1),
  (17,1,1),(17,6,8),
  (18,15,1),(18,9,1),
  (19,22,1),(19,23,6),
  (20,22,2),(20,20,100),
  (21,1,1),(21,5,5),
  (22,1,2),(22,6,15),(22,14,1),
  (23,2,1),(23,13,1),
  (24,22,1),(24,5,10),
  (25,9,4),(25,6,6)
) AS mapa(produto_numero, insumo_numero, quantidade)
JOIN tmp_produtos p ON p.numero = mapa.produto_numero AND p.tipo = 'PRODUTO'
JOIN tmp_insumos i ON i.numero = mapa.insumo_numero;

COMMIT;

-- ─── Conferência pós-inserção (rodar manualmente, fora da transação) ────────
-- SELECT COUNT(*) FROM clientes WHERE usuario_id = '9970791d-a06c-42ea-9e91-cfe504868e21' AND numero BETWEEN 6 AND 30;
-- SELECT COUNT(*) FROM insumos WHERE usuario_id = '9970791d-a06c-42ea-9e91-cfe504868e21' AND numero BETWEEN 1 AND 25;
-- SELECT tipo, COUNT(*) FROM produtos WHERE usuario_id = '9970791d-a06c-42ea-9e91-cfe504868e21' AND numero BETWEEN 1 AND 50 GROUP BY tipo;
-- SELECT COUNT(*) FROM ficha_tecnica_itens fti JOIN produtos p ON p.id = fti.produto_id WHERE p.usuario_id = '9970791d-a06c-42ea-9e91-cfe504868e21';
