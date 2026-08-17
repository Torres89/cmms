"""
The collector's link back to Atlas.

Readings go through the API rather than straight into Postgres on purpose: the
API is what rolls a reading's delta into every serialized component installed
under the asset, and what fires meter-based work order triggers. Writing to the
table directly would silently skip both.
"""

import logging
import os
from typing import Optional

import requests

log = logging.getLogger("collector.client")

API_URL = os.getenv("CMMS_API_URL", "http://localhost:8080").rstrip("/")
SERVICE_TOKEN = os.getenv("INTERNAL_SERVICE_TOKEN", "")
TIMEOUT = int(os.getenv("COLLECTOR_HTTP_TIMEOUT", "20"))


class AtlasClient:
    def __init__(self):
        if not SERVICE_TOKEN:
            raise RuntimeError(
                "INTERNAL_SERVICE_TOKEN is required — it must match the API's setting"
            )
        self.session = requests.Session()
        self.session.headers.update({
            "X-Internal-Token": SERVICE_TOKEN,
            "Content-Type": "application/json",
        })

    def sources(self) -> list[dict]:
        response = self.session.get(f"{API_URL}/internal/telemetry/sources", timeout=TIMEOUT)
        response.raise_for_status()
        return response.json()

    def post_reading(self, meter_id: int, value: float) -> Optional[dict]:
        response = self.session.post(
            f"{API_URL}/internal/telemetry/readings",
            json={"meterId": meter_id, "value": value},
            timeout=TIMEOUT,
        )
        if response.status_code >= 400:
            log.warning("Reading rejected for meter %s: %s", meter_id, response.text[:200])
            return None
        return response.json()

    def post_fault(
        self,
        asset_id: int,
        code: str,
        description: str = "",
        severity: str = "",
        occurred_at: Optional[str] = None,
        cleared: bool = False,
        source: str = "WEBHOOK",
        raw_payload: str = "",
    ) -> Optional[dict]:
        response = self.session.post(
            f"{API_URL}/internal/telemetry/faults",
            json={
                "assetId": asset_id,
                "code": code,
                "description": description or None,
                "severity": severity or None,
                "occurredAt": occurred_at,
                "cleared": cleared,
                "source": source,
                "rawPayload": raw_payload or None,
            },
            timeout=TIMEOUT,
        )
        if response.status_code >= 400:
            log.warning("Fault rejected for asset %s: %s", asset_id, response.text[:200])
            return None
        return response.json()

    def report_error(self, source_id: int, error: str) -> None:
        """
        Record why a source stopped producing data.

        Surfacing this is the difference between "the integration broke three
        weeks ago" and "the machine hasn't been used in three weeks", which are
        very different conversations to have with a customer.
        """
        try:
            self.session.post(
                f"{API_URL}/internal/telemetry/sources/{source_id}/error",
                json={"error": error[:2000]},
                timeout=TIMEOUT,
            )
        except requests.RequestException:
            pass
