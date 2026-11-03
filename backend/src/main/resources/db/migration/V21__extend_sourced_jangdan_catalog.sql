-- S15P31E102-411: 확인한 기본형만 추가. 기존 1~3·곡 FK/BPM/악보·음원은 보존한다.
-- 이름/ID 충돌은 실패시켜 별도 검토한다. 출처의 범위 하한을 카탈로그 시작 BPM으로 사용한다.
-- beats는 8분음표 칸, group은 점4분음표 네 박. 빈 칸은 앞 소리의 지속이다.
INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source) VALUES
  (4, '자진모리', '{"beats":["떵","","덕","쿵","덕","","떵","","덕","쿵","덕",""],"group":[3,3,3,3]}', 96, 'DOTTED_QUARTER', '국립국악원 e-국악아카데미 자진모리 기본형(12/8). 구음·♩.=96~108: https://academy.gugak.go.kr/dp/pds/HTML5-1-7-3/HTML5-1-7-3-4/index.htm . 시작 BPM 96은 자료 하한이며 곡별 빠르기가 아님.'),
  (5, '중중모리', '{"beats":["덩","","따","궁","따","따","궁","궁","따","궁","","궁"],"group":[3,3,3,3]}', 80, 'DOTTED_QUARTER', '국립국악원 국악사전 중중모리장단, 김인숙. 기본형 표의 구음·3소박4박·♩.=80~90: https://www.gugak.go.kr/ency/topic/view/1319 . 시작 BPM 80은 기본형 표 하한이며 곡별 빠르기가 아님.');
