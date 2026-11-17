-- S15P31E102-1073: 장단 기본 빠르기를 앱 전체에서 80%로 낮춘다(반올림, 편집 범위 하한 40).
-- 정간보 인식 저장·장단 고르기·편집 중 장단 변경·새 악보·오선보 박자표 기본값이 모두 이 값을 쓴다.
-- 곡별 songs.default_bpm과 이미 저장된 user_sheets.bpm은 바꾸지 않는다.
-- 이전 기본값일 때만 바꿔 운영에서 따로 고친 값은 덮어쓰지 않는다. data/reference/jangdans.json과 같다.
UPDATE jangdans SET default_bpm = CASE name
    WHEN '세마치' THEN 77
    WHEN '굿거리' THEN 53
    WHEN '중모리' THEN 70
    WHEN '자진모리' THEN 77
    WHEN '중중모리' THEN 64
    WHEN '엇모리' THEN 72
    WHEN '단모리' THEN 96
    WHEN '타령' THEN 40
    WHEN '계면가락도드리' THEN 40
    WHEN '세령산' THEN 50
    WHEN '자진타령' THEN 48
    WHEN '4박 기본 반주' THEN 128
  END
WHERE (name, default_bpm) IN (
    ('세마치', 96), ('굿거리', 66), ('중모리', 88), ('자진모리', 96), ('중중모리', 80), ('엇모리', 90),
    ('단모리', 120), ('타령', 48), ('계면가락도드리', 48), ('세령산', 63), ('자진타령', 60), ('4박 기본 반주', 160)
);
