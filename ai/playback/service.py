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
import xml.etree.ElementTree as ET
import zipfile
from typing import Literal
from uuid import uuid4

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from PIL import Image, ImageOps, UnidentifiedImageError
from pydantic import BaseModel, ConfigDict, Field, StrictBool
import soundfile as sf
from starlette.middleware.trustedhost import TrustedHostMiddleware

from .__main__ import run
from .score import compile_score, require
from .catalog import Instrument, INSTRUMENTS, LABELS, DAEGEUM_NOTICE, instrument_catalog, selected_instrument
from .sampler import CatAssetError

MAX_UPLOAD = 8 * 1024 * 1024
MAX_PIXELS = 16_000_000
MAX_SCORE_SECONDS = 600
Image.MAX_IMAGE_PIXELS = MAX_PIXELS


class Revision(BaseModel):
    model_config = ConfigDict(extra="forbid")
    revision: int = Field(ge=0, strict=True)


class Draft(Revision):
    score: dict
    regions: list[dict] = Field(default_factory=list, max_length=1000)
    instrument: Instrument | None = None


class Render(Revision):
    approved: StrictBool
    vibrato: Literal["none", "quarter", "third"] = "quarter"
    instrument: Instrument | None = None


class Recognition(Revision):
    context: dict


class Automatic(Revision):
    vibrato: Literal["none", "quarter", "third"] = "quarter"
    context: dict | None = None
    instrument: Instrument | None = None


class Preview(Revision):
    vibrato: Literal["none", "quarter", "third"] = "quarter"
    instrument: Instrument | None = None


def recognition_message(error):
    message = str(error)
    if any(term in message for term in ("rectangular grid", "Partial grid", "Grid cells", "Jeonggan cell", "symbol positions", "side-by-side", "incomplete cell")):
        return "정간보의 격자나 기호 위치를 읽지 못했습니다. 칸 테두리가 온전히 보이고 기울어지지 않은 선명한 페이지 또는 구간을 사용하세요."
    if any(term in message for term in ("Unknown or uncertain glyph", "unknown pitch/symbol", "unknown isolated glyphs", "Ambiguous register")):
        return "확실히 읽지 못한 정간보 음·기호가 있습니다. 더 선명한 이미지를 사용하거나 원본과 대조해 음표를 직접 입력하세요."
    if "Explicit duration factors do not fill the time row" in message:
        return "정간보 길이표의 합계가 한 행의 박자와 맞지 않습니다. 원본의 덧길이·반길이 표시와 연결 음을 확인하세요."
    if "unavailable or timed out" in message:
        return "로컬 인식 모델에 연결하지 못했거나 시간이 초과됐습니다. 모델 실행 상태를 확인하세요."
    return message


def write_json(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False), encoding="utf-8")
    temporary.replace(path)


def blank_score(notation):
    context = {"pitch_mode":"sounding", "notation_octave_shift":0, "tuning_a_hz":440,
               "style":"국악 악보 합성 미리듣기", "tuning_description":"사용자가 확인할 A440 12평균율 비교 기준"}
    if notation == "jeonggan":
        context.update(pitch_mode="yul", huangjong_midi=None)
    return {"schema":"gugak-reviewed-score/v1", "title":"새 악보",
            "source":{"edition":"업로드한 악보 사진", "review_status":"manually-transcribed-unverified"},
            "context":context, "quarter_bpm":60, "notes":[], "assumptions":[]}


def checked_score(score, notation, regions, instrument="danso"):
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
        expected = "yul" if notation == "jeonggan" else "sounding"
        require(candidate["context"]["pitch_mode"] == expected, f"이 표기법은 pitch_mode={expected}를 사용합니다")
        perf = compile_score(candidate, instrument)
        require(perf["end_tick"]*perf["tempo_us"]/1e6/perf["ppqn"] <= MAX_SCORE_SECONDS, "로컬 요청은 10분 이내로 나눠 주세요")
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


def create_app(storage, assets, *, model=None, ollama_url="http://127.0.0.1:11434", audiveris=None):
    root, assets = Path(storage).resolve(), Path(assets).resolve()
    root.mkdir(parents=True, exist_ok=True)
    app = FastAPI(title="악보에서 국악기 연주로", version="0.2.0")
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
        job = json.loads((folder(key)/"job.json").read_text(encoding="utf-8"))
        job.setdefault("instrument", "danso")
        return job

    def available(instrument):
        choice = next(c for c in instrument_catalog(assets) if c["id"] == instrument)
        if not choice["available"]:
            raise HTTPException(503, f'{choice["label"]}: {choice["message"]}')

    def render_error(key, expected, instrument, error):
        with mutex:
            job = read(key)
            if job["revision"] == expected:
                job["error"] = str(error.detail) if isinstance(error,HTTPException) else str(error)
                job["error_instrument"] = instrument
                write_json(folder(key)/"job.json",job)

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
        return {"recognition_configured":bool(model or audiveris), "model":model,
                "staff_engine":"audiveris" if audiveris else "vision",
                "instrument":"danso", "default_instrument":"danso", "instruments":instrument_catalog(assets),
                "default_vibrato":"quarter", "preview_huangjong_midi":63}

    @app.post("/api/scores", status_code=201)
    async def upload(request: Request, notation: Literal["staff","jeonggan"], instrument: Instrument = "danso"):
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
        job = {"id":key, "notation":notation, "instrument":instrument, "revision":0, "state":"needs_review",
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
            instrument = selected_instrument(job,body.instrument)
            checked_score(body.score,job["notation"],body.regions,instrument)
            job.update(score=body.score,regions=body.regions,instrument=instrument,revision=job["revision"]+1,state="needs_review",result=None)
            job.pop("error",None)
            job.pop("error_instrument",None)
            write_json(folder(key)/"job.json",job)
            return job

    def recognize_job(key, expected, context):
        with mutex:
            job = read(key)
            revision(job,expected)
        if not model and not (audiveris and job["notation"] == "staff"):
            raise HTTPException(503,"이 표기법의 로컬 인식 모델이 연결되지 않았습니다")
        from .recognition import recognize, RecognitionError
        path = folder(key)
        # Page-sized Jeonggan needs original strokes; its reader bounds each glyph crop.
        recognition_image = path/("source.png" if job["notation"] == "jeonggan" else "recognition.jpg")
        if not recognition_image.exists():
            recognition_image = path/"source.png"
        try:
            if job["notation"] == "staff" and audiveris:
                from .staff import recognize_staff
                result = recognize_staff(path/"source.png",context,executable=audiveris,
                                         model=model,base_url=ollama_url,trace_path=path/"recognition-trace.json")
            else:
                result = recognize(recognition_image,job["notation"],context,model=model,base_url=ollama_url,
                                   trace_path=path/"recognition-trace.json",structured=True)
        except RecognitionError as exc:
            message = recognition_message(exc)
            with mutex:
                current = read(key)
                if current["revision"] == expected:
                    current["error"] = message
                    write_json(path/"job.json",current)
            raise HTTPException(503,message) from exc
        # Preserve the machine draft even if the instrument renderer rejects it.
        result["score"]["source"]["original_image_sha256"] = job["image_sha256"]
        with mutex:
            job = read(key)
            revision(job,expected)
            job.update(score=result["score"],regions=result["regions"],warnings=result["warnings"],provider=result["provider"],recognition_context=deepcopy(context),
                       revision=job["revision"]+1,state="needs_review",result=None)
            job.pop("error",None)
            job.pop("error_instrument",None)
            write_json(folder(key)/"job.json",job)
        return job

    @app.post("/api/scores/{key}/recognize")
    def recognize_photo(key: str, body: Recognition):
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            return recognize_job(key,body.revision,body.context)
        finally:
            worker.release()

    def render_job(key, expected, vibrato, mode, instrument=None):
        with mutex:
            job = read(key)
            revision(job,expected)
            instrument = selected_instrument(job,instrument)
            score = checked_score(job["score"],job["notation"],job["regions"],instrument)
            if (job["result"] and job["result"].get("instrument","danso") == instrument
                    and job["result"]["vibrato"] == vibrato and job["result"].get("review_mode") == mode
                    and job["result"].get("revision") == expected):
                if "error" in job or "error_instrument" in job:
                    job.pop("error",None)
                    job.pop("error_instrument",None)
                    write_json(folder(key)/"job.json",job)
                return job["result"]
        available(instrument)
        score["source"].update(original_image_sha256=job["image_sha256"],review_mode=mode)
        if mode == "user-confirmed":
            score["source"]["reviewed_revision"] = expected
        else:
            score["source"].pop("reviewed_revision",None)
        destination = folder(key)/"renders"
        destination.mkdir(exist_ok=True)
        render_id = uuid4().hex
        with tempfile.TemporaryDirectory(prefix="pending-",dir=destination) as temporary:
            stage = Path(temporary)
            write_json(stage/"input.json",score)
            try:
                run(stage/"input.json",assets,stage/"output",[instrument],vibrato=vibrato)
            except (OSError, KeyError, zipfile.BadZipFile, ET.ParseError, sf.SoundFileRuntimeError, CatAssetError) as exc:
                write_json(folder(key)/"render-error.json",{"instrument":instrument,"revision":expected,
                           "error_type":type(exc).__name__,"detail":str(exc)})
                raise HTTPException(503,f"{LABELS[instrument]} CAT 파일을 읽지 못했습니다. 로컬 음원 파일과 프리셋 설정을 확인하세요") from exc
            report = json.loads((stage/"output"/f"{instrument}.json").read_text(encoding="utf-8"))
            timeline = [{"note_ids":e["parent_ids"],"start":e["start_tick"]*report["tempo_us"]/1e6/report["ppqn"],
                         "end":e["end_tick"]*report["tempo_us"]/1e6/report["ppqn"],"pitch":e["pitch"]} for e in report["events"]]
            prefix = f"/api/scores/{key}/renders/{render_id}"
            result = {"audio_url":prefix+f"/{instrument}.wav","midi_url":prefix+f"/{instrument}.mid", "score_url":prefix+"/score.json",
                      "instrument":instrument,"instrument_label":LABELS[instrument],
                      "vibrato":vibrato,"revision":expected,"review_mode":mode,"timeline":timeline,"regions":job["regions"],
                      "seconds":report["seconds"],"warnings":[*job["warnings"],*report["warnings"]],"credit":
                      json.loads((stage/"output"/"manifest.json").read_text(encoding="utf-8"))["credit"]}
            if instrument.endswith("daegeum"):
                result["warnings"].append(DAEGEUM_NOTICE)
            with mutex:
                current = read(key)
                revision(current,expected)
                (stage/"output").replace(destination/render_id)
                current.update(instrument=instrument,state="preview_ready" if mode == "automatic-preview" else "ready",result=result)
                current.pop("error",None)
                current.pop("error_instrument",None)
                write_json(folder(key)/"job.json",current)
        return result

    @app.post("/api/scores/{key}/automatic")
    def automatic(key: str, body: Automatic):
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            with mutex:
                job = read(key)
                revision(job,body.revision)
                instrument = selected_instrument(job,body.instrument)
            try:
                available(instrument)
            except HTTPException as exc:
                render_error(key,job["revision"],instrument,exc)
                raise
            context = body.context
            assumption = None
            if context is None:
                context = blank_score(job["notation"])["context"]
                if job["notation"] == "jeonggan":
                    context["huangjong_midi"] = 63
                    context["cell_quarters"] = 1.5
                    context["reading_direction"] = "top-to-bottom,right-to-left"
                    assumption = "정간보 미리듣기 기준: 중성 黃=E♭4(MIDI 63), 한 정간=점4분, 위→아래·오른쪽→왼쪽. 사진에서 확인한 조율값이 아닙니다."
                else:
                    context["ornament_pitch_classes"] = [1,3,5,8,10]
                    assumption = "국악 위음 장식의 미리듣기는 黃=E♭ 기준 黃太仲林無 음계로 해석합니다. 다른 청·서양식 장식음은 해당 음계 문맥으로 다시 인식하거나 직접 수정하세요."
            job = recognize_job(key,body.revision,context)
            if assumption:
                with mutex:
                    current = read(key)
                    revision(current,job["revision"])
                    current["warnings"].append(assumption)
                    current["score"]["assumptions"].append(assumption)
                    write_json(folder(key)/"job.json",current)
            try:
                render_job(key,job["revision"],body.vibrato,"automatic-preview",instrument)
            except (ValueError,HTTPException) as exc:
                render_error(key,job["revision"],instrument,exc)
                raise
            with mutex:
                return read(key)
        finally:
            worker.release()

    @app.post("/api/scores/{key}/render")
    def render(key: str, body: Render):
        require(body.approved,"사진과 음표를 확인한 뒤 생성하세요")
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            return render_job(key,body.revision,body.vibrato,"user-confirmed",body.instrument)
        except (ValueError,HTTPException) as exc:
            render_error(key,body.revision,body.instrument or read(key)["instrument"],exc)
            raise
        finally:
            worker.release()

    @app.post("/api/scores/{key}/preview")
    def preview(key: str, body: Preview):
        """Audition the saved draft with another instrument, without re-running OCR."""
        if not worker.acquire(blocking=False):
            raise HTTPException(409,"다른 생성 작업이 진행 중입니다")
        try:
            render_job(key,body.revision,body.vibrato,"automatic-preview",body.instrument)
            with mutex:
                return read(key)
        except (ValueError,HTTPException) as exc:
            render_error(key,body.revision,body.instrument or read(key)["instrument"],exc)
            raise
        finally:
            worker.release()

    @app.get("/api/scores/{key}/renders/{render_id}/{name}")
    def artifact(key: str, render_id: str, name: str):
        types = {f"{key}.{suffix}":mime for key in INSTRUMENTS
                 for suffix,mime in (("wav","audio/wav"),("mid","audio/midi"))}
        types["score.json"] = "application/json"
        if name not in types:
            raise HTTPException(404,"생성 결과가 없습니다")
        if not re.fullmatch(r"[a-f0-9]{32}",render_id):
            raise HTTPException(404,"생성 결과가 없습니다")
        path = folder(key)/"renders"/render_id/name
        if not path.is_file():
            raise HTTPException(404,"생성 결과가 없습니다")
        return FileResponse(path,media_type=types[name])

    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets",required=True)
    parser.add_argument("--storage",required=True,help="Private local output directory, outside Git")
    parser.add_argument("--port",type=int,default=8765)
    parser.add_argument("--model",help="Installed Ollama vision model name; no automatic download")
    parser.add_argument("--ollama-url",default="http://127.0.0.1:11434")
    parser.add_argument("--audiveris",help="Local Audiveris executable for staff notation")
    args = parser.parse_args()
    import uvicorn
    uvicorn.run(create_app(args.storage,args.assets,model=args.model,ollama_url=args.ollama_url,audiveris=args.audiveris),host="127.0.0.1",port=args.port)


if __name__ == "__main__":
    main()
