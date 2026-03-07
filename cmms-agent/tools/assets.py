_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_asset(
    name: str,
    description: str = "",
    location: int = None,
    category: int = None,
    parentAsset: int = None,
    serialNumber: str = "",
    model: str = "",
    assignedTo: list = None,
    teams: list = None,
    vendors: list = None,
) -> dict:
    body = {"name": name}
    if description:
        body["description"] = description
    if location is not None:
        body["location"] = {"id": location}
    if category is not None:
        body["category"] = {"id": category}
    if parentAsset is not None:
        body["parentAsset"] = {"id": parentAsset}
    if serialNumber:
        body["serialNumber"] = serialNumber
    if model:
        body["model"] = model
    if assignedTo:
        body["assignedTo"] = [{"id": uid} for uid in assignedTo]
    if teams:
        body["teams"] = [{"id": tid} for tid in teams]
    if vendors:
        body["vendors"] = [{"id": vid} for vid in vendors]

    result = _get_client().post("/assets", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Asset '{name}' created successfully.",
        "asset": result,
        "_entity": "asset",
        "_id": result.get("id"),
    }


def list_assets(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
    criteria = {
        "filterFields": [],
        "pageSize": pageSize,
        "pageNum": page,
        "direction": "DESC",
    }
    if search:
        criteria["filterFields"].append(
            {"field": "name", "value": search, "operation": "cn", "joinType": "AND"}
        )
    result = _get_client().post_search("/assets/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    asset_list = [
        {
            "id": a.get("id"),
            "name": a.get("name"),
            "description": a.get("description", ""),
            "location": a.get("location", {}).get("name") if a.get("location") else None,
            "serialNumber": a.get("serialNumber", ""),
            "model": a.get("model", ""),
        }
        for a in content
    ]
    return {
        "assets": asset_list,
        "total": result.get("totalElements", len(asset_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_assets(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_assets(page=page, pageSize=pageSize, search=query)


TOOL_FUNCTIONS = {
    "add_asset": add_asset,
    "list_assets": list_assets,
    "search_assets": search_assets,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_asset",
            "description": "Register a new asset/equipment in Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Asset name (required)"},
                    "description": {"type": "string", "description": "Asset description"},
                    "location": {"type": "integer", "description": "Location ID where the asset is installed"},
                    "category": {"type": "integer", "description": "Asset category ID"},
                    "parentAsset": {"type": "integer", "description": "Parent asset ID for hierarchy"},
                    "serialNumber": {"type": "string", "description": "Serial number"},
                    "model": {"type": "string", "description": "Model name/number"},
                    "assignedTo": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of user IDs responsible for this asset",
                    },
                    "teams": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of team IDs responsible for this asset",
                    },
                    "vendors": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of vendor IDs for this asset",
                    },
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_assets",
            "description": "List registered assets with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by asset name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_assets",
            "description": "Search assets by name",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Search query"},
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                },
                "required": ["query"],
            },
        },
    },
]
