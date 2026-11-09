-- Keep the scored school textbook editions public and retain the unscored reference editions.
UPDATE songs AS s
JOIN instruments AS i ON i.id = s.instrument_id AND i.code = 'SOGEUM'
JOIN (
    SELECT 92 AS song_id, 'mr203-fixed-sogeum-004-p1' AS source_id
    UNION ALL SELECT 97, 'mr203-fixed-sogeum-009-p1'
    UNION ALL SELECT 107, 'mr203-fixed-sogeum-018-p1'
) AS editions ON s.id = editions.song_id
    AND s.score_json->>'$.score.sourceId' = editions.source_id
LEFT JOIN song_model_audios AS a ON a.song_id = s.id AND a.status = 'PUBLISHED'
SET s.status = 'HIDDEN',
    a.status = 'DRAFT'
WHERE s.status = 'PUBLISHED'
  AND s.is_scorable = FALSE
  AND s.deleted_at IS NULL;
