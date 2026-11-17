-- S15P31E102-1078. Optional slot positions of each event's sigimsae; score_json stays unchanged.
-- Shape: [{"cellId":"c0-j1","row":0,"col":0,"symbols":["농음"]}]. NULL means the API falls back to event-level symbols.
ALTER TABLE songs ADD COLUMN sigimsae_placements_json JSON NULL;
ALTER TABLE song_score_versions ADD COLUMN sigimsae_placements_json JSON NULL;
