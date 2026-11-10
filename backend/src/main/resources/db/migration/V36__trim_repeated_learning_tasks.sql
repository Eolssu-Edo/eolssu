-- S15P31E102-656: 기초부터 배우기에서 반복되는 할 일을 뺀다(단계 간소화 1차). 네 트랙(V14 id) 모두 같은 문구다.
-- 1단계: '모범 연주 듣기'와 '이 악기 소리의 특징 느끼기'가 모두 듣는 할 일이라 한 할 일로 합친다.
-- 7단계: 정간 안 박 나누기(2분·3분)는 정간보 쉽게 읽기와 같은 내용이라 빼고 혀치기만 남긴다. 제목·목표·통과 기준도 맞춘다.
-- 첫 할 일(악기 구조 · 혀치기)은 V14 값을 그대로 옮긴다.

UPDATE lessons
SET content_json = JSON_SET(
        content_json,
        '$.tasks', JSON_ARRAY(
            JSON_EXTRACT(content_json, '$.tasks[0]'),
            JSON_OBJECT('title', '모범 연주 듣기', 'description', '이 악기로 연주한 소리를 듣고 높낮이·음색을 귀로 익혀요.')
        )
    )
WHERE id IN (1, 10, 19, 28) AND seq = 1 AND JSON_LENGTH(content_json, '$.tasks') = 3;

UPDATE lessons
SET title = '혀치기',
    content_json = JSON_SET(
        content_json,
        '$.goal', '같은 음을 혀로 끊어 반복한다',
        '$.why', '같은 음을 여러 번 낼 때 숨은 그대로 두고 혀로 끊어요. 정간 안 박 나누기는 정간보 쉽게 읽기에서 봐요.',
        '$.tasks', JSON_ARRAY(JSON_EXTRACT(content_json, '$.tasks[0]')),
        '$.passCriteria.description', '같은 음을 고르게 끊어 내는지 봐요(예시).'
    )
WHERE id IN (7, 16, 25, 34) AND seq = 7 AND title = '혀치기·박 나누기';
