"""Printed hanbae signs outside the melody cells; no numeric tempo inference.

Text uses actual jamo, not an interpretation of ornament names. The scan path
accepts upright connected jamo with a separating dot and a dotted continuation.
That narrow geometry is deliberately separate from the note model vocabulary.
"""
import re
import unicodedata

ALIASES = {'ㅈㄴ': '조금느리게', 'ㅈㅅ': '조금속하게', 'ㅈㅈㄴ': '점점느리게',
           'ㅈㅈㅅ': '점점속하게', 'ㅂㅅ': '본래속도로'}
JAMO = str.maketrans({'ᄌ': 'ㅈ', 'ᄂ': 'ㄴ', 'ᄉ': 'ㅅ', 'ᄇ': 'ㅂ', 'ᆫ': 'ㄴ', 'ᆺ': 'ㅅ'})
DOTS = '.·ㆍ‧∙⋅ᆞ'


def canonical(raw):
    text = unicodedata.normalize('NFKC', raw).translate(JAMO)
    text = re.sub(r'[\s' + re.escape(DOTS) + r']', '', text)
    return ALIASES.get(text)


def _located(mark, cells, x, y):
    choices, counts = [], {}
    for cell in cells:
        gak = cell['gak']
        index = counts.get(gak, 0)
        counts[gak] = index + 1
        # A sign belongs to the right-hand margin of a cell, never the next
        # available melody column when the grid reader has omitted its column.
        if (cell['x'] + cell['w'] < x < cell['x'] + cell['w'] * 1.65 and
                cell['y'] <= y < cell['y'] + cell['h']):
            choices.append((gak, index))
    if len(choices) == 1:
        mark.update(gak=choices[0][0], jeonggan=choices[0][1])
    return mark


def text_marks(glyphs, cells):
    letters = []
    for glyph in glyphs:
        char = unicodedata.normalize('NFKC', glyph['c']).translate(JAMO)
        if char in 'ㅈㄴㅅㅂ' + DOTS:
            letters.append(glyph | {'c': char, 'rawC': glyph['c']})
    out, used = [], set()
    for axis in ('vertical', 'horizontal'):
        ordered = sorted(range(len(letters)), key=lambda i: (letters[i]['y'], letters[i]['x']))
        for start in ordered:
            if start in used or letters[start]['c'] not in 'ㅈㅂ':
                continue
            group = [start]
            for _ in range(4):
                last = letters[group[-1]]
                candidates = []
                for i, item in enumerate(letters):
                    if i in group or i in used:
                        continue
                    size = max(last['size'], item['size'])
                    forward = item['y'] - last['y'] if axis == 'vertical' else item['x'] - last['x']
                    side = abs(item['x'] - last['x']) if axis == 'vertical' else abs(item['y'] - last['y'])
                    if 0 < forward < size * 2 and side < size * .55:
                        candidates.append((forward, i))
                if not candidates:
                    break
                group.append(min(candidates)[1])
            raw = ''.join(letters[i]['c'] for i in group)
            name = canonical(raw)
            if name is None:
                continue
            first = letters[start]
            x0 = min(letters[i]['x'] - letters[i]['size'] / 2 for i in group)
            y0 = min(letters[i]['y'] - letters[i]['size'] / 2 for i in group)
            x1 = max(letters[i]['x'] + letters[i]['size'] / 2 for i in group)
            y1 = max(letters[i]['y'] + letters[i]['size'] / 2 for i in group)
            mark = dict(symbol=name, rawText=''.join(letters[i]['rawC'] for i in group),
                        bbox=[x0, y0, x1 - x0, y1 - y0])
            out.append(_located(mark, cells, first['x'], first['y']))
            used.update(group)
    return out


def _letter(mask):
    """Conservative topology for a connected, upright jamo glyph."""
    import numpy as np
    h, w = mask.shape
    if h < 5 or w < 5 or not .45 <= h / w <= 1.6:
        return None
    third, quarter = max(1, h // 3), max(1, w // 4)
    left = mask[:h * 3 // 4, :quarter].mean()
    right = mask[:h * 3 // 4, -quarter:].mean()
    bottom = mask[-max(2, h // 5):].mean()
    if left > .58 and bottom > .65 and right < .35:
        return 'ㄴ'
    if left > .52 and right > .52 and bottom > .65 and mask[h // 2 - 1:h // 2 + 2].mean() > .65:
        return 'ㅂ'
    # Both diagonal feet must survive; a note's ordinary horizontal dash or
    # single angled ornament cannot become a tempo letter.
    feet = mask[-third:]
    if feet[:, :quarter].mean() < .2 or feet[:, -quarter:].mean() < .2:
        return None
    if mask[:max(2, h // 4)].mean() > .48:
        return 'ㅈ'
    top = np.nonzero(mask[:max(2, h // 4)])[1]
    if len(top) and .3 < float(top.mean()) / w < .75:
        return 'ㅅ'
    return None


def image_marks(image, cells):
    """Read dotted, vertical printed cues. Return unanchored candidates too."""
    if not cells:
        return []
    image = image.convert('L')
    if image.getextrema()[0] >= 150:
        return []
    import cv2
    import numpy as np
    gray = np.asarray(image)
    ink = (gray < 150).astype('uint8')
    _, labels, stats, _ = cv2.connectedComponentsWithStats(ink)
    typical = sorted(c['w'] for c in cells)[len(cells) // 2]
    dots, letters = [], []
    for label, (x, y, w, h, area) in enumerate(stats[1:], 1):
        x, y, w, h, area = map(int, (x, y, w, h, area))
        if (2 <= area and 1 <= w <= typical * .085 and 1 <= h <= typical * .085 and
                .35 <= w / h <= 2.8):
            dots.append((x + w / 2, y + h / 2, w, h))
        if not (typical * .075 <= w <= typical * .34 and typical * .06 <= h <= typical * .35):
            continue
        letter = _letter(labels[y:y + h, x:x + w] == label)
        if letter:
            letters.append(dict(c=letter, x=x, y=y, w=w, h=h))
    letters.sort(key=lambda c: (c['y'], c['x']))
    out, used = [], set()
    for start, first in enumerate(letters):
        if start in used or first['c'] not in 'ㅈㅂ':
            continue
        group = [start]
        for _ in range(2):
            last = letters[group[-1]]
            cx = last['x'] + last['w'] / 2
            choices = []
            for i, item in enumerate(letters):
                gap = item['y'] - last['y'] - last['h']
                size = max(item['w'], last['w'])
                if (i in group or i in used or not 2 <= gap <= size * 1.7 or
                        abs(item['x'] + item['w'] / 2 - cx) > size * .3):
                    continue
                if not any(abs(dx - cx) <= size * .23 and last['y'] + last['h'] <= dy <= item['y']
                           for dx, dy, _, _ in dots):
                    continue
                choices.append((item['y'], i))
            if not choices:
                break
            group.append(min(choices)[1])
        raw = ''.join(letters[i]['c'] for i in group)
        name = ALIASES.get(raw)
        if name is None:
            continue
        last = letters[group[-1]]
        cx, end = last['x'] + last['w'] / 2, last['y'] + last['h']
        continuation = [d for d in dots if abs(d[0] - cx) <= last['w'] * .25 and
                        end < d[1] < end + last['w'] * 3.6]
        if len(continuation) < 2:
            continue
        x0 = min(letters[i]['x'] for i in group)
        x1 = max(letters[i]['x'] + letters[i]['w'] for i in group)
        mark = dict(symbol=name, rawText=raw, bbox=[x0, first['y'], x1 - x0, end - first['y']])
        out.append(_located(mark, cells, first['x'] + first['w'] / 2, first['y'] + first['h'] / 2))
        used.update(group)
    return out
