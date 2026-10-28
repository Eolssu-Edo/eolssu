# 단소 재생 테스트별 확인 범위

[S15P31E102-166](https://ssafy.atlassian.net/browse/S15P31E102-166)의 단일 JGB/score → MIDI → CAT PCM → M4A/WAV 파이프라인 검사다. 실행 명령·의존성·FFmpeg 설정은 [상위 README](../README.md#설치와-빠른-검사)를 따른다.

## 결과를 읽는 기준

2026-10-28, Windows / Python 3.12 / FFmpeg·ffprobe 9.0.2에서 **112개 통과, 건너뜀 0개**를 확인했다. 숫자는 아래 8개 파일의 테스트 메서드 수다. `subTest` 안의 사례 수나 검수한 악보 수가 아니다.

- 고정 JSON과 합성 사인파를 사용한다. 음원 검사는 실제 샘플러의 렌더링 로직을 거치지만, 실제 CAT 뱅크를 읽거나 단소 음색을 평가하지 않는다.
- `test_audio.py`의 코덱 검사 1개와 `test_pipeline.py`의 CLI 검사 1개만 실제 FFmpeg/ffprobe 인코딩·디코딩 통합 검사다. 입력 소리는 합성 신호이며, 인코더를 찾지 못하면 이 2개는 건너뛴다.
- PDF 기호 판독, 원보와 수정 JSON의 일치, 동일 판본의 실연과 유사성, 선택 BPM의 음악적 타당성은 검사하지 않는다. 실제 CAT 수동 생성 및 원보 재대조는 [별도 근거 문서](../../../../docs/knowledge/data/danso-expanded-auditions.md)에 기록한다.
- `official_twelve`도 코드에 적은 기대 음높이 배열과 비교한다. 공식 자료의 의미를 자동으로 재검증하는 검사는 아니다. 풀어내림의 곡선·숨표 시간·늘임 배수는 현재 재생 정책대로 작동하는지 확인한다.

## 파일을 나누는 기준

파일 이름은 주된 검증 대상 모듈에 맞춘다. JGB 해석 검사는 `test_jgb.py`에 모으고, 여러 단계를 함께 확인하는 검사는 `test_pipeline.py`에 둔다. JGB 변환 결과의 MIDI 시간 확인처럼 보조 모듈을 사용하는 회귀 검사도 있으므로 모든 검사를 엄격한 단위 테스트라고 부르지는 않는다. `test_jgb.py`의 기호 검사는 문자열 입력·배치·원문 연결까지 확인하므로 유지하고, 표현 정책 함수만 직접 호출하는 검사는 `test_expressions.py`에 둔다.

| 테스트 파일 | 대응 구현·범위 | 개수 |
| --- | --- | ---: |
| `test_jgb.py` | `jgb.py`: 구문·기호·시간·빠르기 선택·원문 대응 | 37 |
| `test_score.py` | `score.py`: 공통 악보 검증·이벤트 생성 | 13 |
| `test_midi.py` | `midi.py`: 제어 메시지·자동 떨림·직렬화 | 8 |
| `test_sampler.py` | `sampler.py`: PCM·주파수·위상·무음·샘플 부족 | 12 |
| `test_audio.py` | `audio.py`: 파일 출력·프로세스 오류·AAC 시간 정렬 | 4 |
| `test_pipeline.py` | `__main__.py`와 모듈 연결: score→MIDI, JGB→PCM, CLI 전체 실행 | 20 |
| `test_expressions.py` | `expressions.py`: 표현 정책·음형·꾸밈/숨 시간·프로필 검증 | 7 |
| `test_tempo_repeat.py` | `tempo_repeat.py`: 빠르기 적분·반복·원문 시간 보존 | 11 |

`_helpers.py`는 고정 악보·합성 샘플·MIDI 왕복 헬퍼만 제공하며 테스트를 정의하지 않는다. 테스트 파일끼리는 import하지 않는다. 자동 ±25cent 떨림의 설정값은 `expressions.py`, MIDI 제어 생성은 `midi.py`에 있다. 실제 제어 메시지 검사는 `test_midi.py`에 둔다.

아래 표의 이름은 각 파일에 정의된 메서드 이름이다.

## test_audio.py — 4개

[코드](test_audio.py).

### AudioOutputTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_wav_option_needs_no_encoder_and_keeps_exact_length` | 인코더 없이 WAV의 샘플 수·샘플레이트·PCM16·SHA를 확인 |
| `test_invalid_pcm_creates_no_audio_file` | 빈 배열·NaN·미지원 배열 형태·클리핑 PCM을 파일 생성 전에 거부 |
| `test_process_failure_and_timeout_become_value_errors` | 인코딩 프로세스 실패·시간 초과를 변환 오류로 전달 |

### AacCodecIntegrationTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_aac_priming_is_removed_and_only_tail_padding_remains` | **실제 AAC 통합:** 합성 신호의 인코딩/디코딩 후 시작 정렬 오차 1샘플 이내, 끝 패딩 1024샘플 미만, 컨테이너 길이 오차 2샘플 이내 |

## test_expressions.py — 7개

[코드](test_expressions.py).

### ExpressionTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_builtin_effects_keep_delay_scope_conflicts_and_policy_isolation` | 기존 효과의 지연·음열 적용 범위·충돌 거부·정책 객체 독립성 확인 |
| `test_ornament_pitches_and_short_timing_preserve_main_and_special_roll` | 떠이어 특례·앞뒤 꾸밈 음높이·짧은 꾸밈 본음 절반·늘임 정책 직접 검사 |
| `test_breath_policy_and_retiming_reserve_sound_and_silence` | 긴/짧은 숨과 빠르기 변경 후 발음·무음 예산 직접 검사 |
| `test_builtin_accent_is_declared_and_other_gestures_require_a_profile` | 기본 강세 선택값 표시와 다른 기호의 명시 프로필 요구 |
| `test_explicit_deep_yoseong_keeps_input_and_returned_policies_independent` | 명시한 깊은 요성 값 보존과 원본/반환 객체 독립성 확인 |
| `test_direction_and_repetition_match_explicit_curve_shape` | 추성·퇴성 방향과 겹흘림·겹미는 반복 곡선 검사 |
| `test_unverified_aliases_and_incomplete_or_unknown_fields_are_rejected` | 근거 누락·알 수 없는 기호·필드·불완전 표현 설정 거부 |

## test_jgb.py — 37개

[코드](test_jgb.py).

### JgbTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_all_instrument_needs_explicit_danso_selection_without_source_edit` | all은 명시한 단소 선택만 허용하고 다른 악기는 거부하며 원본을 보존 |
| `test_tempo_annotations_do_not_take_subdivision_or_change_source_offsets` | 빠르기 주석은 분박을 차지하지 않고 실제 시간·원문 위치를 유지 |
| `test_accent_is_not_geokeum_and_profile_shake_is_not_wavy_vibrato` | 특강·격음 및 요성표·농음표를 구별하고 명시 요성을 지속 위치부터 적용 |
| `test_short_subdivided_graces_keep_main_half_and_exact_timing` | 짧은 분박에서 꾸밈을 줄여 본음 절반·tick·총길이를 보존 |
| `test_reduced_surround_graces_remain_symmetric_after_hold` | 줄인 앞뒤 꾸밈의 대칭과 본음 연장·다음 정간 시작 보존 |
| `test_jeonggan_tempo_is_not_rejected_by_quarter_conversion` | 19·270·800정간 BPM의 환산과 실제 MIDI 빠르기 한도 검증 |
| `test_versions_have_the_same_midi_and_keep_source_untouched` | 같은 내용의 v1/v2가 같은 MIDI를 만들고 원본 구조와 미검수 표시를 유지 |
| `test_quarter_unit_and_speed_preserve_explicit_jeonggan_time` | 기준 단위·속도 환산이 명시한 정간 시간을 보존하고 구형 단위 추정은 기록 |
| `test_hold_stops_at_gak_boundary_and_repeated_pitch_rearticulates` | 지속은 각 경계를 넘지 않고 반복 기보는 재발음하며 지속 위치를 기록 |
| `test_rest_then_hold_stays_silent` | 쉼 뒤 지속이 앞선 소리를 되살리지 않음 |
| `test_subdivisions_and_variable_gak_length` | 정간 분할과 각별 길이를 정확한 분수 시간으로 변환 |
| `test_source_offsets_and_ids_survive_crlf_empty_cells_and_double_bars` | 줄바꿈·빈 정간·겹세로줄이 있어도 각/정간·문자 위치·음 ID 연결을 유지 |
| `test_graces_and_relative_sequences_use_scale_and_preserve_time` | 앞꾸밈·상대 음형의 음계·길이·세기·이음과 별칭 처리가 기대값에 일치 |
| `test_attached_sequence_and_multiple_graces_keep_their_slot` | 붙임 음형과 여러 꾸밈음이 배정된 시간 안에 들어가 전체 길이를 유지 |
| `test_breath_is_applied_at_end_of_cell_after_hold` | 지속 포함 정간 끝에 숨을 적용하고 짧은 음의 발음 길이를 남기며 모호한 후속 지속은 거부 |
| `test_part_selection_does_not_follow_active_editor_or_mix_voices` | 복수 성부는 명시적으로 선택하며 편집기의 활성 성부를 자동 선택하거나 섞지 않음 |
| `test_missing_tempo_and_reference_need_explicit_overrides` | BPM·황종 누락은 실패하고 명시한 CLI 값이 있을 때만 보완 |
| `test_ambiguous_or_unsupported_notation_fails_with_position` | 모호한/미지원 기호·배치를 위치와 함께 거부하고 상대 음형에 조 정보를 요구 |
| `test_invalid_settings_and_unrepresentable_timing_fail` | 잘못된 BPM·박수·단위·버전·각, MIDI로 표현 못 하는 분할, 음역 초과를 거부 |
| `test_source_and_asset_mismatch_do_not_silently_drop_sound` | 다른 악기·음소거 성부·장구 반주·효과 비활성화 등 소리 누락 위험 설정을 거부 |
| `test_nested_triplets_preserve_exact_cell_and_midi_timing` | 중첩 3분할의 분수 시간과 다음 정간 시작을 보존 |
| `test_long_breath_keeps_existing_120ms_gap_and_50ms_release` | 긴 음은 무음 120ms·감쇠 50ms 정책을 유지 |
| `test_blank_sustain_keeps_subdivision_and_source_location` | 빈지속의 분할·원문 위치를 보존하며 앞음이 없으면 쉼으로 처리 |
| `test_vibrato_on_hold_starts_at_mark_without_rearticulating` | 지속 중 농음은 표기 시점부터 재발음 없이 적용하며 앞음이 없으면 거부 |
| `test_marked_vibrato_and_fall_change_controls_without_changing_pitches` | 농음·흘림이 기본 음높이·시각을 유지하며 음량·피치 제어에 반영 |
| `test_paired_length_marks_redistribute_one_cell_and_keep_annotation` | 덧길이·반길이 쌍의 정간 총길이와 주석을 보존하고 짝 없는 표기는 거부 |
| `test_surrounding_graces_and_independent_three_note_figure_keep_time` | 느니르·니로나의 기대 음열·길이 비율과 전체 시간 보존 |
| `test_official_twelve_attached_figures` | 붙임 12음형을 기대 음높이·시간과 비교하고 빠른 꾸밈과 균등 음형을 구분 |
| `test_official_twelve_substitute_figures` | 대용 12음형을 기대 음높이·길이·개수와 비교하고 상류와 다른 해석을 기록 |
| `test_new_aliases_produce_identical_midi` | 같은 의미의 한글 이름·내부 이름이 같은 MIDI를 생성 |
| `test_post_grace_moves_after_sustained_main_and_preserves_reference` | 뒤꾸밈을 연장된 본음 끝으로 이동하고 원문·지속·농음 시작점을 보존하며 모호한 숨/지속은 거부 |
| `test_post_grace_never_crosses_rest_or_gak` | 뒤꾸밈이 쉼·각 경계를 넘지 않음 |
| `test_unwind_is_distinct_pitch_vibrato_plus_fall_and_push_is_upward` | 풀어내림의 음높이 떨림+하강, 미는표의 상승, 흘림과의 차이 및 근사 정책 표시를 확인 |
| `test_unwind_on_hold_does_not_bend_before_mark` | 지속 중 풀어내림이 기호 시점 이전 음높이에 영향을 주지 않음 |
| `test_known_unresolved_figures_are_not_silently_skipped` | 미해결 기호를 위치·사유와 함께 거부하고 떠이어의 지원 기준음은 기대 음형으로 처리 |
| `test_ambiguous_combinations_and_disabled_effects_fail` | 표현 충돌·쉼에 붙은 효과·잘못된 발음 비율·효과 비활성화를 거부 |
| `test_tempo_requires_explicit_playback_selection_not_a_number_or_jangdan` | BPM·장단만으로 허용하지 않고 명시 선택 표시 또는 CLI BPM을 요구하며 선택 경로를 기록 |

## test_midi.py — 8개

[코드](test_midi.py).

### MidiTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_wide_and_piecewise_curves_roundtrip_with_automatic_rpn_range` | 300cent와 분할 곡선을 충분한 RPN 범위로 왕복 |
| `test_delayed_piecewise_curve_keeps_held_prefix_at_original_pitch` | 지연 곡선 이전의 지속음 높이를 보존 |
| `test_explicit_deep_yoseong_and_combined_controls_do_not_clip` | 깊은 요성과 복합 곡선을 clipping 없이 처리하고12반음 초과 거부 |
| `test_rpn_fractional_cents_are_decoded_and_malformed_order_rejected` | RPN의cent·순서를 읽고 잘못된 메시지 순서·범위 거부 |
| `test_unsupported_midi_cc_fails` | 미지원 MIDI 제어 메시지를 거부 |
| `test_fixed_25_cent_vibrato_keeps_short_notes_and_timing` | 길이 조건에 맞는 음에만 자동 ±25센트 떨림을 추가하고 음높이 목록·시각·원문을 보존 |
| `test_authored_pitch_and_mixed_yoseong_are_not_added_twice` | 명시된 음높이 요성에 자동 떨림을 중복 추가하지 않음 |
| `test_amplitude_yoseong_remains_with_fixed_pitch_vibrato` | 명시한 음량 요성과 자동 음높이 떨림이 함께 유지됨 |

## test_tempo_repeat.py — 11개

[코드](test_tempo_repeat.py).

### TempoRepeatTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_immediate_tempo_changes_note_time_and_preserves_inputs` | 즉시 빠르기가 실제 음 길이를 바꾸고 원본을 보존 |
| `test_tempo_inside_held_note_and_delayed_expression_is_integrated` | 지속음 내부 빠르기와 표현 시작 위치를 적분·재계산 |
| `test_gradual_tempo_uses_reciprocal_integral_not_average_bpm` | 점진 빠르기의 정확한 역수 적분과 다음 음 시작 검증 |
| `test_fermata_and_global_speed_remain_independent_of_written_ramp` | 늘임·전역 배속과 원문 위치 기준 점진 빠르기의 조합 검증 |
| `test_repeats_keep_source_positions_and_have_unique_ids` | 각 반복의 고유 ID·시간·원문 위치 보존 |
| `test_original_tempo_cue_restores_base_and_other_cues_require_numbers` | 본래속도로 복귀와 다른 빠르기 기호의 숫자 요구 |
| `test_shortened_breath_remains_bounded_and_curve_points_are_preserved` | 빠른 구간의 숨표 절반 예산·곡선·지연과 이전 숨표 메타데이터 구분 |
| `test_invalid_plan_hash_shape_positions_and_overlap_are_rejected` | 원본해시 불일치·잘못된 위치·겹친 빠르기/반복 계획 거부 |
| `test_repeat_boundary_rejects_held_note_and_slur_cuts` | 지속음·이어진 꾸밈음을 자르는 반복 경계 거부 |
| `test_gradual_cue_requires_endpoint_and_plan_is_not_applied_twice` | 점진 기호의 끝 위치와 계획 중복 적용 거부 |
| `test_empty_plan_preserves_timing_and_quantization_rejects_lost_notes` | 빈 계획은 시간을 유지하고 지나친 속도로 tick이 사라지는 음은 거부 |

## test_pipeline.py — 20개

[코드](test_pipeline.py).

### ScoreMidiIntegrationTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_fixture_expands_37_to_44_and_preserves_parent_ids` | 예제의 원음 37개가 꾸밈을 포함한 MIDI 음 44개로 확장되고 원음 ID·음높이 순서가 유지됨 |
| `test_rest_tie_and_rearticulation_are_different` | 붙임줄은 음을 합치고 쉼은 침묵을 만들며 같은 음을 다시 적으면 재발음 |

### JgbRenderIntegrationTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_short_breath_preserves_half_note_next_onset_and_audible_gap` | 여러 BPM의 짧은 숨에서 원음 절반 이상·다음 음 시작·실제 무음과 정책 기록을 확인 |
| `test_staccato_breath_share_release_budget_and_leave_actual_silence` | 끊기·숨이 감쇠 포함 발음 시간을 공유하며 다음 음 시각·실제 무음을 유지 |
| `test_fermata_records_written_and_performed_times_including_rest` | 늘임이 음·쉼의 시간을 2배로 늘리고 기보/연주 시간을 따로 기록하며 샘플 부족은 거부 |
| `test_short_staccato_silence_cannot_leak_previous_note_tails` | 빠른 분박에서 앞음의 감쇠가 끊는표의 무음 구간으로 새지 않음 |

### CliPipelineTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_publish_rename_failure_restores_complete_previous_bundle` | 최종 폴더 교체 실패를 주입해 이전 결과 묶음 전체 복원 |
| `test_publish_refuses_unrelated_files_and_replaces_valid_bundle` | 다른 개인 파일이 있는 출력은 보존·거부하고 전용 결과만 교체 |
| `test_cli_preserves_explicit_configs_and_resource_limit` | 계획·프로필 원본 바이트/해시·자원 한도 및 실제 MIDI시간 확인 |
| `test_failed_cli_preserves_previous_result` | 잘못된 자산 설정으로 CLI가 실패해도 이전 결과를 보존 |
| `test_cli_pipeline_keeps_original_bytes_and_resolvable_source_map` | BOM 포함 원본 바이트·해시를 보존하고 생성 음에서 원문 위치를 추적 가능 |
| `test_bad_jgb_preserves_previous_output` | JGB 해석 실패 시 기존 WAV를 보존 |
| `test_hash_describes_captured_input_even_if_original_changes_during_render` | 렌더링 중 입력 파일이 바뀌어도 사본·해시는 시작할 때 읽은 내용을 가리킴 |
| `test_output_cannot_overwrite_original_input` | 출력이 입력 원본을 덮어쓸 수 있는 경로를 거부 |
| `test_ir_cannot_leave_an_obsolete_jgb_map_beside_new_audio` | 공통 악보 입력 결과에 이전 JGB 대응표가 잘못 남을 수 있는 폴더 사용을 거부 |
| `test_unconfirmed_tempo_fails_before_assets_or_output` | 빠르기 미선택 입력은 자산 로딩·출력 생성 전에 실패하고 기존 자료를 보존 |
| `test_switching_format_rejects_stale_audio_without_deleting_it` | 반대 형식 음원이 남은 폴더를 거부하며 이전 파일을 삭제하지 않음 |
| `test_encoder_failure_preserves_all_previous_outputs` | 인코딩 실패 시 이전 M4A·MIDI·manifest·대응표 바이트를 보존 |
| `test_missing_encoder_fails_before_writing_any_output` | 인코더 누락 시 출력 폴더/파일 생성 전에 실패 |

### M4aPipelineIntegrationTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_default_pipeline_emits_m4a_without_intermediate_wav` | **실제 AAC 통합:** 기본 CLI가 중간 WAV 없이 AAC-LC M4A를 만들고 SHA·인코더·소스 버전을 기록하며 HTML도 생성하지 않음 |

## test_sampler.py — 12개

[코드](test_sampler.py).

### SamplerTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_cc11_changes_actual_pcm` | MIDI 음량 제어 CC11에 따라 생성 PCM의 크기가 감소 |
| `test_pitch_bend_changes_actual_frequency_and_resets` | 피치벤드가 실제 합성 주파수를 바꾸고 이후 기준값으로 초기화 |
| `test_next_note_expression_does_not_leak_backwards` | 다음 음의 음량 제어가 이전 음의 감쇠 구간에 적용되지 않음 |
| `test_same_pitch_slur_preserves_phase` | 같은 음의 이음줄 연결이 위상 단절 없이 연속 음과 같은 PCM을 생성 |
| `test_breath_has_real_silence_after_release` | 숨표가 감쇠 종료 뒤 실제 PCM 무음을 생성 |
| `test_outside_zone_and_exhaustion_fail` | 맞는 샘플 영역이 없거나 필요한 길이보다 샘플이 짧으면 실패 |
| `test_rendered_pcm_has_25_cent_width_and_delayed_start` | 합성 PCM의 떨림이 지연 후 시작하고 약 ±25센트 폭을 가짐 |
| `test_long_tone_uses_validated_loop_without_pitch_or_boundary_jumps` | 긴 합성음 루프의 음높이·연결 경계 연속성과 정책 기록 확인 |
| `test_same_pitch_long_slur_uses_one_continuous_loop` | 같은음 연결을 하나의 지속 구간으로 선택하고 위상 유지 |
| `test_extended_tone_still_leaves_breath_silence` | 연장된 음도 숨표 뒤 실제 무음 보존 |
| `test_short_tone_does_not_search_or_apply_sustain_loop` | 충분히 짧은 음은 루프 탐색·적용 없이 기존 파형 사용 |
| `test_unstable_noise_glide_and_silence_have_no_sustain_loop` | 소음·급격한 음높이 변화·무음을 안정 지속 구간으로 승인하지 않음 |

## test_score.py — 13개

[코드](test_score.py).

### ScoreTests

| 테스트 | 확인 내용 |
| --- | --- |
| `test_piecewise_pitch_curves_require_ordered_bounded_complete_points` | 곡선 점의 순서·끝점·값·필드 충돌과 지연 검증 |
| `test_explicit_wide_yoseong_and_curve_survive_compilation` | 명시한 넓은 음높이 요성과 곡선을 이벤트에 보존 |
| `test_tempo_checks_encoded_midi_limit_instead_of_musical_band` | 실제24bit MIDI Set Tempo 표현 범위 검사 |
| `test_duration_resource_limit_is_explicit_and_configurable` | 기본600초 초과 거부와 명시한1200초 자원 한도 허용 |
| `test_written_octave_applied_once` | 기보 음높이의 옥타브 보정을 한 번만 적용하고 실음 입력의 중복 보정은 거부 |
| `test_yul_requires_reference_and_respects_register` | 율명 변환에 황종 기준이 필요하며 청·배 등의 음역을 반영 |
| `test_unresolved_or_oversized_ornament_rejected` | 비어 있거나 미지원이거나 본음 길이를 초과하는 꾸밈을 거부 |
| `test_range_checked_after_ornament_and_no_automatic_fold` | 꾸밈 확장 뒤 음역을 검사하며 범위 밖 음을 임의로 옥타브 이동시키지 않음 |
| `test_explicit_danso_yoseong_uses_amplitude_default` | 모드 미지정 단소 요성에 기본 음량 변조를 적용 |
| `test_bad_input_cannot_be_silently_ignored` | 미지원 필드·잘못된 길이/세기/쉼·과도한 숨·중복 ID를 거부 |
| `test_unclosed_tie_rejected` | 닫히지 않은 붙임줄을 거부 |
| `test_tie_cannot_silently_drop_new_slur_or_velocity` | 붙임줄 뒤 음의 새 이음줄·세기가 무시될 수 있는 입력을 거부 |
| `test_delayed_curve_with_ir_ornament_fails_before_midi_generation` | 지연 피치 곡선과 공통 악보 꾸밈의 미지원 조합을 MIDI 생성 전에 거부 |
