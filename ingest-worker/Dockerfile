FROM python:3.11-slim

WORKDIR /app

# tesseract + poppler for the OCR fallback on scanned manuals; the rest are
# build deps for the parsing stack.
RUN apt-get update && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-spa \
        poppler-utils \
        libgl1 \
        libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# CPU-only torch. There is no GPU in this architecture and the CUDA wheels are
# several gigabytes of image for nothing.
ENV PIP_EXTRA_INDEX_URL=https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Keep model weights on a volume so a container restart doesn't re-download
# them, and cap thread counts — this shares a box with Postgres and the API.
ENV HF_HOME=/models \
    TORCH_HOME=/models \
    OMP_NUM_THREADS=2 \
    MKL_NUM_THREADS=2
VOLUME ["/models"]

COPY . .

EXPOSE 8002

CMD ["python", "main.py"]
