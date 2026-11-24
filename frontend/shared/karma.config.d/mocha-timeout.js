// S15P31E102-1287: 브라우저(JS·Wasm) 테스트 하나의 Mocha 제한 시간을 기본 2초에서 10초로 늘린다.
// 녹음 변환·한 음씩 재생처럼 계산이 많은 테스트는 같은 서버에서 다른 빌드가 돌 때 2초를 넘겨 실패했다(운영 웹 잡 #27·#28).
// Kotlin Gradle 플러그인이 이 폴더의 파일을 생성된 karma.conf.js 끝에 붙인다.
config.client = config.client || {};
config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 10000 });
