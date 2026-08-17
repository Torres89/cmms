"""
Database access for the ingest worker.

The queue is a plain Postgres table drained with ``FOR UPDATE SKIP LOCKED``.
That is deliberate: ingestion is bursty and slow, nobody is waiting on it, and
a queue is not worth another container on a box that is also running Postgres,
the API, Caddy and the agent.
"""

import logging
from contextlib import contextmanager
from typing import Any, Iterator, Optional

import psycopg
from psycopg_pool import ConnectionPool

import config

log = logging.getLogger("ingest.db")

_pool: Optional[ConnectionPool] = None


def pool() -> ConnectionPool:
    global _pool
    if _pool is None:
        _pool = ConnectionPool(config.dsn(), min_size=1, max_size=4, open=True, timeout=15)
        _pool.wait(timeout=15)
    return _pool


@contextmanager
def connection() -> Iterator[psycopg.Connection]:
    with pool().connection() as conn:
        yield conn


# ---------------------------------------------------------------------------
# Queue
# ---------------------------------------------------------------------------

def claim_job() -> Optional[dict]:
    """
    Take the next queued job, or None.

    SKIP LOCKED means two workers never fight over the same document, so this
    stays correct if a deployment ever runs more than one.
    """
    with connection() as conn:
        conn.execute(
            """UPDATE ingest_job
                  SET status = 'QUEUED', locked_at = NULL, updated_at = now()
                WHERE status = 'RUNNING'
                  AND locked_at < now() - make_interval(mins => %s)""",
            (config.STALE_LOCK_MINUTES,),
        )
        row = conn.execute(
            """WITH next_job AS (
                   SELECT id FROM ingest_job
                    WHERE status = 'QUEUED' AND attempts < %s
                    ORDER BY priority, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
               )
               UPDATE ingest_job j
                  SET status = 'RUNNING',
                      attempts = j.attempts + 1,
                      locked_at = now(),
                      updated_at = now()
                 FROM next_job
                WHERE j.id = next_job.id
            RETURNING j.id, j.document_id, j.company_id, j.attempts""",
            (config.MAX_ATTEMPTS,),
        ).fetchone()
    if row is None:
        return None
    return {"id": row[0], "document_id": row[1], "company_id": row[2], "attempts": row[3]}


def finish_job(job_id: int, status: str, error: Optional[str] = None) -> None:
    with connection() as conn:
        conn.execute(
            """UPDATE ingest_job
                  SET status = %s, last_error = %s, locked_at = NULL, updated_at = now()
                WHERE id = %s""",
            (status, (error or "")[:4000] or None, job_id),
        )


def queue_depth() -> int:
    with connection() as conn:
        row = conn.execute(
            "SELECT COUNT(*) FROM ingest_job WHERE status IN ('QUEUED', 'RUNNING')"
        ).fetchone()
    return int(row[0] or 0)


# ---------------------------------------------------------------------------
# Documents
# ---------------------------------------------------------------------------

def load_document(document_id: int) -> Optional[dict]:
    with connection() as conn:
        row = conn.execute(
            """SELECT d.id, d.company_id, d.asset_id, d.equipment_class, d.doc_type,
                      d.title, d.manufacturer, d.revision, d.language, d.checksum,
                      f.path, f.name, a.equipment_class
                 FROM document d
                 JOIN file f ON f.id = d.file_id
                 LEFT JOIN asset a ON a.id = d.asset_id
                WHERE d.id = %s""",
            (document_id,),
        ).fetchone()
    if row is None:
        return None
    return {
        "id": row[0],
        "company_id": row[1],
        "asset_id": row[2],
        # A manual attached to a machine still covers that machine's class.
        "equipment_class": row[3] or row[12],
        "doc_type": row[4],
        "title": row[5],
        "manufacturer": row[6],
        "revision": row[7],
        "language": row[8],
        "checksum": row[9],
        "file_path": row[10],
        "file_name": row[11],
    }


def set_document_status(
    document_id: int,
    status: str,
    error: Optional[str] = None,
    page_count: Optional[int] = None,
    chunk_count: Optional[int] = None,
    checksum: Optional[str] = None,
) -> None:
    with connection() as conn:
        conn.execute(
            """UPDATE document
                  SET ingest_status = %s,
                      ingest_error = %s,
                      page_count = COALESCE(%s, page_count),
                      chunk_count = COALESCE(%s, chunk_count),
                      checksum = COALESCE(%s, checksum),
                      ingested_at = CASE WHEN %s = 'READY' THEN now() ELSE ingested_at END,
                      updated_at = now()
                WHERE id = %s""",
            (status, (error or "")[:2000] or None, page_count, chunk_count,
             checksum, status, document_id),
        )


# ---------------------------------------------------------------------------
# Chunks
# ---------------------------------------------------------------------------

def delete_chunks(document_id: int) -> None:
    with connection() as conn:
        conn.execute("DELETE FROM document_chunk WHERE document_id = %s", (document_id,))


def insert_chunks(document: dict, chunks: list[dict], embeddings: list[list[float]]) -> int:
    """
    Write chunks with their vectors.

    company_id is stamped on every row rather than joined for: tenant isolation
    has to be enforceable in the hot query without a join, and a shared corpus
    across customers is something this system must never accidentally acquire.
    """
    if not chunks:
        return 0
    written = 0
    with connection() as conn:
        with conn.cursor() as cur:
            for chunk, embedding in zip(chunks, embeddings):
                cur.execute(
                    """INSERT INTO document_chunk
                           (document_id, company_id, asset_id, equipment_class,
                            page_from, page_to, section, content, embedding,
                            token_count, embedding_model)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s::vector, %s, %s)""",
                    (
                        document["id"],
                        document["company_id"],
                        document.get("asset_id"),
                        document.get("equipment_class"),
                        chunk.get("page_from"),
                        chunk.get("page_to"),
                        chunk.get("section"),
                        chunk["content"],
                        _vector_literal(embedding) if embedding else None,
                        chunk.get("token_count"),
                        config.EMBEDDING_MODEL,
                    ),
                )
                written += 1
    return written


def _vector_literal(values: list[float]) -> str:
    return "[" + ",".join(f"{v:.6f}" for v in values) + "]"


# ---------------------------------------------------------------------------
# Extraction proposals
# ---------------------------------------------------------------------------

def propose_spec(
    company_id: int,
    asset_id: int,
    spec_key: str,
    spec_group: str,
    label: Optional[str],
    value_text: Optional[str],
    value_num: Optional[float],
    unit: Optional[str],
    document_id: int,
    page: Optional[int],
    confidence: float,
) -> bool:
    """
    Insert a proposed spec value.

    Never overwrites a value a person verified: the ON CONFLICT clause leaves
    verified rows exactly as they are. Provenance only means something if
    machine output cannot quietly replace human confirmation.
    """
    with connection() as conn:
        cur = conn.execute(
            """INSERT INTO asset_spec
                   (id, company_id, asset_id, spec_group, spec_key, label, value_text,
                    value_num, unit, source, source_document_id, source_page, confidence,
                    created_at, updated_at)
               VALUES (nextval('asset_spec_seq'), %s, %s, %s, %s, %s, %s, %s, %s,
                       'DOC_EXTRACTION', %s, %s, %s, now(), now())
               ON CONFLICT (asset_id, spec_key) DO UPDATE
                  SET value_text = EXCLUDED.value_text,
                      value_num  = EXCLUDED.value_num,
                      unit       = COALESCE(EXCLUDED.unit, asset_spec.unit),
                      source     = EXCLUDED.source,
                      source_document_id = EXCLUDED.source_document_id,
                      source_page = EXCLUDED.source_page,
                      confidence = EXCLUDED.confidence,
                      updated_at = now()
                WHERE asset_spec.verified_by_id IS NULL""",
            (company_id, asset_id, spec_group, spec_key, label, value_text,
             value_num, unit, document_id, page, confidence),
        )
        return cur.rowcount > 0


def find_asset_meta(asset_id: int) -> Optional[dict]:
    with connection() as conn:
        row = conn.execute(
            "SELECT id, company_id, equipment_class, name FROM asset WHERE id = %s",
            (asset_id,),
        ).fetchone()
    if row is None:
        return None
    return {"id": row[0], "company_id": row[1], "equipment_class": row[2], "name": row[3]}


def spec_catalog(company_id: int, equipment_class: str) -> list[dict]:
    """The keys we expect for this class — extraction only proposes known keys."""
    if not equipment_class:
        return []
    with connection() as conn:
        rows = conn.execute(
            """SELECT spec_key, spec_group, label_en, unit, value_type
                 FROM spec_key_catalog
                WHERE company_id = %s AND equipment_class = %s""",
            (company_id, equipment_class),
        ).fetchall()
    return [
        {"spec_key": r[0], "spec_group": r[1], "label": r[2], "unit": r[3], "value_type": r[4]}
        for r in rows
    ]


def upsert_fault_codes(company_id: int, equipment_class: Optional[str],
                       manufacturer: Optional[str], document_id: int,
                       entries: list[dict]) -> int:
    """
    Enrich the fault-code dictionary from the customer's own manuals.

    Telematics payloads generally carry the code but not its meaning, so this is
    often the only place a shop will ever have the description.
    """
    if not entries:
        return 0
    written = 0
    with connection() as conn:
        with conn.cursor() as cur:
            for entry in entries:
                cur.execute(
                    """SELECT id FROM fault_code_dictionary
                        WHERE company_id = %s AND UPPER(code) = UPPER(%s)
                          AND COALESCE(equipment_class, '') = COALESCE(%s, '')""",
                    (company_id, entry["code"], equipment_class),
                )
                if cur.fetchone():
                    continue
                cur.execute(
                    """INSERT INTO fault_code_dictionary
                           (company_id, equipment_class, manufacturer, code,
                            description_en, source, document_id, page)
                       VALUES (%s, %s, %s, %s, %s, 'DOC_EXTRACTION', %s, %s)""",
                    (company_id, equipment_class, manufacturer, entry["code"],
                     entry.get("description"), document_id, entry.get("page")),
                )
                written += 1
    return written
