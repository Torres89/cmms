# telemetry-collector

Polls machines and pushes what they say into Atlas.

**Optional per customer, and a natural paid setup line.** Getting data off a
specific control is routinely the hardest part of a project — control kernel
versus HMI OS versus network boundaries vary by vendor and vintage — so this is
scoped as paid setup *per machine*, never as something that "just works".

Nothing depends on it. Manual entry always works, PMs never require telemetry,
and a shop with no integration at all still gets a working dossier.

## Adapters

| Adapter | Covers | Notes |
|---|---|---|
| `MTCONNECT` | CNC | The primary target. An open standard giving even legacy controls a common vocabulary for spindle speed, axis positions, feed rates, load, program execution, alarms and cycle counters. Fanuc controls have a free MTConnect adapter; the usual topology is an adapter on a small industrial PC on the shop network. |
| `ISO15143` | Earthmoving | ISO 15143-3 (AEMP 2.0). Caterpillar, John Deere, Komatsu, Hitachi and Volvo all expose it, designed so a mixed fleet reports through one vocabulary — **one adapter covers every major OEM**, which is an unusually good effort-to-coverage ratio. |
| `WEBHOOK` | Anything | The escape hatch: a machine or a middlebox POSTs to us instead of us polling it. |

FOCAS (Fanuc-native) and OPC UA (Siemens/newer) are the fallbacks where
MTConnect isn't available; both need vendor libraries on the collector host and
are wired the same way — a `poll()` returning readings and faults.

## How it runs

Configuration lives in the database, on `meter_source.config`, so adding a
machine is a settings change rather than a deploy. The collector asks the API
what to poll, polls it, and posts the results back through the API's internal
endpoints — going through `ReadingService` rather than straight into the table,
so a new reading still rolls hours into every serialized component installed
under the asset and still fires meter-based work order triggers.

```bash
cd telemetry-collector
pip install -r requirements.txt
python main.py
```

## Configuration examples

MTConnect (`meter_source.config`):

```json
{
  "agentUrl": "http://192.168.1.50:5000",
  "deviceName": "VF4SS",
  "dataItemId": "SpindleRunTime",
  "collectFaults": true
}
```

ISO 15143-3 / AEMP 2.0:

```json
{
  "baseUrl": "https://services.cat.com/iso15143",
  "username": "...",
  "password": "...",
  "equipmentId": "CAT0D6TXXXX",
  "field": "CumulativeOperatingHours",
  "collectFaults": true
}
```

Fault descriptions generally aren't in the payload — Cat, for instance, expects
lookup via service tooling or the dealer — so codes land in `fault_event` with
whatever description the payload carried, and the `fault_code_dictionary` fills
in the meaning from public J1939 data plus whatever the customer's own manuals
yielded during ingestion.
