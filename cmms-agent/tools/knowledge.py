"""
The knowledge tier — the tools that make a general model behave like a
specialist on one specific machine.

Every retrieval result carries its document title and page as structured
fields, and sourcing/BOM tools return empty rather than approximate. An
external model we don't control is reading these responses, so the discipline
has to live in the data, not in a system prompt.
"""

from api_client import get_client as _get_client


def get_machine_dossier(asset_id: int, format: str = "text") -> dict:
    """The compact, deterministic card describing a machine right now."""
    result = _get_client().get(f"/assets/{asset_id}/dossier", params={"format": format})
    if isinstance(result, dict) and "error" in result:
        return result
    return result


def search_machine_docs(
    asset_id: int = None,
    query: str = "",
    doc_type: str = "",
    limit: int = 8,
) -> dict:
    body = {"query": query, "limit": limit}
    if asset_id is not None:
        body["assetId"] = asset_id
    if doc_type:
        body["docType"] = doc_type
    result = _get_client().post("/knowledge/search", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "query": query,
        "results": result.get("results", []),
        "note": "Cite the document title and page for anything you state from these excerpts.",
    }


def lookup_fault_code(code: str, asset_id: int = None) -> dict:
    params = {"code": code}
    if asset_id is not None:
        params["assetId"] = asset_id
    return _get_client().get("/knowledge/fault-code", params=params)


def get_maintenance_history(
    asset_id: int,
    since: str = "",
    failure_mode: str = "",
    component_id: int = None,
    limit: int = 50,
) -> dict:
    params = {"limit": limit}
    if since:
        params["since"] = since
    if failure_mode:
        params["failureMode"] = failure_mode
    if component_id is not None:
        params["componentId"] = component_id
    return _get_client().get(f"/assets/{asset_id}/maintenance-history", params=params)


def get_component_status(asset_id: int) -> dict:
    return _get_client().get(f"/assets/{asset_id}/components")


def get_bom(asset_id: int, subunit: str = "") -> dict:
    params = {}
    if subunit:
        params["subunit"] = subunit
    result = _get_client().get(f"/assets/{asset_id}/bom", params=params)
    if isinstance(result, dict) and "error" in result:
        return result
    lines = result.get("lines", []) if isinstance(result, dict) else result
    if not lines:
        return {
            "lines": [],
            "note": (
                "No bill of materials is recorded for this asset. Do not guess part "
                "numbers — say that the BOM has not been captured yet."
            ),
        }
    return result


def get_part_sourcing(part_id: int) -> dict:
    result = _get_client().get(f"/parts/{part_id}/sourcing")
    if isinstance(result, dict) and "error" in result:
        return result
    if not result.get("suppliers"):
        result["note"] = (
            "No supplier record exists for this part. Do not invent a supplier, "
            "price or lead time."
        )
    return result


def diagnose(asset_id: int, symptom: str, observations: str = "") -> dict:
    """
    Orchestrated diagnosis: dossier, then candidate failure modes ranked by this
    machine's own history, then retrieval per candidate.
    """
    body = {"symptom": symptom}
    if observations:
        body["observations"] = observations
    return _get_client().post(f"/assets/{asset_id}/diagnose", body)


def propose_maintenance_plan(asset_id: int) -> dict:
    """Manual interval charts plus actual usage, as PM proposals for human approval."""
    return _get_client().get(f"/assets/{asset_id}/maintenance-plan-proposal")


def get_spec_sheet(asset_id: int, group: str = "") -> dict:
    params = {}
    if group:
        params["group"] = group
    return _get_client().get(f"/assets/{asset_id}/specs", params=params)


def extract_from_image(asset_id: int, file_id: int, mode: str = "nameplate") -> dict:
    """
    Prepare a nameplate (or handwritten log) image for reading.

    We run no vision model. This returns a signed image URL, whatever local OCR
    could read, and the exact fields this equipment class expects — the caller's
    own model does the reading, and `apply_extracted_specs` writes the result
    back as unverified values.
    """
    result = _get_client().get(
        f"/assets/{asset_id}/nameplate/prepare", params={"fileId": file_id}
    )
    if isinstance(result, dict) and "error" not in result:
        result["howToRespond"] = (
            "Look at imageUrl. Fill in only the fields in expectedFields that you can "
            "actually read on the plate — omit anything unclear rather than guessing. "
            "Then call apply_extracted_specs with the result."
        )
    return result


def apply_extracted_specs(
    asset_id: int,
    specs: list = None,
    manufacturer: str = "",
    model: str = "",
    serialNumber: str = "",
    confirmed: bool = False,
) -> dict:
    """Write a nameplate reading back as unverified spec values."""
    body = {"specs": specs or []}
    if manufacturer:
        body["manufacturer"] = manufacturer
    if model:
        body["model"] = model
    if serialNumber:
        body["serialNumber"] = serialNumber
    return _get_client().post(f"/assets/{asset_id}/nameplate/apply", body)


TOOL_FUNCTIONS = {
    "get_machine_dossier": get_machine_dossier,
    "search_machine_docs": search_machine_docs,
    "lookup_fault_code": lookup_fault_code,
    "get_maintenance_history": get_maintenance_history,
    "get_component_status": get_component_status,
    "get_bom": get_bom,
    "get_part_sourcing": get_part_sourcing,
    "diagnose": diagnose,
    "propose_maintenance_plan": propose_maintenance_plan,
    "get_spec_sheet": get_spec_sheet,
    "extract_from_image": extract_from_image,
    "apply_extracted_specs": apply_extracted_specs,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "get_machine_dossier",
            "description": (
                "Get the current state of one machine: identity, status, meters, key specs, "
                "installed serialized components with remaining life, PMs due, recent failures "
                "and indexed documents. Call this FIRST for any question about a specific machine."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer", "description": "Asset ID"},
                    "format": {
                        "type": "string",
                        "enum": ["text", "json"],
                        "default": "text",
                        "description": "text is a compact card; json is structured",
                    },
                },
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_machine_docs",
            "description": (
                "Hybrid (keyword + semantic) search over the manuals, schematics and parts "
                "catalogs indexed for a machine. Returns excerpts with document title and page "
                "number — always cite them."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer", "description": "Restrict to one machine's documents"},
                    "query": {"type": "string", "description": "What to look for"},
                    "doc_type": {
                        "type": "string",
                        "description": "Optional filter: MANUAL, PARTS_CATALOG, SCHEMATIC, DRAWING, CERTIFICATE, INSPECTION_REPORT, OIL_ANALYSIS",
                    },
                    "limit": {"type": "integer", "default": 8},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "lookup_fault_code",
            "description": (
                "Resolve an alarm or fault code (e.g. 'SV0410', 'SPN 100 FMI 1') to its manual "
                "section, plus every previous occurrence of that code on this machine."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "code": {"type": "string", "description": "The fault or alarm code"},
                    "asset_id": {"type": "integer", "description": "Asset the code appeared on"},
                },
                "required": ["code"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_maintenance_history",
            "description": "Work orders, failure events and downtime for a machine, optionally filtered.",
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "since": {"type": "string", "description": "ISO date, e.g. 2025-01-01"},
                    "failure_mode": {"type": "string", "description": "Failure mode code, e.g. SPN-BRG-SEIZE"},
                    "component_id": {"type": "integer", "description": "Restrict to one serialized component"},
                    "limit": {"type": "integer", "default": 50},
                },
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_component_status",
            "description": (
                "Serialized components currently installed on a machine, with hours/cycles since "
                "install and since overhaul, their limits, and percent life remaining."
            ),
            "parameters": {
                "type": "object",
                "properties": {"asset_id": {"type": "integer"}},
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_bom",
            "description": (
                "The bill of materials for a machine: which parts fit, quantity per assembly, "
                "which are consumables and their replacement intervals. Returns empty if the BOM "
                "has not been captured — never guess a part number."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "subunit": {"type": "string", "description": "Position code, e.g. SPN, LUBE, COOL"},
                },
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_part_sourcing",
            "description": (
                "Where to buy a part: suppliers, SKUs, prices, lead times, product URLs, "
                "cross-referenced alternates, on-hand quantity and reorder point."
            ),
            "parameters": {
                "type": "object",
                "properties": {"part_id": {"type": "integer"}},
                "required": ["part_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "diagnose",
            "description": (
                "Given a symptom on a machine, return candidate failure modes ranked by this "
                "machine's own history and its equipment class, the manual excerpts for each, "
                "the checks to run in order, and the parts likely needed."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "symptom": {"type": "string", "description": "What the operator reports"},
                    "observations": {"type": "string", "description": "Anything already checked or measured"},
                },
                "required": ["asset_id", "symptom"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "propose_maintenance_plan",
            "description": (
                "Propose preventive maintenance schedules for a machine from its manual interval "
                "charts and its actual usage. Output is a proposal for a human to approve, not a change."
            ),
            "parameters": {
                "type": "object",
                "properties": {"asset_id": {"type": "integer"}},
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "extract_from_image",
            "description": (
                "Prepare a photographed nameplate for reading. Returns a signed image URL, "
                "local OCR text, and the exact fields this equipment class expects. You read "
                "the image yourself, then call apply_extracted_specs with what you found."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "file_id": {"type": "integer", "description": "Uploaded image file ID"},
                    "mode": {
                        "type": "string",
                        "enum": ["nameplate", "handwritten_log"],
                        "default": "nameplate",
                    },
                },
                "required": ["asset_id", "file_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "apply_extracted_specs",
            "description": (
                "Write values read off a nameplate back to the machine. They land as "
                "UNVERIFIED with the source recorded, and appear in the review queue with a "
                "'verify' chip until a person confirms them. Only include what you could "
                "actually read."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "manufacturer": {"type": "string"},
                    "model": {"type": "string"},
                    "serialNumber": {"type": "string"},
                    "specs": {
                        "type": "array",
                        "description": "Values read from the plate",
                        "items": {
                            "type": "object",
                            "properties": {
                                "specKey": {
                                    "type": "string",
                                    "description": "Must be one of the specKey values from extract_from_image",
                                },
                                "value": {"type": "string"},
                                "unit": {"type": "string"},
                                "confidence": {"type": "number"},
                            },
                            "required": ["specKey", "value"],
                        },
                    },
                },
                "required": ["asset_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_spec_sheet",
            "description": (
                "The typed spec sheet for a machine, grouped, with the source of each value "
                "(manual entry, nameplate OCR, document extraction) and whether it was verified."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "asset_id": {"type": "integer"},
                    "group": {"type": "string", "description": "Optional spec group, e.g. Spindle, Travels"},
                },
                "required": ["asset_id"],
            },
        },
    },
]
