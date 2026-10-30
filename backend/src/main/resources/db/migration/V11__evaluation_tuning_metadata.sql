-- AI가 보고한 조율 편차와 범위 초과 판정을 함께 보존한다. 기존 평가는 미수집(NULL)이다.
-- 편차는 실제 보정 적용량이 아니므로 범위 초과여도 0으로 바꾸거나 반올림하지 않는다.
ALTER TABLE evaluations
    ADD COLUMN tuning_offset_cent DOUBLE NULL,
    ADD COLUMN tuning_out_of_range BOOLEAN NULL,
    ADD CONSTRAINT chk_evaluations_tuning_metadata CHECK (
        (tuning_offset_cent IS NULL AND tuning_out_of_range IS NULL)
        OR (tuning_offset_cent IS NOT NULL AND tuning_out_of_range IS NOT NULL AND tuning_out_of_range IN (0, 1))
    );
