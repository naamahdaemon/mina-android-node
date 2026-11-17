"""Require evidence that Android unit tests were discovered and passed."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(directory):
    reports = list(directory.rglob('TEST-*.xml'))
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in reports:
        suite = ET.parse(path).getroot()
        for name in totals:
            totals[name] += int(suite.get(name, '0'))
    if not reports or totals['tests'] <= totals['skipped'] or totals['failures'] or totals['errors']:
        raise ValueError(f'Expected executed, successful Android unit tests: {totals}')
    print(f"Verified {totals['tests'] - totals['skipped']} executed Android unit tests with no failures")


if __name__ == '__main__':
    verify(Path(sys.argv[1]))
