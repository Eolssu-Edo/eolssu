-- S15P31E102-746: user-approved arrangement, not a newly named traditional jangdan.
-- The operational registration already uses ID12. Reuse it only when timing and pattern match.
CREATE TEMPORARY TABLE v40_jangdan_guard (ok TINYINT NOT NULL CHECK (ok=1));
INSERT INTO v40_jangdan_guard
SELECT IF(COUNT(*)=0,1,0) FROM jangdans
WHERE (id=12 OR name='4박 기본 반주') AND NOT (
  id=12 AND BINARY name=BINARY '4박 기본 반주' AND default_bpm=160 AND bpm_unit='QUARTER'
  AND pattern=CAST('{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}' AS JSON)
);
INSERT INTO jangdans (id,name,pattern,default_bpm,bpm_unit,source)
SELECT 12,'4박 기본 반주','{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}',160,'QUARTER',
  '사용자 제공 Blue Valentine JGB의 덩 | 덕 | 궁 | 덕. 4/4, 1칸=4분음표1박. 기본160 BPM은 원본 시작 빠르기. 사용자 승인 편곡 반주 패턴이며 전통 장단 기본형으로 확정한 명칭이 아님. S15P31E102-746.'
WHERE NOT EXISTS (SELECT 1 FROM jangdans WHERE id=12);
DROP TEMPORARY TABLE v40_jangdan_guard;
