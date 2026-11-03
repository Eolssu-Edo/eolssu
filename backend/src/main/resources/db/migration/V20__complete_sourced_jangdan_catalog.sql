-- S15P31E102-411 후속. 기존 ID 1~5·곡 FK/BPM/tick·음원은 보존한다.
-- 기닥·다 등의 구음은 원전 기보표에 따른다. 빈 칸은 추가 타격을 적지 않는 칸이다.
-- 6~9는 8분음표 칸, 10은 점4분음표(정간) 칸. group은 칸 묶음이며 균등 박을 뜻하지 않는다.
-- 정악3종 BPM은 사용자가 승인한 서비스 시작값이며 원전의 확정 빠르기가 아니다.
INSERT INTO jangdans (id, name, pattern, default_bpm, bpm_unit, source) VALUES
  (6, '엇모리', '{"beats":["덩","","덕","쿵","","쿵","","덕","쿵",""],"group":[3,2,3,2]}', 90, 'QUARTER', '국립국악원 e-국악아카데미 자진아라리 지도방법의 구음·3+2+3+2: https://academy.gugak.go.kr/dp/pds/HTML5-4-40/HTML5-4-40-4/index.htm . 국악사전 엇모리장단의 ♪=180을 ♩=90으로 정확히 환산(180/2).'),
  (7, '단모리', '{"beats":["덩","","따","따","궁","따","궁",""],"group":[2,2,2,2]}', 120, 'QUARTER', '국립국악원 아카데미 장단통일안 휘모리(단모리) 4/4 기본형: https://academy.gugak.go.kr/dp/pds/HTML5-2-4-2/HTML5-2-4-2-4/HTML5-2-4-2-4-1/index.htm . BPM은 국악기 이해와 활용2(2019) p236 악보29의 4/4·♩=120~160 하한. 유파의 명칭 차이는 별도.'),
  (8, '타령', '{"beats":["덩","","","기닥","","다","덩","","기닥","","","다"],"group":[3,3,3,3]}', 48, 'QUARTER', '국립국악원 국악기 이해와 활용2(2019) p227 악보10·p265 표23~25. https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&lang=ko . 앱 시작48(QUARTER)은 서비스곡65 기준·사용자승인값. 원전 확정 빠르기 아님.'),
  (9, '계면가락도드리', '{"beats":["덩","","","기닥","","다","덩","","기닥","","","다"],"group":[3,3,3,3]}', 48, 'QUARTER', '국립국악원 국악기 이해와 활용2(2019) p228 악보14·p265 표23~25. https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&lang=ko . 앱 시작48(QUARTER)은 서비스곡45 기준·사용자승인값. 원전 확정 빠르기 아님.'),
  (10, '세령산', '{"beats":["덩","","덩","더러러러","","궁","","기닥","","다"],"group":[3,2,2,3]}', 63, 'QUARTER', '국립국악원 국악기 이해와 활용2(2019) p226 악보8·p265 표23~25. https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=138&menuid=001003003006002&lang=ko . 앱 시작63(QUARTER)은 서비스곡63 기준·사용자승인값. 원전 확정 빠르기 아님.');
