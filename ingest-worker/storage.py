"""
Fetching document bytes from whatever object store this deployment uses.

Mirrors the API's StorageType: MinIO and S3 both speak S3, and LOCAL is the
filesystem for self-hosted installs.
"""

import logging
import os
from pathlib import Path
from typing import Optional

import config

log = logging.getLogger("ingest.storage")

_client = None


def _s3_client():
    """One boto3 client, built lazily so a LOCAL deployment needs no AWS deps."""
    global _client
    if _client is not None:
        return _client
    import boto3
    from botocore.config import Config as BotoConfig

    if config.STORAGE_TYPE == "s3":
        endpoint = config.S3_ENDPOINT or None
        access_key, secret_key = config.S3_ACCESS_KEY, config.S3_SECRET_KEY
        region = config.S3_REGION or "us-east-1"
    else:
        endpoint = config.MINIO_ENDPOINT
        access_key, secret_key = config.MINIO_ACCESS_KEY, config.MINIO_SECRET_KEY
        region = "us-east-1"

    _client = boto3.client(
        "s3",
        endpoint_url=endpoint,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
        region_name=region,
        config=BotoConfig(signature_version="s3v4", s3={"addressing_style": "path"}),
    )
    return _client


def bucket() -> str:
    return config.S3_BUCKET if config.STORAGE_TYPE == "s3" else config.MINIO_BUCKET


def fetch(file_path: str, destination: Path) -> Path:
    """
    Download an object to a local path.

    Streams to disk rather than into memory: a 300 MB scanned manual is a
    perfectly ordinary thing for a customer to upload, and buffering it would
    make the worker's memory ceiling depend on the customer's scanner.
    """
    destination.parent.mkdir(parents=True, exist_ok=True)

    if config.STORAGE_TYPE == "local":
        source = Path(config.LOCAL_PATH) / file_path
        resolved = source.resolve()
        base = Path(config.LOCAL_PATH).resolve()
        if not str(resolved).startswith(str(base)):
            raise ValueError(f"Refusing to read outside the storage root: {file_path}")
        if not resolved.exists():
            raise FileNotFoundError(f"{resolved} does not exist")
        return resolved

    _s3_client().download_file(bucket(), file_path, str(destination))
    return destination


def size_of(file_path: str) -> Optional[int]:
    try:
        if config.STORAGE_TYPE == "local":
            return (Path(config.LOCAL_PATH) / file_path).stat().st_size
        head = _s3_client().head_object(Bucket=bucket(), Key=file_path)
        return head.get("ContentLength")
    except Exception as exc:
        log.debug("Could not stat %s: %s", file_path, exc)
        return None
