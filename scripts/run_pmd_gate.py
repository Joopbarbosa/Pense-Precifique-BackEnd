#!/usr/bin/env python3
"""Roda PMD em modo relatório e reprova violações novas sobre a linha de base."""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / 'target' / 'pmd-gate.log'


def main():
    LOG.parent.mkdir(exist_ok=True)
    with LOG.open('w', encoding='utf-8') as stream:
        result = subprocess.run(['./mvnw', '-B', 'compile', 'pmd:check'], cwd=ROOT,
                                stdout=stream, stderr=subprocess.STDOUT, check=False)
    if result.returncode:
        print(LOG.read_text(encoding='utf-8')[-2000:], file=sys.stderr)
        return result.returncode
    result = subprocess.run([sys.executable, str(ROOT / 'scripts/check_pmd_baseline.py'),
                             '--report', str(ROOT / 'target/pmd.xml'),
                             '--baseline', str(ROOT / 'config/pmd-baseline.json')],
                            cwd=ROOT, text=True, capture_output=True, check=False)
    if result.returncode:
        print(result.stdout or result.stderr, file=sys.stderr)
        return result.returncode
    print(result.stdout.strip())
    print('PMD_GATE_OK')
    return 0


if __name__ == '__main__':
    sys.exit(main())
