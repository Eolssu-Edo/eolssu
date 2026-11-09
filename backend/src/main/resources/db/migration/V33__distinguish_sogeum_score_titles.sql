-- NGC textbook PDF pages 202 (duet), 203 (advanced), 201 (intermediate), 190 (beginner).
-- Match the catalog title and common score title without changing the score version.
UPDATE songs AS s
JOIN instruments AS i ON i.id = s.instrument_id AND i.code = 'SOGEUM'
JOIN (
    SELECT 104 AS song_id, 'mr203-fixed-sogeum-016-p1' AS source_id,
           '북천이 맑다커늘' AS old_title, '북천이 맑다커늘 · 소금1' AS new_title
    UNION ALL SELECT 105, 'mr203-fixed-sogeum-016-p2', '북천이 맑다커늘', '북천이 맑다커늘 · 소금2'
    UNION ALL SELECT 109, 'mr203-fixed-sogeum-020-p1', '취타 중 초장', '취타 중 초장(고급)'
    UNION ALL SELECT 110, 'mr203-fixed-sogeum-021-p1', '취타 중 초장', '취타 중 초장(중급)'
    UNION ALL SELECT 111, 'mr203-fixed-sogeum-022-p1', '취타 중 초장', '취타 중 초장(초급)'
) AS titles ON s.id = titles.song_id
    AND s.score_json->>'$.score.sourceId' = titles.source_id
SET s.title = titles.new_title,
    s.score_json = JSON_SET(s.score_json, '$.score.title', titles.new_title)
WHERE s.deleted_at IS NULL
  AND s.title = titles.old_title
  AND s.score_json->>'$.score.title' = titles.old_title;
