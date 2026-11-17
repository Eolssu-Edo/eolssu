# 얼쑤 제출 산출물 (S15P31E102-1106)

| 번호 | 산출물 | 파일 |
| --- | --- | --- |
| 1 | GitLab 소스 클론 이후 빌드·배포 문서(포팅 매뉴얼) | [1-porting-manual.md](1-porting-manual.md) |
| 2 | 프로젝트에서 사용하는 외부 서비스 정보 | [2-external-services.md](2-external-services.md) |
| 3 | DB 덤프 파일 최신본 | [3-eolssu-db-dump.sql](3-eolssu-db-dump.sql) |
| 4 | 시연 시나리오 | [4-demo-scenario.md](4-demo-scenario.md) |

- 문서 기준 소스: `develop` `9d8c7dfc4` (2026-11-17)
- 비밀값(비밀번호·API 키·토큰)은 어떤 파일에도 넣지 않았다. 변수 이름과 용도만 적었다.

## DB 덤프 범위

- 원본: 운영 서버 edo1의 MySQL 8.4.13 `eolssu` DB, Flyway V46 적용 상태, 2026-11-17T13:02Z 덤프
- **34개 테이블 전체의 구조**와 **기준 데이터 13개 테이블의 행**을 담았다: `pitches`, `instruments`, `fingering_charts`, `jangdans`, `songs`, `notation_symbols`, `song_score_versions`, `song_model_audios`, `reference_audio_assets`, `reference_audio_pitch_relations`, `lessons`, `terms`, `flyway_schema_history`
- 회원·인증·연습·평가·내 악보 등 개인정보와 사용자 생성 데이터가 담긴 테이블(`users`, `refresh_tokens`, `practice_sessions`, `evaluations`, `user_sheets` 등 21개)은 구조만 넣고 행은 제외했다.
- 복원: `mysql -uroot < 3-eolssu-db-dump.sql` (`CREATE DATABASE IF NOT EXISTS eolssu` 포함). 운영 컨테이너 기준 명령은 포팅 매뉴얼 6-1을 따른다.
