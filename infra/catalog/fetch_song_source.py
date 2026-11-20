"""운영 곡 하나의 DB 행과 모범 음원·수행 시간표·공통 악보를 읽기만 해서 받는다 (S15P31E102-1227).

운영 서버에서 root로 실행한다. DB는 SELECT만, R2는 GET·LIST만 한다. 결과는 --out 폴더에 쓴다.
  sudo python3 fetch_song_source.py --song-id 93 --env-file /opt/eolssu/.env --out /tmp/ops1227-u1
out/song.json, out/model-audios.json, out/files/<R2 키 마지막 두 단계>, out/case-names.json, out/sha256.json
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

from enable_scoring import mysql
from stage_model_audio import create_client

CASE = re.compile(r"^model-audio/drafts/[0-9a-f]{64}/cases/([^/]+)/")


def case_names(keys):
    return sorted({m.group(1) for m in map(CASE.match, keys) if m})


def json_select(table, columns, where):
    """행마다 한 줄 JSON을 내는 SELECT. 컬럼 이름은 information_schema에서 읽은 것만 넣는다."""
    pairs = ",".join(f"'{c}',`{c}`" for c in columns)
    return f"SELECT JSON_OBJECT({pairs}) FROM {table} WHERE {where};"


def rows(table, where):
    columns = mysql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                    f"AND table_name='{table}' ORDER BY ordinal_position;").split()
    return [json.loads(line) for line in mysql(json_select(table, columns, where)).splitlines() if line]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--song-id", type=int, required=True)
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=False)
    song = rows("songs", f"id={args.song_id}")
    audios = rows("song_model_audios", f"song_id={args.song_id}")
    (out / "song.json").write_text(json.dumps(song, ensure_ascii=False, indent=1), encoding="utf-8")
    (out / "model-audios.json").write_text(json.dumps(audios, ensure_ascii=False, indent=1), encoding="utf-8")
    client, bucket = create_client(args.env_file)
    keys = []
    for page in client.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix="model-audio/drafts/"):
        keys += [item["Key"] for item in page.get("Contents", [])]
    (out / "case-names.json").write_text(json.dumps(case_names(keys), indent=1), encoding="utf-8")
    sums = {}
    for audio in audios:
        folder = audio["object_key"].rsplit("/", 1)[0]
        for key in (audio["object_key"], audio["performance_object_key"], folder + "/source.common.json"):
            data = client.get_object(Bucket=bucket, Key=key)["Body"].read()
            target = out / "files" / "/".join(key.split("/")[-2:])
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            sums[key] = hashlib.sha256(data).hexdigest()
    (out / "sha256.json").write_text(json.dumps(sums, indent=1), encoding="utf-8")
    print(json.dumps({"songs": len(song), "audios": len(audios), "cases": len(case_names(keys)), "files": len(sums)}))


if __name__ == "__main__":
    main()
