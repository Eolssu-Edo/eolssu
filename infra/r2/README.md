# R2 파일·폴더 업로드

Cloudflare 화면에 접속하지 않고 기존 `RECORDING_STORAGE_*` 설정으로 파일이나 폴더를 올립니다. 저장소 루트에서 실행합니다. 파일 보관용 도구이며 DB 등록·앱 게시까지 처리하지 않습니다.

## 처음 한 번 준비

Python 3.10 이상(권장 3.11 이상)과 boto3를 사용합니다. AWS CLI는 필요 없습니다. 가상환경은 Git에서 제외됩니다.

```sh
python3.11 -m venv .venv
.venv/bin/python -m pip install -r infra/r2/requirements.txt
```

위 `python3.11`은 설치한 Python 3.10 이상 실행파일로 바꿀 수 있습니다. `python3` 기본 버전이 3.9 이하이면 그대로 사용하지 않습니다. Windows PowerShell에서는 `py -3.11 -m venv .venv`로 만들고 아래 명령의 `.venv/bin/python`을 `.venv/Scripts/python.exe`로 바꿉니다.

이미 R2 값이 들어 있는 환경파일이 있으면 그대로 `--env-file`로 지정합니다. 별도 로컬 설정이 필요한 팀원은 처음에만 다음 예시를 복사하고 담당자에게 받은 값을 입력합니다. 기존 `.env.r2`가 있으면 복사하지 않습니다.

```sh
cp infra/r2/.env.example .env.r2
```

| 환경변수 | 값 |
| --- | --- |
| `RECORDING_STORAGE_ENDPOINT` | 기존 `https://<account_id>.r2.cloudflarestorage.com` |
| `RECORDING_STORAGE_BUCKET` | 기존 버킷 이름. 운영 게시 기록의 버킷은 `eolssu-recordings` |
| `RECORDING_STORAGE_ACCESS_KEY_ID` | 기존 R2 S3 Access Key ID |
| `RECORDING_STORAGE_SECRET_ACCESS_KEY` | 기존 R2 S3 Secret Access Key |
| `RECORDING_STORAGE_REGION` | `auto`(생략·빈 값도 auto) |

`.env.r2`는 Git 제외 대상입니다. 운영 `.env` 전체를 팀원 PC로 복사하지 않습니다. 대시보드 계정은 필요 없지만 API 읽기·쓰기 키 또는 아래 서버 접근 권한은 필요합니다. 읽기 전용 `REFERENCE_AUDIO_STORAGE_*` 키는 이 도구의 입력이 아닙니다. 스크립트는 키 발급·권한 부여를 하지 않습니다.

환경파일은 실행하지 않습니다. 단일 행 `KEY=value`, `export KEY=value`, 따옴표와 따옴표 밖 주석을 지원하며 셸 명령·`$변수`를 치환하지 않습니다. 명시한 파일만 사용하고 프로세스 환경과 섞지 않습니다. 파일을 지정하지 않으면 현재 프로세스의 `RECORDING_STORAGE_*`를 사용합니다. `.env` 자동 탐색은 하지 않습니다.

## 파일 하나 올리기

먼저 미리보기로 키를 확인한 뒤 `--dry-run`을 빼면 업로드합니다.

```sh
.venv/bin/python infra/r2/upload.py ./sample.wav dev-assets/joe/sample.wav --env-file .env.r2 --dry-run
.venv/bin/python infra/r2/upload.py ./sample.wav dev-assets/joe/sample.wav --env-file .env.r2
```

파일 목적지가 `/`로 끝나면 원래 파일명이 붙습니다.

```sh
.venv/bin/python infra/r2/upload.py "./한글 파일.wav" dev-assets/joe/ --env-file .env.r2
```

## 폴더 전체 올리기

```sh
.venv/bin/python infra/r2/upload.py ./my-data dev-assets/joe/my-data/ --env-file .env.r2 --dry-run
.venv/bin/python infra/r2/upload.py ./my-data dev-assets/joe/my-data/ --env-file .env.r2
```

`my-data/a.wav` → `dev-assets/joe/my-data/a.wav`, `my-data/sub/b.json` → `dev-assets/joe/my-data/sub/b.json`으로 저장합니다. 원본 폴더 이름을 자동으로 한 번 더 붙이지 않습니다. 빈 폴더 객체는 만들지 않습니다. `dev-assets/joe/`는 개발 자료용 예시 경로이며 작업자·자료에 맞게 바꿉니다.

출력의 `UPLOADED s3://버킷/객체키`를 팀원에게 공유하면 됩니다. 비공개 객체의 주소이므로 웹 공개 링크가 아닙니다. 코드는 같은 버킷과 객체 키로 읽습니다. 기본음·시김새의 앱 노출이 목적이면 [카탈로그 적재·게시 절차](../catalog/README.md)를 별도로 따릅니다. 그때 객체 키는 임의 개발 경로 대신 검증된 manifest를 따릅니다.

## 충돌·미리보기·실패

- 같은 키가 이미 있으면 조건부 PUT으로 기존 객체를 보존하고 `SKIP existing`을 표시합니다. 건너뛰기는 내용이 동일하다는 검증이 아닙니다. 기존 객체 존재로 인한 SKIP은 정상 종료(0)입니다.
- 교체할 파일과 키를 확인한 경우에만 `--overwrite`를 추가합니다. 다른 파일을 삭제하거나 폴더 전체를 원격과 동기화하지 않습니다.
- `--dry-run`은 설정·로컬 입력과 키·크기만 검사합니다. SDK·네트워크·실제 자격증명이 필요 없으며 원격 객체 존재나 쓰기 권한은 확인하지 않습니다. 단, 설정 필드는 채워져 있어야 합니다.
- 입력 트리 전체를 먼저 검사합니다. `.git`, `.env`, `.env.*`, 심볼릭 링크·특수 파일, 5GiB 초과 단일 파일은 거절합니다. 대용량 multipart는 지원하지 않습니다.
- `recordings/`는 사용자 녹음과 30일 삭제 경로여서 거절합니다. 이 도구는 버킷 공개 설정·수명 주기를 변경하지 않습니다. 다른 접두사의 보관 규칙도 운영 담당자의 기존 정책을 따릅니다.
- 폴더 업로드는 파일별 처리입니다. 실패하면 종료 1이고 앞서 올라간 파일은 남습니다. 문제를 해결한 뒤 같은 명령을 다시 실행하면 기존 객체는 건너뜁니다. 크기·전송 무결성은 Content-Length·Content-MD5로 전달하며 SHA-256은 객체 metadata에도 기록합니다. 다운로드 후 해시를 대조하는 검증은 이 도구에 포함하지 않습니다.
- SDK 오류의 원문·키·서명 URL은 출력하지 않습니다. 오류 시 설정 누락, 파일 접근, 키의 쓰기 권한, 네트워크를 확인합니다. 오래된 boto3가 조건부 PUT을 지원하지 않으면 가상환경에서 requirements를 다시 설치합니다.

## 키를 PC에 받지 않고 기존 서버에서 사용

운영 R2 설정 위치는 서버 `/opt/eolssu/.env`입니다. 기존 SSH 접속과 이 파일을 읽을 수 있는 권한이 있는 팀원·담당자는 서버에서 같은 스크립트를 실행할 수 있습니다. 이 절은 수동 준비 방법이며 이 Task에서 서버 설치나 권한 변경을 수행한 것은 아닙니다.

서버에 도구를 준비하는 담당자가 처음 한 번 실행합니다. 서버도 `python3 --version`으로 3.10 이상임을 확인합니다. 다르면 아래 `python3`를 설치된 3.10 이상 실행파일로 바꿉니다. SSH 별칭 `edo1`은 각자의 SSH 설정에 필요합니다. 기존 같은 이름의 도구·폴더가 있으면 내용을 확인하고 사용합니다.

```sh
ssh -o ClearAllForwardings=yes edo1 'mkdir -p "$HOME/eolssu-r2" "$HOME/eolssu-r2-input"'
scp infra/r2/upload.py infra/r2/requirements.txt edo1:eolssu-r2/
ssh -o ClearAllForwardings=yes edo1 'python3 -m venv "$HOME/eolssu-r2/.venv" && "$HOME/eolssu-r2/.venv/bin/python" -m pip install -r "$HOME/eolssu-r2/requirements.txt"'
```

파일을 서버에 보내고, 서버 안에서 설정을 읽어 업로드합니다. 기존 sudo 권한이 있는 실행자를 위한 예시입니다. sudo 없이 환경파일을 읽을 수 있으면 `sudo`를 생략합니다.

```sh
scp ./sample.wav edo1:eolssu-r2-input/sample.wav
ssh -t -o ClearAllForwardings=yes edo1 'sudo "$HOME/eolssu-r2/.venv/bin/python" "$HOME/eolssu-r2/upload.py" "$HOME/eolssu-r2-input/sample.wav" dev-assets/joe/sample.wav --env-file /opt/eolssu/.env --dry-run'
```

목록 확인 후 마지막 `--dry-run`을 빼고 다시 실행합니다. 폴더는 `scp -r ./my-data edo1:eolssu-r2-input/`로 전송하고 source를 `$HOME/eolssu-r2-input/my-data`, destination을 `dev-assets/joe/my-data/`로 바꿉니다. 재전송 전에는 서버 입력 폴더의 예전 파일이 섞이지 않도록 새 작업 폴더를 사용합니다. SSH·환경파일 권한이 없는 팀원은 담당자에게 업로드를 요청하거나 별도 개발용 키를 받아 로컬 방식을 사용해야 합니다.

## 검증

```sh
.venv/bin/python -m unittest discover -s infra/r2 -p 'test_*.py' -v
.venv/bin/python infra/r2/upload.py --help
```

파일·중첩 폴더·한글/공백·환경파일·충돌·교체·실패·미리보기·입력 보호를 검사합니다. boto3가 있으면 로컬 HTTP 서버로 실제 SDK 서명·조건부 PUT·체크섬도 검사하고, 없으면 HTTP 검사만 건너뜁니다. 테스트에는 운영 키·R2 접속이 없습니다. 운영 업로드·서버 설치 및 팀원별 권한은 이 검사로 확인하지 않습니다.

[스펙](../../docs/specs/r2-upload.md) · [392 구현·검증 기록](../../docs/plans/S15P31E102-392.md)
