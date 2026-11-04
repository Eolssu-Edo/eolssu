-- pitches는 V5 시드(단소 율명, id 1 = 중, 2 = 임)를 쓴다.
-- V6 시드의 장단·곡은 아래 고정 데이터와 id·이름이 겹쳐 지운다. 이 파일을 쓰는 검사는 트랜잭션이 끝나면 되돌린다.
-- 악기 1은 가입 검사가 커밋한 회원이 남아 가리킬 수 있어 지우지 않고 고정 값으로 덮어쓴다(지우면 외래 키 오류로 모든 검사가 실패).
DELETE FROM songs WHERE id BETWEEN 1 AND 4;
DELETE FROM jangdans WHERE id BETWEEN 1 AND 3;
INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active, created_at, updated_at)
VALUES (1, 'DANSO', '단소', 1, 2, 50, FALSE, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456') AS f
ON DUPLICATE KEY UPDATE code = f.code, name = f.name, range_low_pitch_id = f.range_low_pitch_id, range_high_pitch_id = f.range_high_pitch_id,
  pitch_tolerance_cent = f.pitch_tolerance_cent, is_active = f.is_active, created_at = f.created_at, updated_at = f.updated_at;

DELETE FROM fingering_charts WHERE instrument_id=1 OR id=1;
INSERT INTO fingering_charts (id, instrument_id, pitch_id, image_url, description, created_at, updated_at)
VALUES (1, 1, 2, 'https://example.test/fingering.png', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source, created_at, updated_at)
VALUES (1, '세마치', '{"beats":["덩","덕"],"group":[2]}', 90, 'DOTTED_QUARTER', '테스트', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO songs (id, instrument_id, jangdan_id, title, description, difficulty, default_bpm, score_json, score_version, umulsai_text, note_count, source, is_scorable, status, created_at, updated_at, deleted_at)
VALUES (1, 1, 1, '아리랑', '경기 민요', 2, 90, '{"notes":[{"yul":"황"}]}', 3, '황', 1, NULL, TRUE, 'PUBLISHED', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL);

-- V17 기호 시드와 고정 fixture의 ID만 겹친다. 이 fixture의 트랜잭션 롤백으로 원복된다.
DELETE FROM notation_symbols WHERE id IN (1,2);
INSERT INTO notation_symbols (id, symbol, name, description, image_url, created_at, updated_at)
VALUES (1, '－', '뻗는표', '음을 이어 연주', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456'),
       (2, '-', '반각 테스트', '전각과 구분', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, status, last_login_at, withdrawal_requested_at, purge_scheduled_at, created_at, updated_at, deleted_at)
VALUES (1, 'test@example.test', 'test-fixture-hash', '연습자', 1, 'HANGUL', 'ACTIVE', NULL, NULL, NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL),
       (2, NULL, NULL, NULL, 1, 'HANJA', 'DELETED', '2026-10-22 01:02:03.123456', NULL, NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456'),
       (3, 'pending@example.test', 'test-fixture-hash-3', '대기 회원', 1, 'HANJA', 'WITHDRAWAL_PENDING', NULL, '2026-10-22 01:02:03.123456', '2026-11-21 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL);

INSERT INTO practice_sessions (id, user_id, song_id, parent_session_id, score_version, bpm, bpm_unit, idempotency_key, request_hash, start_note_idx, end_note_idx, recording_url, recording_bytes, recording_duration_ms, recording_sample_rate, recording_channels, status, fail_reason, started_at, ended_at, saved_at, memo, viewed_at, record_deleted_at, created_at)
VALUES (1, 1, 1, NULL, 1, 90, 'DOTTED_QUARTER', NULL, NULL, 0, 0, 'https://example.test/recording.wav', 20971520, 600500, 48000, 2, 'DONE', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:04.123456', NULL, NULL, NULL, NULL, '2026-10-22 01:02:03.123456'),
       (2, 1, 1, 1, 3, 90, 'DOTTED_QUARTER', 'practice-key', REPEAT('a', 64), 0, 0, NULL, NULL, NULL, NULL, NULL, 'DONE', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:04.123456', '2026-10-22 01:02:03.123456', '다시 연습', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO evaluations (id, session_id, total_score, pitch_score, timing_score, duration_score, model_version, evaluated_at)
VALUES (1, 1, 85, 80, 90, 85, 'test-v1', '2026-10-22 01:02:03.123456');

INSERT INTO note_results (id, evaluation_id, note_idx, expected_pitch_id, detected_pitch_id, pitch_error_cent, onset_error_ms, duration_error_ms, verdict)
VALUES (1, 1, 0, 2, NULL, NULL, -16, NULL, 'MISSED');

-- 레슨 1은 V14 시드(단소 1단계) 행이라 지우지 않고 고정 값으로 덮어쓴다.
INSERT INTO lessons (id, track, seq, title, judge_type, is_optional, content_json, status, created_at, updated_at)
VALUES (1, 'DANSO', 1, '악기 알기·감상', 'READ', FALSE, '{"goal":"소리를 안다","pitchNames":["汰"]}', 'PUBLISHED', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456') AS f
ON DUPLICATE KEY UPDATE track = f.track, seq = f.seq, title = f.title, judge_type = f.judge_type, is_optional = f.is_optional,
  content_json = f.content_json, status = f.status, created_at = f.created_at, updated_at = f.updated_at;

INSERT INTO lesson_progress (id, user_id, lesson_id, completed_at, created_at, updated_at)
VALUES (1, 1, 1, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO user_social_accounts (id, user_id, provider, provider_user_id, created_at)
VALUES (1, 3, 'NAVER', 'AbC-네이버', '2026-10-22 01:02:03.123456');

INSERT INTO terms (id, code, version, title, content_url, is_required, seq, effective_at, created_at)
VALUES (1, 'SERVICE_TERMS', '2026-10', '서비스 이용약관', 'https://example.test/terms', TRUE, 1, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO user_term_agreements (id, user_id, term_id, agreed, agreed_at)
VALUES (1, 1, 1, FALSE, '2026-10-22 01:02:03.123456');

INSERT INTO email_verifications (id, email, purpose, code_hash, attempt_count, expires_at, verified_at, token_hash, token_expires_at, consumed_at, invalidated_at, created_at)
VALUES (1, 'test@example.test', 'SIGN_UP', 'code-hash', 2, '2026-10-22 01:02:03.123456', NULL, NULL, NULL, NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456'),
       (2, 'test@example.test', 'SIGN_UP', 'code-hash', 0, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', REPEAT('b', 64), '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL, '2026-10-22 01:02:03.123456');

INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked_at, revoke_reason, created_at)
VALUES (1, 1, REPEAT('c', 64), '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', 'ROTATED', '2026-10-22 01:02:03.123456'),
       (2, 1, REPEAT('d', 64), '2026-10-22 01:02:03.123456', NULL, NULL, '2026-10-22 01:02:03.123456');

INSERT INTO user_song_settings (id, user_id, song_id, bpm, created_at, updated_at)
VALUES (1, 1, 1, 72, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO score_views (id, user_id, song_id, idempotency_key, started_at, ended_at, visible_seconds, counted_seconds, created_at)
VALUES (1, 1, 1, 'view-key', '2026-10-22 01:02:03.123456', '2026-10-22 01:14:03.123456', 690, 600, '2026-10-22 01:02:03.123456');

INSERT INTO user_sheets (id, user_id, title, kind, source, status, jangdan_id, bpm, bpm_unit, column_count, idempotency_key, request_hash, created_at, updated_at)
VALUES (1, 1, '새 악보 1', 'JEONGGANBO', 'MANUAL', 'NEEDS_REVIEW', 1, 60, 'DOTTED_QUARTER', 8, 'sheet-key', REPEAT('e', 64), '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');
