-- S15P31E102-409. Snapshot of FE Fingering.kt, FingeringTables.kt and YulNotation.kt.
-- Preserve source glyphs/readings, HALF/UNKNOWN and the sanjo shared-jeongak limitation.
-- Nominal reference data only: no measured tuning, expert review or scoring approval.
CREATE TEMPORARY TABLE _fingering409_instruments (
    code VARCHAR(20) PRIMARY KEY, source TEXT NOT NULL, note TEXT NOT NULL, hwang SMALLINT NOT NULL
);
INSERT INTO _fingering409_instruments (code,source,note,hwang) VALUES
    ('DANSO', '출처: 국립국악원 e국악아카데미 「단소 안공법」. 높은 태는 제1공(뒷구멍)과 제3공을 막아요.', '뒷구멍은 왼손 엄지로 막아요. 역취는 입김을 세게 불어요. 높은 태(청태)는 낮은 태와 운지가 달라요.', 3),
    ('SOGEUM', '출처: 국립국악원 『창작을 위한 국악기 이해와 활용1』 3장 소금 61쪽 [표 1] 향악계곡 안공법.', '소금은 대금보다 한 옥타브 높게 소리 나요. 향악계곡 안공법 기준이에요.', 3),
    ('JEONGAK_DAEGEUM', '출처: 국립국악원 『2021 대금정악보』 9쪽 대금 안공표(공공누리 4유형).', '같은 운지에서 입김 세기로 저취·평취·역취 옥타브를 나눠요. 서양 음이름은 황종=미♭(E♭) 기준이에요.', 3),
    ('SANJO_DAEGEUM', '출처: 정악대금 안공표의 기본 운지틀. 유파별 차이는 선생님께 확인하세요.', '기본 운지틀은 정악대금과 같아요. 서양 음이름은 황종=F(정악대금보다 장2도 높음) 기준이고, 유파마다 조율·세부 운지가 조금 다를 수 있어요.', 5);
CREATE TEMPORARY TABLE _fingering409_notes (
    code VARCHAR(20) NOT NULL, yul VARCHAR(10) NOT NULL, octave SMALLINT NOT NULL, semitone SMALLINT NOT NULL,
    blow VARCHAR(4) NOT NULL, hanja VARCHAR(5) NOT NULL, name VARCHAR(20) NOT NULL,
    reading VARCHAR(20) NOT NULL, holes JSON NOT NULL, basic BOOLEAN NOT NULL,
    PRIMARY KEY(code,yul,octave)
);
INSERT INTO _fingering409_notes (code,yul,octave,semitone,blow,hanja,name,reading,holes,basic) VALUES
    ('DANSO', '중', 0, 5, 'MID', '仲', '중려', '중', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('DANSO', '임', 0, 7, 'MID', '林', '임종', '임', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 1),
    ('DANSO', '무', 0, 10, 'MID', '無', '무역', '무', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('DANSO', '황', 1, 12, 'MID', '潢', '청황종', '황', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('DANSO', '태', 1, 14, 'MID', '汰', '청태주', '태', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('DANSO', '협', 0, 3, 'MID', '夾', '협종', '협', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 0),
    ('DANSO', '남', 0, 9, 'MID', '南', '남려', '남', '["CLOSED", "CLOSED", "HALF", "OPEN", "OPEN"]', 0),
    ('DANSO', '중', 1, 17, 'HIGH', '㳞', '청중려', '청중', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('DANSO', '임', 1, 19, 'HIGH', '淋', '청임종', '청임', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 1),
    ('DANSO', '무', 1, 22, 'HIGH', '潕', '청무역', '청무', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('DANSO', '황', 2, 24, 'HIGH', '㶂', '중청황종', '청황', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('DANSO', '태', 2, 26, 'HIGH', '㶃', '중청태주', '청태', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN"]', 1),
    ('SOGEUM', '임', 0, 7, 'MID', '林', '임종', '임', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('SOGEUM', '남', 0, 9, 'MID', '南', '남려', '남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('SOGEUM', '무', 0, 10, 'MID', '無', '무역', '무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('SOGEUM', '응', 0, 11, 'MID', '應', '응종', '응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('SOGEUM', '황', 1, 12, 'MID', '潢', '청황종', '청황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('SOGEUM', '태', 1, 14, 'MID', '汰', '청태주', '청태', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('SOGEUM', '협', 1, 15, 'MID', '浹', '청협종', '청협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "OPEN"]', 0),
    ('SOGEUM', '고', 1, 16, 'MID', '㴌', '청고선', '청고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN"]', 0),
    ('SOGEUM', '중', 1, 17, 'MID', '㳞', '청중려', '청중', '["OPEN", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SOGEUM', '임', 1, 19, 'HIGH', '淋', '청임종', '청임', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('SOGEUM', '남', 1, 21, 'HIGH', '湳', '청남려', '청남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('SOGEUM', '무', 1, 22, 'HIGH', '潕', '청무역', '청무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('SOGEUM', '응', 1, 23, 'HIGH', '㶐', '청응종', '청응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('SOGEUM', '황', 2, 24, 'HIGH', '㶂', '중청황종', '중청황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('SOGEUM', '태', 2, 26, 'HIGH', '㶃', '중청태주', '중청태', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('SOGEUM', '협', 2, 27, 'HIGH', '㶄', '중청협종', '중청협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "OPEN"]', 0),
    ('SOGEUM', '고', 2, 28, 'HIGH', '㴩', '중청고선', '중청고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SOGEUM', '중', 2, 29, 'HIGH', '㶌', '중청중려', '중청중', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SOGEUM', '임', 2, 31, 'HIGH', '㶍', '중청임종', '중청임', '["OPEN", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SOGEUM', '남', 2, 33, 'HIGH', '㶝', '중청남려', '중청남', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '임', -1, -5, 'LOW', '㑣', '탁임종', '탁임', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '이', -1, -4, 'LOW', '㑥', '탁이칙', '탁이', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "HALF"]', 0),
    ('JEONGAK_DAEGEUM', '남', -1, -3, 'LOW', '㑲', '탁남려', '탁남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('JEONGAK_DAEGEUM', '무', -1, -2, 'LOW', '㒇', '탁무역', '탁무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '응', -1, -1, 'LOW', '㒣', '탁응종', '탁응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '황', 0, 0, 'LOW', '黃', '황종', '황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('JEONGAK_DAEGEUM', '대', 0, 1, 'LOW', '大', '대려', '대', '["CLOSED", "CLOSED", "HALF", "OPEN", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '태', 0, 2, 'LOW', '太', '태주', '태', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '협', 0, 3, 'LOW', '夾', '협종', '협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '고', 0, 4, 'LOW', '姑', '고선', '고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '중', 0, 5, 'LOW', '仲', '중려', '중', '["OPEN", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '유', 0, 6, 'LOW', '蕤', '유빈', '유', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '임', 0, 7, 'MID', '林', '임종', '임', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '이', 0, 8, 'MID', '夷', '이칙', '이', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "HALF"]', 0),
    ('JEONGAK_DAEGEUM', '남', 0, 9, 'MID', '南', '남려', '남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('JEONGAK_DAEGEUM', '무', 0, 10, 'MID', '無', '무역', '무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '응', 0, 11, 'MID', '應', '응종', '응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '황', 1, 12, 'MID', '潢', '청황종', '청황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('JEONGAK_DAEGEUM', '대', 1, 13, 'MID', '汏', '청대려', '청대', '["CLOSED", "CLOSED", "HALF", "OPEN", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '태', 1, 14, 'MID', '汰', '청태주', '청태', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('JEONGAK_DAEGEUM', '협', 1, 15, 'MID', '浹', '청협종', '청협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '고', 1, 16, 'MID', '㴌', '청고선', '청고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '중', 1, 17, 'MID', '㳞', '청중려', '청중', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '유', 1, 18, 'MID', '㶋', '청유빈', '청유', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('JEONGAK_DAEGEUM', '임', 1, 19, 'HIGH', '淋', '청임종', '청임', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '이', 1, 20, 'HIGH', '洟', '청이칙', '청이', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '남', 1, 21, 'HIGH', '湳', '청남려', '청남', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('JEONGAK_DAEGEUM', '무', 1, 22, 'HIGH', '潕', '청무역', '청무', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '응', 1, 23, 'HIGH', '㶐', '청응종', '청응', '["CLOSED", "OPEN", "OPEN", "CLOSED", "CLOSED", "CLOSED"]', 0),
    ('JEONGAK_DAEGEUM', '황', 2, 24, 'HIGH', '㶂', '중청황종', '중청황', '["OPEN", "OPEN", "OPEN", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '임', -1, -5, 'LOW', '㑣', '탁임종', '탁임', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '이', -1, -4, 'LOW', '㑥', '탁이칙', '탁이', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "HALF"]', 0),
    ('SANJO_DAEGEUM', '남', -1, -3, 'LOW', '㑲', '탁남려', '탁남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('SANJO_DAEGEUM', '무', -1, -2, 'LOW', '㒇', '탁무역', '탁무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '응', -1, -1, 'LOW', '㒣', '탁응종', '탁응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '황', 0, 0, 'LOW', '黃', '황종', '황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('SANJO_DAEGEUM', '대', 0, 1, 'LOW', '大', '대려', '대', '["CLOSED", "CLOSED", "HALF", "OPEN", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '태', 0, 2, 'LOW', '太', '태주', '태', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '협', 0, 3, 'LOW', '夾', '협종', '협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '고', 0, 4, 'LOW', '姑', '고선', '고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '중', 0, 5, 'LOW', '仲', '중려', '중', '["OPEN", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '유', 0, 6, 'LOW', '蕤', '유빈', '유', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '임', 0, 7, 'MID', '林', '임종', '임', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '이', 0, 8, 'MID', '夷', '이칙', '이', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "HALF"]', 0),
    ('SANJO_DAEGEUM', '남', 0, 9, 'MID', '南', '남려', '남', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN"]', 1),
    ('SANJO_DAEGEUM', '무', 0, 10, 'MID', '無', '무역', '무', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '응', 0, 11, 'MID', '應', '응종', '응', '["CLOSED", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '황', 1, 12, 'MID', '潢', '청황종', '청황', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN"]', 1),
    ('SANJO_DAEGEUM', '대', 1, 13, 'MID', '汏', '청대려', '청대', '["CLOSED", "CLOSED", "HALF", "OPEN", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '태', 1, 14, 'MID', '汰', '청태주', '청태', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "OPEN"]', 1),
    ('SANJO_DAEGEUM', '협', 1, 15, 'MID', '浹', '청협종', '청협', '["CLOSED", "OPEN", "CLOSED", "OPEN", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '고', 1, 16, 'MID', '㴌', '청고선', '청고', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '중', 1, 17, 'MID', '㳞', '청중려', '청중', '["OPEN", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '유', 1, 18, 'MID', '㶋', '청유빈', '청유', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN"]', 0),
    ('SANJO_DAEGEUM', '임', 1, 19, 'HIGH', '淋', '청임종', '청임', '["OPEN", "CLOSED", "CLOSED", "CLOSED", "OPEN", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '이', 1, 20, 'HIGH', '洟', '청이칙', '청이', '["CLOSED", "CLOSED", "CLOSED", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '남', 1, 21, 'HIGH', '湳', '청남려', '청남', '["CLOSED", "CLOSED", "OPEN", "OPEN", "OPEN", "CLOSED"]', 1),
    ('SANJO_DAEGEUM', '무', 1, 22, 'HIGH', '潕', '청무역', '청무', '["CLOSED", "OPEN", "OPEN", "OPEN", "OPEN", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '응', 1, 23, 'HIGH', '㶐', '청응종', '청응', '["CLOSED", "OPEN", "OPEN", "CLOSED", "CLOSED", "CLOSED"]', 0),
    ('SANJO_DAEGEUM', '황', 2, 24, 'HIGH', '㶂', '중청황종', '중청황', '["OPEN", "OPEN", "OPEN", "CLOSED", "CLOSED", "CLOSED"]', 1);

-- Fail before persistent schema/data writes if any expected identity is missing or inconsistent.
CREATE TEMPORARY TABLE _fingering409_assert (ok INT NOT NULL CHECK(ok=1));
INSERT INTO _fingering409_assert
SELECT IF(COUNT(*)=92,1,0) FROM _fingering409_notes n
JOIN instruments i ON BINARY i.code=BINARY n.code
JOIN pitches p ON BINARY p.yul=BINARY n.yul AND p.octave=n.octave AND p.semitone=n.semitone;

ALTER TABLE fingering_charts
    MODIFY COLUMN image_url VARCHAR(512) NULL,
    ADD COLUMN pattern_json JSON NULL,
    ADD CONSTRAINT chk_fingering_content CHECK (image_url IS NOT NULL OR pattern_json IS NOT NULL),
    ADD CONSTRAINT chk_fingering_pattern_object CHECK (pattern_json IS NULL OR JSON_TYPE(pattern_json)='OBJECT');

CREATE TEMPORARY TABLE _fingering409_seed (
    instrument_id BIGINT NOT NULL, pitch_id BIGINT NOT NULL, semitone SMALLINT NOT NULL,
    pattern JSON NOT NULL, PRIMARY KEY(instrument_id,pitch_id)
);
INSERT INTO _fingering409_seed
SELECT i.id,p.id,n.semitone,JSON_OBJECT(
    'blow',n.blow,'hanja',n.hanja,'name',n.name,'reading',n.reading,'holes',n.holes,'basic',CAST(IF(n.basic, 'true', 'false') AS JSON),
    'source',m.source,'instrumentNote',m.note,'nominalHwangPitchClass',m.hwang,'verificationStatus','BUILTIN_REFERENCE')
FROM _fingering409_notes n JOIN _fingering409_instruments m ON m.code=n.code
JOIN instruments i ON BINARY i.code=BINARY n.code
JOIN pitches p ON BINARY p.yul=BINARY n.yul AND p.octave=n.octave;

-- Retain the ID, image URL and description of any pre-existing image chart.
UPDATE fingering_charts c JOIN _fingering409_seed s ON s.instrument_id=c.instrument_id AND s.pitch_id=c.pitch_id
SET c.pattern_json=s.pattern;
INSERT INTO fingering_charts (instrument_id,pitch_id,image_url,description,pattern_json)
SELECT s.instrument_id,s.pitch_id,NULL,NULL,s.pattern FROM _fingering409_seed s
LEFT JOIN fingering_charts c ON c.instrument_id=s.instrument_id AND c.pitch_id=s.pitch_id
WHERE c.id IS NULL ORDER BY s.instrument_id,s.semitone;

DROP TEMPORARY TABLE _fingering409_seed;
DROP TEMPORARY TABLE _fingering409_assert;
DROP TEMPORARY TABLE _fingering409_notes;
DROP TEMPORARY TABLE _fingering409_instruments;
