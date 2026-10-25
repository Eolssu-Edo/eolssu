"""Conservative image check for inconsistent Jeonggan octave transcripts.

This verifies high-register classifications whose literal OCR transcript is the
same unmodified yul character, possibly omitting a radical. It is not a general glyph recognizer: uncertain
or damaged evidence becomes unknown instead of defaulting to a lower octave.
"""
from copy import deepcopy
import unicodedata

import cv2
import numpy as np


def _tree_body(mask):
    """Positive 木-shaped strokes: central stem, crossbar, both lower branches."""
    h,w=mask.shape
    if h<12 or w<5:
        return False
    sums=mask.sum(axis=0)
    stem=int(np.argmax(sums))
    if not (.25*w<=stem<=.75*w and sums[stem]>=h*.80):
        return False
    crossbar=False
    for y in range(round(h*.15),max(round(h*.15)+1,round(h*.55))):
        columns=np.flatnonzero(mask[y])
        if len(columns)>=w*.65 and columns[0]<=stem-w*.22 and columns[-1]>=stem+w*.22:
            crossbar=True
            break
    lower=mask[round(h*.50):max(round(h*.50)+1,round(h*.95))]
    left=int(lower[:,:max(1,round(stem-w*.20))].sum())
    right=int(lower[:,min(w-1,round(stem+w*.20)):].sum())
    return bool(crossbar and min(left,right)>=max(3,round(h*.12)))


def _features(gray,threshold):
    h,w=gray.shape
    ink=(gray<threshold).astype(np.uint8)
    _,labels,stats,_=cv2.connectedComponentsWithStats(ink)
    components=[{'bbox':[int(v) for v in s[:4]],'area':int(s[4]),'label':index}
                for index,s in enumerate(stats[1:],1) if s[4]>=2]
    prefix_width=max(1,round(w*.26))
    _,_,prefix_stats,_=cv2.connectedComponentsWithStats(ink[:,:prefix_width])
    prefix_components=[{'bbox':[int(v) for v in s[:4]],'area':int(s[4])}
                       for s in prefix_stats[1:] if s[4]>=2]
    total=int(ink.sum())
    dots=[]
    if w>=12 and h>=12 and total:
        for component in prefix_components:
            cx,cy,cw,ch=component['bbox']
            if (cx+cw/2<=w*.23 and cw<=w*.30 and ch<=h*.28
                    and cy+ch<=h*.55 and component['area']<=total*.12):
                dots.append(component)
    upper=[c for c in dots if c['bbox'][1]+c['bbox'][3]/2<=h*.25]
    middle=[c for c in dots if h*.25<c['bbox'][1]+c['bbox'][3]/2<=h*.55]
    connected_lower=any(h*.20<=c['bbox'][1]<=h*.45 and c['bbox'][3]>=h*.55
                        for c in prefix_components)
    lower_ink=int(ink[round(h*.55):,:max(1,round(w*.30))].sum())
    right_ink=int(ink[:,round(w*.30):].sum())
    water=(bool(upper) and bool(middle or connected_lower)
           and lower_ink>=max(2,round(h*.05)) and right_ink>=total*.30)
    tree_bodies=[]
    for component in components:
        x,y,cw,ch=component['bbox']
        if ch>=h*.80 and w*.25<=cw<=w*.70 and component['area']>=total*.18:
            if _tree_body(labels[y:y+ch,x:x+cw]==component['label']):
                tree_bodies.append(component)
    # A tall connected left shape can be joined water strokes. Require the
    # complete paired-tree body structure, not just its bounding rectangle.
    plain=(any(c['bbox'][0]<=w*.15 for c in tree_bodies)
           and any(c['bbox'][0]>=w*.25 and c['bbox'][0]+c['bbox'][2]>=w*.85 for c in tree_bodies))
    return {'threshold':threshold,'components':components,'prefix_width':prefix_width,
            'prefix_components':prefix_components,'water_upper_dots':len(upper),
            'water_middle_dots':len(middle),'water_joined_lower_stroke':connected_lower,
            'lower_left_ink':lower_ink,'foreground_ink':total,'plain_left_body':bool(plain),
            'paired_tree_bodies':tree_bodies,'water_supported':bool(water)}


def validate_register(pixels, bbox, classification):
    """Return classification plus image evidence when a register check applies."""
    items=classification.get('items',[])
    if len(items)!=1:
        return classification,None
    item=items[0]
    token=item['token']
    if not token.startswith('high:') or unicodedata.normalize('NFKC',item['glyph'])!=token[5:]:
        return classification,None
    # The literal transcript may have dropped the radical. Neither the token nor
    # the transcript alone resolves this information loss; inspect the image.
    x,y,w,h=bbox
    gray=cv2.cvtColor(pixels[y:y+h,x:x+w],cv2.COLOR_RGB2GRAY)
    evidence_170=_features(gray,170)
    attempts=[evidence_170]
    selected=evidence_170
    if not selected['water_supported'] and not selected['plain_left_body']:
        # Dark core pixels can separate antialiased bridges without inventing
        # strokes. Require substantial retained ink and positive water structure.
        core=_features(gray,100)
        core['retained_ink_fraction']=core['foreground_ink']/max(1,evidence_170['foreground_ink'])
        attempts.append(core)
        if core['water_supported'] and core['retained_ink_fraction']>=.45:
            selected=core
    value=deepcopy(classification)
    if selected['water_supported'] and not selected['plain_left_body']:
        outcome='water-radical-supported'
    elif selected['plain_left_body'] and not selected['water_supported'] and w>=12 and h>=12:
        value['items'][0]['token']='base:'+token[5:]
        outcome='plain-body-supported'
    else:
        value['items'][0]['token']='unknown'
        value['uncertain']=True
        outcome='ambiguous-register'
    evidence={**selected,'method':'connected-ink-register-consistency/v2','original_token':token,
              'original_glyph':item['glyph'],'result_token':value['items'][0]['token'],
              'outcome':outcome,'bbox':list(bbox),'attempts':attempts}
    return value,evidence
