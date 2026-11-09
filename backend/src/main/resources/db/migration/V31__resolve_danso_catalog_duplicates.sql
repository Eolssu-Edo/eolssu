-- The app checks that the catalog title and the title in the common score match.
UPDATE songs AS s
JOIN (
    SELECT 31 AS song_id, 'mr203-fixed-danso-027-p1' AS source_id, '어이 얼어자리 · 단소1' AS part_title
    UNION ALL
    SELECT 30, 'mr203-fixed-danso-026-p1', '어이 얼어자리 · 단소2'
) AS parts ON s.id = parts.song_id
    AND s.score_json->>'$.score.sourceId' = parts.source_id
SET s.title = parts.part_title,
    s.score_json = JSON_SET(s.score_json, '$.score.title', parts.part_title)
WHERE s.instrument_id = 1
  AND s.deleted_at IS NULL
  AND s.title = '어이 얼어자리'
  AND s.score_json->>'$.score.title' = '어이 얼어자리';

-- Retain the reference edition and its model audio while removing public access.
UPDATE songs AS s
LEFT JOIN song_model_audios AS a ON a.song_id = s.id AND a.status = 'PUBLISHED'
SET s.status = 'HIDDEN',
    a.status = 'DRAFT'
WHERE s.id = 16
  AND s.instrument_id = 1
  AND s.score_json->>'$.score.sourceId' = 'mr203-fixed-danso-012-p1'
  AND s.status = 'PUBLISHED'
  AND s.deleted_at IS NULL;
