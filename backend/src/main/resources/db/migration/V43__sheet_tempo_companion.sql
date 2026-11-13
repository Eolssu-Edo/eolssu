-- Optional companion; legacy score documents and published revisions are unchanged.
ALTER TABLE user_sheets ADD COLUMN expressions_json JSON NULL,
  DROP CHECK chk_user_sheets_bpm,
  MODIFY COLUMN bpm INT NOT NULL,
  ADD CONSTRAINT chk_user_sheets_bpm CHECK (bpm > 0);
ALTER TABLE sheet_audios DROP CHECK chk_sheet_audio_settings,
  ADD CONSTRAINT chk_sheet_audio_settings CHECK (score_version > 0 AND bpm > 0);
ALTER TABLE sheet_conversions DROP CHECK chk_sheet_conversions_bpm,
  MODIFY COLUMN bpm INT NULL,
  ADD CONSTRAINT chk_sheet_conversions_bpm CHECK (bpm IS NULL OR bpm > 0);
