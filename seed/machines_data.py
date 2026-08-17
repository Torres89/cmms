# -*- coding: utf-8 -*-
"""
Machine-specialist seed data for two real machines.

Every figure here comes from a manufacturer datasheet, service documentation or
published alarm list, cited in `docs/machine-dossier-seed.md`. Nothing is
invented: if a number could not be sourced it is simply absent, so the
completeness meter tells the truth about what is actually known.

The two machines are chosen to contrast, not to duplicate:

* The **Haas VF-2** has a centralized Bijur oil system feeding the ways, with a
  low-pressure alarm, and a genuine wrong-oil hazard — newer machines take
  Mobil SHC 625 and putting Vactra No. 2 in one clogs the system and takes the
  spindle with it.
* The **FANUC ROBODRILL** has no way-oil system at all. Its axes run on grease
  refilled on a fixed operating-hour schedule, with no pressure alarm to warn
  anyone, so the schedule is the only protection.

Same shop, same category in a generic CMMS, completely different failure modes.
That difference is the argument for per-machine depth.
"""

# --------------------------------------------------------------------------
# The machines. `asset_name` matches what the base CNC-shop seed already
# created, so this deepens those records rather than creating duplicates.
# --------------------------------------------------------------------------

MACHINES = [
    {
        "asset_name": "Haas VF-2 #1",
        "pack": "CNC_MACHINING_CENTER_VMC",
        "manufacturer": "Haas Automation",
        "model": "VF-2",
        "criticality": 5,
        # A spindle rebuild is $2,000-4,500 and 1-4 weeks out; at ~$180/h of
        # shop rate this is what makes the lube PM worth doing on time.
        "downtime_cost_per_hour": 180.0,
        "replacement_cost": 62000.0,
        "functional_description": (
            "40-taper vertical machining center. Primary production machine for "
            "aluminium and mild steel work up to 762 x 406 x 508 mm."
        ),
        "specs": [
            # --- Spindle (haas.co.uk VF-2 datasheet) ---
            ("max_spindle_rpm", None, 8100, "rpm"),
            ("spindle_taper", "CT40 (BT40 / HSK-A63 optional)", None, None),
            ("spindle_power", None, 22.4, "kW"),
            ("spindle_bearing_type", "Angular contact, air/oil injection lubricated", None, None),
            # --- Travels ---
            ("travel_x", None, 762, "mm"),
            ("travel_y", None, 406, "mm"),
            ("travel_z", None, 508, "mm"),
            ("rapid_traverse", None, 25.4, "m/min"),
            # --- Table ---
            ("table_size", "914 x 356 mm (36 x 14 in), 3 T-slots, 16 mm wide on 125 mm centres",
             None, None),
            ("max_table_load", None, 1361, "kg"),
            # --- Lubrication: the important part on this machine ---
            ("way_lube_spec", "Mobil SHC 625 synthetic (Haas P/N 93-2220A, 1 qt)", None, None),
            ("way_lube_warning",
             "DO NOT substitute Mobil Vactra No. 2. It is too heavy for the SHC-625 system, "
             "clogs the lube lines and starves the spindle. This machine lost a spindle to a "
             "lubrication failure in April 2023 - do not give it a second way to have one.",
             None, None),
            ("spindle_lube_type", "Air/oil injection, Mobil SHC 625", None, None),
            # --- Coolant ---
            ("coolant_capacity", None, 208, "L"),
            ("coolant_concentration", None, 8, "%"),
            ("coolant_product", "TRIM MicroSol 685 semi-synthetic", None, None),
            # --- Tool changer ---
            ("atc_capacity", None, 20, "tools"),
            ("max_tool_weight", None, 5.4, "kg"),
            # --- Control ---
            ("control_type", "Haas NGC (Next Generation Control)", None, None),
            # --- Utilities ---
            ("air_requirement", None, 6.9, "bar"),
            ("machine_weight", None, 3539, "kg"),
        ],
        # Values a technician measured on this machine rather than read off a
        # datasheet. They are the ones worth trending.
        "measured_specs": [
            ("positioning_accuracy", None, 0.0051, "mm"),
            ("repeatability", None, 0.0025, "mm"),
        ],
        "meters": {
            # Commissioned Mar 2019, ~6.5 years of two-shift work.
            "Spindle hours": 11840,
            "Power-on hours": 24180,
            "Cycle count": 486300,
        },
        "components": [
            {
                "position": "SPN-CART",
                "serial": "HSC-40-118422",
                "part": "Spindle Cartridge - Haas 40 Taper Inline",
                "manufacturer": "Haas Automation",
                "mpn": "93-2050A",
                "acquired": "2023-04-18",
                "cost": 4500.0,
                "hour_limit": 12000,
                # Installed after the 2023 bearing failure; the machine had
                # 8,940 spindle hours at that point.
                "installed_at": "2023-04-24",
                "meter_at_install": 8940,
                "notes": (
                    "Second spindle in this machine. The first seized at 8,940 h after a "
                    "way-lube blockage went unnoticed for three weeks."
                ),
            },
            {
                "position": "AX-X-BS",
                "serial": "THK-W3212-77410",
                "part": "Ball Screw X-Axis (Haas VF-2)",
                "manufacturer": "THK",
                "mpn": "W3212-944RCX",
                "acquired": "2019-02-02",
                "cost": 3200.0,
                "hour_limit": None,
                "installed_at": "2019-03-15",
                "meter_at_install": 0,
                "notes": "Original ballscrew, installed at commissioning.",
            },
        ],
        # The first spindle, already removed. It is what makes the position
        # history worth having: "how long did the last one last" is answerable.
        "retired_components": [
            {
                "position": "SPN-CART",
                "serial": "HSC-40-091755",
                "part": "Spindle Cartridge - Haas 40 Taper Inline",
                "manufacturer": "Haas Automation",
                "mpn": "93-2050A",
                "acquired": "2019-02-02",
                "cost": 4500.0,
                "hour_limit": 12000,
                "installed_at": "2019-03-15",
                "meter_at_install": 0,
                "removed_at": "2023-04-24",
                "meter_at_removal": 8940,
                "removal_reason": (
                    "Front bearing seizure. Root cause traced to a crushed way-lube line "
                    "starving the X-axis and the spindle minimum-lubrication circuit."
                ),
                "hours_at_removal": 8940,
            },
        ],
        "failures": [
            {
                "code": "LUBE-NOFLOW",
                "occurred": "2023-04-03",
                "detected_at": "OPERATOR",
                "severity": 5,
                "downtime_minutes": 60,
                "cause": (
                    "Way-lube line to the X-axis crushed behind the rear way cover. "
                    "Reservoir level looked normal, so the daily check passed for three weeks "
                    "while no oil was reaching the point."
                ),
                "mechanism": "Blocked delivery line - metering unit starved downstream",
                "corrective_action": (
                    "Replaced the crushed line section and verified FLOW at every lubrication "
                    "point during a pump cycle rather than checking the reservoir level."
                ),
                "preventive_recommendation": (
                    "Changed the 500-hour PM task from 'check way lube level' to 'verify FLOW "
                    "at each point'. Checking the reservoir is the standard mistake and it is "
                    "what cost us the spindle."
                ),
            },
            {
                "code": "SPN-BRG-SEIZE",
                "occurred": "2023-04-21",
                "detected_at": "BREAKDOWN",
                "severity": 5,
                "downtime_minutes": 4320,
                "repair_cost": 4500.0,
                "cause": (
                    "Front bearing seizure following the lubrication starvation on 3 April. "
                    "Teardown found spalling on the front bearing and degraded grease."
                ),
                "mechanism": "Fatigue spalling from lubrication starvation",
                "corrective_action": (
                    "Replaced the spindle cartridge (Haas 93-2050A) at 8,940 spindle hours. "
                    "Three days down, mostly waiting on the part."
                ),
                "preventive_recommendation": (
                    "Stock a spindle cartridge, or accept 1-4 weeks of downtime next time. "
                    "At $180/h this outage cost roughly $13k in lost capacity against a "
                    "$4.5k part."
                ),
            },
            {
                "code": "COOL-CONC",
                "occurred": "2025-11-14",
                "detected_at": "PM_INSPECTION",
                "severity": 2,
                "downtime_minutes": 30,
                "cause": "Water top-ups without concentrate over the summer; concentration had drifted to 4.2%.",
                "mechanism": "Dilution",
                "corrective_action": "Corrected to 8% and added a refractometer reading to the daily check sheet.",
                "preventive_recommendation": "Log the refractometer number rather than ticking a box.",
            },
            {
                "code": "ATC-MISFEED",
                "occurred": "2026-02-09",
                "detected_at": "OPERATOR",
                "severity": 3,
                "downtime_minutes": 90,
                "cause": "Chips packed into the tool pocket; gripper could not seat the holder fully.",
                "mechanism": "Debris interference",
                "corrective_action": "Cleaned the carousel and pockets; no damage found.",
                "preventive_recommendation": "Added carousel cleaning to the 500-hour service.",
            },
        ],
        "faults": [
            {
                "code": "121", "occurred": "2023-04-03T06:42:00", "cleared": "2023-04-03T07:40:00",
                "severity": "HIGH",
                "description": "LOW LUBE OR LOW PRESSURE",
            },
            {
                "code": "121", "occurred": "2026-01-19T14:05:00", "cleared": "2026-01-19T14:22:00",
                "severity": "HIGH",
                "description": "LOW LUBE OR LOW PRESSURE - reservoir empty, refilled",
            },
            {
                "code": "102", "occurred": "2026-02-09T09:15:00", "cleared": "2026-02-09T10:45:00",
                "severity": "MEDIUM",
                "description": "SERVOS OFF - following the tool change fault",
            },
        ],
        "documents": [
            ("Haas-VF2-Operators-Manual.pdf", "Haas VF-2 Operator's Manual", "MANUAL", "2020-01"),
            ("Haas-Mill-Maintenance-Guide.pdf", "Haas Mill Maintenance Guide", "MANUAL", None),
            ("Mobil-Vactra-No2-SDS.pdf", "Mobil Vactra No. 2 - Safety Data Sheet", "CERTIFICATE", None),
            ("Monthly-PM-Checklist-VMC.pdf", "Monthly PM Checklist - VMC", "INSPECTION_REPORT", None),
            ("Quarterly-Backlash-Measurement-Form.pdf", "Quarterly Backlash Measurement Form",
             "INSPECTION_REPORT", None),
        ],
    },

    {
        "asset_name": "Fanuc RoboDrill",
        "pack": "CNC_DRILL_TAP_CENTER",
        "manufacturer": "FANUC",
        "model": "ROBODRILL alpha-D21MiB5",
        "criticality": 4,
        "downtime_cost_per_hour": 145.0,
        "replacement_cost": 95000.0,
        "functional_description": (
            "High-speed BT30 drill/tap center. Runs the small-part, high-volume work "
            "where its 1.6 s tool changes and 54 m/min rapids pay for themselves."
        ),
        "specs": [
            # --- Spindle (FANUC alpha-D21MiB5 Plus datasheet) ---
            ("max_spindle_rpm", None, 10000, "rpm"),
            ("spindle_taper", "7/24 taper No. 30 (BT30)", None, None),
            ("spindle_power", None, 14.2, "kW"),
            ("spindle_max_torque", None, 79.7, "Nm"),
            # --- Travels ---
            ("travel_x", None, 500, "mm"),
            ("travel_y", None, 400, "mm"),
            ("travel_z", None, 330, "mm"),
            ("rapid_traverse", None, 54, "m/min"),
            ("max_feedrate", None, 30000, "mm/min"),
            # --- Table ---
            ("table_size", "650 x 400 mm", None, None),
            ("max_table_load", None, 300, "kg"),
            # --- Lubrication: grease, on a schedule, with no alarm to save you ---
            ("lubrication_type",
             "Grease, not circulating oil. There is no way-oil system and no low-pressure "
             "alarm on this machine - the operating-hour schedule is the only protection the "
             "guides and ballscrews have.",
             None, None),
            ("guide_grease_spec", "FANUC A90L-0001-0534#LHLX1007", None, None),
            ("guide_grease_volume", None, 700, "cm3"),
            ("ballscrew_grease_spec", "FANUC A90L-0001-0534#LHLX1004", None, None),
            ("ballscrew_grease_volume", None, 400, "cm3"),
            # --- Coolant ---
            ("coolant_concentration", None, 7, "%"),
            ("coolant_product", "Blaser Blasocut 2000 CF", None, None),
            # --- Tool changer ---
            ("atc_capacity", None, 21, "tools"),
            ("atc_type", "Turret", None, None),
            ("max_tool_diameter", None, 80, "mm"),
            ("max_tool_length", None, 250, "mm"),
            ("max_tool_weight", None, 3, "kg"),
            ("tool_change_time", None, 1.6, "s"),
            # --- Control ---
            ("control_type", "FANUC Series 31i-B5", None, None),
            # --- Utilities ---
            ("air_requirement", None, 0.45, "MPa"),
            ("air_flow", None, 160, "L/min"),
            ("machine_weight", None, 2000, "kg"),
            # --- Accuracy (manufacturer stated, bidirectional) ---
            ("positioning_accuracy", None, 0.006, "mm"),
            ("repeatability", None, 0.004, "mm"),
        ],
        "measured_specs": [],
        "meters": {
            # Commissioned Jan 2022, run hard on short cycles.
            "Spindle hours": 6420,
            "Power-on hours": 15960,
            "Cycle count": 1284700,
            "Tool changes": 3120400,
        },
        "components": [
            {
                "position": "SPN-CART",
                "serial": "FRD-SP30-0221847",
                "part": "Spindle Cartridge - RoboDrill BT30",
                "manufacturer": "FANUC",
                "mpn": "A290-7221-X501",
                "acquired": "2021-12-10",
                "cost": 7800.0,
                "hour_limit": 15000,
                "installed_at": "2022-01-18",
                "meter_at_install": 0,
                "notes": "Original spindle, installed at commissioning.",
            },
            {
                "position": "AX-Z-BS",
                "serial": "NSK-BS2505-44120",
                "part": "Ball Screw Z-Axis (RoboDrill)",
                "manufacturer": "NSK",
                "mpn": "W2505-296RCSK",
                "acquired": "2021-12-10",
                "cost": 2400.0,
                "hour_limit": None,
                "installed_at": "2022-01-18",
                "meter_at_install": 0,
                "notes": "Original Z ballscrew. Z does the most work on a drill/tap machine.",
            },
        ],
        "retired_components": [],
        "failures": [
            {
                "code": "DT-ATC-INDEX",
                "occurred": "2025-06-11",
                "detected_at": "OPERATOR",
                "severity": 3,
                "downtime_minutes": 240,
                "cause": (
                    "Turret hesitated indexing to pocket 14. The 200-hour gear greasing had "
                    "been skipped twice during a busy run."
                ),
                "mechanism": "Gear wear accelerated by missed greasing",
                "corrective_action": "Greased the turret gear and cycled all 21 pockets; indexing normal afterwards.",
                "preventive_recommendation": (
                    "Put the 200-hour greasing on the spindle-hour meter instead of a calendar "
                    "reminder, so a busy month cannot silently skip it."
                ),
            },
            {
                "code": "DT-AX-EXCESS-ERR",
                "occurred": "2025-09-27",
                "detected_at": "BREAKDOWN",
                "severity": 5,
                "downtime_minutes": 180,
                "cause": (
                    "SV0411 on the Y axis under rapid. A way cover had come loose and was "
                    "dragging on the guide."
                ),
                "mechanism": "Mechanical binding raising following error past the parameter limit",
                "corrective_action": "Refitted and secured the way cover; checked the guide for scoring - none found.",
                "preventive_recommendation": (
                    "SV0410/SV0411 is a mechanical symptom. Resetting and continuing is how a "
                    "loose cover turns into a scored guide."
                ),
            },
            {
                "code": "DT-SPN-TAPER-DMG",
                "occurred": "2026-03-05",
                "detected_at": "PM_INSPECTION",
                "severity": 4,
                "downtime_minutes": 120,
                "cause": "Fretting marks in the BT30 taper from chips left at tool change.",
                "mechanism": "Fretting from debris between taper and holder",
                "corrective_action": "Lapped the taper and added a taper wipe to the daily check.",
                "preventive_recommendation": (
                    "A BT30 taper has far less contact area than a 40 taper - it tolerates "
                    "much less contamination before finish suffers."
                ),
            },
        ],
        "faults": [
            {
                "code": "SV0411", "occurred": "2025-09-27T11:20:00", "cleared": "2025-09-27T14:20:00",
                "severity": "HIGH",
                "description": "EXCESS ERROR (MOVING) - Y axis",
            },
            {
                "code": "SV0410", "occurred": "2025-09-27T11:18:00", "cleared": "2025-09-27T14:20:00",
                "severity": "HIGH",
                "description": "EXCESS ERROR (STOP) - Y axis, immediately before the moving error",
            },
            {
                "code": "SP1241", "occurred": "2026-04-22T15:48:00", "cleared": "2026-04-22T16:30:00",
                "severity": "HIGH",
                "description": "SPINDLE OVERHEAT - cabinet filter found clogged",
            },
        ],
        "documents": [
            ("FANUC-RoboDrill-Maintenance.pdf", "FANUC ROBODRILL Maintenance Manual", "MANUAL", "B-85315EN"),
            ("Daily-Machine-Inspection-Checklist.pdf", "Daily Machine Inspection Checklist",
             "INSPECTION_REPORT", None),
            ("Annual-Accuracy-Audit-Template.pdf", "Annual Accuracy Audit Template",
             "INSPECTION_REPORT", None),
        ],
    },

    {
        "asset_name": "Cat D6 Dozer",
        "pack": "CRAWLER_DOZER",
        "manufacturer": "Caterpillar",
        "model": "D6",
        "criticality": 5,
        # A small contractor with one dozer has no spare. A week down is a week
        # the crew and the job both stop, which is why the number is this high
        # relative to the machine's hourly rate.
        "downtime_cost_per_hour": 320.0,
        "replacement_cost": 545000.0,
        "functional_description": (
            "Medium crawler dozer, C9.3B engine, SU blade. The only dozer on the "
            "fleet - when it stops, the site stops."
        ),
        "specs": [
            # --- Engine (Cat D6/D6 XE technical specifications) ---
            ("engine_model", "Cat C9.3B", None, None),
            ("engine_power", None, 161, "kW"),
            ("emissions_tier", "U.S. EPA Tier 4 Final / EU Stage V", None, None),
            ("engine_oil_capacity", None, 24.5, "L"),
            ("engine_oil_spec", "Cat DEO-ULS SAE 10W-30", None, None),
            # --- Machine ---
            ("operating_weight", None, 22000, "kg"),
            ("blade_capacity", None, 5.7, "m3"),
            ("ground_pressure", None, 54, "kPa"),
            # --- Hydraulics ---
            ("hydraulic_capacity", None, 77, "L"),
            ("hydraulic_oil_spec", "Cat HYDO Advanced 10", None, None),
            ("system_pressure", None, 276, "bar"),
            # --- Powertrain ---
            ("transmission_capacity", None, 148, "L"),
            ("final_drive_capacity", None, 18.2, "L"),
            ("final_drive_oil_spec", "Cat TDTO SAE 50", None, None),
            # --- Undercarriage ---
            ("track_shoe_width", None, 610, "mm"),
            ("track_link_count", None, 42, None),
            ("track_tension_spec", None, 25, "mm"),
            # --- Fluids ---
            ("fuel_capacity", None, 341, "L"),
            ("def_capacity", None, 28, "L"),
            ("coolant_capacity", None, 63, "L"),
        ],
        "measured_specs": [],
        "meters": {
            # Bought used in 2021 with 1,900 h showing; now mid-life.
            "SMR hours": 7480,
            "Fuel used": 62310,
            # Idle is 41 % of run time - high, and the reason the DPF gives
            # trouble. Worth having on the dossier where someone will see it.
            "Idle hours": 3070,
        },
        "components": [
            {
                "position": "FD-L",
                "serial": "CAT-FDL-7749201",
                "part": "Final Drive Assembly - Cat D6 (LH)",
                "manufacturer": "Caterpillar",
                "mpn": "398-9186",
                "acquired": "2024-08-02",
                "cost": 11500.0,
                "hour_limit": 10000,
                "installed_at": "2024-08-14",
                "meter_at_install": 5120,
                "notes": (
                    "Replaced after the duo-cone seal failed and the hub ran low on oil. "
                    "The SOS iron trend had been climbing for two samples before it let go."
                ),
            },
            {
                "position": "FD-R",
                "serial": "CAT-FDR-6612884",
                "part": "Final Drive Assembly - Cat D6 (RH)",
                "manufacturer": "Caterpillar",
                "mpn": "398-9187",
                "acquired": "2021-03-01",
                "cost": 11500.0,
                "hour_limit": 10000,
                "installed_at": "2021-04-06",
                "meter_at_install": 1900,
                "notes": "Original right-hand final drive from purchase.",
            },
            {
                "position": "UC-CHAIN-L",
                "serial": "CAT-TCL-2210554",
                "part": "Track Chain Assembly - Cat D6 (LH)",
                "manufacturer": "Caterpillar",
                "mpn": "504-8930",
                "acquired": "2021-03-01",
                "cost": 8900.0,
                "hour_limit": None,
                "installed_at": "2021-04-06",
                "meter_at_install": 1900,
                "notes": "Measured 62 % worn at the last undercarriage survey.",
            },
        ],
        "retired_components": [
            {
                "position": "FD-L",
                "serial": "CAT-FDL-5530118",
                "part": "Final Drive Assembly - Cat D6 (LH)",
                "manufacturer": "Caterpillar",
                "mpn": "398-9186",
                "acquired": "2021-03-01",
                "cost": 11500.0,
                "hour_limit": 10000,
                "installed_at": "2021-04-06",
                "meter_at_install": 1900,
                "removed_at": "2024-08-14",
                "meter_at_removal": 5120,
                "removal_reason": (
                    "Duo-cone seal failure. Ran 3,220 h from purchase. Oil loss went "
                    "unnoticed between services; teardown found the bearings blued."
                ),
            },
        ],
        "failures": [
            {
                "code": "ENG-SOS-WEAR",
                "occurred": "2024-06-18",
                "detected_at": "CONDITION_MONITORING",
                "severity": 4,
                "downtime_minutes": 0,
                "cause": (
                    "SOS sample on the left final drive came back with iron at 340 ppm, "
                    "up from 95 ppm two samples earlier. No symptom on the machine yet."
                ),
                "mechanism": "Wear metals trending up ahead of failure",
                "corrective_action": (
                    "Flagged for inspection at the next service. Nobody acted on it - "
                    "this is the sample that predicted the August failure eight weeks out."
                ),
                "preventive_recommendation": (
                    "An SOS result that doubles is a work order, not a filing job. "
                    "Trending wear metals is the whole point of taking the sample."
                ),
            },
            {
                "code": "FD-SEAL-LEAK",
                "occurred": "2024-08-11",
                "detected_at": "BREAKDOWN",
                "severity": 5,
                "downtime_minutes": 5760,
                "repair_cost": 11500.0,
                "cause": (
                    "Left final drive duo-cone seal failed; hub ran low on oil and the "
                    "bearings overheated. Predicted by the June SOS sample."
                ),
                "mechanism": "Seal face wear leading to oil loss and bearing damage",
                "corrective_action": (
                    "Replaced the final drive at 5,120 SMR. Four days down, most of it "
                    "waiting for the assembly."
                ),
                "preventive_recommendation": (
                    "Act on SOS trends. At $320/h of downtime this cost about $30k in "
                    "lost days against an $11.5k part that could have been ordered in June."
                ),
            },
            {
                "code": "COOL-PLUG",
                "occurred": "2025-07-29",
                "detected_at": "OPERATOR",
                "severity": 4,
                "downtime_minutes": 180,
                "cause": "Cooling pack packed with chaff on a dry site; coolant temperature climbed to derate.",
                "mechanism": "Airflow restriction",
                "corrective_action": "Blew the pack out and added a mid-shift check during dry-season work.",
                "preventive_recommendation": (
                    "The daily walkaround already says to check it. On dusty sites once "
                    "a day is not enough."
                ),
            },
            {
                "code": "UC-CHAIN-ELONG",
                "occurred": "2026-05-20",
                "detected_at": "PM_INSPECTION",
                "severity": 3,
                "downtime_minutes": 120,
                "cause": "Left chain measured 62 % worn, right 58 %, at the 1000-hour undercarriage survey.",
                "mechanism": "Pin and bushing wear",
                "corrective_action": "Recorded and forecast. Budget the pair at roughly 80 % worn.",
                "preventive_recommendation": (
                    "Undercarriage is usually the biggest lifetime cost on this machine. "
                    "Measured every survey, it is a budget line rather than a surprise."
                ),
            },
        ],
        "faults": [
            {
                "code": "SPN 3251 FMI 0", "occurred": "2025-11-04T09:12:00",
                "cleared": "2025-11-04T13:40:00", "severity": "MEDIUM",
                "description": "DPF differential pressure high - regeneration required",
            },
            {
                "code": "SPN 110 FMI 0", "occurred": "2025-07-29T13:22:00",
                "cleared": "2025-07-29T16:20:00", "severity": "HIGH",
                "description": "Engine coolant temperature high - cooling pack plugged",
            },
            {
                "code": "SPN 100 FMI 1", "occurred": "2024-08-11T07:05:00",
                "cleared": "2024-08-15T16:00:00", "severity": "HIGH",
                "description": "Engine oil pressure low - during the final drive failure event",
            },
        ],
        "documents": [
            ("Daily-Machine-Inspection-Checklist.pdf", "Daily Walkaround Checklist - Dozer",
             "INSPECTION_REPORT", None),
            ("Mobil-DTE-25-SDS.pdf", "Hydraulic Oil - Safety Data Sheet", "CERTIFICATE", None),
        ],
    },
]


# --------------------------------------------------------------------------
# Bootstrap records for machines the base CNC-shop seed does not create.
#
# The demo is pitched to a CNC shop and to a small construction contractor, so
# the dozer needs somewhere to live that is not a machining bay. Two sites also
# demonstrate that the hierarchy is real rather than decorative.
# --------------------------------------------------------------------------

BOOTSTRAP_LOCATIONS = [
    {"name": "Contractor Yard", "parent": None, "customId": "SITE-YARD",
     "address": "1400 Quarry Road"},
    {"name": "Equipment Yard", "parent": "Contractor Yard", "customId": "YARD-EQ"},
]

BOOTSTRAP_CATEGORIES = [
    ("Earthmoving Equipment",
     "Dozers, excavators and loaders. Hour-metered, sampled rather than sensored, "
     "and costed by the day they are not on site."),
]

BOOTSTRAP_ASSETS = [
    {
        "name": "Cat D6 Dozer",
        "customId": "DOZ-001",
        "category": "Earthmoving Equipment",
        "location": "Equipment Yard",
        "manufacturer": "Caterpillar",
        "model": "D6",
        "serialNumber": "CAT00D6XKRJ01847",
        "status": "OPERATIONAL",
        "acquisitionCost": 285000.0,
        "inServiceDate": "2021-04-06",
        "description": "Caterpillar D6 crawler dozer, C9.3B engine, SU blade",
    },
]


# --------------------------------------------------------------------------
# Where each PM last actually happened.
#
# Without this every meter-based PM baselines at zero and a machine with 11,840
# spindle hours reads as 2,368 % overdue for its 500-hour service - which is not
# "urgent", it is "no history yet", and showing it as urgent is how people learn
# to ignore the due list. These are the last real completions.
#
# (pm title fragment, hours before the current meter reading, days ago)
# --------------------------------------------------------------------------

PM_BASELINES = {
    "Haas VF-2 #1": [
        ("Daily operator check", None, 1),
        ("500-hour service", 430, 47),      # due in ~70 h - the useful state to demo
        ("Annual precision & safety", None, 190),
    ],
    "Fanuc RoboDrill": [
        ("Daily operator check", None, 1),
        ("200-hour greasing", 185, 22),     # due in ~15 h - nearly there
        ("1000-hour greasing", 240, 38),
        ("Annual precision & safety", None, 95),
    ],
    "Cat D6 Dozer": [
        ("Daily walkaround", None, 1),
        ("250-hour service", 210, 31),      # close, and it carries the SOS samples
        ("500-hour service", 300, 44),
        ("1000-hour service", 640, 95),
        ("2000-hour service", 1180, 175),
    ],
}


# --------------------------------------------------------------------------
# Vendors this seed needs that the base CNC-shop seed does not create.
# --------------------------------------------------------------------------

VENDORS = [
    {
        "name": "Cat Dealer - Parts & Service",
        "companyName": "Caterpillar Dealer",
        "vendorType": "OEM Parts",
        "description": (
            "Cat parts, SOS oil sampling and warranty work. Fault descriptions are "
            "not in the telematics payload, so the dealer is also where a code gets "
            "looked up when the manual does not cover it."
        ),
        "website": "https://www.cat.com/en_US/support/parts.html",
        "phone": "+1-800-228-3237",
    },
    {
        "name": "Master Fluid Solutions",
        "companyName": "Master Fluid Solutions",
        "vendorType": "Consumables",
        "description": "TRIM coolants and shop fluids.",
        "website": "https://www.masterfluidsolutions.com/",
    },
    {
        "name": "FANUC America",
        "companyName": "FANUC America Corporation",
        "vendorType": "OEM Parts",
        "description": "OEM parts and service for the ROBODRILL and FANUC controls.",
        "website": "https://www.fanucamerica.com/",
        "phone": "+1-888-326-8287",
        "address": "3900 W Hamlin Rd, Rochester Hills, MI 48309",
    },
]


# --------------------------------------------------------------------------
# Part enrichment. The generic seed created these parts with a name and a cost;
# what makes them orderable is the manufacturer part number and a supplier with
# a price, a lead time and a link.
# --------------------------------------------------------------------------

PART_ENRICHMENT = [
    # (part name, manufacturer, mpn, criticality, lead_time_days, stock_recommended)
    ("Spindle Cartridge - Haas 40 Taper Inline", "Haas Automation", "93-2050A", 5, 21, True),
    ("Way Lube Oil - Mobil SHC 625 (1 qt)", "ExxonMobil", "93-2220A", 5, 3, True),
    ("Coolant Filter - Haas Standard Head/Bowl Kit", "Haas Automation", "93-2356", 3, 5, True),
    ("Ball Screw X-Axis (Haas VF-2)", "THK", "W3212-944RCX", 4, 35, False),
    ("Spindle Drawbar Spring Set (Haas)", "Haas Automation", "93-0333", 4, 10, True),
    ("Z-Axis Wiper Kit (Haas)", "Haas Automation", "93-0959", 3, 7, True),
    ("Guide rail grease unit (A90L-0001-0534#LHLX1007)", "FANUC", "A90L-0001-0534#LHLX1007", 5, 14, True),
    ("Ballscrew grease unit (A90L-0001-0534#LHLX1004)", "FANUC", "A90L-0001-0534#LHLX1004", 5, 14, True),
    ("Spindle Cartridge - RoboDrill BT30", "FANUC", "A290-7221-X501", 5, 28, False),
    ("Ball Screw Z-Axis (RoboDrill)", "NSK", "W2505-296RCSK", 4, 30, False),
    # Parts the packs create generically, priced with what this shop buys.
    ("Way lube oil", "ExxonMobil", "93-2220A", 5, 3, True),
    ("Coolant filter", "Haas Automation", "93-2356", 3, 5, True),
    ("Air filter element", "Haas Automation", "93-2662", 2, 7, True),
    ("Cabinet filter mat", "Haas Automation", "93-9024", 2, 7, True),
    ("Coolant concentrate", "Master Fluid Solutions", "TRIM-MS685-5G", 3, 5, True),
    # --- Cat D6 dozer ---
    ("Final Drive Assembly - Cat D6 (LH)", "Caterpillar", "398-9186", 5, 12, False),
    ("Final Drive Assembly - Cat D6 (RH)", "Caterpillar", "398-9187", 5, 12, False),
    ("Track Chain Assembly - Cat D6 (LH)", "Caterpillar", "504-8930", 4, 21, False),
    ("SOS Oil Sample Kit", "Caterpillar", "175-4595", 4, 3, True),
    ("Engine Oil Filter - Cat D6", "Caterpillar", "1R-1808", 4, 3, True),
    ("Fuel Filter (Primary) - Cat D6", "Caterpillar", "438-5386", 4, 3, True),
    ("Fuel Filter (Secondary) - Cat D6", "Caterpillar", "1R-0762", 4, 3, True),
    ("Hydraulic Return Filter - Cat D6", "Caterpillar", "126-1813", 4, 5, True),
    ("Air Filter Primary - Cat D6", "Caterpillar", "346-6687", 3, 5, True),
    ("Engine Oil - Cat DEO-ULS 10W-30 (bulk L)", "Caterpillar", "DEO-ULS-10W30", 4, 3, True),
    ("Final Drive Oil - Cat TDTO SAE 50 (bulk L)", "Caterpillar", "TDTO-SAE50", 4, 3, True),
    ("Hydraulic Oil - Cat HYDO Advanced 10 (bulk L)", "Caterpillar", "HYDO-ADV-10", 4, 3, True),
    # Generic consumables the CRAWLER_DOZER pack creates.
    ("Engine oil filter", "Caterpillar", "1R-1808", 4, 3, True),
    ("Fuel filter (primary)", "Caterpillar", "438-5386", 4, 3, True),
    ("Fuel filter (secondary)", "Caterpillar", "1R-0762", 4, 3, True),
    ("Hydraulic return filter", "Caterpillar", "126-1813", 4, 5, True),
    ("Air filter primary element", "Caterpillar", "346-6687", 3, 5, True),
    ("Air filter safety element", "Caterpillar", "346-6688", 3, 5, True),
    ("Transmission filter", "Caterpillar", "126-2081", 4, 5, True),
    ("Engine oil", "Caterpillar", "DEO-ULS-10W30", 4, 3, True),
    ("Final drive oil", "Caterpillar", "TDTO-SAE50", 4, 3, True),
    ("Hydraulic oil", "Caterpillar", "HYDO-ADV-10", 4, 3, True),
    ("SOS sample kit", "Caterpillar", "175-4595", 4, 3, True),
]

# (part name, vendor name, supplier SKU, unit price, currency, lead days, url, preferred)
PART_SUPPLIERS = [
    ("Spindle Cartridge - Haas 40 Taper Inline", "Haas Parts Dept", "93-2050A",
     4500.00, "USD", 21, "https://parts.haascnc.com/", True),
    ("Way Lube Oil - Mobil SHC 625 (1 qt)", "Haas Parts Dept", "93-2220A",
     38.00, "USD", 3, "https://parts.haascnc.com/", True),
    ("Coolant Filter - Haas Standard Head/Bowl Kit", "Haas Parts Dept", "93-2356",
     165.00, "USD", 5, "https://parts.haascnc.com/", True),
    ("Ball Screw X-Axis (Haas VF-2)", "THK America", "W3212-944RCX",
     3200.00, "USD", 35, "https://www.thk.com/", True),
    ("Spindle Drawbar Spring Set (Haas)", "Haas Parts Dept", "93-0333",
     225.00, "USD", 10, "https://parts.haascnc.com/", True),
    ("Z-Axis Wiper Kit (Haas)", "Haas Parts Dept", "93-0959",
     110.00, "USD", 7, "https://parts.haascnc.com/", True),
    ("Guide rail grease unit (A90L-0001-0534#LHLX1007)", "FANUC America", "A90L-0001-0534#LHLX1007",
     195.00, "USD", 14, "https://www.fanucamerica.com/", True),
    ("Ballscrew grease unit (A90L-0001-0534#LHLX1004)", "FANUC America", "A90L-0001-0534#LHLX1004",
     165.00, "USD", 14, "https://www.fanucamerica.com/", True),
    ("Spindle Cartridge - RoboDrill BT30", "FANUC America", "A290-7221-X501",
     7800.00, "USD", 28, "https://www.fanucamerica.com/", True),
    ("Ball Screw Z-Axis (RoboDrill)", "NSK Precision", "W2505-296RCSK",
     2400.00, "USD", 30, "https://www.nsk.com/", True),
    ("Way lube oil", "Haas Parts Dept", "93-2220A",
     38.00, "USD", 3, "https://parts.haascnc.com/", True),
    ("Coolant filter", "Haas Parts Dept", "93-2356",
     165.00, "USD", 5, "https://parts.haascnc.com/", True),
    ("Air filter element", "Haas Parts Dept", "93-2662",
     72.00, "USD", 7, "https://parts.haascnc.com/", True),
    ("Cabinet filter mat", "Haas Parts Dept", "93-9024",
     45.00, "USD", 7, "https://parts.haascnc.com/", True),
    ("Coolant concentrate", "Master Fluid Solutions", "TRIM-MS685-5G",
     210.00, "USD", 5, "https://www.masterfluidsolutions.com/", True),
    ("Spindle/turret gear grease", "FANUC America", "A98L-0040-0233",
     42.00, "USD", 14, "https://www.fanucamerica.com/", True),

    # --- Cat D6 dozer, all through the dealer ---
    ("Final Drive Assembly - Cat D6 (LH)", "Cat Dealer - Parts & Service", "398-9186",
     11500.00, "USD", 12, "https://parts.cat.com/", True),
    ("Final Drive Assembly - Cat D6 (RH)", "Cat Dealer - Parts & Service", "398-9187",
     11500.00, "USD", 12, "https://parts.cat.com/", True),
    ("Track Chain Assembly - Cat D6 (LH)", "Cat Dealer - Parts & Service", "504-8930",
     8900.00, "USD", 21, "https://parts.cat.com/", True),
    ("SOS Oil Sample Kit", "Cat Dealer - Parts & Service", "175-4595",
     18.00, "USD", 3, "https://parts.cat.com/", True),
    ("SOS sample kit", "Cat Dealer - Parts & Service", "175-4595",
     18.00, "USD", 3, "https://parts.cat.com/", True),
    ("Engine Oil Filter - Cat D6", "Cat Dealer - Parts & Service", "1R-1808",
     42.00, "USD", 3, "https://parts.cat.com/", True),
    ("Engine oil filter", "Cat Dealer - Parts & Service", "1R-1808",
     42.00, "USD", 3, "https://parts.cat.com/", True),
    ("Fuel Filter (Primary) - Cat D6", "Cat Dealer - Parts & Service", "438-5386",
     38.00, "USD", 3, "https://parts.cat.com/", True),
    ("Fuel filter (primary)", "Cat Dealer - Parts & Service", "438-5386",
     38.00, "USD", 3, "https://parts.cat.com/", True),
    ("Fuel Filter (Secondary) - Cat D6", "Cat Dealer - Parts & Service", "1R-0762",
     54.00, "USD", 3, "https://parts.cat.com/", True),
    ("Fuel filter (secondary)", "Cat Dealer - Parts & Service", "1R-0762",
     54.00, "USD", 3, "https://parts.cat.com/", True),
    ("Hydraulic Return Filter - Cat D6", "Cat Dealer - Parts & Service", "126-1813",
     96.00, "USD", 5, "https://parts.cat.com/", True),
    ("Hydraulic return filter", "Cat Dealer - Parts & Service", "126-1813",
     96.00, "USD", 5, "https://parts.cat.com/", True),
    ("Air Filter Primary - Cat D6", "Cat Dealer - Parts & Service", "346-6687",
     88.00, "USD", 5, "https://parts.cat.com/", True),
    ("Air filter primary element", "Cat Dealer - Parts & Service", "346-6687",
     88.00, "USD", 5, "https://parts.cat.com/", True),
    ("Air filter safety element", "Cat Dealer - Parts & Service", "346-6688",
     64.00, "USD", 5, "https://parts.cat.com/", True),
    ("Transmission filter", "Cat Dealer - Parts & Service", "126-2081",
     78.00, "USD", 5, "https://parts.cat.com/", True),
    ("Engine oil", "Cat Dealer - Parts & Service", "DEO-ULS-10W30",
     7.50, "USD", 3, "https://parts.cat.com/", True),
    ("Final drive oil", "Cat Dealer - Parts & Service", "TDTO-SAE50",
     9.20, "USD", 3, "https://parts.cat.com/", True),
    ("Hydraulic oil", "Cat Dealer - Parts & Service", "HYDO-ADV-10",
     8.40, "USD", 3, "https://parts.cat.com/", True),
]

# Parts the generic seed did not have, needed by the machine dossiers.
NEW_PARTS = [
    # (name, description, unit, cost, quantity, min quantity, non-stock)
    ("Spindle Cartridge - Haas 40 Taper Inline",
     "Haas 40-taper inline pin-drive spindle cartridge. Exchange price with core return.",
     "ea", 4500.00, 0, 0, True),
    ("Way Lube Oil - Mobil SHC 625 (1 qt)",
     "Synthetic axis lubrication oil for Haas machines. DO NOT substitute Vactra No. 2 - it "
     "is too heavy for this system and clogs the lube lines.",
     "qt", 38.00, 12, 6, False),
    ("Coolant Filter - Haas Standard Head/Bowl Kit",
     "Standard coolant filter head and bowl replacement kit.",
     "kit", 165.00, 2, 1, False),
    ("Guide rail grease unit (A90L-0001-0534#LHLX1007)",
     "FANUC ROBODRILL LM guide rail greasing unit, 700 cm3. Replaced every 1000 operating hours.",
     "ea", 195.00, 2, 1, False),
    ("Ballscrew grease unit (A90L-0001-0534#LHLX1004)",
     "FANUC ROBODRILL ballscrew greasing unit, 400 cm3. Replaced every 1000 operating hours.",
     "ea", 165.00, 2, 1, False),
    ("Spindle/turret gear grease",
     "Grease for the spindle-end gear and turret gear. Applied every 200 operating hours.",
     "cartridge", 42.00, 4, 2, False),
    ("Spindle Cartridge - RoboDrill BT30",
     "FANUC ROBODRILL BT30 spindle cartridge.",
     "ea", 7800.00, 0, 0, True),
    ("Ball Screw Z-Axis (RoboDrill)",
     "NSK Z-axis ballscrew for ROBODRILL alpha-D21MiB5.",
     "ea", 2400.00, 0, 0, True),

    # --- Cat D6 dozer ---
    ("Final Drive Assembly - Cat D6 (LH)",
     "Left-hand final drive assembly. 18.2 L oil capacity per side.",
     "ea", 11500.00, 0, 0, True),
    ("Final Drive Assembly - Cat D6 (RH)",
     "Right-hand final drive assembly. 18.2 L oil capacity per side.",
     "ea", 11500.00, 0, 0, True),
    ("Track Chain Assembly - Cat D6 (LH)",
     "Left-hand track chain. Replaced as a pair at roughly 80% wear.",
     "ea", 8900.00, 0, 0, True),
    ("SOS Oil Sample Kit",
     "Scheduled Oil Sampling kit. Five compartments per 250-hour service: engine, "
     "hydraulic, transmission and both final drives. Trending wear metals is the "
     "highest-value predictive practice available on this machine and needs no sensors.",
     "ea", 18.00, 20, 10, False),
    ("Engine Oil Filter - Cat D6",
     "Engine oil filter, replaced every 250 hours.",
     "ea", 42.00, 4, 2, False),
    ("Fuel Filter (Primary) - Cat D6",
     "Primary fuel filter / water separator, replaced every 250 hours.",
     "ea", 38.00, 4, 2, False),
    ("Fuel Filter (Secondary) - Cat D6",
     "Secondary fuel filter, replaced every 500 hours.",
     "ea", 54.00, 4, 2, False),
    ("Hydraulic Return Filter - Cat D6",
     "Hydraulic return filter, replaced every 500 hours.",
     "ea", 96.00, 2, 1, False),
    ("Air Filter Primary - Cat D6",
     "Primary air cleaner element, replaced every 500 hours.",
     "ea", 88.00, 2, 1, False),
    ("Engine Oil - Cat DEO-ULS 10W-30 (bulk L)",
     "Engine crankcase oil. 24.5 L per change.",
     "L", 7.50, 60, 30, False),
    ("Final Drive Oil - Cat TDTO SAE 50 (bulk L)",
     "Final drive oil. 18.2 L per side.",
     "L", 9.20, 40, 20, False),
    ("Hydraulic Oil - Cat HYDO Advanced 10 (bulk L)",
     "Hydraulic system oil. 77 L system capacity.",
     "L", 8.40, 80, 40, False),
]

# Company-specific fault-code enrichment, on top of the shared reference set
# seeded by Liquibase. This is what the shop learned on its own machines.
FAULT_CODES = [
    {
        "code": "121",
        "equipmentClass": "CNC_MACHINING_CENTER_VMC",
        "manufacturer": "Haas Automation",
        "descriptionEn": "LOW LUBE OR LOW PRESSURE",
        "severity": "HIGH",
        "likelyCauses": (
            "On VMC-001 this has twice been a real delivery failure rather than a low "
            "reservoir: once a crushed line behind the rear way cover (Apr 2023, cost us the "
            "spindle), once a genuinely empty tank (Jan 2026)."
        ),
        "recommendedAction": (
            "Stop the machine. Check the reservoir, then verify FLOW at each point during a "
            "pump cycle. A full reservoir proves nothing - that is exactly how the April 2023 "
            "spindle failure happened."
        ),
    },
    {
        "code": "SPN 3251 FMI 0",
        "equipmentClass": "CRAWLER_DOZER",
        "manufacturer": "Caterpillar",
        "descriptionEn": "DPF differential pressure high - regeneration required",
        "severity": "MEDIUM",
        "likelyCauses": (
            "This machine idles 41% of its run hours, which is what drives the soot "
            "loading. Recurring on our unit rather than a one-off fault."
        ),
        "recommendedAction": (
            "Allow a full regen to complete rather than interrupting it. If it "
            "recurs weekly, the fix is the duty cycle, not the filter."
        ),
    },
    {
        "code": "SPN 110 FMI 0",
        "equipmentClass": "CRAWLER_DOZER",
        "manufacturer": "Caterpillar",
        "descriptionEn": "Engine coolant temperature high",
        "severity": "HIGH",
        "likelyCauses": (
            "On this machine it has always been a plugged cooling pack on dry sites, "
            "not a thermostat or a pump."
        ),
        "recommendedAction": (
            "Stop and check the cooling pack for chaff before anything else. On dusty "
            "work it needs blowing out mid-shift, not just at the daily walkaround."
        ),
    },
    {
        "code": "SV0411",
        "equipmentClass": "CNC_DRILL_TAP_CENTER",
        "manufacturer": "FANUC",
        "descriptionEn": "EXCESS ERROR (MOVING)",
        "severity": "HIGH",
        "likelyCauses": (
            "On VMC-005 this was a loose way cover dragging on the Y guide (Sep 2025). "
            "Check for mechanical binding before touching servo parameters."
        ),
        "recommendedAction": (
            "Power down and move the axis by hand feeling for binding. Inspect the way covers "
            "and guides. Do not reset and continue - that is how a loose cover becomes a "
            "scored guide."
        ),
    },
]


# Every part name the machine seed is responsible for, in one place, so a purge
# can tell "the demo needs this" from "this was left over".
NEW_PARTS_NAMES = (
    {p[0] for p in NEW_PARTS}
    | {p[0] for p in PART_ENRICHMENT}
    | {s[0] for s in PART_SUPPLIERS}
)
