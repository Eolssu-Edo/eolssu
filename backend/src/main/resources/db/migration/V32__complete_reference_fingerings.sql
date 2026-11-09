-- S15P31E102-601. Add six verified reference entries and correct the Sogeum high Tae diagram.
CREATE TEMPORARY TABLE _fingering601_delta AS
SELECT instrument.id AS instrument_id, pitch.id AS pitch_id,
    CASE WHEN revised.code = 'SOGEUM' THEN
        JSON_SET(reference.pattern_json, '$.holes', CAST(revised.holes AS JSON), '$.source',
            '출처: 국립국악원 『창작을 위한 국악기 이해와 활용 2』 3장 소금 61쪽 [표 1] 향악계곡 안공법.')
    ELSE
        JSON_SET(reference.pattern_json,
            '$.hanja', revised.hanja, '$.name', revised.name, '$.reading', revised.reading,
            '$.holes', CAST(revised.holes AS JSON),
            '$.basic', CAST(IF(revised.code = 'SANJO_DAEGEUM', 'true', 'false') AS JSON),
            '$.source', CASE revised.code
                WHEN 'DANSO' THEN '출처: 국립국악원 e국악아카데미 「단소 운지표」.'
                ELSE '출처: 피리사 「산조대금 안공표」의 가요·창작곡용 참고 운지.'
            END)
    END AS pattern
FROM (
    SELECT 'DANSO' AS code, '협' AS yul, 1 AS octave, '浹' AS hanja, '청협종' AS name, '청협' AS reading,
        '["CLOSED","CLOSED","CLOSED","CLOSED","CLOSED"]' AS holes
    UNION ALL SELECT 'DANSO', '고', 1, '㴌', '청고선', '청고', '["CLOSED","CLOSED","CLOSED","CLOSED","HALF"]'
    UNION ALL SELECT 'DANSO', '남', 1, '湳', '청남려', '청남', '["CLOSED","CLOSED","HALF","OPEN","OPEN"]'
    UNION ALL SELECT 'DANSO', '협', 2, '㶄', '중청협종', '중청협', '["OPEN","CLOSED","CLOSED","OPEN","OPEN"]'
    UNION ALL SELECT 'DANSO', '중', 2, '㶌', '중청중려', '중청중', '["CLOSED","CLOSED","OPEN","CLOSED","OPEN"]'
    UNION ALL SELECT 'SOGEUM', '태', 2, '㶃', '중청태주', '중청태', '["CLOSED","CLOSED","OPEN","OPEN","OPEN","OPEN"]'
    UNION ALL SELECT 'SANJO_DAEGEUM', '태', 2, '㶃', '중청태주', '중청태', '["CLOSED","CLOSED","OPEN","CLOSED","CLOSED","CLOSED"]'
) revised
JOIN instruments instrument ON instrument.code = revised.code
JOIN pitches pitch ON pitch.yul = revised.yul AND pitch.octave = revised.octave
JOIN pitches reference_pitch ON reference_pitch.octave = 2
    AND reference_pitch.yul = IF(revised.code = 'SOGEUM', '태', '황')
JOIN fingering_charts reference ON reference.instrument_id = instrument.id
    AND reference.pitch_id = reference_pitch.id
WHERE reference.pattern_json->>'$.blow' = 'HIGH';

-- Check that all seven rows resolved before writing to the catalog.
CREATE TEMPORARY TABLE _fingering601_assert (row_count INT NOT NULL CHECK (row_count = 7));
INSERT INTO _fingering601_assert SELECT COUNT(*) FROM _fingering601_delta;

INSERT INTO fingering_charts (instrument_id, pitch_id, pattern_json)
SELECT delta.instrument_id, delta.pitch_id, delta.pattern
FROM _fingering601_delta delta
ORDER BY delta.instrument_id, delta.pitch_id
ON DUPLICATE KEY UPDATE pattern_json = delta.pattern;

DROP TEMPORARY TABLE _fingering601_assert;
DROP TEMPORARY TABLE _fingering601_delta;
