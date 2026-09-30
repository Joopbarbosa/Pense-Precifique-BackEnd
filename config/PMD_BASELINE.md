# Linha de base PMD

PMD Maven Plugin 3.28.0, PMD 7.17.0, Java 21. Regras em
`pmd-rules.xml`: método, variável e campo privados sem uso; import
supérfluo; `catch` vazio; comparação de objetos por referência.

Em 2026-09-30, a execução `./mvnw -B clean compile pmd:check` na branch
`processo/pmd-linha-base-v11`, criada de `staging` (`60da00a`), gerou
**3 violações**. O inventário por arquivo, regra e mensagem está em
`pmd-baseline.json`. O relatório bruto fica em `target/pmd.xml`.
Uma execução sem compilar registrava 8, pois a análise de imports
mudava com a disponibilidade das classes. A linha de base usa o mesmo
comando do gate e do CI, sempre com compilação antes da análise.

`./mvnw -B compile pmd:check` roda em modo relatório para preservar o build
existente. `python3 scripts/run_pmd_gate.py` roda esse comando e compara
o resultado com a linha de base. Violação nova, saída ilegível ou linha
de base ausente reprova o portão e o CI. A comparação considera a
quantidade de ocorrências por arquivo, regra e mensagem: outra
ocorrência igual no mesmo arquivo também reprova.

Para atualizar a linha de base, revise cada achado e registre a decisão
antes de alterar `pmd-baseline.json`; o comando `--write-baseline` recusa
sobrescrever o inventário existente.
