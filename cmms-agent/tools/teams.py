_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_team(
    name: str,
    description: str = "",
    users: list = None,
) -> dict:
    body = {"name": name}
    if description:
        body["description"] = description
    if users:
        body["users"] = [{"id": uid} for uid in users]

    result = _get_client().post("/teams", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Team '{name}' created successfully.",
        "team": result,
        "_entity": "team",
        "_id": result.get("id"),
    }


def list_teams(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
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
    result = _get_client().post_search("/teams/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    team_list = [
        {
            "id": t.get("id"),
            "name": t.get("name"),
            "description": t.get("description", ""),
            "users": [u.get("firstName", "") + " " + u.get("lastName", "") for u in t.get("users", [])],
        }
        for t in content
    ]
    return {
        "teams": team_list,
        "total": result.get("totalElements", len(team_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_teams(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_teams(page=page, pageSize=pageSize, search=query)


def delete_team(id: int, confirmed: bool = False) -> dict:
    if not confirmed:
        return {
            "confirm_required": True,
            "message": f"Are you sure you want to delete team with id {id}? Call delete_team again with confirmed=true to proceed.",
        }
    result = _get_client().delete(f"/teams/{id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {"success": True, "message": f"Team {id} deleted successfully."}


TOOL_FUNCTIONS = {
    "add_team": add_team,
    "list_teams": list_teams,
    "search_teams": search_teams,
    "delete_team": delete_team,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_team",
            "description": "Create a new maintenance team in Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Team name (required)"},
                    "description": {"type": "string", "description": "Team description"},
                    "users": {
                        "type": "array",
                        "items": {"type": "integer"},
                        "description": "List of user IDs to add to the team",
                    },
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_teams",
            "description": "List maintenance teams with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by team name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_teams",
            "description": "Search teams by name",
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
            "name": "delete_team",
            "description": "Delete a team. Requires confirmation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Team ID to delete"},
                    "confirmed": {"type": "boolean", "description": "Set to true to confirm deletion", "default": False},
                },
                "required": ["id"],
            },
        },
    },
]
