_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def list_people(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
    criteria = {
        "filterFields": [],
        "pageSize": pageSize,
        "pageNum": page,
        "direction": "DESC",
    }
    if search:
        criteria["filterFields"].append(
            {"field": "firstName", "value": search, "operation": "cn", "joinType": "AND"}
        )
    result = _get_client().post_search("/users/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    people_list = [
        {
            "id": u.get("id"),
            "firstName": u.get("firstName", ""),
            "lastName": u.get("lastName", ""),
            "email": u.get("email", ""),
            "phone": u.get("phone", ""),
            "role": u.get("role", {}).get("name") if u.get("role") else None,
        }
        for u in content
    ]
    return {
        "people": people_list,
        "total": result.get("totalElements", len(people_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_people(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_people(page=page, pageSize=pageSize, search=query)


def invite_user(emails: list, role: int, disableSendingEmail: bool = False) -> dict:
    body = {
        "emails": emails,
        "role": {"id": role},
    }
    if disableSendingEmail:
        body["disableSendingEmail"] = True

    result = _get_client().post("/users/invite", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Invitation sent to {', '.join(emails)}.",
    }


TOOL_FUNCTIONS = {
    "list_people": list_people,
    "search_people": search_people,
    "invite_user": invite_user,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "list_people",
            "description": "List users/people in Atlas CMMS with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by first name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_people",
            "description": "Search users/people by name",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string", "description": "Search query (matches first name)"},
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
            "name": "invite_user",
            "description": "Invite one or more users to Atlas CMMS by email. User creation is via invitation only.",
            "parameters": {
                "type": "object",
                "properties": {
                    "emails": {
                        "type": "array",
                        "items": {"type": "string"},
                        "description": "List of email addresses to invite",
                    },
                    "role": {"type": "integer", "description": "Role ID to assign to invited users"},
                    "disableSendingEmail": {
                        "type": "boolean",
                        "description": "If true, create the user without sending an invitation email",
                        "default": False,
                    },
                },
                "required": ["emails", "role"],
            },
        },
    },
]
