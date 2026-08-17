"""
The queue worker: claim a job, parse, chunk, embed, store, extract.

Deliberately serial. Twenty customers uploading 400-page manuals would saturate
four vCPUs, and nobody is waiting on the result — the technician who needs the
answer will ask tomorrow, not in ninety seconds.
"""

import hashlib
import logging
import tempfile
import threading
import time
from pathlib import Path

import chunker
import config
import db
import embedder
import extractor
import parse
import storage

log = logging.getLogger("ingest.worker")

_stop = threading.Event()


def stop() -> None:
    _stop.set()


def run_forever() -> None:
    log.info("Ingest worker started (poll every %ss)", config.POLL_SECONDS)
    while not _stop.is_set():
        try:
            job = db.claim_job()
        except Exception as exc:
            log.error("Could not reach the queue: %s", exc)
            _stop.wait(config.POLL_SECONDS)
            continue

        if job is None:
            _stop.wait(config.POLL_SECONDS)
            continue

        try:
            process(job)
            db.finish_job(job["id"], "DONE")
        except Exception as exc:
            log.exception("Job %s failed", job["id"])
            final = job["attempts"] >= config.MAX_ATTEMPTS
            db.finish_job(job["id"], "FAILED" if final else "QUEUED", str(exc))
            if final:
                db.set_document_status(job["document_id"], "FAILED", str(exc))


def process(job: dict) -> None:
    document = db.load_document(job["document_id"])
    if document is None:
        raise ValueError(f"Document {job['document_id']} no longer exists")

    log.info("Ingesting document %s — %s", document["id"], document["title"])
    db.set_document_status(document["id"], "PARSING")

    size = storage.size_of(document["file_path"])
    if size and size > config.MAX_DOCUMENT_MB * 1024 * 1024:
        raise ValueError(
            f"{document['file_name']} is {size // (1024 * 1024)} MB, over the "
            f"{config.MAX_DOCUMENT_MB} MB ingest limit"
        )

    with tempfile.TemporaryDirectory(prefix="ingest-") as workdir:
        local = storage.fetch(document["file_path"], Path(workdir) / document["file_name"])
        checksum = _checksum(local)
        parsed = parse.parse(local, document["file_name"])

        if parsed.page_count == 0 or parsed.total_chars < 40:
            db.set_document_status(
                document["id"], "FAILED",
                "No readable text could be extracted. If this is a scan, enable OCR.",
                page_count=parsed.page_count,
            )
            raise ValueError("No readable text extracted")

        log.info("Parsed %s pages with %s%s", parsed.page_count, parsed.parser,
                 " (OCR)" if parsed.used_ocr else "")

        chunks = chunker.chunk_pages(parsed.pages)
        if not chunks:
            db.set_document_status(document["id"], "FAILED", "Parsed, but produced no usable chunks",
                                   page_count=parsed.page_count)
            raise ValueError("No chunks produced")

        db.set_document_status(document["id"], "EMBEDDING", page_count=parsed.page_count)
        vectors = embedder.embed([chunk["content"] for chunk in chunks], kind="document")

        # Re-ingest replaces rather than appends: a document that is indexed
        # twice would double every retrieval result from it.
        db.delete_chunks(document["id"])
        written = db.insert_chunks(document, chunks, vectors)

        if config.ENABLE_EXTRACTION:
            try:
                extractor.extract_specs(document, parsed)
                extractor.extract_fault_codes(document, parsed)
            except Exception as exc:
                # Extraction is a bonus; failing it must not fail the ingest,
                # because retrieval is the part that has to work.
                log.warning("Extraction failed for document %s: %s", document["id"], exc)

        db.set_document_status(
            document["id"], "READY",
            page_count=parsed.page_count,
            chunk_count=written,
            checksum=checksum,
        )
        embedded = sum(1 for v in vectors if v)
        log.info("Document %s ready: %s chunks (%s embedded)", document["id"], written, embedded)


def _checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()
