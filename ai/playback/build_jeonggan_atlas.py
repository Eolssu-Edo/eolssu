"""Build local-only notation crops from operator-supplied official PDFs.

Requires optional pypdfium2 for this offline asset-generation command only.
No source PDF or cropped image is bundled or downloaded by this module.
Coordinates below refer exclusively to fixed notation legends, never a tune.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ENTRIES = [
    ('lower1', 'relative', 'lower1.png', 'relative', [430, 327, 21, 22]),
    ('lower2', 'relative', 'lower2.png', 'relative', [429, 440, 24, 24]),
    ('upper1', 'relative', 'upper1.png', 'relative', [430, 552, 21, 31]),
    ('upper2', 'relative', 'upper2.png', 'relative', [429, 667, 22, 32]),
    ('upper_return', 'relative', 'upper_return.png', 'relative', [428, 786, 23, 28]),
    ('lower_return', 'relative', 'lower_return.png', 'relative', [428, 902, 24, 30]),
    ('upper_pair', 'relative', 'upper_pair.png', 'relative', [431, 1018, 19, 33]),
    ('h', 'relative', 'h.png', 'relative', [431, 1135, 18, 31]),
    ('descending_three', 'relative', 'descending_three.png', 'relative', [429, 1254, 22, 28]),
    ('around_three', 'relative', 'around_three.png', 'relative', [430, 1370, 20, 28]),
    ('infinity', 'relative', 'infinity.png', 'relative', [422, 1514, 36, 18]),
    ('grace_upper1', 'ornament', 'grace_upper1.png', 'ornament', [548, 345, 23, 26]),
    ('grace_upper2', 'ornament', 'grace_upper2.png', 'ornament', [548, 467, 23, 28]),
    ('grace_lower1', 'ornament', 'grace_lower1.png', 'ornament', [545, 602, 25, 32]),
    ('grace_lower2', 'ornament', 'grace_lower2.png', 'ornament', [545, 724, 25, 33]),
    ('turn_upper', 'ornament', 'turn_upper.png', 'ornament', [547, 852, 29, 18]),
    ('turn_upper', 'ornament', 'turn_upper-S.png', 'ornament', [557, 880, 9, 17]),
    ('turn_lower', 'ornament', 'turn_lower.png', 'ornament', [545, 976, 34, 27]),
    ('lower_upper', 'ornament', 'lower_upper.png', 'ornament', [550, 1109, 23, 25]),
    ('around_four', 'ornament', 'around_four.png', 'ornament', [549, 1233, 26, 25]),
    ('triplet_upper', 'ornament', 'triplet_upper.png', 'ornament', [554, 1354, 14, 36]),
    ('triplet_lower', 'ornament', 'triplet_lower.png', 'ornament', [547, 1480, 24, 27]),
    ('long_upper', 'ornament', 'long_upper.png', 'ornament', [545, 1611, 25, 28]),
    ('long_lower', 'ornament', 'long_lower.png', 'ornament', [544, 1738, 30, 18]),
    ('lower2_upper1', 'ornament', 'lower2_upper1.png', 'ornament', [546, 1862, 25, 30]),
    ('duration_long', 'duration', 'duration_long.png', 'mark', [519, 289, 4, 20]),
    ('duration_half', 'duration', 'duration_half.png', 'mark', [518, 345, 7, 5]),
    ('rest', 'rest', 'rest.png', 'mark', [512, 434, 19, 18]),
    ('breath', 'expression', 'breath.png', 'mark', [512, 553, 18, 19]),
    ('deep_yoseong', 'expression', 'deep_yoseong.png', 'mark', [515, 1094, 12, 43]),
    ('light_yoseong', 'expression', 'light_yoseong.png', 'mark', [518, 1209, 3, 42]),
    ('deep_toeseong', 'expression', 'deep_toeseong.png', 'mark', [508, 1319, 21, 30]),
    ('light_toeseong', 'expression', 'light_toeseong.png', 'mark', [514, 1435, 12, 14]),
    ('repeat', 'repeat', 'repeat.png', 'repeat', [269, 328, 8, 13]),
    ('high:黃', 'pitch', 'pitch-0.png', 'ornament', [1033, 352, 15, 16]),
    ('high:林', 'pitch', 'pitch-1.png', 'ornament', [1032, 856, 17, 16]),
    ('high:太', 'pitch', 'pitch-2.png', 'ornament', [1036, 1114, 15, 16]),
    ('high:無', 'pitch', 'pitch-3.png', 'ornament', [1034, 1622, 16, 15]),
    ('high:仲', 'pitch', 'pitch-4.png', 'ornament', [1147, 446, 15, 17]),
    ('base:無', 'pitch', 'pitch-5.png', 'ornament', [1147, 579, 15, 17]),
    ('base:林', 'pitch', 'pitch-6.png', 'ornament', [1146, 703, 16, 17]),
]


def build(danso_pdf,braille_pdf,output,*,pdftoppm):
    import pypdfium2
    from PIL import Image
    output=Path(output)
    if output.exists() and any(output.iterdir()):
        raise ValueError('Output directory must be empty; existing atlas is never overwritten')
    output.mkdir(parents=True,exist_ok=True)
    sources={};images={}
    for group,path,page,scale in [('ornament',danso_pdf,10,3),('relative',danso_pdf,11,25/9),
                                  ('mark',danso_pdf,12,25/9),('repeat',braille_pdf,123,2)]:
        path=Path(path)
        if group in ('relative','mark'):
            with tempfile.TemporaryDirectory(prefix='jeonggan-legend-') as temp:
                prefix=Path(temp)/'page'
                subprocess.run([str(pdftoppm),'-f',str(page+1),'-l',str(page+1),'-singlefile',
                                '-r','200','-png',str(path),str(prefix)],check=True,timeout=60,
                               stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
                with Image.open(prefix.with_suffix('.png')) as image:images[group]=image.convert('L')
            renderer='poppler-pdftoppm-200dpi'
        else:
            document=pypdfium2.PdfDocument(str(path))
            images[group]=document[page].render(scale=scale).to_pil().convert('L')
            document.close();renderer='pypdfium2'
        sources[group]={'pdf_sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
                        'pdf_page_index':page,'render_scale':scale,'renderer':renderer,
                        'render_size':list(images[group].size)}
    expected={'ornament':(1616,2169),'relative':(1497,2008),'mark':(1497,2008),'repeat':(1190,1684)}
    if any(images[g].size!=size for g,size in expected.items()):
        raise ValueError('Source PDF page dimensions differ from the verified legend edition')
    entries=[]
    for token,kind,filename,group,box in ENTRIES:
        x,y,w,h=box;image=images[group].crop((x,y,x+w,y+h));path=output/filename;image.save(path,format='PNG')
        entries.append({'token':token,'kind':kind,'file':filename,'reference_group':group,
                        'reference_bbox':box,'file_sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest={'schema':'jeonggan-legend-atlas/v1','entries':entries,'source_documents':sources,
              'scope':'Generic official notation legends; local assets only; no score-page glyph training'}
    path=output/'manifest.json';path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    return path


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--danso-pdf',required=True)
    parser.add_argument('--braille-pdf',required=True)
    parser.add_argument('--output',required=True)
    parser.add_argument('--pdftoppm',required=True,help='Local Poppler executable; never downloaded or executed from OCR')
    args=parser.parse_args();print(build(args.danso_pdf,args.braille_pdf,args.output,pdftoppm=args.pdftoppm))


if __name__=='__main__':main()
