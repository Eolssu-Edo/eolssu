-- S15P31E102-339. Expand before the new backend is deployed. Existing URL values stay
-- only for the previous application's queries; new code and imports use object_key.
-- Unsupported legacy rows fail before any persistent DDL. Never infer an arbitrary URL's key.
CREATE TEMPORARY TABLE v15_model_audio_candidates AS
SELECT a.song_id, a.score_version, a.audio_url,
       IF(JSON_VALID(s.description), s.description, '{}') AS metadata,
       SHA2(CAST(JSON_ARRAY(a.song_id,a.score_version,a.audio_url,a.bpm,a.bpm_unit,a.duration_ms,
           a.start_offset_ms,a.content_type,a.sha256,a.status,a.created_at,a.updated_at) AS CHAR),256) AS audio_fingerprint,
       s.score_json AS canonical_score,
       s.source, s.score_version AS current_version,
       JSON_UNQUOTE(JSON_EXTRACT(s.score_json, '$.score.sourceId')) AS canonical_source,
       JSON_UNQUOTE(JSON_EXTRACT(s.score_json, '$.score.instrument')) AS instrument_code
FROM song_model_audios a JOIN songs s ON s.id=a.song_id;

CREATE TEMPORARY TABLE v15_model_audio_preconditions (
    ok INT NOT NULL, CONSTRAINT chk_v15_model_audio_preconditions CHECK(ok=1)
);
INSERT INTO v15_model_audio_preconditions SELECT IF(COUNT(*)=(SELECT COUNT(*) FROM song_model_audios),1,0)
FROM v15_model_audio_candidates;
INSERT INTO v15_model_audio_preconditions
SELECT IF(COALESCE(
    BINARY JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.task'))='S15P31E102-339'
    AND REGEXP_LIKE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.manifestSha256')),'^[0-9a-f]{64}$','c')
    AND REGEXP_LIKE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.sourceCommonSha256')),'^[0-9a-f]{64}$','c')
    AND BINARY instrument_code IN ('DANSO','SOGEUM','JEONGAK_DAEGEUM','SANJO_DAEGEUM')
    AND REGEXP_LIKE(JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.caseId')),
        CONCAT('^',REPLACE(LOWER(instrument_code),'_','-'),'-[0-9]{3}-p[0-9]+$'),'c')
    AND BINARY source=CONCAT('mr203-fixed-',JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.caseId')))
    AND BINARY canonical_source=BINARY source
    AND score_version=current_version
    AND REGEXP_LIKE(audio_url,'^https://[0-9a-f]{32}[.]r2[.]cloudflarestorage[.]com/[a-z0-9.-]+/model-audio/drafts/[0-9a-f]{64}/cases/[a-z0-9-]+/[a-z0-9-]+[.]m4a$','c')
    AND BINARY SUBSTRING(audio_url, LOCATE('/model-audio/',audio_url)+1)=BINARY CONCAT(
        'model-audio/drafts/',JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.manifestSha256')),
        '/cases/',JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.caseId')),'/',REPLACE(LOWER(instrument_code),'_','-'),'.m4a')
,FALSE),1,0) FROM v15_model_audio_candidates;

ALTER TABLE songs MODIFY COLUMN jangdan_id BIGINT NULL;
ALTER TABLE song_model_audios
    MODIFY COLUMN audio_url VARCHAR(2048) NULL,
    ADD COLUMN object_key VARCHAR(512) COLLATE utf8mb4_bin NULL,
    ADD COLUMN performance_object_key VARCHAR(512) COLLATE utf8mb4_bin NULL,
    ADD COLUMN source_common_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN generation_metadata JSON NULL;

-- Recheck the snapshot after implicit-commit DDL, holding row/range locks until conversion commits.
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;
SELECT song_id FROM song_model_audios ORDER BY song_id,score_version FOR UPDATE;
SELECT s.id FROM songs s JOIN v15_model_audio_candidates c ON c.song_id=s.id ORDER BY s.id FOR UPDATE;
INSERT INTO v15_model_audio_preconditions SELECT IF(COUNT(*)=(SELECT COUNT(*) FROM v15_model_audio_candidates),1,0)
FROM song_model_audios;
INSERT INTO v15_model_audio_preconditions SELECT IF(COUNT(*)=(SELECT COUNT(*) FROM song_model_audios),1,0)
FROM song_model_audios a JOIN v15_model_audio_candidates c ON c.song_id=a.song_id AND c.score_version=a.score_version;
INSERT INTO v15_model_audio_preconditions
SELECT IF(COALESCE(
    BINARY c.audio_fingerprint=BINARY SHA2(CAST(JSON_ARRAY(a.song_id,a.score_version,a.audio_url,a.bpm,a.bpm_unit,a.duration_ms,
        a.start_offset_ms,a.content_type,a.sha256,a.status,a.created_at,a.updated_at) AS CHAR),256)
    AND BINARY s.description=BINARY c.metadata
    AND BINARY CAST(s.score_json AS CHAR)=BINARY CAST(c.canonical_score AS CHAR)
    AND BINARY s.source=BINARY c.source AND s.score_version=c.current_version
,FALSE),1,0)
FROM song_model_audios a JOIN v15_model_audio_candidates c ON c.song_id=a.song_id AND c.score_version=a.score_version
JOIN songs s ON s.id=a.song_id;
UPDATE song_model_audios a JOIN v15_model_audio_candidates c ON c.song_id=a.song_id AND c.score_version=a.score_version
SET a.object_key=SUBSTRING(c.audio_url,LOCATE('/model-audio/',c.audio_url)+1),
    a.performance_object_key=CONCAT(SUBSTRING(c.audio_url,LOCATE('/model-audio/',c.audio_url)+1,
        LENGTH(SUBSTRING(c.audio_url,LOCATE('/model-audio/',c.audio_url)+1))-LENGTH(SUBSTRING_INDEX(c.audio_url,'/',-1))), 'performance.common.json'),
    a.source_common_sha256=JSON_UNQUOTE(JSON_EXTRACT(c.metadata,'$.sourceCommonSha256')),
    a.generation_metadata=CAST(c.metadata AS JSON),
    a.updated_at=a.updated_at;
UPDATE songs s JOIN v15_model_audio_candidates c ON c.song_id=s.id
SET s.description=NULL,s.updated_at=s.updated_at;
COMMIT;

ALTER TABLE song_model_audios MODIFY COLUMN object_key VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    ADD CONSTRAINT chk_model_audio_object_key CHECK (
        REGEXP_LIKE(object_key,'^model-audio/drafts/[0-9a-f]{64}/cases/(danso|sogeum|jeongak-daegeum|sanjo-daegeum)-[0-9]{3}-p[0-9]+/(danso|sogeum|jeongak-daegeum|sanjo-daegeum)[.]m4a$','c')),
    ADD CONSTRAINT chk_model_audio_performance CHECK (
        (performance_object_key IS NULL AND source_common_sha256 IS NULL) OR
        (performance_object_key IS NOT NULL AND source_common_sha256 IS NOT NULL
            AND REGEXP_LIKE(source_common_sha256,'^[0-9a-f]{64}$','c')
            AND BINARY performance_object_key=BINARY CONCAT(
                LEFT(object_key,LENGTH(object_key)-LENGTH(SUBSTRING_INDEX(object_key,'/',-1))), 'performance.common.json')));
DROP TEMPORARY TABLE v15_model_audio_candidates;
DROP TEMPORARY TABLE v15_model_audio_preconditions;
