-- V0.17.0 #964 (RN-NOVA-19, DT-NOVA-15): telefone e WhatsApp passam a aceitar número estrangeiro
-- com separadores (até 25 caracteres). Só aumenta o tamanho das colunas; nenhum dado é alterado.
ALTER TABLE empresas ALTER COLUMN whatsapp TYPE VARCHAR(25);
ALTER TABLE clientes ALTER COLUMN whatsapp TYPE VARCHAR(25);
ALTER TABLE clientes ALTER COLUMN telefone TYPE VARCHAR(25);
