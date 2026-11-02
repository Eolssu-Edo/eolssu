-- S15P31E102-339: user-approved optional jangdan; migration number is assigned at integration.
-- Instrument identity and difficulty remain required. Existing foreign keys remain enforced.
ALTER TABLE songs MODIFY COLUMN jangdan_id BIGINT NULL;
