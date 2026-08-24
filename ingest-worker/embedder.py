"""
Embeddings — EmbeddingGemma-300M on CPU.

300M parameters, 768 dimensions, 100+ languages, under 200 MB quantized. It
runs comfortably alongside Postgres, the API and the agent on a 16 GiB box with
no GPU anywhere in the picture.

The multilingual part is not a nice-to-have. The UI is English/Spanish, and a
Spanish-speaking technician asking *"¿cada cuánto se cambia el aceite de la
caja?"* has to retrieve English manual text. One model, no translation step.
"""

import logging
import threading
from typing import Optional

import config

log = logging.getLogger("ingest.embed")

_model = None
_lock = threading.Lock()
_load_failed = False

# EmbeddingGemma is trained with asymmetric prefixes; embedding a query as if it
# were a document measurably degrades retrieval.
_QUERY_PREFIX = "task: search result | query: "
_DOCUMENT_PREFIX = "title: none | text: "


def model():
    global _model, _load_failed
    if _model is not None or _load_failed:
        return _model
    with _lock:
        if _model is not None or _load_failed:
            return _model
        try:
            from sentence_transformers import SentenceTransformer

            log.info("Loading %s (CPU)", config.EMBEDDING_MODEL)
            # EmbeddingGemma is a gated repo: without a token that has accepted
            # its licence the download returns 401 and retrieval quietly drops
            # to lexical-only. Passed explicitly rather than left to the
            # HF_TOKEN environment variable, so an empty value is not mistaken
            # for a real one.
            _model = SentenceTransformer(
                config.EMBEDDING_MODEL, device="cpu", token=config.HF_TOKEN or None
            )
        except Exception as exc:
            # Retrieval degrades to lexical-only rather than the whole service
            # failing. A shop with BM25 search over its manuals is still far
            # better off than a shop with a stack trace.
            if not config.HF_TOKEN and "401" in str(exc):
                log.error(
                    "Could not load %s: it is a gated model and HF_TOKEN is not set. "
                    "Accept the licence at https://huggingface.co/%s, create a token, "
                    "and set HF_TOKEN. Retrieval will be lexical-only until then.",
                    config.EMBEDDING_MODEL, config.EMBEDDING_MODEL,
                )
            else:
                log.error("Could not load the embedding model (%s); retrieval will be lexical-only", exc)
            _load_failed = True
    return _model


def available() -> bool:
    return model() is not None


def embed(texts: list[str], kind: str = "document") -> list[Optional[list[float]]]:
    """
    Embed a batch. Returns a list of vectors, or a list of None when the model
    is unavailable so callers can carry on without it.
    """
    if not texts:
        return []
    loaded = model()
    if loaded is None:
        return [None] * len(texts)

    prefix = _QUERY_PREFIX if kind == "query" else _DOCUMENT_PREFIX
    prepared = [prefix + text for text in texts]

    vectors: list[Optional[list[float]]] = []
    for start in range(0, len(prepared), config.EMBEDDING_BATCH_SIZE):
        batch = prepared[start:start + config.EMBEDDING_BATCH_SIZE]
        try:
            encoded = loaded.encode(batch, normalize_embeddings=True, show_progress_bar=False)
        except Exception as exc:
            log.error("Embedding a batch failed: %s", exc)
            vectors.extend([None] * len(batch))
            continue
        for vector in encoded:
            values = [float(v) for v in vector]
            # Matryoshka truncation: the model supports 512/256/128, but the
            # VECTOR(n) column has to agree, so this only trims.
            if len(values) > config.EMBEDDING_DIMENSIONS:
                values = values[:config.EMBEDDING_DIMENSIONS]
            vectors.append(values)
    return vectors
