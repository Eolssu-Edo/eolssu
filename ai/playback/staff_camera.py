"""One bounded illumination/scale retry after a structurally invalid OMR result.

No notes are selected, deleted, reconstructed or supplied to this transform.
Audiveris recommends grayscale input with about 20 pixels between staff lines:
https://audiveris.github.io/audiveris/_pages/guides/advanced/scanning/
"""
import hashlib
import math
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import cv2
import numpy as np
from PIL import Image


def prepare_retry(source, omr_path, target):
    """Return evidence for a derived image, or a reason for leaving input alone.

Scale comes only from the preceding OMR graph. Restrict this recovery to one
five-line staff with measurable scale and appreciable gray background content.
The caller still rejects chords/polyphony in the subsequent MusicXML.
"""
    source, omr_path, target = map(Path, (source, omr_path, target))
    report = {'applied': False, 'method': 'illumination-grayscale-scale-v1',
              'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
              'interline_basis': 'Audiveris scale/interline; no note labels'}
    try:
        if omr_path.stat().st_size > 32*1024*1024:
            raise ValueError('OMR archive too large')
        with zipfile.ZipFile(omr_path) as archive:
            info = archive.getinfo('sheet#1/sheet#1.xml')
            if info.file_size > 8*1024*1024:
                raise ValueError('OMR graph too large')
            raw = archive.read(info)
        if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
            raise ValueError('OMR entities unsupported')
        graph = ET.fromstring(raw)
        staves = graph.findall('.//staff')
        if len(staves) != 1 or len(staves[0].findall('lines/line')) != 5:
            raise ValueError('Retry needs one five-line staff')
        interline = float(graph.find('scale/interline').get('main'))
        if not math.isfinite(interline) or not 8 <= interline <= 80:
            raise ValueError('Unreliable staff scale')
        with Image.open(source) as image:
            width, height = image.size
            if width*height > 16_000_000:
                raise ValueError('Source image exceeds 16MP')
            gray = np.array(image.convert('L'))
        middle_fraction = float(np.mean((gray > 32) & (gray < 224)))
        report.update(interline_pixels=interline, gray_middle_fraction=round(middle_fraction, 6))
        if middle_fraction < .1:
            raise ValueError('Insufficient shaded grayscale content; preserve original input')
        factor = 20/interline
        resized = (round(width*factor), round(height*factor))
        padding = 80
        if min(resized) < 1 or (resized[0]+2*padding)*(resized[1]+2*padding) > 16_000_000:
            raise ValueError('Derived image would exceed 16MP')
        background = cv2.GaussianBlur(gray, (0, 0), sigmaX=2*interline)
        normalized = cv2.divide(gray, np.maximum(background, 1), scale=230)
        normalized = cv2.resize(normalized, resized,
                                interpolation=cv2.INTER_AREA if factor < 1 else cv2.INTER_CUBIC)
        normalized = cv2.copyMakeBorder(normalized, padding, padding, padding, padding,
                                       cv2.BORDER_CONSTANT, value=255)
        Image.fromarray(normalized).save(target)
        sx, sy = resized[0]/width, resized[1]/height
        report.update(applied=True, source_size=[width, height], output_size=list(normalized.shape[::-1]),
                      target_interline_pixels=20, gaussian_sigma=2*interline, background_level=230,
                      padding_pixels=padding, source_to_preprocessed=[[sx, 0, padding], [0, sy, padding], [0, 0, 1]],
                      preprocessed_to_source=[[1/sx, 0, -padding/sx], [0, 1/sy, -padding/sy], [0, 0, 1]],
                      output_sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
                      coordinate_frame='Matrices map source.png after EXIF normalization to the preprocessed image. All subsequent OMR/model crops use the preprocessed image; no original upload regions are claimed.')
    except (OSError, ValueError, KeyError, TypeError, AttributeError, ET.ParseError, zipfile.BadZipFile) as error:
        report['skip_reason'] = str(error)
    return report
