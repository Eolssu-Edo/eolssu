-- S15P31E102-684: the successful download has a lifetime independent of later requests.
ALTER TABLE sheet_audios
    ADD COLUMN result_expires_at DATETIME(6) NULL AFTER result_json,
    ADD INDEX ix_sheet_audio_result_expiry (result_expires_at);
