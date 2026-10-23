INSERT INTO pitches (id, yul, octave, hanja, hangul, semitone)
VALUES (1, '황', -1, '僙', '배황', -12), (2, '황', 0, '黃', '황', 0);

INSERT INTO instruments (id, code, name, range_low_pitch_id, range_high_pitch_id, pitch_tolerance_cent, is_active, created_at, updated_at)
VALUES (1, 'DANSO', '단소', 1, 2, 50, FALSE, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO fingering_charts (id, instrument_id, pitch_id, image_url, description, created_at, updated_at)
VALUES (1, 1, 2, 'https://example.test/fingering.png', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO jangdans (id, name, pattern, default_bpm, source, created_at, updated_at)
VALUES (1, '세마치', '{"beats":["덩","덕"],"group":[2]}', 90, '테스트', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO songs (id, instrument_id, jangdan_id, title, difficulty, default_bpm, score_json, umulsai_text, note_count, source, is_scorable, status, created_at, updated_at, deleted_at)
VALUES (1, 1, 1, '아리랑', 2, 90, '{"notes":[{"yul":"황"}]}', '황', 1, NULL, TRUE, 'PUBLISHED', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL);

INSERT INTO notation_symbols (id, symbol, name, description, image_url, created_at, updated_at)
VALUES (1, '－', '뻗는표', '음을 이어 연주', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456'),
       (2, '-', '반각 테스트', '전각과 구분', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO users (id, email, password_hash, nickname, instrument_id, notation_mode, last_login_at, created_at, updated_at, deleted_at)
VALUES (1, 'test@example.test', 'test-fixture-hash', '연습자', 1, 'HANGUL', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', NULL),
       (2, NULL, NULL, NULL, 1, 'HANJA', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO practice_sessions (id, user_id, song_id, bpm, start_note_idx, end_note_idx, recording_url, status, fail_reason, started_at, ended_at, created_at)
VALUES (1, 1, 1, 90, 0, 0, 'https://example.test/recording.wav', 'DONE', NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:04.123456', '2026-10-22 01:02:03.123456');

INSERT INTO evaluations (id, session_id, total_score, pitch_score, timing_score, duration_score, model_version, evaluated_at)
VALUES (1, 1, 85, 80, 90, 85, 'test-v1', '2026-10-22 01:02:03.123456');

INSERT INTO note_results (id, evaluation_id, note_idx, expected_pitch_id, detected_pitch_id, pitch_error_cent, onset_error_ms, duration_error_ms, verdict)
VALUES (1, 1, 0, 2, NULL, NULL, -16, NULL, 'MISSED');

INSERT INTO lessons (id, kind, instrument_id, seq, title, content_json, status, created_at, updated_at)
VALUES (1, 'YUL', NULL, 1, '황종', '{"yul":"황"}', 'PUBLISHED', '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');

INSERT INTO lesson_progress (id, user_id, lesson_id, score, completed_at, created_at, updated_at)
VALUES (1, 1, 1, NULL, NULL, '2026-10-22 01:02:03.123456', '2026-10-22 01:02:03.123456');
