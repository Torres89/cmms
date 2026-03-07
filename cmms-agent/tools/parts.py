import json

_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_part(
    name: str,
    description: str = "",
    cost: float = 0,
    quantity: float = 0,
    unit: str = "",
    minQuantity: float = 0,
    barcode: str = "",
    area: str = "",
    category: int = None,
    nonStock: bool = False,
    additionalInfos: str = "",
) -> dict:
    body = {"name": name}
    if description:
        body["description"] = description
    if cost:
        body["cost"] = cost
    if quantity:
        body["quantity"] = quantity
    if unit:
        body["unit"] = unit
    if minQuantity:
        body["minQuantity"] = minQuantity
    if barcode:
        body["barcode"] = barcode
    if area:
        body["area"] = area
    if category is not None:
        body["category"] = {"id": category}
    if nonStock:
        body["nonStock"] = nonStock
    if additionalInfos:
        body["additionalInfos"] = additionalInfos

    result = _get_client().post("/parts", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Part '{name}' created successfully.",
        "part": result,
        "_entity": "part",
        "_id": result.get("id"),
    }


def list_parts(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
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
    result = _get_client().post_search("/parts/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    parts_list = [
        {
            "id": p.get("id"),
            "name": p.get("name"),
            "cost": p.get("cost"),
            "quantity": p.get("quantity"),
            "unit": p.get("unit", ""),
            "barcode": p.get("barcode", ""),
            "area": p.get("area", ""),
        }
        for p in content
    ]
    return {
        "parts": parts_list,
        "total": result.get("totalElements", len(parts_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_parts(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_parts(page=page, pageSize=pageSize, search=query)


def delete_part(id: int, confirmed: bool = False) -> dict:
    if not confirmed:
        return {
            "confirm_required": True,
            "message": f"Are you sure you want to delete part with id {id}? Call delete_part again with confirmed=true to proceed.",
        }
    result = _get_client().delete(f"/parts/{id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {"success": True, "message": f"Part {id} deleted successfully."}


TOOL_FUNCTIONS = {
    "add_part": add_part,
    "list_parts": list_parts,
    "search_parts": search_parts,
    "delete_part": delete_part,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_part",
            "description": "Add a new spare part to the Atlas CMMS inventory",
            "parameters": {
                "type": "object",
                "properties": {
                    "name": {"type": "string", "description": "Part name (required)"},
                    "description": {"type": "string", "description": "Part description"},
                    "cost": {"type": "number", "description": "Cost per unit"},
                    "quantity": {"type": "number", "description": "Initial quantity in stock"},
                    "unit": {"type": "string", "description": "Unit of measurement (e.g. 'pcs', 'kg', 'liters')"},
                    "minQuantity": {"type": "number", "description": "Minimum quantity threshold for low-stock alerts"},
                    "barcode": {"type": "string", "description": "Barcode identifier"},
                    "area": {"type": "string", "description": "Storage area/location"},
                    "category": {"type": "integer", "description": "Category ID"},
                    "nonStock": {"type": "boolean", "description": "Whether this is a non-stock part"},
                    "additionalInfos": {"type": "string", "description": "Additional details about the part"},
                },
                "required": ["name"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_parts",
            "description": "List spare parts in inventory with optional name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by part name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_parts",
            "description": "Search spare parts by name or description",
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
            "name": "delete_part",
            "description": "Delete a spare part from inventory. Requires confirmation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Part ID to delete"},
                    "confirmed": {"type": "boolean", "description": "Set to true to confirm deletion", "default": False},
                },
                "required": ["id"],
            },
        },
    },
]
