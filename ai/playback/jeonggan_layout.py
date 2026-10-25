"""Image-derived Jeonggan page geometry; no piece coordinates or note templates."""
import cv2
import numpy as np

from .recognition import RecognitionError, _require


def _groups(indices, gap=1):
    return [] if not len(indices) else np.split(indices, np.flatnonzero(np.diff(indices)>gap)+1)


def _centre(group):
    return int(round(float(np.mean(group))))


def segment_page(pixels):
    """Find one connected grid, musical columns, closed cells and ink rows.

    Long strokes locate the grid independently of surrounding prose/staves.
    Columns containing only marginal marks are retained as excluded evidence.
    Blank cells within a musical column remain explicit time-bearing cells.
    """
    source_height, source_width = pixels.shape[:2]
    scale = min(1., 2500/max(source_height, source_width))
    source_gray = cv2.cvtColor(pixels, cv2.COLOR_RGB2GRAY)
    gray = source_gray
    if scale < 1:
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
    _, ink = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV+cv2.THRESH_OTSU)
    h, w = gray.shape
    vertical = cv2.morphologyEx(ink, cv2.MORPH_OPEN, np.ones((max(25,round(h*.06)),1),np.uint8))
    horizontal = cv2.morphologyEx(ink, cv2.MORPH_OPEN, np.ones((1,max(20,round(w*.025))),np.uint8))
    lines = cv2.dilate(vertical|horizontal, np.ones((3,3),np.uint8))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(lines)
    candidates=[]
    for i in range(1,count):
        x,y,cw,ch,area=map(int,stats[i])
        if cw >= max(40,w*.06) and ch >= max(40,h*.12) and area > (cw+ch)*2:
            vx = _groups(np.flatnonzero((vertical[y:y+ch,x:x+cw]>0).sum(axis=0)>ch*.3))
            if len(vx)>=2:
                candidates.append((x,y,cw,ch))
    _require(len(candidates)==1, "Exactly one connected Jeonggan grid required; no grid or ambiguous multiple grids")
    gx,gy,gw,gh=candidates[0]
    xs=[gx+_centre(g) for g in _groups(np.flatnonzero((vertical[gy:gy+gh,gx:gx+gw]>0).sum(axis=0)>gh*.3))]
    _require(len(xs)>=2, "Cannot find Jeonggan column borders")
    preliminary=[]
    rejected=[]
    for column,(left,right) in enumerate(zip(xs,xs[1:])):
        if right-left<15:
            continue
        ys=[gy+_centre(g) for g in _groups(np.flatnonzero((horizontal[gy:gy+gh,left:right+1]>0).mean(axis=1)>.75))]
        for top,bottom in zip(ys,ys[1:]):
            if bottom-top<18:
                continue
            side_left=(ink[top:bottom,max(0,left-2):left+3]>0).any(axis=1).mean()
            side_right=(ink[top:bottom,max(0,right-2):right+3]>0).any(axis=1).mean()
            if min(side_left,side_right)<.70:
                rejected.append({'column':column,'bbox':[left,top,right-left,bottom-top],
                                 'reason':'incomplete side borders'})
                continue
            margin=max(3,round((right-left)*.08))
            inside=ink[top+3:bottom-2,left+margin:right-margin]
            core=inside[:,round(inside.shape[1]*.18):max(1,round(inside.shape[1]*.82))]
            preliminary.append({'column':column,'bbox':[left,top,right-left,bottom-top],
                                'core_ink':int((core>0).sum()),'ink':int((inside>0).sum())})
    _require(preliminary, "No closed Jeonggan cells found")
    # Repeated cell heights give the printed rhythmic unit, not a piece-specific count.
    heights=[c['bbox'][3] for c in preliminary if c['core_ink']>=8]
    _require(heights, "Grid contains no musical ink")
    best=max(heights,key=lambda value:sum(abs(other-value)<=value*.15 for other in heights))
    unit=float(np.median([value for value in heights if abs(value-best)<=best*.15]))
    ignored=list(rejected)
    eligible=[]
    for cell in preliminary:
        units=round(cell['bbox'][3]/unit)
        if units<1 or units>4 or abs(cell['bbox'][3]/unit-units)>.25:
            ignored.append({**cell,'reason':'non-rhythmic header/footer geometry'})
        else:
            cell['units']=units
            eligible.append(cell)
    groups={column:[c for c in eligible if c['column']==column]
            for column in {c['column'] for c in eligible}}
    occupied_columns={column for column,group in groups.items()
                      if any(c['core_ink']>=max(8,unit*c['bbox'][2]*.004) for c in group)}
    musical_widths=[c['bbox'][2] for column in occupied_columns for c in groups[column]]
    _require(musical_widths, 'Cannot distinguish musical columns from annotation columns')
    music_width=float(np.median(musical_widths))
    columns=[]
    for column in sorted(groups,reverse=True):
        group=groups[column]
        # Preserve sparse long-note columns. A full blank column is rhythmic only
        # when both its width and regular cell divisions agree with music columns.
        blank_music=(all(c['core_ink']==0 and c['units']==1 for c in group)
                     and abs(group[0]['bbox'][2]/music_width-1)<.10)
        if column in occupied_columns or blank_music:
            columns.append(group)
        else:
            ignored.extend({**c,'reason':'column has only blank/marginal ink'} for c in group)
    _require(columns, "Cannot distinguish musical columns from annotation columns")
    cells=[cell for column in columns for cell in sorted(column,key=lambda c:c['bbox'][1])]
    _require(len(cells)<=256,"Page exceeds 256 Jeonggan cells")
    for ci,cell in enumerate(cells):
        cell['bbox']=[round(v/scale) for v in cell['bbox']]
        x,y,cw,ch=cell['bbox']
        margin=max(3,round(cw*.08))
        edge=max(3,round(3/scale))
        content=source_gray[y+edge:y+ch-edge,x+margin:x+cw-margin]<170
        row_groups=_groups(np.flatnonzero(content.sum(axis=1)>=2),gap=max(2,round(unit/scale*.025)))
        rows=[]
        for group in row_groups:
            top,bottom=int(group[0]),int(group[-1])+1
            columns=np.flatnonzero((content[top:bottom]>0).sum(axis=0)>0)
            if len(columns)<2 or (bottom-top<2 and len(columns)<4):
                continue
            left,right=int(columns[0]),int(columns[-1])+1
            box=[x+margin+left,y+edge+top,right-left,bottom-top]
            rows.append({'bbox':box,'relative_y':(edge+(top+bottom)/2)/ch})
        cell['id']=f'j{ci+1}'
        cell['rows']=rows
        cell['blank']=not rows
    for missing in rejected:
        same_column=[c for c in cells if c['column']==missing['column']]
        if same_column:
            top=min(c['bbox'][1] for c in same_column)
            bottom=max(c['bbox'][1]+c['bbox'][3] for c in same_column)
            midpoint=(missing['bbox'][1]+missing['bbox'][3]/2)/scale
            missing_units=missing['bbox'][3]/unit
            rhythmic_height=(1 <= round(missing_units) <= 4
                             and abs(missing_units-round(missing_units)) <= .25)
            # Border loss in the first/last rhythmic cell must not shorten the
            # score. Non-rhythmic open header/footer bands may still be excluded.
            _require(not (top < midpoint < bottom or rhythmic_height),
                     'Selected musical column has an incomplete cell; cannot skip it')
    for cell in ignored:
        cell['bbox']=[round(v/scale) for v in cell['bbox']]
    return {'grid_bbox':[round(v/scale) for v in candidates[0]],'cells':cells,
            'ignored_regions':ignored,'unit_height_pixels':unit/scale,
            'image_size':[source_width,source_height]}
