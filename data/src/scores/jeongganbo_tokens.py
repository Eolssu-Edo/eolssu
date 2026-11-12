"""정간 OMR 토큰을 기존 JG-like 위치 표기로 옮긴다. 모델·PDF 라이브러리 없이 재사용한다."""


def jg_like(text):
    tokens = []
    for token in text.split():
        if ':' not in token:
            continue
        name, position = token.rsplit(':', 1)
        if position.isdigit() and 0 <= int(position) <= 15:
            tokens.append((name, position))
    if len(tokens) == 1 and tokens[0][1] == '5':
        tokens = [(tokens[0][0], '0')]
    return ' '.join(f":{position} {name.replace('_', ' ')}" for name, position in tokens)
