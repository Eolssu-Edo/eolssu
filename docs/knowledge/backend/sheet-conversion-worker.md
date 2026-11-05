# 악보 변환 작업자: 시간 제한·검수 원본·재시도

- 작업: S15P31E102-472. [스펙](../../specs/user-sheets.md), [계획·실행 기록](../../plans/S15P31E102-472.md).
- 확인일: 2026-11-05. JDK 21·프로젝트 Gradle Wrapper·폐기용 MySQL 8.4·로컬 HTTP 서버를 사용했다. 실제 OMR 엔진 품질과 운영 R2 검증은 수행하지 않았다.

## 응답 헤더와 본문 수신의 시간 제한

- `BodyHandlers.ofInputStream()`으로 받은 응답은 헤더 수신 뒤 스트림 읽기에서 계속 기다릴 수 있었다. 헤더와 한 바이트를 보낸 뒤 본문을 멈추는 HTTP 대역에서 TIMEOUT 대신 일반 오류가 나오는 것을 재현했다.
- 응답 전체를 받는 비동기 future에 전체 대기 상한을 적용하고, 미완료 요청은 취소한다. 인식은 630초, 원본 다운로드는 40초다. 테스트는 200ms 상한과 latch로 재현하며 고정 sleep을 사용하지 않는다.
- 재현·회귀: `HttpRecognitionClientTests`, `SheetStoragesTests`. URL·서명·파일 내용은 실패 로그에 남기지 않는다.

## 사진 방향과 bbox

- 실제 OMR 구현은 Pillow `ImageOps.exif_transpose`로 사진 방향을 보정한다. ImageIO로 읽어 바로 PNG로 만들면 EXIF 6 사진의 가로·세로가 바뀌지 않아 검수 원본과 인식 좌표가 어긋났다.
- JPEG APP1·PNG eXIf의 TIFF Orientation 1~8을 적용한 후 PNG로 저장한다. PDF 검수 이미지는 144dpi다. bbox는 OMR 응답의 쪽 크기로 정규화하므로 검수 PNG와 해상도가 달라도 같은 상대 위치를 사용한다.
- 재현·회귀: `SourcePageRendererTests`. PDF 순서·실제 PNG 크기와 EXIF 6 사진 방향을 검사했다. 모든 실제 촬영 기기·PDF 판형의 육안 정합성은 미확인이다.

## 실패 원인과 저장 경계

- 응답 `SERVER_ERROR`만으로는 재시도 여부를 정할 수 없다. 내부 ENGINE_ERROR·일반 서버 실패를 구분하고 TIMEOUT 누적 횟수도 별도로 저장한다. TIMEOUT 재시도 1회와 합계 3회 제한은 동시에 적용한다.
- 접수·재시도는 회원 행 잠금으로 직렬화하고, 전역 RUNNING UNIQUE 슬롯으로 동시에 두 변환이 실행되는 것을 막는다. 한 backend 인스턴스로 운영한다. 외부 HTTP 호출 중 DB 트랜잭션을 유지하지 않는다.
- 삭제 전에 변환을 중단 상태로 바꾼다. 늦게 도착한 결과는 현재 RUNNING·attempt가 맞을 때만 저장한다. 결과 저장 전에 생성한 객체는 저장 실패·삭제 경합 때 정리하며, 저장소 정리가 실패하는 고아 객체까지 복구하는 버킷 대조 작업은 별도 운영 과제다.
- issues·notices·쪽 메타데이터는 변환 결과 JSON에 보관한다. 별도 issue 표보다 한 번의 변환 결과를 원자적으로 저장·검수하기 쉽다. 앱의 issueId·정규화 bbox·확인할 칸 수 계약은 동일하다.
