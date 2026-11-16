-- S15P31E102-1063: 기초부터 배우기의 부는 법·잡는 법 문구를 국립국악원 자료(국악사전 단소·대금·소금·취법,
-- 교육자료 단소 발음법·안공법)에 맞춘다. 화면에서 한 줄로 보이는 문구는 휴대폰(360dp)에서 한 줄을 넘지 않게 썼다.
-- id는 V14 고정값이다(단소 1~9, 소금 10~18, 정악대금 19~27, 산조대금 28~36).

-- 2단계 '악기 잡기'. 화면은 첫 문장만 보인다(손가락 자리는 손 그림). 단소는 화면에 안 보이는 둘째 문장의 손가락이 틀려 고친다.
-- 단소: 뒷구멍 왼손 엄지, 앞 위 두 구멍 왼손 검지·중지, 셋째 구멍 오른손 중지(국악사전 단소). 소금은 그대로 둔다.
UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[1].description',
        '세로로 세워 잡아요. 뒷구멍은 왼손 엄지, 앞 위 두 구멍은 왼손 검지·중지, 셋째 구멍은 오른손 중지로 막아요.')
WHERE id = 2 AND seq = 2;

-- 대금: 왼쪽 어깨에 걸치듯 비스듬히 잡는다(국악사전 대금).
UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[1].description',
        '왼쪽 어깨에 걸쳐 가로로 잡아요. 왼손이 취구 쪽 세 구멍, 오른손이 아래 세 구멍을 막아요.')
WHERE id = 20 AND seq = 2;

UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[1].description',
        '왼쪽 어깨에 걸쳐 가로로 잡아요. 기본 운지틀은 정악대금과 같아요.')
WHERE id = 29 AND seq = 2;

-- 3단계 '취구에 입술 대기'. 취구를 반쯤 덮는다는 근거가 없어 악기별로 나눈다.
-- 단소는 입술을 옆으로 펴(二자) 아랫입술에 취구를 대고, 소금·대금은 취구가 아랫입술 한가운데 오게 댄다.
UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[0].description', '입술을 옆으로 펴 아랫입술에 취구를 대요.')
WHERE id = 3 AND seq = 3;

UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[0].description', '아랫입술을 펴고 취구를 입술 가운데 대요.')
WHERE id IN (12, 21, 30) AND seq = 3;

-- 3단계 '입김 방향·세기 찾기': 단소·소금은 약하게 '휘' 하듯 불어 입김의 반이 취구 안으로 들어가게 한다(단소 발음법, 소금 교육자료).
-- 대금은 '휘'를 말한 공식 자료가 없어 발음 없이 쓴다.
UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[1].description', '''휘'' 하듯 약하게, 반은 취구 안으로 불어요.')
WHERE id IN (3, 12) AND seq = 3;

UPDATE lessons
SET content_json = JSON_SET(content_json, '$.tasks[1].description', '입김을 가늘게 모아 약하게 불어요.')
WHERE id IN (21, 30) AND seq = 3;
