# -*- coding: utf-8 -*-
"""Structured demo data for the CNC machine shop, transcribed from
docs/cnc-shop-demo-data.md. Everything is keyed by human-readable name;
the seed script resolves these to API ids at runtime."""

# ---------------------------------------------------------------------------
# 1. LOCATIONS  (parent referenced by name; root has parent=None)
# ---------------------------------------------------------------------------
LOCATIONS = [
    {"name": "Precision CNC Shop", "customId": "PLANT-01", "parent": None,
     "address": "4500 Industrial Blvd, Houston, TX 77040"},
    {"name": "Machine Shop Floor", "customId": "SHOP-FLOOR", "parent": "Precision CNC Shop"},
    {"name": "VMC Area", "customId": "SHOP-VMC", "parent": "Machine Shop Floor"},
    {"name": "Lathe Area", "customId": "SHOP-LATHE", "parent": "Machine Shop Floor"},
    {"name": "5-Axis Cell", "customId": "SHOP-5AX", "parent": "Machine Shop Floor"},
    {"name": "Swiss Cell", "customId": "SHOP-SWISS", "parent": "Machine Shop Floor"},
    {"name": "Grinding Area", "customId": "SHOP-GRIND", "parent": "Machine Shop Floor"},
    {"name": "EDM Department", "customId": "SHOP-EDM", "parent": "Machine Shop Floor"},
    {"name": "Tool Crib", "customId": "TOOL-CRIB", "parent": "Precision CNC Shop"},
    {"name": "Quality Lab / CMM Room", "customId": "QC-LAB", "parent": "Precision CNC Shop"},
    {"name": "Coolant Mixing Area", "customId": "MAINT-COOL", "parent": "Precision CNC Shop"},
    {"name": "Maintenance Shop", "customId": "MAINT-SHOP", "parent": "Precision CNC Shop"},
    {"name": "Shipping & Receiving", "customId": "SHIP-REC", "parent": "Precision CNC Shop"},
    {"name": "Raw Material Storage", "customId": "MAT-STORE", "parent": "Precision CNC Shop"},
    {"name": "Compressor Room", "customId": "UTIL-COMP", "parent": "Precision CNC Shop"},
    {"name": "Electrical Room", "customId": "UTIL-ELEC", "parent": "Precision CNC Shop"},
    {"name": "Office / Programming", "customId": "OFFICE", "parent": "Precision CNC Shop"},
    {"name": "Loading Dock", "customId": "DOCK", "parent": "Precision CNC Shop"},
]

# ---------------------------------------------------------------------------
# 2. CATEGORIES
# ---------------------------------------------------------------------------
ASSET_CATEGORIES = [
    ("CNC Vertical Machining Center", "VMCs and milling machines"),
    ("CNC Turning Center", "CNC lathes and turning centers"),
    ("CNC 5-Axis Machine", "Multi-axis machining centers"),
    ("Swiss-Type Lathe", "Swiss-type CNC lathes"),
    ("Grinding Machine", "Surface and cylindrical grinders"),
    ("EDM Machine", "Wire and sinker EDM"),
    ("Inspection Equipment", "CMMs, measurement tools"),
    ("Support Equipment", "Saws, conveyors, coolant systems"),
    ("Facility Equipment", "Compressors, HVAC, material handling"),
]

PART_CATEGORIES = [
    ("Spindle Components", "Bearings, drawbar springs, spindle seals"),
    ("Ball Screws & Linear Guides", "Ball screws, nuts, linear guide blocks/rails"),
    ("Way Covers & Enclosure", "Telescoping covers, wipers, door switches"),
    ("Tool Holders", "CAT40, BT40, collet chucks, retention knobs"),
    ("Collets", "ER32, ER40 collets"),
    ("Cutting Tools - Milling", "End mills, face mills, indexable inserts"),
    ("Cutting Tools - Turning", "Turning inserts, grooving, threading"),
    ("Cutting Tools - Drilling", "Carbide drills, indexable drills, center drills"),
    ("Filters & Consumables", "Coolant filters, oil mist filters, air filters"),
    ("Fluids & Lubricants", "Coolant, way lube, hydraulic oil, spindle oil"),
    ("Electrical & Servo", "Servo motors, drives, encoders, sensors"),
    ("Belts & Seals", "Drive belts, wiper seals, O-rings"),
]

METER_CATEGORIES = [
    ("Runtime", "Operational hours, cycle counts"),
    ("Vibration", "Vibration levels and spectral data"),
    ("Fluid Condition", "Coolant, oil, and hydraulic fluid readings"),
    ("Temperature", "Bearing, oil, and spindle temperatures"),
    ("Precision", "Backlash, runout, positioning accuracy"),
    ("Pressure", "Air, hydraulic, differential pressure"),
]

WO_CATEGORIES = [
    ("Preventive", "Scheduled preventive maintenance"),
    ("Corrective", "Reactive repairs and fixes"),
    ("Emergency", "Urgent breakdown repairs"),
    ("Inspection", "Audits, measurements, quality checks"),
    ("Installation", "New equipment or component installation"),
    ("Calibration", "Precision calibration and alignment"),
    ("Safety", "Safety-related inspections and repairs"),
]

# ---------------------------------------------------------------------------
# 3. VENDORS
# ---------------------------------------------------------------------------
VENDORS = [
    {"name": "Haas Parts Dept", "companyName": "Haas Automation", "vendorType": "OEM Parts",
     "phone": "(805) 278-1800", "email": "parts@haascnc.com", "website": "haascnc.com"},
    {"name": "DMG MORI Parts", "companyName": "DMG MORI USA", "vendorType": "OEM Parts",
     "phone": "(847) 593-5400", "email": "parts@dmgmori.com", "website": "dmgmori.com"},
    {"name": "Kennametal", "companyName": "Kennametal Inc.", "vendorType": "Cutting Tools",
     "phone": "(800) 446-7738", "email": "orders@kennametal.com", "website": "kennametal.com"},
    {"name": "Sandvik Coromant", "companyName": "Sandvik Coromant", "vendorType": "Cutting Tools",
     "phone": "(800) 726-3845", "email": "us.coromant@sandvik.com", "website": "sandvik.coromant.com"},
    {"name": "Iscar USA", "companyName": "Iscar Metals Inc.", "vendorType": "Cutting Tools",
     "phone": "(817) 258-3200", "email": "info@iscarusa.com", "website": "iscar.com"},
    {"name": "Techniks / Parlec", "companyName": "Techniks Inc.", "vendorType": "Toolholding",
     "phone": "(800) 597-3921", "email": "sales@techniks.com", "website": "techniks.com"},
    {"name": "Rego-Fix", "companyName": "Rego-Fix AG", "vendorType": "Collets & Holders",
     "phone": "(317) 870-5959", "email": "info@regofix.com", "website": "rego-fix.com"},
    {"name": "THK America", "companyName": "THK Co., Ltd.", "vendorType": "Linear Motion",
     "phone": "(847) 310-1111", "email": "sales@thk.com", "website": "thk.com"},
    {"name": "NSK Precision", "companyName": "NSK Ltd.", "vendorType": "Bearings",
     "phone": "(888) 675-2675", "email": "info@nskprecision.com", "website": "nskamericas.com"},
    {"name": "Master Fluid Solutions", "companyName": "Master Fluid Solutions", "vendorType": "Coolant",
     "phone": "(800) 537-3365", "email": "info@masterfluid.com", "website": "masterfluid.com"},
    {"name": "Atlas Copco", "companyName": "Atlas Copco USA", "vendorType": "Compressed Air",
     "phone": "(800) 232-3234", "email": "info@atlascopco.com", "website": "atlascopco.com"},
    {"name": "Donaldson Filtration", "companyName": "Donaldson Company", "vendorType": "Filtration",
     "phone": "(800) 365-1331", "email": "shop@donaldson.com", "website": "donaldson.com"},
    {"name": "GTI Spindle Technology", "companyName": "GTI Spindle Technology", "vendorType": "Spindle Repair",
     "phone": "(603) 669-5993", "email": "service@gtispindle.com", "website": "gtispindle.com"},
    {"name": "Precision Machine Service", "companyName": "Precision Machine Service LLC",
     "vendorType": "Machine Repair", "phone": "(713) 555-0142",
     "email": "service@precisionmachinesvs.com", "website": ""},
    # Schaeffler & a few referenced part vendors not in vendor table -> add minimal entries
    {"name": "Schaeffler Group", "companyName": "Schaeffler Group USA", "vendorType": "Bearings",
     "phone": "", "email": "", "website": "schaeffler.com"},
    {"name": "ExxonMobil", "companyName": "ExxonMobil", "vendorType": "Lubricants",
     "phone": "", "email": "", "website": "mobil.com"},
    {"name": "Omron Industrial", "companyName": "Omron Automation", "vendorType": "Electrical",
     "phone": "", "email": "", "website": "omron.com"},
    {"name": "Heidenhain", "companyName": "Heidenhain Corporation", "vendorType": "Encoders",
     "phone": "", "email": "", "website": "heidenhain.us"},
    {"name": "Eriez", "companyName": "Eriez Manufacturing", "vendorType": "Filtration",
     "phone": "", "email": "", "website": "eriez.com"},
    {"name": "Donaldson", "companyName": "Donaldson Company", "vendorType": "Filtration",
     "phone": "", "email": "", "website": "donaldson.com"},
    {"name": "Gates", "companyName": "Gates Corporation", "vendorType": "Belts",
     "phone": "", "email": "", "website": "gates.com"},
    {"name": "Parker Hannifin", "companyName": "Parker Hannifin", "vendorType": "Pneumatics",
     "phone": "", "email": "", "website": "parker.com"},
    {"name": "NACHI America", "companyName": "NACHI America Inc.", "vendorType": "Bearings",
     "phone": "", "email": "", "website": "nachiamerica.com"},
    {"name": "Lyndex-Nikken", "companyName": "Lyndex-Nikken", "vendorType": "Toolholding",
     "phone": "", "email": "", "website": "lyndexnikken.com"},
    {"name": "SGS Tool", "companyName": "SGS Tool Company", "vendorType": "Cutting Tools",
     "phone": "", "email": "", "website": "kyocera-sgstool.com"},
    {"name": "Harvey / Helical", "companyName": "Helical Solutions", "vendorType": "Cutting Tools",
     "phone": "", "email": "", "website": "helicaltool.com"},
    {"name": "Mitsubishi Materials", "companyName": "Mitsubishi Materials", "vendorType": "Cutting Tools",
     "phone": "", "email": "", "website": "mitsubishicarbide.com"},
    {"name": "Chicago-Latrobe", "companyName": "Chicago-Latrobe", "vendorType": "Cutting Tools",
     "phone": "", "email": "", "website": "chicago-latrobe.com"},
    {"name": "Graymills", "companyName": "Graymills Corporation", "vendorType": "Pumps",
     "phone": "", "email": "", "website": "graymills.com"},
    {"name": "Habor", "companyName": "Habor Precision", "vendorType": "Chillers",
     "phone": "", "email": "", "website": "habor.com"},
]

# ---------------------------------------------------------------------------
# 4. USERS  (role -> built-in role name; will be resolved to role id)
# ---------------------------------------------------------------------------
USERS = [
    {"firstName": "Mike", "lastName": "Torres", "email": "m.torres@cncshop.com",
     "jobTitle": "Shop Manager", "role": "Administrator", "rate": 65, "location": "Office / Programming"},
    {"firstName": "Dave", "lastName": "Kowalski", "email": "d.kowalski@cncshop.com",
     "jobTitle": "Maintenance Manager", "role": "Administrator", "rate": 55, "location": "Maintenance Shop"},
    {"firstName": "Carlos", "lastName": "Ruiz", "email": "c.ruiz@cncshop.com",
     "jobTitle": "Lead Machinist (Day)", "role": "Limited Administrator", "rate": 48, "location": "VMC Area"},
    {"firstName": "Jim", "lastName": "Patterson", "email": "j.patterson@cncshop.com",
     "jobTitle": "Lead Machinist (Swing)", "role": "Limited Administrator", "rate": 48, "location": "Lathe Area"},
    {"firstName": "Sarah", "lastName": "Chen", "email": "s.chen@cncshop.com",
     "jobTitle": "Maintenance Technician", "role": "Technician", "rate": 42, "location": "Maintenance Shop"},
    {"firstName": "Marcus", "lastName": "Johnson", "email": "m.johnson@cncshop.com",
     "jobTitle": "Maintenance Technician", "role": "Technician", "rate": 42, "location": "Maintenance Shop"},
    {"firstName": "Tommy", "lastName": "Nguyen", "email": "t.nguyen@cncshop.com",
     "jobTitle": "CNC Operator", "role": "Technician", "rate": 32, "location": "VMC Area"},
    {"firstName": "Angela", "lastName": "Martinez", "email": "a.martinez@cncshop.com",
     "jobTitle": "CNC Operator", "role": "Technician", "rate": 32, "location": "VMC Area"},
    {"firstName": "Rick", "lastName": "Hoffman", "email": "r.hoffman@cncshop.com",
     "jobTitle": "CNC Operator", "role": "Technician", "rate": 32, "location": "Lathe Area"},
    {"firstName": "Jake", "lastName": "Williams", "email": "j.williams@cncshop.com",
     "jobTitle": "CNC Operator", "role": "Technician", "rate": 32, "location": "5-Axis Cell"},
    {"firstName": "Lisa", "lastName": "Park", "email": "l.park@cncshop.com",
     "jobTitle": "Tool Crib Attendant", "role": "Technician", "rate": 28, "location": "Tool Crib"},
    {"firstName": "Brian", "lastName": "O'Neill", "email": "b.oneill@cncshop.com",
     "jobTitle": "Quality Inspector", "role": "Viewer", "rate": 38, "location": "Quality Lab / CMM Room"},
    {"firstName": "Kevin", "lastName": "Pham", "email": "k.pham@cncshop.com",
     "jobTitle": "CNC Programmer", "role": "Viewer", "rate": 45, "location": "Office / Programming"},
    {"firstName": "Maria", "lastName": "Santos", "email": "m.santos@cncshop.com",
     "jobTitle": "Shipping & Receiving", "role": "Viewer", "rate": 25, "location": "Shipping & Receiving"},
]

# ---------------------------------------------------------------------------
# 5. TEAMS  (members by email)
# ---------------------------------------------------------------------------
TEAMS = [
    {"name": "Day Shift Maintenance", "description": "Maintenance crew - Day shift (6 AM - 2:30 PM)",
     "members": ["d.kowalski@cncshop.com", "s.chen@cncshop.com"]},
    {"name": "Swing Shift Maintenance", "description": "Maintenance crew - Swing shift (2 PM - 10:30 PM)",
     "members": ["m.johnson@cncshop.com"]},
    {"name": "VMC Operators", "description": "Vertical machining center operators",
     "members": ["c.ruiz@cncshop.com", "t.nguyen@cncshop.com", "a.martinez@cncshop.com"]},
    {"name": "Lathe Operators", "description": "CNC lathe operators",
     "members": ["j.patterson@cncshop.com", "r.hoffman@cncshop.com"]},
    {"name": "5-Axis / Specialty", "description": "5-Axis and specialty machine operators",
     "members": ["j.williams@cncshop.com"]},
    {"name": "Quality Team", "description": "Inspection and quality control",
     "members": ["b.oneill@cncshop.com"]},
]

# ---------------------------------------------------------------------------
# 6. ASSETS
# ---------------------------------------------------------------------------
# tuple fields: name, customId, category, manufacturer, model, serial, location,
#               status, inServiceDate, acquisitionCost, primaryUserEmail(optional)
ASSETS = [
    # VMC Area
    ("Haas VF-2 #1", "VMC-001", "CNC Vertical Machining Center", "Haas Automation", "VF-2", "H2V-20185432", "VMC Area", "OPERATIONAL", "2019-03-15", 62000, "t.nguyen@cncshop.com"),
    ("Haas VF-2 #2", "VMC-002", "CNC Vertical Machining Center", "Haas Automation", "VF-2", "H2V-20205876", "VMC Area", "OPERATIONAL", "2020-07-22", 64500, "a.martinez@cncshop.com"),
    ("Haas VF-4", "VMC-003", "CNC Vertical Machining Center", "Haas Automation", "VF-4", "H4V-20197721", "VMC Area", "OPERATIONAL", "2019-11-08", 82000, "a.martinez@cncshop.com"),
    ("DMG MORI CMX 800 V", "VMC-004", "CNC Vertical Machining Center", "DMG MORI", "CMX 800 V", "DE128-90453", "VMC Area", "OPERATIONAL", "2021-04-10", 145000, "c.ruiz@cncshop.com"),
    ("Fanuc RoboDrill", "VMC-005", "CNC Vertical Machining Center", "FANUC", "RoboDrill a-D21MiB5", "RD-2022-1847", "VMC Area", "OPERATIONAL", "2022-01-18", 95000, "t.nguyen@cncshop.com"),
    # Lathe Area
    ("Mazak QTN-200M", "LATHE-001", "CNC Turning Center", "Mazak", "QTN-200M", "MZ-21065483", "Lathe Area", "OPERATIONAL", "2020-02-14", 135000, "r.hoffman@cncshop.com"),
    ("Haas ST-20 #1", "LATHE-002", "CNC Turning Center", "Haas Automation", "ST-20", "HS2-20194561", "Lathe Area", "OPERATIONAL", "2019-06-20", 55000, "r.hoffman@cncshop.com"),
    ("Haas ST-20 #2", "LATHE-003", "CNC Turning Center", "Haas Automation", "ST-20", "HS2-20214589", "Lathe Area", "OPERATIONAL", "2021-09-05", 58000, "j.williams@cncshop.com"),
    ("Doosan Lynx 2100LB", "LATHE-004", "CNC Turning Center", "Doosan", "Lynx 2100LB", "DL-20227834", "Lathe Area", "OPERATIONAL", "2022-05-11", 75000, None),
    # 5-Axis Cell
    ("DMG MORI DMU 50", "5AX-001", "CNC 5-Axis Machine", "DMG MORI", "DMU 50 3rd Gen", "DE225-11287", "5-Axis Cell", "OPERATIONAL", "2023-02-28", 285000, "j.williams@cncshop.com"),
    ("Haas UMC-750", "5AX-002", "CNC 5-Axis Machine", "Haas Automation", "UMC-750", "HU7-20221195", "5-Axis Cell", "OPERATIONAL", "2022-08-15", 165000, "j.williams@cncshop.com"),
    # Swiss Cell
    ("Citizen Cincom L20", "SWISS-001", "Swiss-Type Lathe", "Citizen", "Cincom L20 XII", "CL20-2021-4476", "Swiss Cell", "OPERATIONAL", "2021-11-20", 225000, None),
    ("Star SR-20J II", "SWISS-002", "Swiss-Type Lathe", "Star Micronics", "SR-20J II", "SR20-22-8831", "Swiss Cell", "OPERATIONAL", "2022-03-10", 210000, None),
    # Grinding
    ("Okamoto Surface Grinder", "GRIND-001", "Grinding Machine", "Okamoto", "ACC-818DX", "OK-818-19456", "Grinding Area", "OPERATIONAL", "2018-07-01", 48000, None),
    # EDM
    ("Mitsubishi Wire EDM", "EDM-001", "EDM Machine", "Mitsubishi", "MV2400R", "MV24-2020-3312", "EDM Department", "OPERATIONAL", "2020-10-05", 180000, None),
    ("Sodick Sinker EDM", "EDM-002", "EDM Machine", "Sodick", "AQ35L", "SD-AQ35-21985", "EDM Department", "OPERATIONAL", "2021-06-14", 120000, None),
    # Quality Lab
    ("Zeiss Contura CMM", "CMM-001", "Inspection Equipment", "Zeiss", "Contura 7/10/6", "ZC-2019-77432", "Quality Lab / CMM Room", "OPERATIONAL", "2019-01-25", 185000, "b.oneill@cncshop.com"),
    ("Zoller Tool Presetter", "TOOL-PRE-001", "Inspection Equipment", "Zoller", "Venturion 600", "ZV-2021-5543", "Tool Crib", "OPERATIONAL", "2021-03-18", 95000, "l.park@cncshop.com"),
    # Support & Facility
    ("DoALL Band Saw", "SAW-001", "Support Equipment", "DoALL", "DC-330NC", "DA-330-20765", "Raw Material Storage", "OPERATIONAL", "2018-04-12", 28000, None),
    ("Eriez Coolant Recycler", "COOL-001", "Support Equipment", "Eriez", "HydroFlow HF-10", "EZ-HF10-2021", "Coolant Mixing Area", "OPERATIONAL", "2021-08-22", 18000, None),
    ("Atlas Copco Compressor", "COMP-001", "Facility Equipment", "Atlas Copco", "GA37 VSD+", "AC-GA37-20198", "Compressor Room", "OPERATIONAL", "2019-05-30", 42000, None),
    ("Donaldson Mist Collector #1", "MIST-001", "Facility Equipment", "Donaldson Torit", "WSO 25-2", "DT-WSO-2019-431", "VMC Area", "OPERATIONAL", "2019-03-15", 8500, None),
    ("Donaldson Mist Collector #2", "MIST-002", "Facility Equipment", "Donaldson Torit", "WSO 25-2", "DT-WSO-2020-612", "Lathe Area", "OPERATIONAL", "2020-02-14", 8500, None),
    ("LNS Bar Feeder", "FEED-001", "Support Equipment", "LNS", "Alpha SL65 S", "LNS-SL65-21443", "Lathe Area", "OPERATIONAL", "2020-02-14", 35000, None),
    ("Toyota Forklift", "FORK-001", "Facility Equipment", "Toyota", "8FBE18U", "TM-8FBE-2020-987", "Loading Dock", "OPERATIONAL", "2020-01-10", 32000, None),
]

# child -> parent
ASSET_PARENTS = {
    "Donaldson Mist Collector #1": "Haas VF-2 #1",
    "Donaldson Mist Collector #2": "Mazak QTN-200M",
    "LNS Bar Feeder": "Mazak QTN-200M",
}

# ---------------------------------------------------------------------------
# 7. PARTS
# tuple: name, partNumber, category, cost, qty, minQty, unit, vendor
# ---------------------------------------------------------------------------
PARTS = [
    # Spindle Components
    ("Spindle Bearing Set - Front (CAT40)", "NSK-7014CTYNDBLP5", "Spindle Components", 1500, 2, 1, "set", "NSK Precision"),
    ("Spindle Bearing Set - Rear (CAT40)", "FAG-B7012CTP4SUL", "Spindle Components", 950, 2, 1, "set", "Schaeffler Group"),
    ("Spindle Drawbar Spring Set (Haas)", "HAAS-93-0333", "Spindle Components", 225, 4, 2, "set", "Haas Parts Dept"),
    ("Spindle Drawbar Spring Set (DMG)", "DMG-SPR-CMX800", "Spindle Components", 310, 2, 1, "set", "DMG MORI Parts"),
    # Ball Screws & Linear Guides
    ("Ball Screw X-Axis (Haas VF-2)", "THK-W3212-944RCX", "Ball Screws & Linear Guides", 3200, 1, 1, "ea", "THK America"),
    ("Ball Screw Nut (Haas VF)", "NSK-BSN4010", "Ball Screws & Linear Guides", 1100, 2, 1, "ea", "NSK Precision"),
    ("Linear Guide Block (THK SSR25)", "THK-SSR25XW", "Ball Screws & Linear Guides", 320, 4, 2, "ea", "THK America"),
    ("Ball Screw Support Bearing", "NACHI-25TAB06DF", "Ball Screws & Linear Guides", 425, 2, 1, "ea", "NACHI America"),
    # Way Covers & Enclosure
    ("X-Axis Way Cover (Haas VF-2)", "HAAS-93-3015", "Way Covers & Enclosure", 1050, 1, 1, "ea", "Haas Parts Dept"),
    ("Z-Axis Wiper Kit (Haas)", "HAAS-93-0959", "Way Covers & Enclosure", 110, 4, 2, "kit", "Haas Parts Dept"),
    ("Spindle Window Wiper Seal", "HAAS-58-4026", "Way Covers & Enclosure", 55, 6, 3, "ea", "Haas Parts Dept"),
    ("Door Interlock Switch", "OMRON-D4NS4CF", "Way Covers & Enclosure", 60, 4, 2, "ea", "Omron Industrial"),
    # Tool Holders
    ("CAT40 End Mill Holder 3/4\"", "PARLEC-C40-75EM4", "Tool Holders", 100, 12, 6, "ea", "Techniks / Parlec"),
    ("CAT40 End Mill Holder 1/2\"", "PARLEC-C40-50EM3", "Tool Holders", 95, 15, 8, "ea", "Techniks / Parlec"),
    ("CAT40 ER32 Collet Chuck", "TECH-04522-CAT40", "Tool Holders", 150, 10, 5, "ea", "Techniks / Parlec"),
    ("CAT40 Shell Mill Arbor", "KMT-A4016CSS075", "Tool Holders", 185, 4, 2, "ea", "Kennametal"),
    ("CAT40 Pull Stud (Retention Knob)", "TECH-PS-CAT40-A1", "Tool Holders", 15, 50, 20, "ea", "Techniks / Parlec"),
    ("BT40 ER32 Collet Chuck", "NIKKEN-BT40-C32", "Tool Holders", 140, 4, 2, "ea", "Lyndex-Nikken"),
    # Collets
    ("ER32 Collet 1/2\"", "REGO-1132-50000", "Collets", 35, 10, 5, "ea", "Rego-Fix"),
    ("ER32 Collet 3/8\"", "REGO-1132-37500", "Collets", 35, 10, 5, "ea", "Rego-Fix"),
    ("ER32 Collet 1/4\"", "REGO-1132-25000", "Collets", 35, 10, 5, "ea", "Rego-Fix"),
    ("ER32 Collet 3/4\"", "REGO-1132-75000", "Collets", 38, 8, 4, "ea", "Rego-Fix"),
    ("ER40 Collet 3/4\"", "TECH-04216-34", "Collets", 42, 6, 3, "ea", "Techniks / Parlec"),
    # Cutting Tools - Milling
    ("1/2\" 4-Flute Carbide End Mill", "KMT-F4AE0500AWL", "Cutting Tools - Milling", 55, 20, 10, "ea", "Kennametal"),
    ("3/4\" 4-Flute Carbide End Mill", "HELICAL-07584", "Cutting Tools - Milling", 85, 12, 6, "ea", "Harvey / Helical"),
    ("1/2\" Ball Nose End Mill", "SGS-36528", "Cutting Tools - Milling", 62, 8, 4, "ea", "SGS Tool"),
    ("2\" Indexable Face Mill Body", "SAND-R245-050Q22", "Cutting Tools - Milling", 425, 3, 1, "ea", "Sandvik Coromant"),
    ("R245 Face Mill Insert", "SAND-R245-1204-PM4340", "Cutting Tools - Milling", 15, 50, 20, "ea", "Sandvik Coromant"),
    ("3\" Shell Mill Body", "KMT-KSSR300SE125", "Cutting Tools - Milling", 550, 2, 1, "ea", "Kennametal"),
    # Cutting Tools - Turning
    ("CNMG 432 Turning Insert (Gen Purpose)", "SAND-CNMG120408-PM4325", "Cutting Tools - Turning", 14, 60, 30, "ea", "Sandvik Coromant"),
    ("WNMG 432 Turning Insert", "KMT-WNMG080408-MP", "Cutting Tools - Turning", 12, 40, 20, "ea", "Kennametal"),
    ("DNMG 432 Finishing Insert", "ISCAR-DNMG150408-TF", "Cutting Tools - Turning", 13, 30, 15, "ea", "Iscar USA"),
    ("CCMT 32.51 Light Turning Insert", "MIT-CCMT09T304", "Cutting Tools - Turning", 10, 40, 20, "ea", "Mitsubishi Materials"),
    ("Grooving Insert 0.125\"", "ISCAR-GRIP4004Y-IC354", "Cutting Tools - Turning", 16, 20, 10, "ea", "Iscar USA"),
    ("Threading Insert (External)", "SAND-266RG16MM01A", "Cutting Tools - Turning", 21, 20, 10, "ea", "Sandvik Coromant"),
    # Cutting Tools - Drilling
    ("1/2\" Carbide Drill 5xD", "SAND-860-0500-040A1", "Cutting Tools - Drilling", 102, 6, 3, "ea", "Sandvik Coromant"),
    ("3/8\" Carbide Drill 3xD", "KMT-B105A09525", "Cutting Tools - Drilling", 75, 8, 4, "ea", "Kennametal"),
    ("1\" Indexable U-Drill Body", "SAND-880-D1000L25", "Cutting Tools - Drilling", 340, 2, 1, "ea", "Sandvik Coromant"),
    ("U-Drill Insert", "SAND-880-040305H-CLM", "Cutting Tools - Drilling", 12, 30, 15, "ea", "Sandvik Coromant"),
    ("Center Drill #3", "CL-69103", "Cutting Tools - Drilling", 8, 20, 10, "ea", "Chicago-Latrobe"),
    # Filters & Consumables
    ("Coolant Filter Bag", "ERIEZ-HF1020-05", "Filters & Consumables", 24, 20, 10, "ea", "Eriez"),
    ("Oil Mist Filter Cartridge", "DONALDSON-P191280", "Filters & Consumables", 80, 6, 3, "ea", "Donaldson"),
    ("Air Compressor Intake Filter", "AC-1613940000", "Filters & Consumables", 45, 3, 2, "ea", "Atlas Copco"),
    ("Air/Oil Separator Element", "AC-2901053200", "Filters & Consumables", 100, 2, 1, "ea", "Atlas Copco"),
    ("Spindle Air Purge Filter", "PARKER-025AA", "Filters & Consumables", 20, 10, 5, "ea", "Parker Hannifin"),
    # Fluids & Lubricants
    ("Coolant - TRIM MicroSol 685 (55 gal)", "MFS-MICROSOL685-55", "Fluids & Lubricants", 1050, 2, 1, "drum", "Master Fluid Solutions"),
    ("Spindle Oil - Mobil Velocite No.10 (5 gal)", "MOBIL-VELOCITE10-5", "Fluids & Lubricants", 70, 3, 2, "pail", "ExxonMobil"),
    ("Way Lube - Mobil Vactra No.2 (5 gal)", "MOBIL-VACTRA2-5", "Fluids & Lubricants", 85, 4, 2, "pail", "ExxonMobil"),
    ("Hydraulic Oil - Mobil DTE 25 (5 gal)", "MOBIL-DTE25-5", "Fluids & Lubricants", 65, 3, 2, "pail", "ExxonMobil"),
    # Electrical & Servo
    ("Servo Motor X-Axis (Haas)", "HAAS-30-3010A", "Electrical & Servo", 2750, 1, 1, "ea", "Haas Parts Dept"),
    ("Spindle Encoder (Heidenhain)", "HEIDENHAIN-ROD486", "Electrical & Servo", 1100, 1, 1, "ea", "Heidenhain"),
    ("Axis Encoder (Heidenhain)", "HEIDENHAIN-ERN1387", "Electrical & Servo", 800, 2, 1, "ea", "Heidenhain"),
    ("Proximity Sensor", "OMRON-E2EX5ME1Z", "Electrical & Servo", 45, 6, 3, "ea", "Omron Industrial"),
    ("Coolant Pump Motor", "GRAYMILLS-IMV50F", "Electrical & Servo", 400, 2, 1, "ea", "Graymills"),
    ("Spindle Cooling Unit", "HABOR-HBO250P", "Electrical & Servo", 2000, 1, 0, "ea", "Habor"),
    # Belts & Seals
    ("Spindle Drive Belt (Haas)", "GATES-8MGT1600-36", "Belts & Seals", 105, 3, 2, "ea", "Gates"),
    ("ATC Belt (Haas)", "HAAS-93-0616", "Belts & Seals", 65, 3, 2, "ea", "Haas Parts Dept"),
    ("Linear Guide Wiper Seal Kit", "THK-SSR25-WIPER", "Belts & Seals", 28, 10, 5, "kit", "THK America"),
]

# parts -> linked assets (by name); "All VMCs" etc expanded in seed script
PART_ASSETS = {
    "Spindle Bearing Set - Front (CAT40)": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "Spindle Bearing Set - Rear (CAT40)": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "Spindle Drawbar Spring Set (Haas)": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "Spindle Drawbar Spring Set (DMG)": ["DMG MORI CMX 800 V"],
    "Ball Screw X-Axis (Haas VF-2)": ["Haas VF-2 #1", "Haas VF-2 #2"],
    "Ball Screw Nut (Haas VF)": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "Linear Guide Block (THK SSR25)": ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"],
    "X-Axis Way Cover (Haas VF-2)": ["Haas VF-2 #1", "Haas VF-2 #2"],
    "BT40 ER32 Collet Chuck": ["DMG MORI CMX 800 V"],
    "Spindle Encoder (Heidenhain)": ["DMG MORI CMX 800 V", "DMG MORI DMU 50"],
    "Axis Encoder (Heidenhain)": ["DMG MORI CMX 800 V"],
    "Spindle Cooling Unit": ["DMG MORI DMU 50"],
}

# Asset groups referenced by parts text
ALL_VMCS = ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4", "DMG MORI CMX 800 V", "Fanuc RoboDrill"]
ALL_HAAS_VMCS = ["Haas VF-2 #1", "Haas VF-2 #2", "Haas VF-4"]

# ---------------------------------------------------------------------------
# 8. MULTIPARTS (sets) -> list of part names
# ---------------------------------------------------------------------------
MULTIPARTS = [
    ("Haas VF-2 Spindle Rebuild Kit", [
        "Spindle Bearing Set - Front (CAT40)", "Spindle Bearing Set - Rear (CAT40)",
        "Spindle Drawbar Spring Set (Haas)", "Spindle Window Wiper Seal"]),
    ("Haas Annual PM Kit", [
        "Z-Axis Wiper Kit (Haas)", "Spindle Drive Belt (Haas)", "ATC Belt (Haas)",
        "Spindle Air Purge Filter", "Way Lube - Mobil Vactra No.2 (5 gal)",
        "Hydraulic Oil - Mobil DTE 25 (5 gal)"]),
    ("CAT40 Tooling Starter Set", [
        "CAT40 End Mill Holder 3/4\"", "CAT40 End Mill Holder 1/2\"",
        "CAT40 ER32 Collet Chuck", "CAT40 Shell Mill Arbor", "CAT40 Pull Stud (Retention Knob)"]),
    ("ER32 Collet Set", [
        "ER32 Collet 1/4\"", "ER32 Collet 3/8\"", "ER32 Collet 1/2\"", "ER32 Collet 3/4\""]),
    ("Turning Insert Starter Pack", [
        "CNMG 432 Turning Insert (Gen Purpose)", "WNMG 432 Turning Insert",
        "DNMG 432 Finishing Insert", "CCMT 32.51 Light Turning Insert"]),
    ("Compressor Annual Service Kit", [
        "Air Compressor Intake Filter", "Air/Oil Separator Element",
        "Hydraulic Oil - Mobil DTE 25 (5 gal)"]),
    ("Coolant System Refresh Kit", [
        "Coolant Filter Bag", "Coolant - TRIM MicroSol 685 (55 gal)", "Spindle Air Purge Filter"]),
]

# ---------------------------------------------------------------------------
# 9. METERS  tuple: name, asset, unit, category, updateFreq(days), userEmail
# ---------------------------------------------------------------------------
METERS = [
    ("Spindle Hours - VF2 #1", "Haas VF-2 #1", "hours", "Runtime", 7, "t.nguyen@cncshop.com"),
    ("Spindle Hours - VF2 #2", "Haas VF-2 #2", "hours", "Runtime", 7, "a.martinez@cncshop.com"),
    ("Spindle Hours - VF4", "Haas VF-4", "hours", "Runtime", 7, "a.martinez@cncshop.com"),
    ("Spindle Hours - CMX800", "DMG MORI CMX 800 V", "hours", "Runtime", 7, "c.ruiz@cncshop.com"),
    ("Spindle Hours - RoboDrill", "Fanuc RoboDrill", "hours", "Runtime", 7, "t.nguyen@cncshop.com"),
    ("Spindle Hours - QTN200", "Mazak QTN-200M", "hours", "Runtime", 7, "r.hoffman@cncshop.com"),
    ("Spindle Hours - ST20 #1", "Haas ST-20 #1", "hours", "Runtime", 7, "r.hoffman@cncshop.com"),
    ("Spindle Hours - ST20 #2", "Haas ST-20 #2", "hours", "Runtime", 7, "j.williams@cncshop.com"),
    ("Spindle Hours - DMU50", "DMG MORI DMU 50", "hours", "Runtime", 7, "j.williams@cncshop.com"),
    ("Spindle Hours - UMC750", "Haas UMC-750", "hours", "Runtime", 7, "j.williams@cncshop.com"),
    ("Spindle Vibration - VF2 #1", "Haas VF-2 #1", "mm/s RMS", "Vibration", 7, "s.chen@cncshop.com"),
    ("Spindle Vibration - VF4", "Haas VF-4", "mm/s RMS", "Vibration", 7, "s.chen@cncshop.com"),
    ("Spindle Vibration - CMX800", "DMG MORI CMX 800 V", "mm/s RMS", "Vibration", 7, "s.chen@cncshop.com"),
    ("Spindle Vibration - DMU50", "DMG MORI DMU 50", "mm/s RMS", "Vibration", 7, "s.chen@cncshop.com"),
    ("Coolant Concentration - VMC Area", "Haas VF-2 #1", "% Brix", "Fluid Condition", 1, "t.nguyen@cncshop.com"),
    ("Coolant Concentration - Lathe Area", "Mazak QTN-200M", "% Brix", "Fluid Condition", 1, "r.hoffman@cncshop.com"),
    ("Coolant pH - VMC Area", "Haas VF-2 #1", "pH", "Fluid Condition", 1, "t.nguyen@cncshop.com"),
    ("Coolant pH - Lathe Area", "Mazak QTN-200M", "pH", "Fluid Condition", 1, "r.hoffman@cncshop.com"),
    ("X-Axis Backlash - VF2 #1", "Haas VF-2 #1", "mm", "Precision", 90, "s.chen@cncshop.com"),
    ("Y-Axis Backlash - VF2 #1", "Haas VF-2 #1", "mm", "Precision", 90, "s.chen@cncshop.com"),
    ("Z-Axis Backlash - VF2 #1", "Haas VF-2 #1", "mm", "Precision", 90, "s.chen@cncshop.com"),
    ("Spindle Runout - VF2 #1", "Haas VF-2 #1", "mm", "Precision", 30, "c.ruiz@cncshop.com"),
    ("Spindle Runout - DMU50", "DMG MORI DMU 50", "mm", "Precision", 30, "c.ruiz@cncshop.com"),
    ("Shop Air Pressure", "Atlas Copco Compressor", "PSI", "Pressure", 1, "s.chen@cncshop.com"),
    ("Oil Mist Filter DP - VMC", "Donaldson Mist Collector #1", "in WG", "Pressure", 7, "m.johnson@cncshop.com"),
    ("Oil Mist Filter DP - Lathe", "Donaldson Mist Collector #2", "in WG", "Pressure", 7, "m.johnson@cncshop.com"),
    ("Hydraulic Oil Temp - QTN200", "Mazak QTN-200M", "C", "Temperature", 1, "r.hoffman@cncshop.com"),
    ("Spindle Bearing Temp - DMU50", "DMG MORI DMU 50", "C", "Temperature", 1, "j.williams@cncshop.com"),
]

# readings: meter name, date, value, notes
METER_READINGS = [
    ("Spindle Hours - VF2 #1", "2026-03-01", 12450, ""),
    ("Spindle Hours - VF2 #1", "2026-03-15", 12680, ""),
    ("Spindle Hours - VF2 #1", "2026-04-01", 12910, ""),
    ("Spindle Hours - VF2 #2", "2026-04-01", 8320, ""),
    ("Spindle Hours - VF4", "2026-04-01", 10150, ""),
    ("Spindle Hours - CMX800", "2026-04-01", 6780, ""),
    ("Spindle Hours - DMU50", "2026-04-01", 3250, ""),
    ("Spindle Hours - QTN200", "2026-04-01", 9430, ""),
    ("Spindle Vibration - VF2 #1", "2026-03-01", 1.8, "Within spec"),
    ("Spindle Vibration - VF2 #1", "2026-04-01", 2.1, "Slight increase - monitor"),
    ("Spindle Vibration - VF4", "2026-04-01", 1.2, "Good"),
    ("Spindle Vibration - CMX800", "2026-04-01", 0.9, "Excellent"),
    ("Spindle Vibration - DMU50", "2026-04-01", 0.7, "Excellent"),
    ("Coolant Concentration - VMC Area", "2026-04-01", 7.5, "Target: 7-9%"),
    ("Coolant Concentration - Lathe Area", "2026-04-01", 8.2, "Good"),
    ("Coolant pH - VMC Area", "2026-04-01", 9.1, "Good"),
    ("Coolant pH - Lathe Area", "2026-04-01", 9.0, "Good"),
    ("X-Axis Backlash - VF2 #1", "2026-01-15", 0.008, "Within tolerance"),
    ("Y-Axis Backlash - VF2 #1", "2026-01-15", 0.010, "Approaching limit"),
    ("Z-Axis Backlash - VF2 #1", "2026-01-15", 0.005, "Good"),
    ("Spindle Runout - VF2 #1", "2026-03-15", 0.008, "Within spec"),
    ("Spindle Runout - DMU50", "2026-03-15", 0.003, "Excellent"),
    ("Shop Air Pressure", "2026-04-01", 112, "Normal range"),
    ("Oil Mist Filter DP - VMC", "2026-04-01", 2.8, "OK (change at 4.0)"),
    ("Hydraulic Oil Temp - QTN200", "2026-04-01", 42, "Normal"),
    ("Spindle Bearing Temp - DMU50", "2026-04-01", 35, "Normal"),
]

# ---------------------------------------------------------------------------
# 10. PREVENTIVE MAINTENANCE
# fields: title, description, asset, category, priority, freqDays, assignedToEmail, durationHrs
# freqDays: Daily=1, Weekly=7, Monthly=30, Quarterly=90, Semi=180, Yearly=365
# ---------------------------------------------------------------------------
PM = [
    # Daily
    ("Daily Coolant Check - VMC Area", "Check coolant concentration with refractometer (target 7-9% Brix), check pH (target 8.5-9.5), top off as needed. Log readings in meters.", "Haas VF-2 #1", "Preventive", "MEDIUM", 1, "t.nguyen@cncshop.com", 0.25),
    ("Daily Coolant Check - Lathe Area", "Check coolant concentration and pH, top off sump. Log readings.", "Mazak QTN-200M", "Preventive", "MEDIUM", 1, "r.hoffman@cncshop.com", 0.25),
    ("Daily Machine Inspection - VMC", "Wipe way covers, check for leaks (coolant/hydraulic/way lube), verify chip conveyor running, check hydraulic oil level, check air pressure at FRL.", "Haas VF-2 #1", "Preventive", "LOW", 1, "t.nguyen@cncshop.com", 0.25),
    ("Daily Machine Inspection - Lathe", "Wipe machine, check for leaks, verify chip conveyor, check hydraulic level, empty chip bin.", "Mazak QTN-200M", "Preventive", "LOW", 1, "r.hoffman@cncshop.com", 0.25),
    # Weekly
    ("Weekly Coolant Filter Clean", "Clean coolant tank strainer and filter basket on all machines. Replace filter bags if fouled.", "Eriez Coolant Recycler", "Preventive", "MEDIUM", 7, "s.chen@cncshop.com", 0.5),
    ("Weekly Tool Holder Inspection", "Inspect CAT40 holders for fretting, clean tapers with Scotch-Brite, check pull stud torque.", None, "Inspection", "MEDIUM", 7, "l.park@cncshop.com", 0.5),
    ("Weekly Way Cover Inspection", "Inspect bellows and telescoping covers for tears, chip accumulation, or damage. Report issues immediately.", "Haas VF-2 #1", "Preventive", "MEDIUM", 7, "s.chen@cncshop.com", 0.25),
    ("Weekly Compressor Drain", "Drain water from air compressor receiver tank and check dryer operation.", "Atlas Copco Compressor", "Preventive", "LOW", 7, "m.johnson@cncshop.com", 0.1),
    ("Weekly Oil Mist Filter Check", "Check differential pressure on oil mist collectors. Record in meters. Change filter if DP > 4 in. WG.", "Donaldson Mist Collector #1", "Preventive", "LOW", 7, "m.johnson@cncshop.com", 0.15),
    # Monthly
    ("Monthly Coolant Sump Cleanout - VMC", "Fully drain sump, remove tramp oil and sludge, clean strainers, recharge with fresh TRIM MicroSol 685 at 8% concentration.", "Haas VF-2 #1", "Preventive", "HIGH", 30, "s.chen@cncshop.com", 2.0),
    ("Monthly Spindle Runout Check", "Check spindle TIR with test indicator in spindle. Max 0.0005\" (0.012mm). Record in meters.", "Haas VF-2 #1", "Inspection", "HIGH", 30, "c.ruiz@cncshop.com", 0.25),
    ("Monthly ATC Inspection", "Inspect auto tool changer arm/carousel, clean tool pockets, check tool change alignment, lubricate per OEM.", "Haas VF-2 #1", "Preventive", "MEDIUM", 30, "s.chen@cncshop.com", 0.75),
    ("Monthly Way Lube Top-Off", "Check and top off way lube reservoir on all machines. Use Mobil Vactra No. 2 only.", "Haas VF-2 #1", "Preventive", "MEDIUM", 30, "m.johnson@cncshop.com", 0.25),
    ("Monthly Safety Interlock Test", "Test all door interlocks, chuck guards, and E-stop circuits. Document results.", "Haas VF-2 #1", "Safety", "HIGH", 30, "s.chen@cncshop.com", 0.35),
    ("Monthly Drive Belt Inspection", "Inspect spindle drive belt and ATC belt for wear, cracking, or glazing. Check tension per OEM spec.", "Haas VF-2 #1", "Preventive", "MEDIUM", 30, "s.chen@cncshop.com", 0.25),
    ("Monthly Chip Conveyor Clean", "Remove and clean chip conveyor chain/belt, inspect for wear, clean coolant return trough.", "Haas VF-2 #1", "Preventive", "LOW", 30, "m.johnson@cncshop.com", 0.5),
    # Quarterly
    ("Quarterly Backlash Measurement", "Measure backlash on X, Y, Z axes with dial indicator per Haas service procedure. Record in meters. If any axis >0.015mm, schedule ball screw adjustment.", "Haas VF-2 #1", "Inspection", "HIGH", 90, "s.chen@cncshop.com", 1.0),
    ("Quarterly Oil Mist Filter Replace", "Replace oil mist filter cartridge (Donaldson P191280). Reset differential pressure baseline.", "Donaldson Mist Collector #1", "Preventive", "MEDIUM", 90, "m.johnson@cncshop.com", 0.5),
    ("Quarterly Coolant Filter Replace", "Replace coolant filter bags on Eriez HydroFlow system. Inspect housing for buildup.", "Eriez Coolant Recycler", "Preventive", "MEDIUM", 90, "s.chen@cncshop.com", 0.5),
    ("Quarterly Hydraulic Oil Sample", "Pull hydraulic oil sample and send to lab for analysis (viscosity, water content, particle count).", "Mazak QTN-200M", "Inspection", "MEDIUM", 90, "s.chen@cncshop.com", 0.25),
    ("Quarterly Electrical Cabinet Inspection", "Open and inspect electrical cabinet: clean fan filters, check for loose connections, verify drive temperatures, look for discoloration.", "Haas VF-2 #1", "Inspection", "HIGH", 90, "s.chen@cncshop.com", 0.75),
    ("Quarterly E-Stop Circuit Test", "Full test of emergency stop circuit on all machines. Verify all E-stops kill motion within spec. Document test results.", "Haas VF-2 #1", "Safety", "HIGH", 90, "s.chen@cncshop.com", 0.25),
    ("Quarterly Machine Leveling Check", "Check machine level with precision level (0.0005\"/ft). Adjust leveling pads if needed.", "DMG MORI DMU 50", "Calibration", "HIGH", 90, "s.chen@cncshop.com", 1.0),
    # Semi-Annual
    ("Semi-Annual Geometry Check", "Full geometric alignment: spindle squareness to table (X and Y), tramming, parallelism. Correct if out of spec.", "DMG MORI DMU 50", "Calibration", "HIGH", 180, "c.ruiz@cncshop.com", 4.0),
    ("Semi-Annual Spindle Belt Replacement", "Replace spindle drive belt (Gates 8MGT-1600-36). Check pulley alignment and tension.", "Haas VF-2 #1", "Preventive", "MEDIUM", 180, "s.chen@cncshop.com", 1.5),
    ("Semi-Annual Compressor Filter Replace", "Replace air intake filter. Check oil level. Inspect hoses and connections.", "Atlas Copco Compressor", "Preventive", "MEDIUM", 180, "m.johnson@cncshop.com", 0.5),
    ("Semi-Annual Linear Guide Wiper Replace", "Inspect and replace wiper seals on all linear guide blocks. Clean and re-lubricate rails.", "Haas VF-2 #1", "Preventive", "MEDIUM", 180, "s.chen@cncshop.com", 2.0),
    # Annual
    ("Annual Accuracy Audit (Ballbar Test)", "Full machine accuracy audit using Renishaw ballbar. Test circularity, backlash, squareness, servo mismatch. Compare to baseline. Schedule corrections as needed.", "DMG MORI DMU 50", "Calibration", "HIGH", 365, "s.chen@cncshop.com", 8.0),
    ("Annual Hydraulic Oil Change", "Drain and replace hydraulic oil. Replace suction strainer. Clean reservoir. Use Mobil DTE 25.", "Mazak QTN-200M", "Preventive", "HIGH", 365, "s.chen@cncshop.com", 2.0),
    ("Annual Way Lube Oil Change", "Drain and replace way lube oil on all machines. Use Mobil Vactra No. 2.", "Haas VF-2 #1", "Preventive", "MEDIUM", 365, "m.johnson@cncshop.com", 1.0),
    ("Annual Drawbar Spring Inspection", "Remove and inspect spindle drawbar springs. Replace if compressed height is out of spec or if >8,000 spindle hours since last replacement.", "Haas VF-2 #1", "Inspection", "HIGH", 365, "s.chen@cncshop.com", 2.0),
    ("Annual Compressor Full Service", "Full compressor service: oil change, air/oil separator, intake filter, belt inspection, drain valve check, safety valve test.", "Atlas Copco Compressor", "Preventive", "HIGH", 365, "d.kowalski@cncshop.com", 4.0),
    ("Annual CMM Calibration", "Full CMM calibration by Zeiss certified technician. Update calibration certificate.", "Zeiss Contura CMM", "Calibration", "HIGH", 365, "b.oneill@cncshop.com", 8.0),
]

# ---------------------------------------------------------------------------
# 11. WORK ORDERS
# fields: title, description, asset, priority, status, category, assignedToEmail,
#         dueDate, completedOn(optional), feedback(optional)
# status values mapped in seed script to API enum
# ---------------------------------------------------------------------------
WORK_ORDERS = [
    # Open
    ("Investigate vibration increase on VF-2 #1", "Spindle vibration reading increased from 1.8 to 2.1 mm/s RMS over last month. Not critical yet but trending upward. Perform detailed vibration analysis, check bearing condition, inspect belt tension.", "Haas VF-2 #1", "MEDIUM", "OPEN", "Corrective", "s.chen@cncshop.com", "2026-04-10", None, None),
    ("Replace torn Y-axis way cover - VF-4", "Operator reported torn accordion bellows on Y-axis way cover. Chips getting past cover onto linear guides. Order Hennig replacement and schedule install during next planned downtime.", "Haas VF-4", "HIGH", "OPEN", "Corrective", "s.chen@cncshop.com", "2026-04-07", None, None),
    ("Coolant pump making noise - ST-20 #2", "Grinding/whining noise from coolant pump on Haas ST-20 #2. Coolant flow appears reduced. Inspect pump impeller and motor bearings. May need pump replacement (Graymills IMV50-F on shelf).", "Haas ST-20 #2", "HIGH", "OPEN", "Corrective", "m.johnson@cncshop.com", "2026-04-06", None, None),
    # In Progress
    ("Replace door interlock switch - RoboDrill", "Door interlock switch intermittently failing to register closed. Machine faults randomly during cycle. Replacement Omron D4NS-4CF in stock.", "Fanuc RoboDrill", "HIGH", "IN_PROGRESS", "Corrective", "s.chen@cncshop.com", "2026-04-04", None, None),
    ("Quarterly backlash check - DMU 50", "Scheduled quarterly backlash measurement on all 5 axes. Ball bar test also due. Coordinate with Jake Williams for machine availability.", "DMG MORI DMU 50", "HIGH", "IN_PROGRESS", "Inspection", "s.chen@cncshop.com", "2026-04-05", None, None),
    # Completed
    ("Monthly ATC inspection - VF-2 #1", "Cleaned tool pockets, verified tool change alignment, lubricated arm pivot. All within spec.", "Haas VF-2 #1", "MEDIUM", "COMPLETE", "Preventive", "s.chen@cncshop.com", "2026-03-28", "2026-03-28", "ATC operating normally. Tool pocket #14 slightly worn - monitor next month."),
    ("Replace coolant filter bags", "Replaced 5 coolant filter bags on Eriez HydroFlow. Previous bags heavily loaded with aluminum fines.", "Eriez Coolant Recycler", "LOW", "COMPLETE", "Preventive", "m.johnson@cncshop.com", "2026-03-25", "2026-03-25", "Bags were at capacity. Consider increasing change frequency to every 2 months during heavy aluminum jobs."),
    ("Emergency spindle repair - Doosan Lynx", "Spindle seized during production. Bearings failed catastrophically. Removed spindle, sent to GTI Spindle Technology for emergency rebuild. Reinstalled and tested. Total downtime: 5 days.", "Doosan Lynx 2100LB", "HIGH", "COMPLETE", "Emergency", "s.chen@cncshop.com", "2026-03-20", "2026-03-20", "Root cause: coolant ingress through worn labyrinth seal. Added monthly seal inspection to PM checklist. GTI rebuild cost: $6,800."),
    ("Annual compressor service", "Full annual service performed by Atlas Copco technician. Oil change, separator, intake filter, belt check. All passed.", "Atlas Copco Compressor", "MEDIUM", "COMPLETE", "Preventive", "d.kowalski@cncshop.com", "2026-03-15", "2026-03-15", "Atlas Copco tech noted compressor running slightly warm. Recommended cleaning radiator more frequently (quarterly vs semi-annual)."),
    ("Install new bar feeder - Mazak QTN", "Installed LNS Alpha SL65 S bar feeder on Mazak QTN-200M. Configured for 2\" bar stock. Tested with production run - feeding smoothly.", "Mazak QTN-200M", "LOW", "COMPLETE", "Installation", "s.chen@cncshop.com", "2026-03-10", "2026-03-10", "Bar feeder commissioning complete. Operator training done for Day shift. Swing shift training scheduled for next week."),
]
