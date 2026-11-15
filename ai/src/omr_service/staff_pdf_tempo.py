"""Supplement missing MusicXML tempo at an unambiguous PDF measure start.

PDFium uses the service lock. No extra OCR, source mutation or guessed note unit.
Audiveris geometry must agree with the exported measure count and coordinate scale.
"""
import io
import math
import re
import zipfile
import xml.etree.ElementTree as ET
from fractions import Fraction

from omr_service import staff_musicxml


def read_marks(job, *, with_bounds=False):
    if job.kind != 'PDF':
        return []
    from omr_service.api import PDFIUM_LOCK, pdfium
    pages = []
    with PDFIUM_LOCK, pdfium.PdfDocument(job.files[0]) as doc:
        for page in doc:
            textpage = page.get_textpage()
            try:
                text = textpage.get_text_range()
                scale = job.pages[len(pages)].width / page.get_width()
                positioned_marks = []
                # Explicit SMuFL metronome quarters (Leipzig Type3 in AuClair).
                # Trust the PDF's Unicode mapping, never guess arbitrary PUA units.
                # q is also used by engraved PDFs, but not inside prose words.
                for match in re.finditer(
                    r'(?:♩|(?<!\w)q|[\ueca5\ueca6])([.\uecb7])?[^\S\r\n]*='
                    r'[^\S\r\n]*(\d+(?:\.\d+)?)(?![\w.,/])', text
                ):
                    if re.match(r'[^\S\r\n]*(?:[-–—~～/,][^\S\r\n]*)?\d', text[match.end():]):
                        continue  # Ranges, fractions and adjacent numbers are not one BPM.
                    left, bottom, _, top = textpage.get_charbox(match.start())
                    unit_x = left * scale
                    # In AuClair the adjacent "Moderato" starts before beat one,
                    # while its unit glyph lies over beat two. Bind that complete
                    # printed mark only when the actual glyph boxes are adjacent.
                    label = re.search(r'\b(Moderato)[^\S\r\n]*$', text[:match.start()], re.I)
                    if label:
                        first = textpage.get_charbox(label.start(1))
                        last = textpage.get_charbox(label.end(1) - 1)
                        if (0 <= left - last[2] <= top - bottom
                                and min(top, first[3], last[3]) > max(bottom, first[1], last[1])):
                            left = first[0]
                    # The word changes only the measure anchor; accel still
                    # belongs to a nearby numeric unit at its original position.
                    mark = {'x': left * scale, 'bottom': (page.get_height()-bottom)*scale,
                            'bpm': match[2], 'dots': int(bool(match[1])), 'accel': False}
                    if with_bounds:
                        glyphs = []
                        for index in range(match.start(), match.end()):
                            if text[index].isspace():
                                continue
                            x1, y1, x2, y2 = textpage.get_charbox(index)
                            glyphs.append({'text': text[index], 'number': match.start(2) <= index < match.end(2),
                                           'bounds': [x1*scale, (page.get_height()-y2)*scale,
                                                      x2*scale, (page.get_height()-y1)*scale]})
                        mark['glyphs'] = glyphs
                    positioned_marks.append((mark, unit_x))
                for match in re.finditer(r'\b(?:molto\s+)?accel(?:erando)?\.?', text, re.I):
                    x, y, _, _ = textpage.get_charbox(match.start())
                    x, y = x * scale, (page.get_height()-y)*scale
                    candidates = [(m, unit_x) for m, unit_x in positioned_marks
                                  if 0 <= x-unit_x <= 300 and abs(y-m['bottom']) <= 40]
                    if candidates:
                        max(candidates, key=lambda item: item[1])[0]['accel'] = True
                pages.append([m for m, _ in positioned_marks])
            finally:
                textpage.close()
                page.close()
    return pages


def tuplet_regions(data, archive, marks):
    """Groups of PDF tempo ink boxes misclassified as tuplet3/6 by Audiveris.

    Require a unique matching digit, raster-rounding agreement and separation
    above the owning staff. Mask every printed tempo character, not just the6
    (which would turn160 into10), and preserve the spaces between characters.
    """
    part = ET.fromstring(staff_musicxml._xml_bytes(data)).find('part')
    measures = part.findall('measure') if part is not None else []
    eligible = []
    for index, candidates in _aligned_marks(archive, marks, len(measures)).items():
        values = {Fraction(m['bpm']) * (Fraction(3, 2) if m['dots'] else 1) for m in candidates}
        existing = [cue['bpm'] for element in measures[index]
                    if element.tag in ('direction', 'sound')
                    and (cue := staff_musicxml._tempo_direction(element)) and cue['bpm'] is not None]
        # Every erased mark must be recoverable by supplement, including when
        # the first pass already contains a conflicting machine-readable tempo.
        if len(values) == 1 and (not existing or len(existing) == 1 and existing[0] in values):
            eligible.extend(candidates)
    matched = set()
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if not re.fullmatch(r'sheet#\d+/sheet#\d+\.xml', name):
                continue
            sheet = ET.fromstring(z.read(name))
            staff_tops = {staff.get('id'): min(float(p.get('y')) for p in points)
                          for staff in sheet.iter('staff')
                          if (points := staff.findall('lines/line[1]/point'))}
            for tuplet in sheet.iter('tuplet'):
                digit = {'TUPLET_THREE': '3', 'TUPLET_SIX': '6'}.get(tuplet.get('shape'))
                bounds, top = tuplet.find('bounds'), staff_tops.get(tuplet.get('staff'))
                if digit is None or bounds is None or top is None:
                    continue
                x, y, w, h = (float(bounds.get(k)) for k in ('x', 'y', 'w', 'h'))
                box = (x, y, x+w, y+h)
                candidates = []
                for index, mark in enumerate(marks):
                    glyphs = mark.get('glyphs', [])
                    if mark not in eligible or not glyphs or any(not (b[0] < b[2] and b[1] < b[3]
                                                                       and math.ceil(b[3])+1 < top)
                                         for g in glyphs for b in [g['bounds']]):
                        continue
                    candidates += [index for glyph in glyphs if glyph['number'] and glyph['text'] == digit
                                   and all(abs(a-b) <= 2 for a, b in zip(box, glyph['bounds']))]
                if len(candidates) == 1:
                    matched.add(candidates[0])
    return [[(math.floor(b[0])-1, math.floor(b[1])-1, math.ceil(b[2])+1, math.ceil(b[3])+1)
             for glyph in marks[index]['glyphs'] for b in [glyph['bounds']]] for index in sorted(matched)]


def _aligned_marks(archive, marks, measure_count):
    """Share the same first-slot and measure-count proof before masking/restoring."""
    geometry = []
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if re.fullmatch(r'sheet#\d+/sheet#\d+\.xml', name):
                sheet = ET.fromstring(z.read(name))
                for system in sheet.iter('system'):
                    staff = system.find('part/staff')
                    if staff is None:
                        return {}
                    points = staff.findall('lines/line/point')
                    heights = sorted(set(float(p.get('y')) for p in points))
                    if len(heights) < 2:
                        return {}
                    top, spacing = heights[0], heights[1]-heights[0]
                    for stack in system.findall('stack'):
                        geometry.append((stack,top,spacing))
    if len(geometry) != measure_count:
        return {}
    aligned = {}
    for mark in marks:
        candidates = []
        for i,(stack,top,spacing) in enumerate(geometry):
            slot = stack.find('slot')
            left,right = float(stack.get('left')),float(stack.get('right'))
            first_x = left + float(slot.get('x-offset')) if slot is not None else None
            if (slot is not None and slot.get('time-offset') == '0' and left-spacing <= mark['x'] < right
                    and 0 < top-mark['bottom'] <= 8*spacing
                    # Key/time signatures can leave a wide header before the zero-time attack.
                    and (abs(mark['x']-first_x) <= 4*spacing or left <= mark['x'] <= first_x)
                    and all(abs(mark['x']-first_x) < abs(mark['x']-left-float(other.get('x-offset')))
                            for other in stack.findall('slot')[1:])):
                candidates.append(i)
        if len(candidates) != 1:
            continue
        aligned.setdefault(candidates[0], []).append(mark)
    return aligned


def supplement(data, archive, marks):
    """Accept only first-slot alignment; a midmeasure/ambiguous mark stays unbound."""
    root = ET.fromstring(staff_musicxml._xml_bytes(data))
    part = root.find('part')
    if part is None:
        return data, 0
    measures = part.findall('measure')
    aligned = _aligned_marks(archive, marks, len(measures))
    count = 0
    for index, candidates in aligned.items():
        # Resolve PDF conflicts before mutating XML: an earlier PDF insertion is
        # not an original machine-readable value that can override later marks.
        values = {Fraction(m['bpm']) * (Fraction(3, 2) if m['dots'] else 1) for m in candidates}
        if len(values) != 1:
            continue
        mark = {**candidates[0], 'accel': any(m['accel'] for m in candidates)}
        measure = measures[index]
        t = 0
        existing = []
        for element in measure:
            if element.tag == 'note' and element.find('chord') is None and element.find('grace') is None:
                t += int(element.findtext('duration') or 0)
            elif element.tag in ('backup','forward'):
                t += int(element.findtext('duration') or 0) * (-1 if element.tag == 'backup' else 1)
            elif element.tag in ('direction','sound'):
                cue = staff_musicxml._tempo_direction(element)
                if cue and cue['bpm'] is not None:
                    existing.append((element,cue,t + Fraction(element.findtext('offset') or 0)))
        # Existing machine-readable values win; contradictory PDF text is not silently substituted.
        if existing:
            expected = Fraction(mark['bpm']) * (Fraction(3,2) if mark['dots'] else 1)
            if len(existing) == 1 and existing[0][1]['bpm'] == expected:
                element, cue, position = existing[0]
                changed = False
                if position != 0:
                    measure.remove(element)
                    for offset in element.findall('offset'):
                        element.remove(offset)
                    measure.insert(0,element)
                    changed = True
                if mark['accel'] and not cue['accel']:
                    direction = element
                    if element.tag == 'sound':
                        direction = ET.Element('direction')
                        measure.insert(list(measure).index(element)+1,direction)
                    ET.SubElement(ET.SubElement(direction,'direction-type'),'words').text = 'accel.'
                    changed = True
                count += int(changed)
            continue
        direction = ET.Element('direction',placement='above')
        metronome = ET.SubElement(ET.SubElement(direction,'direction-type'),'metronome')
        ET.SubElement(metronome,'beat-unit').text = 'quarter'
        if mark['dots']:
            ET.SubElement(metronome,'beat-unit-dot')
        ET.SubElement(metronome,'per-minute').text = mark['bpm']
        if mark['accel']:
            ET.SubElement(ET.SubElement(direction,'direction-type'),'words').text = 'accel.'
        measure.insert(0,direction)
        count += 1
    return ET.tostring(root), count
