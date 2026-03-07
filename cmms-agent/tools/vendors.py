_client = None


def _get_client():
    global _client
    if _client is None:
        from api_client import APIClient
        _client = APIClient()
    return _client


def add_vendor(
    companyName: str,
    description: str = "",
    vendorType: str = "",
    rate: float = None,
    address: str = "",
    phone: str = "",
    email: str = "",
) -> dict:
    body = {"companyName": companyName}
    if description:
        body["description"] = description
    if vendorType:
        body["vendorType"] = vendorType
    if rate is not None:
        body["rate"] = rate
    if address:
        body["address"] = address
    if phone:
        body["phone"] = phone
    if email:
        body["email"] = email

    result = _get_client().post("/vendors", body)
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "success": True,
        "message": f"Vendor '{companyName}' created successfully.",
        "vendor": result,
        "_entity": "vendor",
        "_id": result.get("id"),
    }


def list_vendors(page: int = 0, pageSize: int = 10, search: str = "") -> dict:
    criteria = {
        "filterFields": [],
        "pageSize": pageSize,
        "pageNum": page,
        "direction": "DESC",
    }
    if search:
        criteria["filterFields"].append(
            {"field": "companyName", "value": search, "operation": "cn", "joinType": "AND"}
        )
    result = _get_client().post_search("/vendors/search", criteria)
    if isinstance(result, dict) and "error" in result:
        return result
    content = result.get("content", [])
    vendor_list = [
        {
            "id": v.get("id"),
            "companyName": v.get("companyName"),
            "description": v.get("description", ""),
            "vendorType": v.get("vendorType", ""),
            "rate": v.get("rate"),
            "phone": v.get("phone", ""),
            "email": v.get("email", ""),
        }
        for v in content
    ]
    return {
        "vendors": vendor_list,
        "total": result.get("totalElements", len(vendor_list)),
        "page": page,
        "pageSize": pageSize,
    }


def search_vendors(query: str, page: int = 0, pageSize: int = 10) -> dict:
    return list_vendors(page=page, pageSize=pageSize, search=query)


def delete_vendor(id: int, confirmed: bool = False) -> dict:
    if not confirmed:
        return {
            "confirm_required": True,
            "message": f"Are you sure you want to delete vendor with id {id}? Call delete_vendor again with confirmed=true to proceed.",
        }
    result = _get_client().delete(f"/vendors/{id}")
    if isinstance(result, dict) and "error" in result:
        return result
    return {"success": True, "message": f"Vendor {id} deleted successfully."}


TOOL_FUNCTIONS = {
    "add_vendor": add_vendor,
    "list_vendors": list_vendors,
    "search_vendors": search_vendors,
    "delete_vendor": delete_vendor,
}

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "add_vendor",
            "description": "Add a new vendor/supplier to Atlas CMMS",
            "parameters": {
                "type": "object",
                "properties": {
                    "companyName": {"type": "string", "description": "Vendor company name (required)"},
                    "description": {"type": "string", "description": "Vendor description"},
                    "vendorType": {"type": "string", "description": "Type of vendor"},
                    "rate": {"type": "number", "description": "Vendor hourly rate"},
                    "address": {"type": "string", "description": "Vendor address"},
                    "phone": {"type": "string", "description": "Contact phone number"},
                    "email": {"type": "string", "description": "Contact email address"},
                },
                "required": ["companyName"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_vendors",
            "description": "List vendors with optional company name filter",
            "parameters": {
                "type": "object",
                "properties": {
                    "page": {"type": "integer", "description": "Page number (0-based)", "default": 0},
                    "pageSize": {"type": "integer", "description": "Results per page", "default": 10},
                    "search": {"type": "string", "description": "Filter by company name (partial match)"},
                },
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_vendors",
            "description": "Search vendors by company name",
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
            "name": "delete_vendor",
            "description": "Delete a vendor. Requires confirmation.",
            "parameters": {
                "type": "object",
                "properties": {
                    "id": {"type": "integer", "description": "Vendor ID to delete"},
                    "confirmed": {"type": "boolean", "description": "Set to true to confirm deletion", "default": False},
                },
                "required": ["id"],
            },
        },
    },
]
