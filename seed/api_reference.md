# Atlas CMMS REST API — Create Reference

Precise reference for **creating** records via the Atlas CMMS Spring Boot backend (`com.grash`).
Derived directly from controller / DTO / model source under
`api/src/main/java/com/grash`.

---

## Global conventions

### Auth header
All authenticated endpoints expect a JWT bearer token:

```
Authorization: Bearer <accessToken>
Content-Type: application/json
```

- Obtain the token from `POST /auth/signin` (see [Auth](#auth)).
- Most create endpoints require `hasRole('ROLE_CLIENT')` **and** the caller's role must
  contain the relevant `CREATE` permission (e.g. `WORK_ORDERS`, `ASSETS`, `METERS`,
  `CATEGORIES`, `PARTS_AND_MULTIPARTS`, `PEOPLE_AND_TEAMS`, `LOCATIONS`,
  `VENDORS_AND_CUSTOMERS`, `FILES`, `SETTINGS`). The seeded admin role normally has all.

### Base URL
The API is mounted at the root (no `/api` prefix in controllers). Paths below are relative to
the server origin, e.g. `http://localhost:8080`. (`server.servlet.context-path` may add a prefix
in some deployments — check your env.)

### Date / time format  ⚠️ IMPORTANT
Every date field in the create bodies (`dueDate`, `inServiceDate`, `warrantyExpirationDate`,
`estimatedStartDate`, `startsOn`, `endsOn`, `completedOn`, reading dates, etc.) is a Java
**`java.util.Date`** (NOT `LocalDate`). There is **no custom Jackson date format** configured
(`api/src/main/resources/application.yml` only sets `fail-on-empty-beans: false`).

Therefore Jackson uses its default `Date` (de)serialization:
- **Output**: epoch milliseconds by default (numeric), or ISO-8601 instant if the global
  default is changed. In practice the frontend sends ISO strings.
- **Input (what you send)**: send an **ISO-8601 instant string** such as
  `"2026-06-14T00:00:00.000Z"` (also accepts `"2026-06-14T08:00:00+00:00"`), **or** an epoch
  millisecond number. A bare `"2026-06-14"` (date-only) is NOT reliably parsed by the default
  `Date` deserializer — always include a time component.

There are **no `LocalDate` `"yyyy-MM-dd"` fields** on the create bodies covered here.

### Nested references
Relationship fields are serialized/deserialized as objects carrying only `id`
(annotated `@Schema(implementation = IdDTO.class)` or write-only associations). Send them as:

```json
"location":   { "id": 5 },
"assignedTo": [ { "id": 3 }, { "id": 7 } ]
```

### Read-only / ignored on input
Fields inherited from the audit bases are ignored on create input and only appear on output:
`id` (generated), `createdBy`, `updatedBy`, `createdAt`, `updatedAt`, and `company` /
`companySettings` (auto-derived from the authenticated user). Do **not** send these.

---

## Entity index

| # | Entity | Create endpoint | Request body type |
|---|--------|-----------------|-------------------|
| 1 | Location | `POST /locations` | `Location` (model) |
| 2 | Asset Category | `POST /asset-categories` | `AssetCategory` (model) |
| 3 | Part Category | `POST /part-categories` | `PartCategory` (model) |
| 4 | Meter Category | `POST /meter-categories` | `MeterCategory` (model) |
| 5 | Work Order Category | `POST /work-order-categories` | `WorkOrderCategory` (model) |
| 6 | Vendor | `POST /vendors` | `Vendor` (model) |
| 7 | User (invite) | `POST /users/invite` | `UserInvitationDTO` |
| 8 | Team | `POST /teams` | `Team` (model) |
| 9 | Asset | `POST /assets` | `Asset` (model) |
| 10 | Part | `POST /parts` | `Part` (model) |
| 11 | MultiParts | `POST /multi-parts` | `MultiParts` (model) |
| 12 | Meter | `POST /meters` | `Meter` (model) |
| 13 | Reading | `POST /readings` | `Reading` (model) |
| 14 | Preventive Maintenance | `POST /preventive-maintenances` | `PreventiveMaintenancePostDTO` |
| 15 | Work Order | `POST /work-orders` | `WorkOrderPostDTO` |
| 16 | File (upload) | `POST /files/upload` | multipart/form-data |
| 17 | Role | `POST /roles` | `Role` (model) |
| 18 | Auth (signin/signup) | `POST /auth/signin`, `POST /auth/signup` | login / signup DTO |

18 entities documented.

---

## 1. Location

- **Endpoint:** `POST /locations`  — `LocationController.create` (`controller/LocationController.java`)
- **Body:** `Location` model (`model/Location.java`)

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `address` | String | optional |
| `longitude` | Double | optional |
| `latitude` | Double | optional |
| `customId` | String | optional |
| `parentLocation` | `{id}` | optional, nested Location |
| `image` | `{id}` | optional, nested File |
| `workers` | `[{id}]` | optional, list of OwnUser |
| `teams` | `[{id}]` | optional, list of Team |
| `vendors` | `[{id}]` | optional, list of Vendor |
| `customers` | `[{id}]` | optional, list of Customer |
| `files` | `[{id}]` | optional, list of File |

```json
{
  "name": "Main Plant",
  "address": "1200 Industrial Ave",
  "longitude": -122.42,
  "latitude": 37.77,
  "parentLocation": { "id": 1 },
  "workers": [ { "id": 3 } ],
  "teams": [ { "id": 2 } ]
}
```

---

## 2–5. Categories

All four category controllers share the same shape. The model fields come from
`model/abstracts/CategoryAbstract.java` (`name`, `description`, `isDemo`, `companySettings`).
`companySettings` is auto-assigned from the authenticated user — do **not** send it.

> There is **no `color` or `code` field** on these category models. Only `name` and
> `description` are settable. (The PATCH body `CategoryPatchDTO` also has only
> `name` (required) + `description`.)

| Entity | Endpoint | Controller | Body model |
|--------|----------|------------|------------|
| Asset Category | `POST /asset-categories` | `AssetCategoryController.create` | `AssetCategory` |
| Part Category | `POST /part-categories` | `PartCategoryController.create` | `PartCategory` |
| Meter Category | `POST /meter-categories` | `MeterCategoryController.create` | `MeterCategory` |
| Work Order Category | `POST /work-order-categories` | `WorkOrderCategoryController.create` | `WorkOrderCategory` |

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `description` | String | optional |

```json
{ "name": "Electrical", "description": "Electrical equipment" }
```

---

## 6. Vendor

- **Endpoint:** `POST /vendors` — `VendorController.create` (`controller/VendorController.java`)
- **Body:** `Vendor` model (`model/Vendor.java`; contact fields from `BasicInfos`)

| Field | Type | Notes |
|-------|------|-------|
| `companyName` | String | **required** (`@NotNull`) |
| `description` | String | optional |
| `vendorType` | String | optional (free text) |
| `email` | String | optional (from BasicInfos) |
| `phone` | String | optional |
| `address` | String | optional |
| `website` | String | optional |
| `rate` | long | optional, default 0 |

> `assets`, `locations`, `parts` lists exist but are `@JsonIgnore` — not settable here.

```json
{
  "companyName": "Acme Bearings Inc.",
  "description": "Bearing supplier",
  "vendorType": "Supplier",
  "email": "sales@acme.example",
  "phone": "+1-555-0100",
  "address": "55 Supply Rd",
  "website": "https://acme.example",
  "rate": 0
}
```

---

## 7. User — invite (this is how users are created)

There is **no plain `POST /users`**. Users are created either by **inviting** an email to an
existing role, or via **signup** (see Auth). To list available roles for the `role.id`, use
`GET /roles`.

- **Endpoint:** `POST /users/invite` — `UserController.invite` (`controller/UserController.java`)
- **Body:** `UserInvitationDTO` (`dto/UserInvitationDTO.java`)
- **Permission:** role needs `CREATE` on `PEOPLE_AND_TEAMS`.

| Field | Type | Notes |
|-------|------|-------|
| `role` | `{id}` | **required** (`@NotNull`). The whole `Role` object — but only `id` is used; the role must belong to the caller's company. |
| `emails` | `[String]` | list of email addresses to invite |
| `disableSendingEmail` | Boolean | optional; `true` to skip the invitation email |

```json
{
  "role": { "id": 4 },
  "emails": [ "tech1@shop.example", "tech2@shop.example" ],
  "disableSendingEmail": true
}
```

**Response:** `SuccessResponse` `{ "success": true, "message": "Users have been invited" }`.
Invited users are created as records but complete their own `firstName` / `lastName` /
`password` when they accept. The invite body does **not** carry firstName/lastName/rate/
jobTitle/phone — those are not settable at invite time.

### Setting role / profile fields after creation
- **Role assignment is by role _id_, not enum.** Reassign with
  `PATCH /users/{id}/role?role={roleId}` (`UserController.patchRole`).
- Update profile (firstName, lastName, rate, jobTitle, phone, etc.) with
  `PATCH /users/{id}` using `UserPatchDTO`.

### Listing roles — `GET /roles`
- **Endpoint:** `GET /roles` — `RoleController.getAll`. Returns the company's roles
  (`Role` objects with `id`, `name`, `roleType`, `code`, permission sets). Requires `SETTINGS`
  view permission for client users.
- `GET /roles/{id}` fetches one role.

The `OwnUser` model (output / signup) profile fields, for reference:
`firstName` (req), `lastName` (req), `email` (req, unique), `phone`, `rate` (long, default 0),
`jobTitle`, `role` `{id}` (req), `image` `{id}`, `location` `{id}`. `enabled`,
`enabledInSubscription`, `ownsCompany`, `password` are read-only / ignored on input.

---

## 8. Team

- **Endpoint:** `POST /teams` — `TeamController.create` (`controller/TeamController.java`)
- **Body:** `Team` model (`model/Team.java`)

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `description` | String | optional |
| `users` | `[{id}]` | list of OwnUser members |

> `asset`, `locations` lists are `@JsonIgnore` — not settable here.

```json
{
  "name": "Maintenance Crew A",
  "description": "First shift",
  "users": [ { "id": 3 }, { "id": 5 } ]
}
```

---

## 9. Asset  ⚠️ create body is the full `Asset` model

- **Endpoint:** `POST /assets` — `AssetController.create` (`controller/AssetController.java`)
- **Body:** `Asset` model directly (`@Valid @RequestBody Asset`) — `model/Asset.java`

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `description` | String | optional |
| `customId` | String | optional |
| `area` | String | optional |
| `barCode` | String | optional; must be unique in company (409 if dup) |
| `nfcId` | String | optional; must be unique in company |
| `serialNumber` | String | optional |
| `model` | String | optional |
| `manufacturer` | String | optional |
| `power` | String | optional |
| `acquisitionCost` | Double | optional |
| `additionalInfos` | String | optional |
| `inServiceDate` | Date | optional — **field name is `inServiceDate`**, ISO instant (see date note) |
| `warrantyExpirationDate` | Date | optional, ISO instant |
| `status` | enum `AssetStatus` | optional, default `OPERATIONAL`. Values: `OPERATIONAL`, `DOWN`, `MODERNIZATION`, `STANDBY`, `INSPECTION_SCHEDULED`, `COMMISSIONING`, `EMERGENCY_SHUTDOWN` |
| `location` | `{id}` | optional, nested Location |
| `category` | `{id}` | optional, nested AssetCategory |
| `parentAsset` | `{id}` | optional, nested Asset |
| `primaryUser` | `{id}` | optional, nested OwnUser |
| `assignedTo` | `[{id}]` | optional, list of OwnUser |
| `teams` | `[{id}]` | optional, list of Team |
| `vendors` | `[{id}]` | optional, list of Vendor |
| `customers` | `[{id}]` | optional, list of Customer |
| `parts` | `[{id}]` | optional, list of Part |
| `image` | `{id}` | optional, nested File |
| `files` | `[{id}]` | optional, list of File |
| `deprecation` | object | optional |

> Note: `assignedTo`, `teams`, `vendors`, `customers`, `parts`, `files` are write-only
> associations (accepted on input, not echoed in the raw entity output — use the show DTO).

```json
{
  "name": "CNC Lathe #3",
  "description": "Haas ST-20",
  "serialNumber": "ST20-2021-0345",
  "model": "ST-20",
  "manufacturer": "Haas",
  "area": "Machining Bay",
  "barCode": "AST-0003",
  "acquisitionCost": 85000.0,
  "inServiceDate": "2022-01-15T00:00:00.000Z",
  "status": "OPERATIONAL",
  "customId": "CNC-003",
  "additionalInfos": "Annual calibration required",
  "location": { "id": 5 },
  "category": { "id": 2 },
  "parentAsset": { "id": 1 },
  "primaryUser": { "id": 3 },
  "assignedTo": [ { "id": 3 }, { "id": 7 } ],
  "teams": [ { "id": 2 } ],
  "vendors": [ { "id": 4 } ],
  "image": { "id": 10 },
  "files": [ { "id": 11 } ]
}
```

---

## 10. Part

- **Endpoint:** `POST /parts` — `PartController.create` (`controller/PartController.java`)
- **Body:** `Part` model (`model/Part.java`)

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `cost` | double | optional, default 0 |
| `quantity` | double | optional, default 0 |
| `minQuantity` | double | optional, default 0 |
| `barcode` | String | optional; unique in company |
| `description` | String | optional |
| `area` | String | optional |
| `unit` | String | optional |
| `additionalInfos` | String | optional |
| `nonStock` | boolean | optional, default false |
| `category` | `{id}` | optional, nested PartCategory |
| `image` | `{id}` | optional, nested File |
| `assignedTo` | `[{id}]` | optional, list of OwnUser |
| `teams` | `[{id}]` | optional, list of Team |
| `vendors` | `[{id}]` | optional, list of Vendor |
| `customers` | `[{id}]` | optional, list of Customer |
| `files` | `[{id}]` | optional, list of File |

```json
{
  "name": "Spindle Bearing 6205",
  "cost": 42.5,
  "quantity": 12,
  "minQuantity": 4,
  "barcode": "PRT-6205",
  "unit": "pcs",
  "category": { "id": 3 },
  "vendors": [ { "id": 4 } ]
}
```

---

## 11. MultiParts

- **Endpoint:** `POST /multi-parts` — `MultiPartsController.create` (`controller/MultiPartsController.java`)
- **Body:** `MultiParts` model (`model/MultiParts.java`)

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `parts` | `[{id}]` | list of Part |

```json
{ "name": "Lathe Service Kit", "parts": [ { "id": 9 }, { "id": 10 } ] }
```

---

## 12. Meter

- **Endpoint:** `POST /meters` — `MeterController.create` (`controller/MeterController.java`)
- **Body:** `Meter` model (`model/Meter.java`)
- **Requires** the `METER` plan feature.

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `unit` | String | optional |
| `updateFrequency` | int | **required** (`@NotNull`), days; must be ≥ 1 |
| `asset` | `{id}` | **required** (`@NotNull`), nested Asset (write-only) |
| `meterCategory` | `{id}` | optional — field name is **`meterCategory`** (not `category`), nested MeterCategory |
| `location` | `{id}` | optional, nested Location |
| `image` | `{id}` | optional, nested File |
| `users` | `[{id}]` | optional, list of OwnUser (notified on triggers) |

```json
{
  "name": "Spindle Hours",
  "unit": "hours",
  "updateFrequency": 7,
  "asset": { "id": 12 },
  "meterCategory": { "id": 1 },
  "users": [ { "id": 3 } ]
}
```

---

## 13. Reading (record a meter reading)

- **Endpoint:** `POST /readings` — `ReadingController.create` (`controller/ReadingController.java`)
- **Body:** `Reading` model (`model/Reading.java`)
- **Requires** the `METER` plan feature.

| Field | Type | Notes |
|-------|------|-------|
| `value` | double | **required** — the reading value |
| `meter` | `{id}` | **required** (`@NotNull`), nested Meter (write-only) |

> There is **no settable `date` field** — the reading timestamp is the auto `createdAt`.
> The server enforces the meter's `updateFrequency`: posting again before
> `lastReading.createdAt + updateFrequency days` returns 406. Posting a reading can also
> auto-create work orders if a `WorkOrderMeterTrigger` threshold is crossed.

```json
{ "value": 1342.5, "meter": { "id": 8 } }
```

To list readings of a meter: `GET /readings/meter/{meterId}`.

---

## 14. Preventive Maintenance (+ schedule)

- **Endpoint:** `POST /preventive-maintenances` — `PreventiveMaintenanceController.create`
- **Body:** `PreventiveMaintenancePostDTO` (`dto/PreventiveMaintenancePostDTO.java`),
  which **extends `WorkOrderBase`** — so it carries the work-order template fields **plus**
  the schedule fields. On create, the controller builds the PM and its `Schedule` from these.

The work-order template fields (from `WorkOrderBase`, `model/abstracts/WorkOrderBase.java`):

| Field | Type | Notes |
|-------|------|-------|
| `title` | String | **required** (`@NotNull`) |
| `description` | String | optional |
| `priority` | enum `Priority` | optional, default `NONE`. Values: `NONE`, `LOW`, `MEDIUM`, `HIGH` |
| `estimatedDuration` | double | optional, hours; must be ≥ 0 |
| `estimatedStartDate` | Date | optional, ISO instant |
| `requiredSignature` | boolean | optional, default false |
| `asset` | `{id}` | optional, nested Asset |
| `location` | `{id}` | optional, nested Location |
| `category` | `{id}` | optional, nested WorkOrderCategory |
| `team` | `{id}` | optional, nested Team |
| `primaryUser` | `{id}` | optional, nested OwnUser |
| `assignedTo` | `[{id}]` | optional, list of OwnUser |
| `customers` | `[{id}]` | optional, list of Customer |
| `files` | `[{id}]` | optional, list of File |
| `image` | `{id}` | optional, nested File |

The schedule / trigger fields (declared on `PreventiveMaintenancePostDTO`, persisted to the
`Schedule` model — `model/Schedule.java`):

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) — the PM/schedule name |
| `frequency` | int | **required** (`@NotNull`) — interval in **days**; must be ≥ 1 |
| `recurrenceType` | enum `RecurrenceType` | **required**. Values: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `recurrenceBasedOn` | enum `RecurrenceBasedOn` | **required**. Values: `SCHEDULED_DATE`, `COMPLETED_DATE` |
| `startsOn` | Date | optional, ISO instant; defaults to now if omitted |
| `endsOn` | Date | optional, ISO instant |
| `dueDateDelay` | Integer | optional — days from generation to due date; if set must be ≥ 1 |
| `daysOfWeek` | `[int]` | optional — for weekly recurrence (0 = Monday) |

```json
{
  "title": "Monthly lubrication",
  "description": "Grease spindle bearings",
  "priority": "MEDIUM",
  "asset": { "id": 12 },
  "category": { "id": 2 },
  "team": { "id": 2 },
  "primaryUser": { "id": 3 },
  "assignedTo": [ { "id": 3 } ],
  "estimatedDuration": 1.5,

  "name": "Spindle PM",
  "frequency": 30,
  "recurrenceType": "MONTHLY",
  "recurrenceBasedOn": "SCHEDULED_DATE",
  "startsOn": "2026-07-01T00:00:00.000Z",
  "dueDateDelay": 3,
  "daysOfWeek": []
}
```

---

## 15. Work Order  ⚠️ status / completion is a separate PATCH

- **Create endpoint:** `POST /work-orders` — `WorkOrderController.create`
- **Body:** `WorkOrderPostDTO` (`dto/workOrder/WorkOrderPostDTO.java`) which
  **extends the `WorkOrder` model**, adding one field `assetStatus`. The work-order fields come
  from `WorkOrder` + `WorkOrderBase`.

| Field | Type | Notes |
|-------|------|-------|
| `title` | String | **required** (`@NotNull`) |
| `description` | String | optional |
| `priority` | enum `Priority` | optional, default `NONE`. Values: `NONE`, `LOW`, `MEDIUM`, `HIGH` |
| `dueDate` | Date | optional, ISO instant (see date note) |
| `estimatedStartDate` | Date | optional, ISO instant |
| `estimatedDuration` | double | optional, hours; ≥ 0 |
| `requiredSignature` | boolean | optional, default false |
| `customId` | String | optional |
| `asset` | `{id}` | optional, nested Asset |
| `location` | `{id}` | optional, nested Location |
| `category` | `{id}` | optional, nested WorkOrderCategory |
| `team` | `{id}` | optional, nested Team |
| `primaryUser` | `{id}` | optional, nested OwnUser |
| `assignedTo` | `[{id}]` | optional, list of OwnUser |
| `customers` | `[{id}]` | optional, list of Customer |
| `files` | `[{id}]` | optional, list of File |
| `image` | `{id}` | optional, nested File |
| `signature` | String | optional (requires SIGNATURE feature/license) |
| `assetStatus` | enum `AssetStatus` | optional — sets the asset's status when the WO is created |

> `status` defaults to `OPEN` on create. Do not rely on setting `status` in the create body —
> use the change-status endpoint below to move it.
>
> **Status enum values (`model/enums/Status.java`):** `OPEN`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETE`.

```json
{
  "title": "Replace worn belt on conveyor 2",
  "description": "Belt slipping under load",
  "priority": "HIGH",
  "dueDate": "2026-06-20T17:00:00.000Z",
  "estimatedDuration": 2.0,
  "requiredSignature": false,
  "asset": { "id": 12 },
  "location": { "id": 5 },
  "category": { "id": 1 },
  "team": { "id": 2 },
  "primaryUser": { "id": 3 },
  "assignedTo": [ { "id": 3 }, { "id": 7 } ]
}
```

### Change status / complete / feedback
Use the dedicated endpoint (NOT the create body):

- **Endpoint:** `PATCH /work-orders/{id}/change-status` — `WorkOrderController.changeStatus`
- **Body:** `WorkOrderChangeStatusDTO` (`dto/WorkOrderChangeStatusDTO.java`)

| Field | Type | Notes |
|-------|------|-------|
| `status` | enum `Status` | required to change status. `OPEN` / `IN_PROGRESS` / `ON_HOLD` / `COMPLETE` |
| `feedback` | String | optional |
| `signature` | String | optional (license-gated) |

When `status` = `COMPLETE`, the server sets `completedBy` and `completedOn` automatically
(you don't send them).

```json
{ "status": "COMPLETE", "feedback": "Belt replaced, tested OK." }
```

### General field edits
`PATCH /work-orders/{id}` with `WorkOrderPatchDTO` (`dto/workOrder/WorkOrderPatchDTO.java`)
edits the other fields (and can set `archived`, `completedBy`, `completedOn`).

### Attaching files to a work order
- `PATCH /work-orders/files/{id}/add` with a JSON array of File objects `[{ "id": 11 }]`
  (`WorkOrderController.addFilesToWorkOrder`).
- Remove: `DELETE /work-orders/files/{id}/{fileId}/remove`.
- Alternatively include `files: [{id}]` (and `image: {id}`) in the create or patch body.

---

## 16. File upload  ⚠️ multipart, then attach by id

- **Endpoint:** `POST /files/upload` — `FileController.handleFileUpload` (`controller/FileController.java`)
- **Content-Type:** `multipart/form-data`
- **Requires** `CREATE` on `FILES`, the `FILE` plan feature, and the `FILE_ATTACHMENTS` license entitlement.

Form fields:

| Form field | Type | Required | Notes |
|-----------|------|----------|-------|
| `files` | file(s) | yes | one or more files (the multipart field name is **`files`**, repeatable) |
| `folder` | text | yes | storage subfolder, e.g. `"assets"` / company-scoped path |
| `hidden` | text | yes | `"true"` or `"false"` (string) |
| `type` | text | yes | enum `FileType`: `IMAGE` or `OTHER` |
| `taskId` | text (int) | no | optional task to associate |

**Response:** `List<FileShowDTO>` (`dto/FileShowDTO.java`):

| Field | Type |
|-------|------|
| `id` | Long |
| `name` | String |
| `url` | String (the stored path/URL) |
| `type` | `FileType` |
| `hidden` | boolean |
| `createdAt` / `updatedAt` / `createdBy` / `updatedBy` | audit |

curl example:

```bash
curl -X POST "$BASE/files/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@./photo.jpg" \
  -F "folder=assets" \
  -F "hidden=false" \
  -F "type=IMAGE"
```

### Attaching an uploaded file
Take the returned `id` and reference it from the target entity:
- Asset / Part / Location: `image: { "id": <fileId> }` (single image) or
  `files: [ { "id": <fileId> } ]` in the create or PATCH body.
- Work Order: `PATCH /work-orders/files/{woId}/add` with `[ { "id": <fileId> } ]`, or
  `files: [...]` / `image: {id}` in create/patch.

---

## 17. Role

- **Endpoint:** `POST /roles` — `RoleController.create` (`controller/RoleController.java`)
- **Body:** `Role` model (`model/Role.java`)
- **Requires** `SETTINGS` view permission and the `ROLE` plan feature. `paid` is forced `true`
  by the server (ignored on input).

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | **required** (`@NotNull`) |
| `roleType` | enum `RoleType` | **required**. Values: `ROLE_SUPER_ADMIN`, `ROLE_CLIENT` (use `ROLE_CLIENT`) |
| `description` | String | optional |
| `code` | enum `RoleCode` | optional, default `USER_CREATED`. Values: `ADMIN`, `LIMITED_ADMIN`, `TECHNICIAN`, `LIMITED_TECHNICIAN`, `VIEW_ONLY`, `REQUESTER`, `USER_CREATED` |
| `externalId` | String | optional |
| `createPermissions` | `[PermissionEntity]` | optional set |
| `viewPermissions` | `[PermissionEntity]` | optional set |
| `viewOtherPermissions` | `[PermissionEntity]` | optional set |
| `editOtherPermissions` | `[PermissionEntity]` | optional set |
| `deleteOtherPermissions` | `[PermissionEntity]` | optional set |

`PermissionEntity` values include (among others): `ASSETS`, `LOCATIONS`, `METERS`,
`PARTS_AND_MULTIPARTS`, `PEOPLE_AND_TEAMS`, `VENDORS_AND_CUSTOMERS`, `CATEGORIES`,
`WORK_ORDERS`, `PREVENTIVE_MAINTENANCES`, `REQUESTS`, `FILES`, `SETTINGS`.

```json
{
  "name": "Shop Technician",
  "roleType": "ROLE_CLIENT",
  "description": "Floor tech",
  "viewPermissions": [ "WORK_ORDERS", "ASSETS", "PARTS_AND_MULTIPARTS" ],
  "createPermissions": [ "WORK_ORDERS" ],
  "editOtherPermissions": [ "WORK_ORDERS" ]
}
```

---

## 18. Auth

### Sign in (get a token)
- **Endpoint:** `POST /auth/signin` — `AuthController.login`
- **Body:** `UserLoginRequest` (`dto/UserLoginRequest.java`)

| Field | Type | Notes |
|-------|------|-------|
| `email` | String | **required** |
| `password` | String | **required** |
| `type` | String | optional, default `"CLIENT"` |

```json
{ "email": "admin@shop.example", "password": "secret" }
```

**Response:** `AuthResponse` (`dto/AuthResponse.java`):

```json
{ "accessToken": "eyJhbGciOi..." }
```

Use it as `Authorization: Bearer eyJhbGciOi...`.

### Sign up (create a user/company)
- **Endpoint:** `POST /auth/signup` — `AuthController.signup`
- **Body:** `UserSignupRequest` (`dto/UserSignupRequest.java`)
- Public registration may be disabled (`registration.disable`, default `true`). When disabled,
  a `role` must be supplied (i.e. it is used for admin-seeded / invited account completion).

| Field | Type | Notes |
|-------|------|-------|
| `email` | String | **required** |
| `password` | String | **required** |
| `firstName` | String | **required** |
| `lastName` | String | **required** |
| `phone` | String | **required** |
| `role` | `{id}` | optional (required when public registration disabled) |
| `companyName` | String | optional |
| `employeesCount` | int | optional, default 0 |
| `language` | enum `Language` | optional |

```json
{
  "email": "owner@shop.example",
  "password": "secret",
  "firstName": "Pat",
  "lastName": "Owner",
  "phone": "+1-555-0101",
  "companyName": "Pat's Machine Shop"
}
```

### Other useful auth endpoints
- `GET /auth/me` — current user (`UserResponseDTO`).
- `GET /auth/refresh` — refresh token.
- `POST /auth/updatepwd` — change password (`UpdatePasswordRequest`).

---

## Quick gotchas checklist

- Send dates as **ISO instant strings with a time component** (`2026-06-14T00:00:00.000Z`),
  not bare `yyyy-MM-dd`. These are `java.util.Date`, not `LocalDate`.
- Nested entities = `{ "id": N }`; collections = `[ { "id": N } ]`.
- **Asset**, **Part**, **Meter**, **Reading**, **Team**, **Vendor**, **Location**, **MultiParts**,
  **Role**, and the four **Categories** consume the raw JPA **model** as the request body.
  **Work Order** uses `WorkOrderPostDTO`, **PM** uses `PreventiveMaintenancePostDTO`,
  **User invite** uses `UserInvitationDTO`.
- Users are created via **`POST /users/invite`** (role by id) or **`POST /auth/signup`** —
  there is no `POST /users`.
- Meter category field is `meterCategory` (not `category`); Meter requires `asset` + `updateFrequency`.
- Reading has no settable date — timestamp is server-assigned; meter `updateFrequency` is enforced.
- Work-order completion / status changes go through **`PATCH /work-orders/{id}/change-status`**
  with `{ status, feedback, signature }`; `completedOn`/`completedBy` are set automatically.
- Categories have only `name` + `description` (no color/code).
- File upload is multipart `POST /files/upload` (field `files`, plus `folder`, `hidden`, `type`),
  returns `[{id, name, url, type, hidden}]`; attach via `image:{id}` / `files:[{id}]`.
