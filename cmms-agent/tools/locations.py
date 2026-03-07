_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_location(
    name: str,
    address: str = "",
    description: str = "",
    parentLocation: int = None,
    workers: list = None,
    teams: list = None,
) -> dict:
    body = {"name": name}
    if address:
        body["address"] = address
    if description:
        body["description"] = description
    if parentLocation is not None:
        body["parentLocation"] = {"id": parentLocation}
    if workers:
        body["workers"] = [{"id": uid} for uid in workers]
    if teams:
        body["teams"] = [{"id": tid} for tid in teams]

    result = _get_client().post("/locations", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Location '{name}' created successfully.",
        "location": result,
        "_entity": "location",
        "_id": result.get("id"),
    }


def list_locations(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
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
    result = _get_client().post_search("/locations/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    loc_list = [
        {
            "id": loc.get("id"),
            "name": loc.get("name"),
            "address": loc.get("address", ""),
            "description": loc.get("description", ""),
            "parentLocation": loc.get("parentLocation", {}).get("name") if loc.get("parentLocation") else None,
        }
        for loc in content
    ]
    return {
        "locations": loc_list,
        "total": result.get("totalElements", len(loc_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_locations(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_locations(page=page, pageSize=pageSize, search=query)


def delete_location(id: int, confirmed: bool = False) -> dict:
    if not confirmed:
        return {
            "confirm_required": True,
            "message": f"Are you sure you want to delete location with id {id}? Call delete_location again with confirmed=true to proceed.",
        }
    result = _get_client().delete(f"/locations/{id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {"success": True, "message": f"Location {id} deleted successfully."}


TOOL_FUNCTIONS = {
    "add_location": add_location,
    "list_locations": list_locations,
    "search_locations": search_locations,
    "delete_location": delete_location,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_location",
            "description": "Add a new facility location to Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Location name (required)"},
                    "address": {"type": "string", "description": "Physical address"},
                    "description": {"type": "string", "description": "Location description"},
                    "parentLocation": {"type": "integer", "description": "Parent location ID for nesting"},
                    "workers": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of user IDs assigned to this location",
                    },
                    "teams": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of team IDs assigned to this location",
                    },
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_locations",
            "description": "List facility locations with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by location name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_locations",
            "description": "Search locations by name",
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
    {
        "type": "function",
        "function": {
            "name": "delete_location",
            "description": "Delete a location. Requires confirmation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Location ID to delete"},
                    "confirmed": {"type": "boolean", "description": "Set to true to confirm deletion", "default": False},
                },
                "required": ["id"],
            },
        },
    },
]
