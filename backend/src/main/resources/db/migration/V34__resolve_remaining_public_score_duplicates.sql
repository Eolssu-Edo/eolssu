-- National Gugak Center daegeum classroom editions, matched to their imported sources.
UPDATE songs AS s
JOIN instruments AS i ON i.id = s.instrument_id AND i.code = 'JEONGAK_DAEGEUM'
JOIN (
    SELECT 54 AS song_id, 'mr203-fixed-jeongak-daegeum-004-p1' AS source_id,
           '뱃노래' AS old_title, '뱃노래(기본)' AS new_title
    UNION ALL SELECT 55, 'mr203-fixed-jeongak-daegeum-005-p1', '뱃노래', '뱃노래(장식음)'
    UNION ALL SELECT 58, 'mr203-fixed-jeongak-daegeum-008-p1', '아리랑', '아리랑(역취)'
    UNION ALL SELECT 59, 'mr203-fixed-jeongak-daegeum-009-p1', '아리랑', '아리랑(저취)'
    UNION ALL SELECT 63, 'mr203-fixed-jeongak-daegeum-013-p1', '세령산 1장', '세령산 1장(장식음 기호)'
    UNION ALL SELECT 64, 'mr203-fixed-jeongak-daegeum-014-p1', '세령산 1장', '세령산 1장(풀어 쓴 악보)'
    UNION ALL SELECT 65, 'mr203-fixed-jeongak-daegeum-015-p1', '타령 1장', '타령 1장(장식음 기호)'
    UNION ALL SELECT 66, 'mr203-fixed-jeongak-daegeum-016-p1', '타령 1장', '타령 1장(풀어 쓴 악보)'
    UNION ALL SELECT 72, 'mr203-fixed-jeongak-daegeum-022-p1', '학교종', '학교종(평취)'
    UNION ALL SELECT 73, 'mr203-fixed-jeongak-daegeum-023-p1', '학교종', '학교종(저취)'
    UNION ALL SELECT 75, 'mr203-fixed-jeongak-daegeum-025-p1', '“황응남임” 연습곡', '“황응남임” 연습곡(평취)'
    UNION ALL SELECT 76, 'mr203-fixed-jeongak-daegeum-026-p1', '“황응남임” 연습곡', '“황응남임” 연습곡(저취)'
) AS titles ON s.id = titles.song_id
    AND s.score_json->>'$.score.sourceId' = titles.source_id
SET s.title = titles.new_title,
    s.score_json = JSON_SET(s.score_json, '$.score.title', titles.new_title)
WHERE s.deleted_at IS NULL
  AND s.title = titles.old_title
  AND s.score_json->>'$.score.title' = titles.old_title;
