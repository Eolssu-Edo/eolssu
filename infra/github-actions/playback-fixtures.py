#!/usr/bin/env python3
"""Generate synthetic, non-production assets for playback's startup probe."""

import json
from pathlib import Path
import struct
import sys
import wave
import zipfile


def main(root):
    assets = root / 'playback-assets'
    janggu = root / 'playback-janggu'
    assets.mkdir()
    janggu.mkdir()
    with zipfile.ZipFile(assets / 'fixture.zip', 'w') as archive:
        archive.writestr('notice.txt', 'Synthetic startup probe. No renderable CAT samples.')
    instruments = ('danso', 'sogeum', 'jeongak-daegeum', 'sanjo-daegeum')
    (assets / 'assets.json').write_text(json.dumps(dict.fromkeys(instruments, 'fixture.zip')))
    for stroke in ('deong', 'gi', 'kung', 'deok', 'da', 'deo'):
        with wave.open(str(janggu / f'{stroke}.wav'), 'wb') as sample:
            sample.setparams((1, 2, 44100, 0, 'NONE', 'not compressed'))
            sample.writeframes(struct.pack('<hh', 1000, -1000))


if __name__ == '__main__':
    main(Path(sys.argv[1]))
