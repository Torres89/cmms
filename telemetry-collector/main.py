"""
The telemetry collector loop.

Asks the API what to poll, polls it, posts the results back. Configuration
lives in the database on ``meter_source.config``, so adding a machine is a
settings change rather than a deploy.

Failures are recorded against the source rather than swallowed: "the
integration broke three weeks ago" and "the machine hasn't been used in three
weeks" look identical from a dashboard, and they are very different
conversations to have with a customer.
"""

import json
import logging
import os
import threading
import time
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv

load_dotenv()

import adapters
from client import AtlasClient

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)-7s %(name)s  %(message)s",
)
log = logging.getLogger("collector")

TICK_SECONDS = int(os.getenv("COLLECTOR_TICK_SECONDS", "60"))
DEFAULT_POLL_MINUTES = int(os.getenv("COLLECTOR_DEFAULT_POLL_MINUTES", "60"))
# Never post the same value twice: meters are cumulative, so a flat reading
# adds nothing and just clutters the history.
SKIP_UNCHANGED = os.getenv("COLLECTOR_SKIP_UNCHANGED", "true").lower() != "false"

_stop = threading.Event()
_last_polled: dict[int, datetime] = {}
_last_value: dict[int, float] = {}


def due(source: dict, now: datetime) -> bool:
    interval = source.get("pollIntervalMinutes") or DEFAULT_POLL_MINUTES
    last = _last_polled.get(source["id"])
    return last is None or now - last >= timedelta(minutes=interval)


def poll_source(client: AtlasClient, source: dict) -> None:
    adapter = adapters.for_source_type(source.get("sourceType"))
    if adapter is None:
        log.debug("No adapter for %s; skipping source %s",
                  source.get("sourceType"), source["id"])
        return

    try:
        config = json.loads(source.get("config") or "{}")
    except json.JSONDecodeError as exc:
        client.report_error(source["id"], f"Config is not valid JSON: {exc}")
        return

    try:
        result = adapter.poll(config)
    except Exception as exc:
        log.warning("Source %s (%s) failed: %s", source["id"], source.get("meterName"), exc)
        client.report_error(source["id"], str(exc))
        return

    if result.value is not None:
        previous = _last_value.get(source["meterId"])
        if SKIP_UNCHANGED and previous is not None and previous == result.value:
            log.debug("Meter %s unchanged at %s; not posting", source["meterId"], result.value)
        else:
            posted = client.post_reading(source["meterId"], result.value)
            if posted:
                _last_value[source["meterId"]] = result.value
                log.info("%s = %s %s", source.get("meterName"), result.value,
                         source.get("meterUnit") or "")

    asset_id = source.get("assetId")
    if asset_id:
        for fault in result.faults:
            client.post_fault(
                asset_id=asset_id,
                code=fault.code,
                description=fault.description,
                severity=fault.severity,
                occurred_at=fault.occurred_at,
                cleared=fault.cleared,
                source=source.get("sourceType", "WEBHOOK"),
                raw_payload=fault.raw,
            )

    _last_polled[source["id"]] = datetime.now(timezone.utc)


def run() -> None:
    client = AtlasClient()
    log.info("Telemetry collector started (tick every %ss)", TICK_SECONDS)

    while not _stop.is_set():
        try:
            sources = client.sources()
        except Exception as exc:
            log.warning("Could not fetch meter sources: %s", exc)
            _stop.wait(TICK_SECONDS)
            continue

        now = datetime.now(timezone.utc)
        for source in sources:
            if _stop.is_set():
                break
            if due(source, now):
                poll_source(client, source)

        _stop.wait(TICK_SECONDS)


if __name__ == "__main__":
    try:
        run()
    except KeyboardInterrupt:
        _stop.set()
        log.info("Stopped")
