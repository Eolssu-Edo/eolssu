"""Local photo review and CAT playback API. Run with python -m playback.service."""
import argparse
from copy import deepcopy
import hashlib
import io
import json
import math
from pathlib import Path
import re
import tempfile
import threading
from typing import Literal
from uuid import uuid4

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel, ConfigDict, Field, StrictBool
from starlette.middleware.trustedhost import TrustedHostMiddleware

from .__main__ import run
from .score import compile_score, require

MAX_UPLOAD = 8 * 1024 * 1024
MAX_PIXELS = 16_000_000
Image.MAX_IMAGE_PIXELS = MAX_PIXELS


class Revision(BaseModel):
    model_config = ConfigDict(extra="forbid")
    revision: int = Field(ge=0, strict=True)


class Draft(Revision):
    score: dict
    regions: list[dict] = Field(default_factory=list, max_length=1000)


class Render(Revision):
    approved: StrictBool
    vibrato: Literal["none", "quarter", "third"] = "quarter"


class Recognition(Revision):
    context: dict


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False), encoding="utf-8")
    temporary.replace(path)


def blank_score(notation):
    context = {"pitch_mode":"sounding", "notation_octave_shift":0, "tuning_a_hz":440,
               "style":"정악 단소", "tuning_description":"사용자가 확인할 A440 12평균율 비교 기준"}
    if notation == "jeonggan":
        context.update(pitch_mode="yul", huangjong_midi=None)
    return {"schema":"gugak-reviewed-score/v1", "title":"새 악보",
            "source":{"edition":"업로드한 악보 사진", "review_status":"manually-transcribed-unverified"},
            "context":context, "quarter_bpm":60, "notes":[], "assumptions":[]}


def checked_score(score, notation, regions):
    """Validate a draft without pretending that model output has been reviewed."""
    candidate = deepcopy(score)
    try:
        require(isinstance(candidate.get("source"),dict) and isinstance(candidate.get("context"),dict), "source와 context는 객체여야 합니다")
        require(len(json.dumps(candidate, allow_nan=False)) <= 1_000_000, "악보 데이터가 너무 큽니다")
        require(isinstance(candidate.get("title"), str) and len(candidate["title"]) <= 200, "제목은 200자 이하여야 합니다")
        require(isinstance(candidate.get("notes"), list) and 0 < len(candidate["notes"]) <= 1000, "음표는 1~1000개여야 합니다")
        for n in candidate["notes"]:
            require(isinstance(n, dict) and isinstance(n.get("id"), str) and len(n["id"]) <= 100, "음표 ID를 확인하세요")
            require(len(str(n.get("duration"))) <= 32, "음표 길이 값이 너무 깁니다")
        require(candidate["source"].get("review_status") in ("manually-transcribed-unverified", "machine-transcribed-unverified"), "지원하지 않는 검수 상태입니다")
        candidate["source"]["review_status"] = "manually-transcribed-unverified"
        expected = "yul" if notation == "jeonggan" else "sounding"
        require(candidate["context"]["pitch_mode"] == expected, f"이 표기법은 pitch_mode={expected}를 사용합니다")
        perf = compile_score(candidate, "danso")
        require(perf["end_tick"]*perf["tempo_us"]/1e6/perf["ppqn"] <= 120, "로컬 요청은 2분 이내로 나눠 주세요")
        ids = {n["id"] for n in candidate["notes"]}
        seen = set()
        for region in regions:
            require(set(region) == {"note_id", "bbox"}, "영역에는 note_id와 bbox만 허용합니다")
            require(region["note_id"] in ids and region["note_id"] not in seen, "영역의 음표 ID가 없거나 중복됩니다")
            seen.add(region["note_id"])
            box = region["bbox"]
            require(isinstance(box,list) and len(box)==4 and all(type(v) in (int,float) and math.isfinite(v) for v in box), "영역은 [x,y,width,height]여야 합니다")
            x,y,w,h = box
            require(0 <= x < 1 and 0 <= y < 1 and 0 < w <= 1-x and 0 < h <= 1-y, "영역은 사진 안의 0~1 좌표여야 합니다")
    except (KeyError, TypeError, OverflowError, ZeroDivisionError) as exc:
        raise ValueError("악보 구조 또는 음표 값을 확인하세요") from exc
    return candidate


def create_app(storage, assets, *, model=None, ollama_url="http://127.0.0.1:11434"):
    root, assets = Path(storage).resolve(), Path(assets).resolve()
    root.mkdir(parents=True, exist_ok=True)
    app = FastAPI(title="악보에서 단소 연주로", version="0.1.0")
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=["127.0.0.1","localhost","testserver"])
    mutex = threading.RLock()
    worker = threading.Lock()

    @app.middleware("http")
    async def local_boundary(request, call_next):
        if request.method in ("POST","PUT","DELETE"):
            if request.headers.get("X-Playback-Client") != "local":
                return JSONResponse({"detail":"로컬 클라이언트 헤더가 필요합니다"},403)
            origin = request.headers.get("origin")
            if origin and origin != str(request.base_url).rstrip("/"):
                return JSONResponse({"detail":"다른 사이트의 요청을 허용하지 않습니다"},403)
            # ASGI body is bounded before JSON or image parsing (also chunked requests).
            chunks, size = [], 0
            async for chunk in request.stream():
                size += len(chunk)
                if size > MAX_UPLOAD:
                    return JSONResponse({"detail":"요청은 8 MiB 이하여야 합니다"},413)
                chunks.append(chunk)
            request._body = b"".join(chunks)
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Cache-Control"] = "no-store"
        response.headers["Content-Security-Policy"] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; media-src 'self'; connect-src 'self'; frame-ancestors 'none'"
        return response

    def folder(key):
        if not re.fullmatch(r"[a-f0-9]{32}",key):
            raise HTTPException(404,"악보를 찾을 수 없습니다")
        path = root/key
        if not (path/"job.json").is_file():
            raise HTTPException(404,"악보를 찾을 수 없습니다")
        return path

    def read(key):
        return json.loads((folder(key)/"job.json").read_text(encoding="utf-8"))

    def revision(job, expected):
        if job["revision"] != expected:
            raise HTTPException(409,"악보가 변경되었습니다. 최신 내용을 다시 확인하세요")

    @app.exception_handler(ValueError)
    async def invalid(request, error):
        return JSONResponse({"detail":str(error)},422)

    @app.get("/")
    def page():
        return FileResponse(Path(__file__).with_name("web")/"index.html")

    @app.get("/static/{name}")
    def static(name: Literal["app.js","style.css"]):
        return FileResponse(Path(__file__).with_name("web")/name,
                            media_type={"app.js":"text/javascript","style.css":"text/css"}[name])

    @app.get("/api/config")
    def config():
        return {"recognition_configured":bool(model), "instrument":"danso", "default_vibrato":"quarter"}

    @app.post("/api/scores", status_code=201)
    async def upload(request: Request, notation: Literal["staff","jeonggan"]):
        raw = await request.body()
        try:
            with Image.open(io.BytesIO(raw)) as source:
                require(source.format in ("PNG","JPEG","WEBP"), "PNG·JPEG·WebP 사진을 사용하세요")
                require(source.width*source.height <= MAX_PIXELS and not getattr(source,"is_animated",False), "사진은 1600만 화소 이내의 정지 이미지여야 합니다")
                source.load()
                normalized = ImageOps.exif_transpose(source).convert("RGB")
        except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
            raise HTTPException(422,"올바른 악보 사진을 선택하세요") from exc
        key = uuid4().hex
        job = {"id":key, "notation":notation, "revision":0, "state":"needs_review",
               "image_url":f"/api/scores/{key}/image", "image_sha256":hashlib.sha256(raw).hexdigest(),
               "width":normalized.width,"height":normalized.height,
               "score":blank_score(notation), "regions":[], "warnings":[], "result":None}
        with mutex:
            path = root/key
            path.mkdir()
            normalized.save(path/"source.png")
            # Lossless preview can exceed the upload size (e.g. camera JPEG).
            # Keep original coordinates; shrink only the local model derivative.
            recognition_image = normalized.copy()
            recognition_image.thumbnail((2500,2500))
            recognition_image.save(path/"recognition.jpg",quality=90)
            write_json(path/"job.json",job)
        return job

    @app.get("/api/scores/{key}")
    def get_job(key: str):
        with mutex:
            return read(key)

    @app.get("/api/scores/{key}/image")
    def get_image(key: str):
        return FileResponse(folder(key)/"source.png",media_type="image/png")

    @app.put("/api/scores/{key}/draft")
    def save_draft(key: str, body: Draft):
        with mutex:
            job = read(key)
            revision(job,body.revision)
            checked_score(body.score,job["notation"],body.regions)
            job.update(score=body.score,regions=body.regions,revision=job["revision"]+1,state="needs_review",result=None)
            write_json(folder(key)/"job.json",job)
            return job

    @app.post("/api/scores/{key}/recognize")
    def recognize_photo(key: str, body: Recognition):
        if not model:
            raise HTTPException(503,"로컬 인식 모델이 연결되지 않았습니다. 음표를 입력하거나 검수한 JSON을 불러오세요")
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            with mutex:
                job = read(key)
                revision(job,body.revision)
            from .recognition import recognize, RecognitionError
            try:
                path = folder(key)
                recognition_image = path/"recognition.jpg"
                if not recognition_image.exists():
                    recognition_image = path/"source.png"
                result = recognize(recognition_image,job["notation"],body.context,model=model,base_url=ollama_url)
            except RecognitionError as exc:
                raise HTTPException(503,str(exc)) from exc
            if result["score"]["notes"]:
                checked_score(result["score"],job["notation"],result["regions"])
            with mutex:
                job = read(key)
                revision(job,body.revision)
                job.update(score=result["score"],regions=result["regions"],warnings=result["warnings"],provider=result["provider"],
                           revision=job["revision"]+1,state="needs_review",result=None)
                write_json(folder(key)/"job.json",job)
            return job
        finally:
            worker.release()

    @app.post("/api/scores/{key}/render")
    def render(key: str, body: Render):
        require(body.approved,"사진과 음표를 확인한 뒤 생성하세요")
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            with mutex:
                job = read(key)
                revision(job,body.revision)
                score = checked_score(job["score"],job["notation"],job["regions"])
                if job["result"] and job["result"]["vibrato"] == body.vibrato:
                    return job["result"]
            score["source"].update(image_sha256=job["image_sha256"],reviewed_revision=job["revision"])
            destination = folder(key)/"renders"
            destination.mkdir(exist_ok=True)
            render_id = uuid4().hex
            with tempfile.TemporaryDirectory(prefix="pending-",dir=destination) as temporary:
                stage = Path(temporary)
                write_json(stage/"input.json",score)
                try:
                    run(stage/"input.json",assets,stage/"output",["danso"],vibrato=body.vibrato)
                except (OSError, KeyError) as exc:
                    raise HTTPException(503,"CAT 설정·파일을 확인하세요. 서버 콘솔의 실행 설정을 점검하세요") from exc
                report = json.loads((stage/"output"/"danso.json").read_text(encoding="utf-8"))
                timeline = [{"note_ids":e["parent_ids"],"start":e["start_tick"]*report["tempo_us"]/1e6/report["ppqn"],
                             "end":e["end_tick"]*report["tempo_us"]/1e6/report["ppqn"],"pitch":e["pitch"]} for e in report["events"]]
                prefix = f"/api/scores/{key}/renders/{render_id}"
                result = {"audio_url":prefix+"/danso.wav","midi_url":prefix+"/danso.mid", "score_url":prefix+"/score.json",
                          "vibrato":body.vibrato,"revision":job["revision"],"timeline":timeline,"regions":job["regions"],
                          "seconds":report["seconds"],"warnings":report["warnings"],"credit":
                          json.loads((stage/"output"/"manifest.json").read_text(encoding="utf-8"))["credit"]}
                with mutex:
                    current = read(key)
                    revision(current,body.revision)
                    (stage/"output").replace(destination/render_id)
                    current.update(state="ready",result=result)
                    write_json(folder(key)/"job.json",current)
            return result
        finally:
            worker.release()

    @app.get("/api/scores/{key}/renders/{render_id}/{name}")
    def artifact(key: str, render_id: str, name: Literal["danso.wav","danso.mid","score.json"]):
        if not re.fullmatch(r"[a-f0-9]{32}",render_id):
            raise HTTPException(404,"생성 결과가 없습니다")
        path = folder(key)/"renders"/render_id/name
        if not path.is_file():
            raise HTTPException(404,"생성 결과가 없습니다")
        return FileResponse(path,media_type={"danso.wav":"audio/wav","danso.mid":"audio/midi","score.json":"application/json"}[name])

    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets",required=True)
    parser.add_argument("--storage",required=True,help="Private local output directory, outside Git")
    parser.add_argument("--port",type=int,default=8765)
    parser.add_argument("--model",help="Installed Ollama vision model name; no automatic download")
    parser.add_argument("--ollama-url",default="http://127.0.0.1:11434")
    args = parser.parse_args()
    import uvicorn
    uvicorn.run(create_app(args.storage,args.assets,model=args.model,ollama_url=args.ollama_url),host="127.0.0.1",port=args.port)


if __name__ == "__main__":
    main()
