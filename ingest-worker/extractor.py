"""
Proposing structured facts from parsed documents.

Everything this produces lands **unverified**, tagged with the document and page
it came from, and shows up in the review queue as "from Maintenance Manual
p. 12 — verify". Nothing extracted is ever presented as established fact.

The rules here are deliberately conservative pattern matching rather than a
model. It only proposes keys that already exist in the equipment class's spec
catalogue, so a bad match produces a wrong value on a real field — visible and
easy to correct — rather than inventing a field nobody asked for.
"""

import logging
import re
from typing import Optional

import config
import db
from parse import ParsedDocument

log = logging.getLogger("ingest.extract")

# "Max spindle speed: 12,000 rpm", "Coolant capacity   75 gal"
_KEY_VALUE = re.compile(
    r"^\s*([A-Za-z][A-Za-z0-9 /()\-\.']{2,60}?)\s*[:\t]\s*([^\n]{1,120})\s*$")

_NUMBER_WITH_UNIT = re.compile(
    r"^([+-]?[0-9][0-9,\.]*)\s*([A-Za-zµ°%/]{1,12})?$")

# Fanuc-style alarm codes, J1939 SPN/FMI pairs, and generic vendor codes.
_FAULT_CODE = re.compile(
    r"\b((?:SV|PS|SR|OT|AL|E|F|P)[ -]?\d{3,4}|SPN\s*\d{2,6}\s*FMI\s*\d{1,2}|\d{3,4}-\d{1,2})\b")

_STOPWORDS = {"the", "and", "for", "with", "this", "that", "see", "page", "note"}


def _normalise_key(label: str) -> str:
    """"Max spindle speed" -> "max_spindle_speed"."""
    slug = re.sub(r"[^a-z0-9]+", "_", label.strip().lower()).strip("_")
    return re.sub(r"_+", "_", slug)


def _key_tokens(text: str) -> set[str]:
    return {
        token for token in re.split(r"[^a-z0-9]+", text.lower())
        if len(token) > 2 and token not in _STOPWORDS
    }


def _match_catalog_key(label: str, catalog: list[dict]) -> Optional[dict]:
    """
    Map a label found in a manual onto a known spec key.

    Exact slug match first, then a token-overlap match strong enough to be
    worth a human's time to confirm.
    """
    slug = _normalise_key(label)
    for entry in catalog:
        if entry["spec_key"] == slug:
            return entry

    label_tokens = _key_tokens(label)
    if not label_tokens:
        return None

    best, best_score = None, 0.0
    for entry in catalog:
        candidate_tokens = _key_tokens(entry["spec_key"]) | _key_tokens(entry.get("label") or "")
        if not candidate_tokens:
            continue
        overlap = len(label_tokens & candidate_tokens)
        if overlap == 0:
            continue
        score = overlap / max(len(candidate_tokens), 1)
        if score > best_score:
            best, best_score = entry, score
    return best if best_score >= 0.6 else None


def _parse_value(raw: str) -> tuple[Optional[str], Optional[float], Optional[str]]:
    """Return (text, number, unit) — a value is stored as both where it can be."""
    value = raw.strip().rstrip(".")
    match = _NUMBER_WITH_UNIT.match(value)
    if match:
        try:
            number = float(match.group(1).replace(",", ""))
            return value, number, match.group(2)
        except ValueError:
            pass
    # "12,000 rpm CT40" — take the leading number, keep the whole string too.
    leading = re.match(r"^([+-]?[0-9][0-9,\.]*)\s*([A-Za-zµ°%/]{1,12})\b", value)
    if leading:
        try:
            return value, float(leading.group(1).replace(",", "")), leading.group(2)
        except ValueError:
            pass
    return value, None, None


def extract_specs(document: dict, parsed: ParsedDocument) -> int:
    """
    Propose spec values for the machine this document belongs to.

    Only runs for documents attached to a specific asset: a class-wide manual
    describes a family, and writing its values onto one machine would be a
    guess dressed up as a measurement.
    """
    asset_id = document.get("asset_id")
    if not asset_id:
        return 0
    asset = db.find_asset_meta(asset_id)
    if not asset or not asset.get("equipment_class"):
        return 0

    catalog = db.spec_catalog(asset["company_id"], asset["equipment_class"])
    if not catalog:
        return 0

    proposed = 0
    seen: set[str] = set()

    for page in parsed.pages:
        for line in page.text.splitlines():
            match = _KEY_VALUE.match(line)
            if not match:
                continue
            label, raw_value = match.group(1), match.group(2)
            if len(raw_value) < 1 or len(raw_value) > 120:
                continue

            entry = _match_catalog_key(label, catalog)
            if entry is None or entry["spec_key"] in seen:
                continue

            value_text, value_num, unit = _parse_value(raw_value)
            if value_text is None:
                continue

            # A numeric key that yielded no number is a bad match, not a value.
            if entry.get("value_type") == "NUM" and value_num is None:
                continue

            confidence = 0.75 if _normalise_key(label) == entry["spec_key"] else 0.55
            if confidence < config.EXTRACTION_MIN_CONFIDENCE:
                continue

            if db.propose_spec(
                company_id=asset["company_id"],
                asset_id=asset_id,
                spec_key=entry["spec_key"],
                spec_group=entry["spec_group"],
                label=entry.get("label") or label.strip(),
                value_text=value_text,
                value_num=value_num,
                unit=unit or entry.get("unit"),
                document_id=document["id"],
                page=page.number,
                confidence=confidence,
            ):
                proposed += 1
                seen.add(entry["spec_key"])

    if proposed:
        log.info("Proposed %s spec values from document %s (all unverified)",
                 proposed, document["id"])
    return proposed


def extract_fault_codes(document: dict, parsed: ParsedDocument) -> int:
    """
    Harvest alarm codes and their descriptions.

    Telematics payloads generally carry the code but not its meaning — Cat, for
    instance, expects lookup through service tooling or the dealer — so the
    customer's own manual is often the only place a shop will ever have the
    description.
    """
    entries: list[dict] = []
    seen: set[str] = set()

    for page in parsed.pages:
        for line in page.text.splitlines():
            match = _FAULT_CODE.search(line)
            if not match:
                continue
            code = re.sub(r"\s+", " ", match.group(1)).strip().upper()
            if code in seen:
                continue
            description = line[match.end():].strip(" \t:|-")
            if len(description) < 6 or len(description) > 300:
                continue
            seen.add(code)
            entries.append({"code": code, "description": description, "page": page.number})

    if not entries:
        return 0
    written = db.upsert_fault_codes(
        document["company_id"], document.get("equipment_class"),
        document.get("manufacturer"), document["id"], entries)
    if written:
        log.info("Added %s fault codes from document %s", written, document["id"])
    return written
