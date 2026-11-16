#!/usr/bin/env python3
"""Convert downloaded public station CSVs. See docs/subway-ground-data.md."""
import csv
import io
import sys
from pathlib import Path


def rows(path):
    data = Path(path).read_bytes()
    for encoding in ('utf-8-sig', 'cp949'):
        try:
            return list(csv.DictReader(io.StringIO(data.decode(encoding))))
        except UnicodeDecodeError:
            pass
    raise ValueError(f'Unsupported encoding: {path}')


def main(depth, korail, incheon, gwangju, output):
    stations = {}
    for row in rows(depth):
        level = row['층수'].strip()
        status = 'ABOVE' if level in ('고가', '지상') else 'BELOW' if level.startswith('B') else 'UNKNOWN'
        stations[(row['호선'].strip() + '호선', row['역명'].strip())] = status
    for path, prefix in ((korail, ''), (incheon, ''), (gwangju, '광주')):
        grouped = {}
        for row in rows(path):
            if row['역층구분'].strip() != '승강장':
                continue
            line = prefix + row['노선명'].strip()
            if not line.endswith(('선', '호선')):
                line += '선'
            key = line, row['역명'].strip()
            grouped.setdefault(key, set()).add(row['지상구분'].strip())
        for key, values in grouped.items():
            status = 'ABOVE' if values == {'지상'} else 'BELOW' if values == {'지하'} else 'UNKNOWN'
            old = stations.get(key)
            stations[key] = status if old is None or old == status else 'UNKNOWN'
    with Path(output).open('w', newline='', encoding='utf-8') as target:
        writer = csv.writer(target, lineterminator='\n')
        writer.writerow(('line', 'station', 'level'))
        for (line, station), status in sorted(stations.items()):
            writer.writerow((line, station, status))


if __name__ == '__main__':
    main(*sys.argv[1:])
