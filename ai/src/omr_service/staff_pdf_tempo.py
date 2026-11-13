"""Supplement missing MusicXML tempo at an unambiguous PDF measure start.

PDFium uses the service lock. No extra OCR, source mutation or guessed note unit.
Audiveris geometry must agree with the exported measure count and coordinate scale.
"""
import io
import re
import zipfile
import xml.etree.ElementTree as ET
from fractions import Fraction

from omr_service import staff_musicxml


def read_marks(job):
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
                marks = []
                # q is the quarter-note glyph encoding in many engraved PDFs.
                for match in re.finditer(r'(?:♩|q)(\.)?\s*=\s*(\d+(?:\.\d+)?)(?![\d.])', text):
                    if re.match(r'\s*[-–—]\s*\d', text[match.end():]):
                        continue  # A range does not specify one BPM.
                    left, bottom, _, _ = textpage.get_charbox(match.start())
                    marks.append({'x': left * scale, 'bottom': (page.get_height()-bottom)*scale,
                                  'bpm': match[2], 'dots': int(bool(match[1])), 'accel': False})
                for match in re.finditer(r'\b(?:molto\s+)?accel(?:erando)?\.?', text, re.I):
                    x, y, _, _ = textpage.get_charbox(match.start())
                    x, y = x * scale, (page.get_height()-y)*scale
                    candidates = [m for m in marks if 0 <= x-m['x'] <= 300 and abs(y-m['bottom']) <= 40]
                    if candidates:
                        max(candidates,key=lambda m:m['x'])['accel'] = True
                pages.append(marks)
            finally:
                textpage.close()
                page.close()
    return pages


def supplement(data, archive, marks):
    """Accept only first-slot alignment; a midmeasure/ambiguous mark stays unbound."""
    root = ET.fromstring(staff_musicxml._xml_bytes(data))
    part = root.find('part')
    if part is None:
        return data, 0
    measures = part.findall('measure')
    geometry = []
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if re.fullmatch(r'sheet#\d+/sheet#\d+\.xml', name):
                sheet = ET.fromstring(z.read(name))
                for system in sheet.iter('system'):
                    staff = system.find('part/staff')
                    if staff is None:
                        return data, 0
                    points = staff.findall('lines/line/point')
                    heights = sorted(set(float(p.get('y')) for p in points))
                    if len(heights) < 2:
                        return data, 0
                    top, spacing = heights[0], heights[1]-heights[0]
                    for stack in system.findall('stack'):
                        geometry.append((stack,top,spacing))
    if len(geometry) != len(measures):
        return data, 0
    count = 0
    for mark in marks:
        candidates = []
        for i,(stack,top,spacing) in enumerate(geometry):
            slot = stack.find('slot')
            left,right = float(stack.get('left')),float(stack.get('right'))
            if (slot is not None and slot.get('time-offset') == '0' and left-spacing <= mark['x'] < right
                    and 0 < top-mark['bottom'] <= 8*spacing
                    and abs(mark['x']-left-float(slot.get('x-offset'))) <= 4*spacing
                    and all(abs(mark['x']-left-float(slot.get('x-offset'))) < abs(mark['x']-left-float(other.get('x-offset')))
                            for other in stack.findall('slot')[1:])):
                candidates.append(i)
        if len(candidates) != 1:
            continue
        measure = measures[candidates[0]]
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
