"""
Entry point: the embedding endpoint and the queue worker, in one process.

The API calls ``POST /embed`` to embed a search query, because the model is
already loaded here for the document pipeline and a second copy in the JVM
would buy nothing.
"""

import logging
import threading

import uvicorn
from fastapi import FastAPI
from pydantic import BaseModel

import config
import db
import embedder
import worker

logging.basicConfig(
    level=config.LOG_LEVEL,
    format="%(asctime)s %(levelname)-7s %(name)s  %(message)s",
)
log = logging.getLogger("ingest")

app = FastAPI(title="Atlas CMMS ingest worker")


class EmbedRequest(BaseModel):
    texts: list[str]
    # "query" and "document" get different prefixes; the model is trained
    # asymmetrically and mixing them up costs retrieval quality.
    kind: str = "document"


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]
    model: str
    dimensions: int


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest):
    vectors = embedder.embed(request.texts, kind=request.kind)
    return EmbedResponse(
        embeddings=[v for v in vectors if v is not None],
        model=config.EMBEDDING_MODEL,
        dimensions=config.EMBEDDING_DIMENSIONS,
    )


class OcrRequest(BaseModel):
    """A storage path, not bytes — the worker fetches the object itself."""
    path: str


class OcrResponse(BaseModel):
    text: str
    available: bool


@app.post("/ocr", response_model=OcrResponse)
def ocr(request: OcrRequest):
    """
    Read text out of an image or a page.

    Used for nameplate capture: turning pixels into characters is cheap CPU
    work worth doing locally, while interpreting those characters into
    structured specs is the customer's model's job.
    """
    import tempfile
    from pathlib import Path

    import storage

    try:
        with tempfile.TemporaryDirectory(prefix="ocr-") as workdir:
            local = storage.fetch(request.path, Path(workdir) / Path(request.path).name)
            text = _ocr_file(local)
        return OcrResponse(text=text, available=True)
    except Exception as exc:
        log.warning("OCR failed for %s: %s", request.path, exc)
        return OcrResponse(text="", available=False)


def _ocr_file(path) -> str:
    from pathlib import Path

    suffix = Path(path).suffix.lower()
    if suffix == ".pdf":
        parsed = __import__("parse").parse(Path(path), Path(path).name)
        return "\n\n".join(page.text for page in parsed.pages)

    import pytesseract
    from PIL import Image

    with Image.open(path) as image:
        # Nameplates are often photographed at an angle in poor light, so a
        # grayscale pass materially improves the read.
        return pytesseract.image_to_string(image.convert("L"))


@app.get("/health")
def health():
    try:
        depth = db.queue_depth()
        database = True
    except Exception:
        depth, database = None, False
    return {
        "status": "ok",
        "role": config.WORKER_ROLE,
        "database": database,
        "queueDepth": depth,
        "embeddingsAvailable": embedder.available(),
        "model": config.EMBEDDING_MODEL,
    }


def main() -> None:
    if config.WORKER_ROLE in ("both", "queue"):
        thread = threading.Thread(target=worker.run_forever, name="ingest-worker", daemon=True)
        thread.start()

    if config.WORKER_ROLE in ("both", "server"):
        log.info("Ingest service listening on port %s", config.PORT)
        uvicorn.run(app, host="0.0.0.0", port=config.PORT, log_level=config.LOG_LEVEL.lower())
    else:
        # Queue-only deployments have nothing to serve; just don't exit.
        try:
            threading.Event().wait()
        except KeyboardInterrupt:
            worker.stop()


if __name__ == "__main__":
    main()
