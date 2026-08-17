# ingest-worker

Turns a customer's manuals into searchable, citable knowledge.

```
 upload (web/mobile)
        │
        ▼
  FileController ──────────►  object store (S3 / R2 / MinIO / local FS)
        │ INSERT ingest_job
        ▼
  ┌────────────────────────────────────────────────────────┐
  │ ingest-worker  (this service — CPU only, no GPU)       │
  │  1. claim a job (FOR UPDATE SKIP LOCKED)               │
  │  2. fetch the object                                   │
  │  3. Docling → structured markdown + tables + page map  │
  │  4. if scanned → OCR page pass                         │
  │  5. structure-aware chunking (heading path, tables kept)│
  │  6. EmbeddingGemma-300M → 768-d vectors                │
  │  7. INSERT document_chunk                              │
  │  8. propose AssetSpec + AssetBomLine rows (unverified) │
  └────────────────────────────────────────────────────────┘
```

Everything here runs on CPU and costs nothing per use. **This is the half of
"AI" we own** — embeddings, parsing and OCR, which is where the token volume
actually lives (a 400-page manual is thousands of embedding calls). Interactive
reasoning is low-volume and gets outsourced to the customer's own model.

## Why these choices

- **Docling** for parsing. Maintenance manuals are *full* of tables — torque
  values, lubricant charts, interval charts, alarm-code lists — and a parser
  that flattens tables destroys exactly the content you most need. Docling's
  TableFormer keeps table structure, and it all runs locally.
- **OCR fallback** for scanned and photocopied legacy manuals. Clean industrial
  scans OCR at 96–99 %.
- **EmbeddingGemma-300M**: 300M params, 768 dimensions, 100+ languages, under
  200 MB quantized. The multilingual part matters concretely — a Spanish-speaking
  technician asking *"¿cada cuánto se cambia el aceite de la caja?"* has to
  retrieve English manual text, in one model, with no translation step.
- **Page numbers on every chunk.** A citation that says "Maintenance Manual,
  p. 5-14" is the difference between a technician trusting the answer and not.
- **No vector database.** pgvector in the Postgres we already run. A ten-machine
  shop is 5k–50k chunks; there is nothing here worth another service to operate,
  back up and explain to a customer.

## Running

```bash
cd ingest-worker
pip install -r requirements.txt
python main.py            # queue worker + /embed endpoint on port 8002
```

Two roles in one process, selectable with `WORKER_ROLE`:

| Value | Behaviour |
|---|---|
| `both` (default) | serve `/embed` and drain the queue |
| `server` | serve `/embed` only |
| `queue` | drain the queue only |

Ingestion is throttled deliberately: jobs run one at a time, because twenty
customers uploading 400-page manuals would saturate four vCPUs and nobody is
waiting on the result.

## Configuration

See `.env.example`. The worker needs the same Postgres as the API and read
access to whatever object store `STORAGE_TYPE` points at.
