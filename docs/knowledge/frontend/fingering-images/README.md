# 단소·소금·산조대금 운지 그림

[S15P31E102-602](https://ssafy.atlassian.net/browse/S15P31E102-602)의 역취 운지 그림 7종이다. 각 그림은 1200×532 PNG이며 앱의 악기 그림, 지공 좌표, 글꼴을 사용한다.

표의 `1`은 막음, `0`은 열림, `h`는 반 막음이다. 단소는 뒷구멍을 1공으로 세고 앞쪽 지공에 2~5공을 매긴다. 소금과 산조대금은 취구에 가까운 지공부터 1~6공을 센다.

| 악기 | 음 | 1공부터의 상태 | PNG |
| --- | --- | --- | --- |
| 단소 | 청협 | `11111` | [청협](danso-cheonghyeop.png) |
| 단소 | 청고 | `1111h` | [청고](danso-cheonggo.png) |
| 단소 | 청남 | `11h00` | [청남](danso-cheongnam.png) |
| 단소 | 중청협 | `01100` | [중청협](danso-jungcheonghyeop.png) |
| 단소 | 중청중 | `11010` | [중청중](danso-jungcheongjung.png) |
| 소금 | 중청태 | `110000` | [중청태](sogeum-jungcheongtae.png) |
| 산조대금 | 중청태 | `110111` | [중청태](daegeum_sanjo-jungcheongtae.png) |

운지는 단소 교육용 운지표, 소금 교재의 향악계곡 표, 피리사 산조대금 참고표를 따랐다. [단소 운지표](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-1/index.htm) · [소금 교재 표 1](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?boardid=10938&boardseq=42073&dirname=%2Fboard%2F42&filename=eng_sogeum.pdf&sistype=D&type=F) · [산조대금 참고 운지표](https://kukaksa.co.kr/web/myimg/daekum%20use.jpg)

저장소 루트에서 Python과 Pillow를 사용해 다시 생성할 수 있다. 생성기는 앱의 `GuideInstrument` 지공 좌표를 읽는다. 운지 적용과 카탈로그 갱신은 [S15P31E102-601](https://ssafy.atlassian.net/browse/S15P31E102-601)에서 별도로 다룬다.

```powershell
python frontend/tools/render_fingering_images.py
```

2026-11-09에 Pillow 11.2.1로 PNG 7개의 크기와 37개 지공 상태를 확인했다. 다른 폴더에 다시 생성한 결과는 바이트 단위로 일치했다. 축소 미리보기에서 율명과 반 막음 표시를 확인했다.
