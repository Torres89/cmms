"""
Protocol adapters.

Each one takes a meter source's config, talks to whatever is on the other end,
and returns readings and faults in one shape. The interesting asymmetry is that
MTConnect is per-machine on the shop network while ISO 15143-3 is per-OEM over
the internet — one adapter for the second covers Caterpillar, John Deere,
Komatsu, Hitachi and Volvo, which is why a mixed earthmoving fleet is far less
work than a mixed CNC shop.
"""

import json
import logging
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Optional
from xml.etree import ElementTree

import requests

log = logging.getLogger("collector.adapters")

TIMEOUT = 20


@dataclass
class Fault:
    code: str
    description: str = ""
    severity: str = ""
    occurred_at: Optional[str] = None
    cleared: bool = False
    raw: str = ""


@dataclass
class PollResult:
    value: Optional[float] = None
    faults: list[Fault] = field(default_factory=list)


class Adapter:
    source_type = "MANUAL"

    def poll(self, config: dict) -> PollResult:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# MTConnect — the primary CNC target
# ---------------------------------------------------------------------------

class MTConnectAdapter(Adapter):
    """
    Reads a DataItem from an MTConnect agent's /current response.

    MTConnect is the target rather than a vendor protocol because it gives even
    legacy controls a common vocabulary — spindle speed, axis positions, feed
    rates, load, program execution, alarms, cycle counters — so one adapter
    works across a shop that has three generations of machines in it.
    """

    source_type = "MTCONNECT"

    def poll(self, config: dict) -> PollResult:
        agent_url = config.get("agentUrl", "").rstrip("/")
        if not agent_url:
            raise ValueError("MTConnect config needs an agentUrl")
        device = config.get("deviceName")
        path = f"{agent_url}/{device}/current" if device else f"{agent_url}/current"

        response = requests.get(path, timeout=TIMEOUT)
        response.raise_for_status()
        root = ElementTree.fromstring(response.content)

        result = PollResult()
        data_item_id = config.get("dataItemId")
        if data_item_id:
            result.value = self._find_value(root, data_item_id)

        if config.get("collectFaults"):
            result.faults = self._find_faults(root)
        return result

    def _find_value(self, root, data_item_id: str) -> Optional[float]:
        # MTConnect namespaces every element; matching on the local name keeps
        # this working across agent versions rather than pinning a schema URL.
        for element in root.iter():
            if element.attrib.get("dataItemId") != data_item_id \
                    and element.attrib.get("name") != data_item_id:
                continue
            text = (element.text or "").strip()
            if not text or text.upper() in ("UNAVAILABLE", "NORMAL"):
                return None
            try:
                return float(text)
            except ValueError:
                return None
        return None

    def _find_faults(self, root) -> list[Fault]:
        faults: list[Fault] = []
        for element in root.iter():
            tag = element.tag.split("}")[-1]
            if tag not in ("Condition", "Fault", "Warning", "Alarm", "Normal"):
                continue
            native_code = element.attrib.get("nativeCode")
            if not native_code:
                continue
            # A Normal condition is the control telling us the alarm cleared.
            cleared = tag == "Normal"
            faults.append(Fault(
                code=native_code,
                description=(element.text or "").strip(),
                severity=element.attrib.get("qualifier") or tag.upper(),
                occurred_at=element.attrib.get("timestamp"),
                cleared=cleared,
                raw=ElementTree.tostring(element, encoding="unicode")[:4000],
            ))
        return faults


# ---------------------------------------------------------------------------
# ISO 15143-3 (AEMP 2.0) — one adapter, every major earthmoving OEM
# ---------------------------------------------------------------------------

class Iso15143Adapter(Adapter):
    """
    The standardized telematics API for construction equipment.

    Caterpillar, John Deere, Komatsu, Hitachi and Volvo all expose it, and it
    was designed precisely so a mixed fleet reports through one vocabulary.
    Polled nightly rather than continuously — these are cumulative counters on
    an OEM's server, not a live feed.
    """

    source_type = "ISO15143"

    FIELD_PATHS = {
        "CumulativeOperatingHours": ("CumulativeOperatingHours", "Hour"),
        "FuelUsed": ("FuelUsed", "FuelConsumed"),
        "CumulativeIdleHours": ("CumulativeIdleHours", "Hour"),
        "DEFRemaining": ("DEFRemaining", "Percent"),
    }

    def poll(self, config: dict) -> PollResult:
        base_url = config.get("baseUrl", "").rstrip("/")
        equipment_id = config.get("equipmentId")
        if not base_url or not equipment_id:
            raise ValueError("ISO 15143 config needs a baseUrl and an equipmentId")

        auth = None
        if config.get("username"):
            auth = (config["username"], config.get("password", ""))

        response = requests.get(
            f"{base_url}/Fleet/v2/Equipment/{equipment_id}",
            auth=auth,
            headers={"Accept": "application/json"},
            timeout=TIMEOUT,
        )
        response.raise_for_status()
        payload = response.json()

        result = PollResult()
        field_name = config.get("field", "CumulativeOperatingHours")
        result.value = self._extract(payload, field_name)

        if config.get("collectFaults"):
            result.faults = self._faults(base_url, equipment_id, auth)
        return result

    def _extract(self, payload: dict, field_name: str) -> Optional[float]:
        outer, inner = self.FIELD_PATHS.get(field_name, (field_name, None))
        node = payload.get(outer)
        if node is None:
            # OEMs vary in how deeply they nest; a flat scan is more robust than
            # a per-vendor path table that goes stale.
            node = self._deep_find(payload, outer)
        if node is None:
            return None
        if isinstance(node, (int, float)):
            return float(node)
        if isinstance(node, dict):
            for key in (inner, "value", "Value"):
                if key and key in node:
                    try:
                        return float(node[key])
                    except (TypeError, ValueError):
                        continue
        return None

    def _deep_find(self, payload, key: str, depth: int = 0):
        if depth > 5 or not isinstance(payload, (dict, list)):
            return None
        if isinstance(payload, dict):
            if key in payload:
                return payload[key]
            for value in payload.values():
                found = self._deep_find(value, key, depth + 1)
                if found is not None:
                    return found
        else:
            for item in payload:
                found = self._deep_find(item, key, depth + 1)
                if found is not None:
                    return found
        return None

    def _faults(self, base_url: str, equipment_id: str, auth) -> list[Fault]:
        try:
            response = requests.get(
                f"{base_url}/Fleet/v2/Equipment/{equipment_id}/Faults",
                auth=auth,
                headers={"Accept": "application/json"},
                timeout=TIMEOUT,
            )
            response.raise_for_status()
            payload = response.json()
        except requests.RequestException as exc:
            log.debug("No fault feed for %s: %s", equipment_id, exc)
            return []

        entries = payload if isinstance(payload, list) else payload.get("Faults", [])
        faults = []
        for entry in entries or []:
            # J1939 identifies a fault as an SPN/FMI pair; keeping that shape
            # means the dictionary lookup and the manuals agree on the code.
            spn = entry.get("SPN") or entry.get("Spn")
            fmi = entry.get("FMI") or entry.get("Fmi")
            code = f"SPN {spn} FMI {fmi}" if spn is not None else (
                entry.get("FaultCode") or entry.get("Code") or ""
            )
            if not code:
                continue
            faults.append(Fault(
                code=str(code),
                description=entry.get("Description") or entry.get("FaultDescription") or "",
                severity=str(entry.get("Severity") or ""),
                occurred_at=entry.get("DateTime") or entry.get("OccurrenceDateTime"),
                cleared=bool(entry.get("Cleared")),
                raw=json.dumps(entry)[:4000],
            ))
        return faults


# ---------------------------------------------------------------------------

ADAPTERS: dict[str, Adapter] = {
    MTConnectAdapter.source_type: MTConnectAdapter(),
    Iso15143Adapter.source_type: Iso15143Adapter(),
}


def for_source_type(source_type: str) -> Optional[Adapter]:
    return ADAPTERS.get((source_type or "").upper())
