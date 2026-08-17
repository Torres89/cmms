"""Configuration for the ingest worker, read from the environment."""

import os

from dotenv import load_dotenv

load_dotenv()


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except ValueError:
        return default


def _bool(name: str, default: bool) -> bool:
    return os.getenv(name, str(default)).lower() not in ("false", "0", "no")


# --- roles -----------------------------------------------------------------

# both | server | queue
WORKER_ROLE = os.getenv("WORKER_ROLE", "both").lower()
PORT = _int("INGEST_PORT", 8002)
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

# --- database --------------------------------------------------------------

DB_DSN = os.getenv("INGEST_DB_DSN")
DB_URL = os.getenv("DB_URL", "localhost:5432/atlas")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PWD = os.getenv("DB_PWD", "")


def dsn() -> str:
    if DB_DSN:
        return DB_DSN
    host, _, database = DB_URL.partition("/")
    if ":" in host:
        host, _, port = host.partition(":")
    else:
        port = "5432"
    return (
        f"host={host} port={port} dbname={database or 'atlas'} "
        f"user={DB_USER} password={DB_PWD}"
    )


# --- storage ---------------------------------------------------------------

# minio | s3 | local  (gcp documents are fetched by signed URL instead)
STORAGE_TYPE = os.getenv("STORAGE_TYPE", "minio").lower()

MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "")
MINIO_BUCKET = os.getenv("MINIO_BUCKET", "")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "")

S3_ENDPOINT = os.getenv("STORAGE_S3_ENDPOINT", "")
S3_REGION = os.getenv("STORAGE_S3_REGION", "")
S3_BUCKET = os.getenv("STORAGE_S3_BUCKET", "")
S3_ACCESS_KEY = os.getenv("STORAGE_S3_ACCESS_KEY", "")
S3_SECRET_KEY = os.getenv("STORAGE_S3_SECRET_KEY", "")

LOCAL_PATH = os.getenv("STORAGE_LOCAL_PATH", "/data/files")

# --- embedding -------------------------------------------------------------

EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "google/embeddinggemma-300m")
# Must match the VECTOR(n) column in document_chunk. EmbeddingGemma is
# Matryoshka-truncatable to 512/256/128 if a deployment ever needs it smaller,
# but the column dimension has to change with it.
EMBEDDING_DIMENSIONS = _int("EMBEDDING_DIMENSIONS", 768)
EMBEDDING_BATCH_SIZE = _int("EMBEDDING_BATCH_SIZE", 16)

# --- parsing ---------------------------------------------------------------

ENABLE_OCR = _bool("ENABLE_OCR", True)
# Below this many characters per page, a PDF is almost certainly scanned images
# rather than text, and needs an OCR pass.
OCR_CHARS_PER_PAGE_THRESHOLD = _int("OCR_CHARS_PER_PAGE_THRESHOLD", 120)
MAX_DOCUMENT_MB = _int("MAX_DOCUMENT_MB", 300)

# --- chunking --------------------------------------------------------------

CHUNK_TARGET_TOKENS = _int("CHUNK_TARGET_TOKENS", 350)
CHUNK_OVERLAP_TOKENS = _int("CHUNK_OVERLAP_TOKENS", 60)
CHUNK_MAX_TOKENS = _int("CHUNK_MAX_TOKENS", 700)

# --- queue -----------------------------------------------------------------

POLL_SECONDS = _int("INGEST_POLL_SECONDS", 15)
MAX_ATTEMPTS = _int("INGEST_MAX_ATTEMPTS", 3)
# Serial by design: ingestion is bursty and slow, and nobody is waiting.
CONCURRENCY = 1
# Reclaim jobs whose worker died mid-run.
STALE_LOCK_MINUTES = _int("INGEST_STALE_LOCK_MINUTES", 60)

# --- extraction ------------------------------------------------------------

# Propose spec and BOM rows from parsed documents. They land unverified and go
# into the review queue; nothing is presented as fact until a human confirms it.
ENABLE_EXTRACTION = _bool("ENABLE_EXTRACTION", True)
EXTRACTION_MIN_CONFIDENCE = float(os.getenv("EXTRACTION_MIN_CONFIDENCE", "0.45"))
