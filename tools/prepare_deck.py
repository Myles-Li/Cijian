'将固定版本 Anki 文本转换为离线 SQLite 词库。 / Convert the pinned egg rolls Anki TSV into an offline SQLite vocabulary asset.'
import collections
import csv
import hashlib
import html
import json
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]


def clean(value):
    value = re.sub(r'\[sound:[^\]]+\]', '', value)
    value = re.sub(r'<br\s*/?>|</div>|</p>', '\n', value, flags=re.I)
    value = re.sub(r'<[^>]+>', '', value)
    return html.unescape(value).replace('\xa0', ' ').strip()


def convert(source, output):
    output.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(output)
    db.executescript('''
        DROP TABLE IF EXISTS words;
        CREATE TABLE words (id TEXT PRIMARY KEY, word TEXT NOT NULL, reading TEXT NOT NULL,
            meaning TEXT NOT NULL, traditional TEXT, example TEXT, example_meaning TEXT,
            note TEXT, pitch TEXT, pos TEXT, level TEXT NOT NULL, rank REAL, category TEXT);
        CREATE TABLE IF NOT EXISTS progress (id TEXT PRIMARY KEY, favorite INTEGER NOT NULL DEFAULT 0,
            familiar INTEGER NOT NULL DEFAULT 0, seen INTEGER NOT NULL DEFAULT 0, last_seen INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS encounters (day TEXT NOT NULL, id TEXT NOT NULL, PRIMARY KEY(day,id));
        CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS words_level_rank ON words(level,rank);
    ''')
    counts = collections.Counter()
    with source.open(encoding='utf-8-sig', newline='') as handle:
        for row in csv.reader((line for line in handle if not line.startswith('#')), delimiter='\t'):
            if len(row) != 39:
                raise ValueError(f'Expected 39 fields, got {len(row)}')
            match = re.search(r'::\d-(N[1-5])', row[1])
            if not match:
                continue
            level = match[1]
            try:
                rank = float(row[35])
            except ValueError:
                rank = 999999.0
            values = [row[2], clean(row[3]), clean(row[6]), clean(row[7]), clean(row[8]),
                      clean(row[12]), clean(row[14]), clean(row[9]), clean(row[4]), clean(row[5]), level,
                      rank, clean(row[38])]
            if not values[1] or not values[3]:
                raise ValueError('Empty word or meaning: ' + row[2])
            db.execute('INSERT INTO words VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)', values)
            counts[level] += 1
    provenance = json.loads((source.parent / 'source.json').read_text(encoding='utf-8'))
    provenance.update({'format_revision': 1, 'counts': dict(sorted(counts.items())), 'total': sum(counts.values()),
                       'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                       'changes': 'Converted Anki fields to SQLite, stripped display HTML and sound markers; uses system Japanese TTS instead of deck audio; keeps first example.'})
    db.execute('INSERT OR REPLACE INTO meta VALUES (?,?)', ('provenance', json.dumps(provenance, ensure_ascii=False)))
    db.commit()
    assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    db.close()
    asset_dir = output.parent
    (asset_dir / 'deck-provenance.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf-8')
    (asset_dir / 'eggrolls-LICENSE.txt').write_bytes((source.parent / 'LICENSE').read_bytes())
    print(json.dumps(provenance, ensure_ascii=False, indent=2))
    return provenance


if __name__ == '__main__':
    convert(ROOT / 'data/upstream/notes.csv', ROOT / 'app/src/main/assets/vocabulary.db')
