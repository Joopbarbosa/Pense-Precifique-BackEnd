#!/usr/bin/env python3
"""Compara violações PMD com a linha de base por arquivo, regra e texto."""

import argparse
import collections
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def findings(report):
    root = ET.parse(report).getroot()
    if root.tag.rsplit('}', 1)[-1] != 'pmd':
        raise ValueError('Relatório PMD ilegível: raiz ausente.')
    counts = collections.Counter()
    for file_node in root.findall('.//{*}file'):
        path = file_node.get('name')
        if not path:
            raise ValueError('Relatório PMD sem caminho do arquivo.')
        path = path.replace('\\', '/')
        if '/src/' in path:
            path = 'src/' + path.rsplit('/src/', 1)[1]
        for violation in file_node.findall('{*}violation'):
            rule = violation.get('rule')
            message = ' '.join((violation.text or '').split())
            if not rule or not message:
                raise ValueError('Violação PMD incompleta.')
            counts[(path, rule, message)] += 1
    return counts


def to_rows(counts):
    return [{'file': file, 'rule': rule, 'message': message, 'count': count}
            for (file, rule, message), count in sorted(counts.items())]


def from_rows(rows):
    if not isinstance(rows, list):
        raise ValueError('Linha de base ilegível.')
    counts = collections.Counter()
    for row in rows:
        if not isinstance(row, dict) or not all(row.get(k) for k in ('file', 'rule', 'message')):
            raise ValueError('Linha de base contém entrada incompleta.')
        count = row.get('count')
        if not isinstance(count, int) or isinstance(count, bool) or count < 1:
            raise ValueError('Contagem inválida na linha de base.')
        key = (row['file'], row['rule'], row['message'])
        if key in counts:
            raise ValueError('Entrada duplicada na linha de base.')
        counts[key] = count
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, default=Path('target/pmd.xml'))
    parser.add_argument('--baseline', type=Path, default=Path('config/pmd-baseline.json'))
    parser.add_argument('--write-baseline', action='store_true')
    args = parser.parse_args()
    try:
        current = findings(args.report)
        if args.write_baseline:
            if args.baseline.exists():
                raise ValueError('Linha de base já existe; alteração exige revisão explícita.')
            args.baseline.write_text(json.dumps(to_rows(current), ensure_ascii=False, indent=2) + '\n')
            print(f'Linha de base criada: {sum(current.values())} violações.')
            return 0
        baseline = from_rows(json.loads(args.baseline.read_text()))
        new = current - baseline
        stale = baseline - current
        print(json.dumps({'status': 'aprovado' if not new and not stale else 'reprovado',
                          'baseline': sum(baseline.values()), 'current': sum(current.values()),
                          'new': to_rows(new), 'stale': to_rows(stale)}, ensure_ascii=False))
        return 0 if not new and not stale else 1
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as error:
        print(f'Falha na checagem PMD: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
