"""Regenerate CAT-09 content from the checked-in FE catalog; --check detects drift.

No network input. Android VectorDrawable paths and affine groups are preserved as SVG.
The V19 SQL is an unapplied seed snapshot: after release create a new migration instead.
"""
import argparse
import html
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FE = ROOT / 'frontend/shared/src/commonMain'
OUT = ROOT / 'backend/src/main/resources/notation-symbols'
ANDROID = '{http://schemas.android.com/apk/res/android}'
MIT = (FE / 'composeResources/files/licenses/sigimsae_symbols_mit.txt').read_text(encoding='utf-8')
OFFICIAL = {'겹흘림표', '풀어내림표', '떠이어표', '노리노', '느네느', '나니로', '나니르노니르', '덧길이표', '반길이표', '네로나', '니느라니', '느나르나니', '겹요성표', '같은음표'}


def svg_node(node):
    attr = lambda key, default='0': node.get(ANDROID + key, default)
    if node.tag == 'group':
        # Current FE vectors use translate then scale, without pivot or rotation.
        assert all(key.removeprefix(ANDROID) in {'translateX', 'translateY', 'scaleX', 'scaleY'} for key in node.attrib)
        transform = f'translate({attr("translateX")} {attr("translateY")}) scale({attr("scaleX", "1")} {attr("scaleY", "1")})'
        return f'<g transform="{transform}">' + ''.join(map(svg_node, node)) + '</g>'
    assert node.tag == 'path' and attr('fillColor') == '#FF000000'
    assert all(key.removeprefix(ANDROID) in {'fillColor', 'fillType', 'pathData'} for key in node.attrib)
    rule = 'evenodd' if attr('fillType') == 'evenOdd' else 'nonzero'
    return f'<path fill="black" fill-rule="{rule}" d="{html.escape(attr("pathData"), quote=True)}"/>'


def generate():
    source = (FE / 'kotlin/com/ssafy/eolssu/feature/guide/Sigimsae.kt').read_text(encoding='utf-8')
    basic, more = source.split('val MoreSigimsae:', 1)
    strings = ET.parse(FE / 'composeResources/values/strings.xml').getroot()
    hold = strings.find("string[@name='guide_reading_length_cap_hold']").text
    rest = strings.find("string[@name='guide_reading_length_cap_rest']").text
    assert hold.startswith('ㅡ ') and rest.startswith('△ ')
    score = (FE / 'kotlin/com/ssafy/eolssu/core/model/Score.kt').read_text(encoding='utf-8')
    assert 'override val symbol = "ㅡ"' in score and 'override val symbol = "△"' in score
    entries = [dict(symbol='ㅡ', name='연장', description=hold.removeprefix('ㅡ '), asset='hold', descriptionStatus='EDUCATIONAL',
                    source='얼쑤 정간보 쉽게 읽기 · 음의 길이', imageLicense=None),
               dict(symbol='△', name='쉼표', description=rest.removeprefix('△ '), asset='rest', descriptionStatus='EDUCATIONAL',
                    source='얼쑤 정간보 쉽게 읽기 · 음의 길이', imageLicense=None)]
    for images, name, description in re.findall(r'SigimsaeItem\(\s*listOf\((.*?)\),\s*"([^"]+)",\s*"([^"]+)"', basic, re.S):
        for asset in re.findall(r'Res.drawable.(sig_\w+)', images):
            symbol = {'sig_shake': '요성표', 'sig_vib': '농음표'}.get(asset, name)
            entries.append(dict(symbol=symbol, name=name, description=description, asset=asset,
                                descriptionStatus='EDUCATIONAL', source='얼쑤 정간보 학습 안내 · 교육용 설명(전공자 검수 전)', imageLicense='MIT'))
    for asset, name, description in re.findall(r'SigimsaeSymbol\(Res.drawable.(sig_\w+), "([^"]+)", "([^"]+)"\)', more):
        reference = ('우물사이 기호 사전 (추정); 공식 정의 미확인' if name == '노라느니' else
                     '국립국악원 대금 정악보 2021 수정본, 대금 악상기호 표' if name in OFFICIAL else
                     '국립국어원 한국 음악 점자 규정 해설 제16~19항')
        entries.append(dict(symbol=name, name=name, description=description, asset=asset,
                            descriptionStatus='INFERRED' if name == '노라느니' else 'DOCUMENTED',
                            source=reference + '; 기존 문헌 대조 기준 · 전공자 검수 전', imageLicense='MIT'))
    for entry in entries:
        if entry['imageLicense'] == 'MIT':
            entry['source'] += '; 그림: 우물사이 https://github.com/danbinaerinHan/jeongganbo-generator (MIT)'
    assert len(entries) == 49 and len({e['symbol'] for e in entries}) == 49
    assert len({e['asset'] for e in entries}) == 49
    renderer = (FE / 'kotlin/com/ssafy/eolssu/core/ui/score/ScoreSigimsae.kt').read_text(encoding='utf-8')
    assert all(f'put("{e["symbol"]}"' in renderer for e in entries if e['asset'].startswith('sig_'))
    outputs = {OUT / 'catalog.json': json.dumps(entries, ensure_ascii=False, indent=2) + '\n', OUT / 'LICENSE.txt': MIT}
    for entry in entries:
        asset = entry['asset']
        if asset in {'hold', 'rest'}:
            path = 'M4 16H28' if asset == 'hold' else 'M16 3L29 28H3Z'
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><path d="{path}" fill="none" stroke="black" stroke-width="2"/></svg>\n'
        else:
            vector = ET.parse(FE / f'composeResources/drawable/{asset}.xml').getroot()
            width, height = vector.get(ANDROID + 'viewportWidth'), vector.get(ANDROID + 'viewportHeight')
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}"><metadata>{html.escape(MIT)}</metadata>' + ''.join(map(svg_node, vector)) + '</svg>\n'
        outputs[OUT / f'{asset}.svg'] = svg
    sql = ['-- S15P31E102-410: FE teaching content, not score/scoring semantics.',
           '-- Preserve any existing row with the exact binary symbol; no normalization or overwrite.']
    quote = lambda value: "'" + value.replace("'", "''") + "'"
    for e in entries:
        values = ','.join(quote(e[k]) for k in ('symbol', 'name', 'description'))
        sql.append(f'INSERT INTO notation_symbols (symbol,name,description) SELECT {values} FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM notation_symbols WHERE symbol = {quote(e["symbol"])} COLLATE utf8mb4_bin);')
    outputs[ROOT / 'backend/src/main/resources/db/migration/V19__seed_notation_symbols.sql'] = '\n'.join(sql) + '\n'
    return outputs


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    for path, content in generate().items():
        if args.check:
            assert path.is_file() and path.read_text(encoding='utf-8') == content, f'Catalog drift: {path}'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8', newline='\n')
    print('49 notation symbols: ' + ('checked' if args.check else 'generated'))
