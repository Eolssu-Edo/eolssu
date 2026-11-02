-- Nominal identities required by existing jeongak/sogeum/sanjo basic-tone labels.
-- Existing V5 IDs/semitones and instrument range endpoints remain unchanged; IDs are allocated by MySQL.
-- Hanja follows data/src/scores/review_page.html HANJA. This seed does not verify recording tuning.
INSERT INTO pitches (yul, octave, hanja, hangul, semitone) VALUES
    ('임', -1, '㑣', '배임', -5),
    ('남', -1, '㑲', '배남', -3),
    ('무', -1, '㒇', '배무', -2),
    ('황', 0, '黃', '황', 0),
    ('태', 0, '太', '태', 2),
    ('고', 0, '姑', '고', 4),
    ('남', 0, '南', '남', 9),
    ('고', 1, '㴌', '청고', 16),
    ('남', 1, '湳', '청남', 21),
    ('고', 2, '㵈', '중청고', 28),
    ('임', 2, '㵉', '중청임', 31);
