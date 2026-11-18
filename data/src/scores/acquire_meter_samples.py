"""Acquire the fixed public meter sample list, retain sources and render OMR inputs.

Raw files/evidence stay in ignored paths. This does not upload to the app.
Requires Pillow and pypdfium2; Python standard library handles downloads.
"""
import hashlib
import json
from pathlib import Path
import re
import urllib.request
from urllib.parse import urljoin

ROOT = Path(__file__).resolve().parents[3]
RAW = ROOT / 'data/raw/meter-validation'
OUT = ROOT / 'data/processed/meter-validation'

SAMPLES = [
    ('bach-bourree', '2/4', 'https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1528',
     'https://www.mutopiaproject.org/ftp/BachJS/BWV1013/bwv1013/bwv1013-a4.pdf', 'Public Domain', 5),
    ('greensleeves', '3/4', 'https://noctavepiano.com/en/sheet-music/trad-greensleeves/',
     'https://noctavepiano.com/partitions/trad-greensleeves/trad-greensleeves.pdf', 'CC0-1.0', 1),
    ('au-clair', '4/4', 'https://noctavepiano.com/en/sheet-music/trad-au-clair-de-la-lune/',
     'https://noctavepiano.com/partitions/trad-au-clair-de-la-lune/trad-au-clair-de-la-lune.pdf', 'CC0-1.0', 1),
    ('kesh', '6/8', 'https://www.celticscores.com/sheet-music/794_The_Kesh', None, 'CC-BY-SA', 1),
    ('butterfly', '9/8', 'https://www.celticscores.com/sheet-music/795_The_Butterfly',
     'https://www.celticscores.com/file/1752_The_Butterfly.pdf', 'CC-BY-SA', 1),
    ('circassian', '12/8', 'https://www.celticscores.com/sheet-music/86_Circassian_circle',
     'https://www.celticscores.com/file/170_Circassian_circle.gif', 'CC-BY', 1),
    ('al-lez-vamm', '5/8', 'https://www.partitions.bzh/partition/819_Al_lez_Vamm',
     'https://www.partitions.bzh/fichier/1821_Al_lez_Vamm.pdf', 'CC-BY-SA', 1),
    ('mileva', '7/8', 'https://www.celticscores.com/sheet-music/1730_Mileva_Rachenitza',
     'https://www.celticscores.com/file/4515_Mileva_Rachenitza.pdf', 'CC-BY-SA', 1),
    ('chopin-12-8', '12/8', 'https://noctavepiano.com/en/sheet-music/chopin-nocturne-op9-2-facile/',
     'https://noctavepiano.com/partitions/chopin-nocturne-op9-2-facile/chopin-nocturne-op9-2-facile.pdf', 'CC0-1.0', 1),
    ('amazing-pickup', '9/8 pickup', 'https://www.mutopiaproject.org/cgibin/piece-info.cgi?id=1832',
     'https://www.mutopiaproject.org/ftp/Traditional/amazing-mutopia/amazing-mutopia-a4.pdf', 'CC-BY-SA-3.0', 1),
    ('ballade-variable', '4/4 -> 6/4', 'https://noctavepiano.com/en/sheet-music/chopin-ballade-1/',
     'https://noctavepiano.com/partitions/chopin-ballade-1/chopin-ballade-1.pdf', 'CC-BY-4.0', 1),
]


def get(url):
    with urllib.request.urlopen(url, timeout=45) as response:
        data = response.read(20 * 1024 * 1024 + 1)
    if len(data) > 20 * 1024 * 1024:
        raise ValueError('download exceeds 20 MiB')
    return data


def main():
    from PIL import Image
    import pypdfium2 as pdfium
    RAW.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    result = []
    for sid, meter, page_url, file_url, license_name, page in SAMPLES:
        html = get(page_url).decode('utf-8')
        (RAW / (sid + '.source.html')).write_text(html, encoding='utf-8')
        if file_url is None:
            href = next(x for x in re.findall(r'href="([^"]+)"', html) if x.endswith('.pdf'))
            file_url = urljoin(page_url, href)
        ext = Path(file_url).suffix
        path = RAW / (sid + ext)
        content = get(file_url)
        if path.exists() and path.read_bytes() != content:
            raise ValueError(f'{sid}: remote changed; retain existing file and review before replacing')
        path.write_bytes(content)
        companions = []
        for suffix in ('.mxl', '.ly'):
            links = [urljoin(page_url, x) for x in re.findall(r'href="([^"]+)"', html) if x.endswith(suffix)]
            if links:
                companion = get(links[0])
                target = RAW / (sid + ('.publisher.mxl' if suffix == '.mxl' else '.ly'))
                if target.exists() and target.read_bytes() != companion:
                    raise ValueError(f'{sid}: companion changed; review before replacing')
                target.write_bytes(companion)
                companions.append(dict(url=links[0], path=str(target.relative_to(ROOT)),
                                       sha256=hashlib.sha256(companion).hexdigest()))
        image = OUT / (sid + '.png')
        if ext == '.pdf':
            doc = pdfium.PdfDocument(str(path))
            count = len(doc)
            doc[page - 1].render(scale=300 / 72).to_pil().convert('RGB').save(image)
            doc[page - 1].render(scale=110 / 72).to_pil().convert('RGB').save(OUT / (sid + '.preview.png'))
            doc.close()
        else:
            with Image.open(path) as img:
                count = 1
                img.convert('RGB').save(image)
                img.convert('RGB').save(OUT / (sid + '.preview.png'))
        with Image.open(image) as img:
            size = img.size
        item = dict(id=sid, expectedMeter=meter, sourceUrl=page_url, downloadUrl=file_url,
                    declaredLicense=license_name, checkedOn='2026-11-13', rawPath=str(path.relative_to(ROOT)),
                    sha256=hashlib.sha256(content).hexdigest(), bytes=len(content), pages=count,
                    testedPage=page, imagePath=str(image.relative_to(ROOT)), imageSize=size, companions=companions)
        result.append(item)
        print(sid, len(content), count, size, flush=True)
    (OUT / 'acquisition.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    (OUT / 'probe-inputs.json').write_text(json.dumps([
        dict(id=x['id'], imagePath='/audit/'+x['id']+'.png') for x in result
    ], ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
