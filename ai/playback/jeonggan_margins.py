"""Nearby notation marks, respecting the grid's independently detected regions."""
import cv2
import numpy as np


def _inside(box, region):
    x,y,w,h=box
    rx,ry,rw,rh=region
    return rx<=x and ry<=y and x+w<=rx+rw and y+h<=ry+rh


def margin_components(pixels,layout,typical):
    """Find outside marks without reinterpreting rejected text columns as music.

    Blank/marginal grid regions remain searchable: that is where a legitimate
    outside tuplet or length mark can live. Only positively rejected non-rhythmic
    header/footer geometry and interiors of other music cells are excluded.
    """
    gray=cv2.cvtColor(pixels,cv2.COLOR_RGB2GRAY)
    ink=(gray<170).astype(np.uint8)
    unit=layout['unit_height_pixels']
    vertical=cv2.morphologyEx(ink,cv2.MORPH_OPEN,np.ones((max(10,round(unit*.8)),1),np.uint8))
    widths=[c['bbox'][2] for c in layout['cells']]
    horizontal=cv2.morphologyEx(ink,cv2.MORPH_OPEN,np.ones((1,max(10,round(np.median(widths)*.8))),np.uint8))
    clean=ink.copy();clean[(vertical|horizontal)>0]=0
    _,labels,global_stats,_=cv2.connectedComponentsWithStats(clean)
    excluded=[c['bbox'] for c in layout['cells']]
    excluded.extend(r['bbox'] for r in layout.get('ignored_regions',[])
                    if r.get('reason')=='non-rhythmic header/footer geometry')
    found={}
    for cell in layout['cells']:
        cx,cy,cw,ch=cell['bbox']
        for row in cell['rows']:
            rx,ry,rw,rh=row['bbox'];center=ry+rh/2
            for side,left,right in [('left',max(0,round(cx-cw*.25)),cx-3),
                                    ('right',cx+cw+3,min(gray.shape[1],round(cx+cw*1.25)))]:
                top=max(cy+3,round(center-typical*.9));bottom=min(cy+ch-3,round(center+typical*.9))
                if right<=left or bottom<=top:continue
                for label in np.unique(labels[top:bottom,left:right]):
                    if label==0:continue
                    x,y,w,h,area=global_stats[label]
                    if area<3 or h<typical*.35:continue
                    if h>typical*2 or w>typical*2:continue
                    box=[int(x),int(y),int(w),int(h)]
                    if any(_inside(box,region) for region in excluded):continue
                    key=int(label);distance=abs(box[1]+h/2-center)
                    candidate={'cell_id':cell['id'],'row_id':row['id'],'bbox':box,'side':side,
                               'row_center_distance':distance}
                    if key not in found or distance<found[key]['row_center_distance']:
                        found[key]=candidate
                    elif abs(distance-found[key]['row_center_distance'])<.5:
                        found[key]['ambiguous_row']=True
    return list(found.values())
