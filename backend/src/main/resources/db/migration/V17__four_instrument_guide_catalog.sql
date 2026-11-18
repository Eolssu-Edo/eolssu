-- S15P31E102-407. Nominal guide identities from FE FingeringTables.kt/YulNotation.kt.
-- Preserve existing IDs, spelling variants, recordings and scoring approvals.
-- Expand browse ranges only; this does not certify tuning or fingering accuracy.
CREATE TEMPORARY TABLE _catalog407_pitches (
    yul VARCHAR(10) NOT NULL, octave SMALLINT NOT NULL, hanja VARCHAR(5) NOT NULL,
    hangul VARCHAR(10) NOT NULL, semitone SMALLINT PRIMARY KEY, UNIQUE(yul,octave)
);
INSERT INTO _catalog407_pitches (yul,octave,hanja,hangul,semitone) VALUES
    ('임', -1, '㑣', '배임', -5),
    ('이', -1, '㑥', '배이', -4),
    ('남', -1, '㑲', '배남', -3),
    ('무', -1, '㒇', '배무', -2),
    ('응', -1, '㒣', '배응', -1),
    ('황', 0, '黃', '황', 0),
    ('대', 0, '大', '대', 1),
    ('태', 0, '太', '태', 2),
    ('협', 0, '夾', '협', 3),
    ('고', 0, '姑', '고', 4),
    ('중', 0, '仲', '중', 5),
    ('유', 0, '蕤', '유', 6),
    ('임', 0, '林', '임', 7),
    ('이', 0, '夷', '이', 8),
    ('남', 0, '南', '남', 9),
    ('무', 0, '無', '무', 10),
    ('응', 0, '應', '응', 11),
    ('황', 1, '潢', '청황', 12),
    ('대', 1, '汏', '청대', 13),
    ('태', 1, '汰', '청태', 14),
    ('협', 1, '浹', '청협', 15),
    ('고', 1, '㴌', '청고', 16),
    ('중', 1, '㳞', '청중', 17),
    ('유', 1, '㶋', '청유', 18),
    ('임', 1, '淋', '청임', 19),
    ('이', 1, '洟', '청이', 20),
    ('남', 1, '湳', '청남', 21),
    ('무', 1, '潕', '청무', 22),
    ('응', 1, '㶐', '청응', 23),
    ('황', 2, '㶂', '중청황', 24),
    ('태', 2, '㶃', '중청태', 26),
    ('협', 2, '㶄', '중청협', 27),
    ('고', 2, '㴩', '중청고', 28),
    ('중', 2, '㶌', '중청중', 29),
    ('임', 2, '㶍', '중청임', 31),
    ('남', 2, '㶝', '중청남', 33);
CREATE TEMPORARY TABLE _catalog407_assert (ok INT NOT NULL CHECK(ok=1));
-- Reject identity collisions before persistent writes; existing glyph variants are deliberately retained.
INSERT INTO _catalog407_assert SELECT IF(COUNT(*)=0,1,0)
FROM pitches p JOIN _catalog407_pitches e ON p.semitone=e.semitone OR (p.yul=e.yul AND p.octave=e.octave)
WHERE p.semitone<>e.semitone OR BINARY p.yul<>BINARY e.yul OR p.octave<>e.octave;
INSERT INTO pitches (yul,octave,hanja,hangul,semitone)
SELECT e.yul,e.octave,e.hanja,e.hangul,e.semitone FROM _catalog407_pitches e
LEFT JOIN pitches p ON p.semitone=e.semitone WHERE p.id IS NULL ORDER BY e.semitone;

CREATE TEMPORARY TABLE _catalog407_instruments (
    code VARCHAR(20) PRIMARY KEY, name VARCHAR(50) NOT NULL, low_semitone SMALLINT NOT NULL, high_semitone SMALLINT NOT NULL
);
INSERT INTO _catalog407_instruments VALUES
    ('DANSO','단소',3,29), ('SOGEUM','소금',7,33),
    ('JEONGAK_DAEGEUM','정악대금',-5,29), ('SANJO_DAEGEUM','산조대금',-5,29);
INSERT INTO instruments (code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active)
SELECT e.code,e.name,lo.id,hi.id,50,TRUE FROM _catalog407_instruments e
JOIN pitches lo ON lo.semitone=e.low_semitone JOIN pitches hi ON hi.semitone=e.high_semitone
LEFT JOIN instruments i ON i.code=e.code WHERE i.id IS NULL ORDER BY FIELD(e.code,'DANSO','SOGEUM','JEONGAK_DAEGEUM','SANJO_DAEGEUM');
UPDATE instruments i JOIN _catalog407_instruments e ON BINARY i.code=BINARY e.code
JOIN pitches old_lo ON old_lo.id=i.range_low_pitch_id JOIN pitches old_hi ON old_hi.id=i.range_high_pitch_id
JOIN pitches lo ON lo.semitone=e.low_semitone JOIN pitches hi ON hi.semitone=e.high_semitone
SET i.range_low_pitch_id=IF(old_lo.semitone>lo.semitone,lo.id,old_lo.id),
    i.range_high_pitch_id=IF(old_hi.semitone<hi.semitone,hi.id,old_hi.id), i.is_active=TRUE;
DROP TEMPORARY TABLE _catalog407_instruments;
DROP TEMPORARY TABLE _catalog407_assert;
DROP TEMPORARY TABLE _catalog407_pitches;
