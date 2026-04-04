# CNC Machine Shop - Demo Data Seed Guide

> **Purpose**: Seed Atlas CMMS with realistic data for demoing to a CNC machine shop.
> All brand names, part numbers, and costs are based on real industry products.

---

## Table of Contents

1. [Locations](#1-locations)
2. [People & Teams](#2-people--teams)
3. [Asset Categories & Assets](#3-asset-categories--assets)
4. [Part Categories & Parts](#4-part-categories--parts)
5. [Sets of Parts (MultiParts)](#5-sets-of-parts-multiparts)
6. [Meters & Initial Readings](#6-meters--initial-readings)
7. [Vendors](#7-vendors)
8. [Preventive Maintenance (Scheduled Work Orders)](#8-preventive-maintenance-scheduled-work-orders)
9. [Sample Work Orders](#9-sample-work-orders)
10. [Files & Attachments](#10-files--attachments)
11. [Reference Videos](#11-reference-videos)

---

## 1. Locations

Create these in **top-down** order (parents first).

| Name | Custom ID | Parent Location | Address |
|---|---|---|---|
| Precision CNC Shop | PLANT-01 | _(root)_ | 4500 Industrial Blvd, Houston, TX 77040 |
| Machine Shop Floor | SHOP-FLOOR | Precision CNC Shop | |
| VMC Area | SHOP-VMC | Machine Shop Floor | |
| Lathe Area | SHOP-LATHE | Machine Shop Floor | |
| 5-Axis Cell | SHOP-5AX | Machine Shop Floor | |
| Swiss Cell | SHOP-SWISS | Machine Shop Floor | |
| Grinding Area | SHOP-GRIND | Machine Shop Floor | |
| EDM Department | SHOP-EDM | Machine Shop Floor | |
| Tool Crib | TOOL-CRIB | Precision CNC Shop | |
| Quality Lab / CMM Room | QC-LAB | Precision CNC Shop | |
| Coolant Mixing Area | MAINT-COOL | Precision CNC Shop | |
| Maintenance Shop | MAINT-SHOP | Precision CNC Shop | |
| Shipping & Receiving | SHIP-REC | Precision CNC Shop | |
| Raw Material Storage | MAT-STORE | Precision CNC Shop | |
| Compressor Room | UTIL-COMP | Precision CNC Shop | |
| Electrical Room | UTIL-ELEC | Precision CNC Shop | |
| Office / Programming | OFFICE | Precision CNC Shop | |
| Loading Dock | DOCK | Precision CNC Shop | |

---

## 2. People & Teams

### Roles (use built-in CMMS roles)

| Role Name | CMMS Role Type | Notes |
|---|---|---|
| Admin | ROLE_ADMIN | Shop Manager, Maintenance Manager |
| Manager | ROLE_MANAGER | Lead Machinists |
| Technician | ROLE_USER | Maintenance Techs, Tool Crib |
| Operator | ROLE_USER | CNC Operators (limited permissions) |
| Viewer | ROLE_USER | QC, Programming, Shipping (read-heavy) |

### Users

| First Name | Last Name | Email | Job Title | Role | Rate ($/hr) | Location |
|---|---|---|---|---|---|---|
| Mike | Torres | m.torres@cncshop.com | Shop Manager | Admin | 65 | Office / Programming |
| Dave | Kowalski | d.kowalski@cncshop.com | Maintenance Manager | Admin | 55 | Maintenance Shop |
| Carlos | Ruiz | c.ruiz@cncshop.com | Lead Machinist (Day) | Manager | 48 | VMC Area |
| Jim | Patterson | j.patterson@cncshop.com | Lead Machinist (Swing) | Manager | 48 | Lathe Area |
| Sarah | Chen | s.chen@cncshop.com | Maintenance Technician | Technician | 42 | Maintenance Shop |
| Marcus | Johnson | m.johnson@cncshop.com | Maintenance Technician | Technician | 42 | Maintenance Shop |
| Tommy | Nguyen | t.nguyen@cncshop.com | CNC Operator | Operator | 32 | VMC Area |
| Angela | Martinez | a.martinez@cncshop.com | CNC Operator | Operator | 32 | VMC Area |
| Rick | Hoffman | r.hoffman@cncshop.com | CNC Operator | Operator | 32 | Lathe Area |
| Jake | Williams | j.williams@cncshop.com | CNC Operator | Operator | 32 | 5-Axis Cell |
| Lisa | Park | l.park@cncshop.com | Tool Crib Attendant | Technician | 28 | Tool Crib |
| Brian | O'Neill | b.oneill@cncshop.com | Quality Inspector | Viewer | 38 | Quality Lab / CMM Room |
| Kevin | Pham | k.pham@cncshop.com | CNC Programmer | Viewer | 45 | Office / Programming |
| Maria | Santos | m.santos@cncshop.com | Shipping & Receiving | Viewer | 25 | Shipping & Receiving |

### Teams

| Team Name | Description | Members |
|---|---|---|
| Day Shift Maintenance | Maintenance crew - Day shift (6 AM - 2:30 PM) | Dave Kowalski, Sarah Chen |
| Swing Shift Maintenance | Maintenance crew - Swing shift (2 PM - 10:30 PM) | Marcus Johnson |
| VMC Operators | Vertical machining center operators | Carlos Ruiz, Tommy Nguyen, Angela Martinez |
| Lathe Operators | CNC lathe operators | Jim Patterson, Rick Hoffman |
| 5-Axis / Specialty | 5-Axis and specialty machine operators | Jake Williams |
| Quality Team | Inspection and quality control | Brian O'Neill |

---

## 3. Asset Categories & Assets

### Asset Categories

| Category Name | Description |
|---|---|
| CNC Vertical Machining Center | VMCs and milling machines |
| CNC Turning Center | CNC lathes and turning centers |
| CNC 5-Axis Machine | Multi-axis machining centers |
| Swiss-Type Lathe | Swiss-type CNC lathes |
| Grinding Machine | Surface and cylindrical grinders |
| EDM Machine | Wire and sinker EDM |
| Inspection Equipment | CMMs, measurement tools |
| Support Equipment | Saws, conveyors, coolant systems |
| Facility Equipment | Compressors, HVAC, material handling |

### Assets

#### VMC Area

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Haas VF-2 #1 | VMC-001 | CNC Vertical Machining Center | Haas Automation | VF-2 | H2V-20185432 | VMC Area | OPERATIONAL | 2019-03-15 | $62,000 |
| Haas VF-2 #2 | VMC-002 | CNC Vertical Machining Center | Haas Automation | VF-2 | H2V-20205876 | VMC Area | OPERATIONAL | 2020-07-22 | $64,500 |
| Haas VF-4 | VMC-003 | CNC Vertical Machining Center | Haas Automation | VF-4 | H4V-20197721 | VMC Area | OPERATIONAL | 2019-11-08 | $82,000 |
| DMG MORI CMX 800 V | VMC-004 | CNC Vertical Machining Center | DMG MORI | CMX 800 V | DE128-90453 | VMC Area | OPERATIONAL | 2021-04-10 | $145,000 |
| Fanuc RoboDrill | VMC-005 | CNC Vertical Machining Center | FANUC | RoboDrill a-D21MiB5 | RD-2022-1847 | VMC Area | OPERATIONAL | 2022-01-18 | $95,000 |

#### Lathe Area

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Mazak QTN-200M | LATHE-001 | CNC Turning Center | Mazak | QTN-200M | MZ-21065483 | Lathe Area | OPERATIONAL | 2020-02-14 | $135,000 |
| Haas ST-20 #1 | LATHE-002 | CNC Turning Center | Haas Automation | ST-20 | HS2-20194561 | Lathe Area | OPERATIONAL | 2019-06-20 | $55,000 |
| Haas ST-20 #2 | LATHE-003 | CNC Turning Center | Haas Automation | ST-20 | HS2-20214589 | Lathe Area | OPERATIONAL | 2021-09-05 | $58,000 |
| Doosan Lynx 2100LB | LATHE-004 | CNC Turning Center | Doosan | Lynx 2100LB | DL-20227834 | Lathe Area | OPERATIONAL | 2022-05-11 | $75,000 |

#### 5-Axis Cell

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| DMG MORI DMU 50 | 5AX-001 | CNC 5-Axis Machine | DMG MORI | DMU 50 3rd Gen | DE225-11287 | 5-Axis Cell | OPERATIONAL | 2023-02-28 | $285,000 |
| Haas UMC-750 | 5AX-002 | CNC 5-Axis Machine | Haas Automation | UMC-750 | HU7-20221195 | 5-Axis Cell | OPERATIONAL | 2022-08-15 | $165,000 |

#### Swiss Cell

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Citizen Cincom L20 | SWISS-001 | Swiss-Type Lathe | Citizen | Cincom L20 XII | CL20-2021-4476 | Swiss Cell | OPERATIONAL | 2021-11-20 | $225,000 |
| Star SR-20J II | SWISS-002 | Swiss-Type Lathe | Star Micronics | SR-20J II | SR20-22-8831 | Swiss Cell | OPERATIONAL | 2022-03-10 | $210,000 |

#### Grinding Area

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Okamoto Surface Grinder | GRIND-001 | Grinding Machine | Okamoto | ACC-818DX | OK-818-19456 | Grinding Area | OPERATIONAL | 2018-07-01 | $48,000 |

#### EDM Department

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Mitsubishi Wire EDM | EDM-001 | EDM Machine | Mitsubishi | MV2400R | MV24-2020-3312 | EDM Department | OPERATIONAL | 2020-10-05 | $180,000 |
| Sodick Sinker EDM | EDM-002 | EDM Machine | Sodick | AQ35L | SD-AQ35-21985 | EDM Department | OPERATIONAL | 2021-06-14 | $120,000 |

#### Quality Lab

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| Zeiss Contura CMM | CMM-001 | Inspection Equipment | Zeiss | Contura 7/10/6 | ZC-2019-77432 | Quality Lab / CMM Room | OPERATIONAL | 2019-01-25 | $185,000 |
| Zoller Tool Presetter | TOOL-PRE-001 | Inspection Equipment | Zoller | Venturion 600 | ZV-2021-5543 | Tool Crib | OPERATIONAL | 2021-03-18 | $95,000 |

#### Support & Facility Equipment

| Name | Custom ID | Category | Manufacturer | Model | Serial Number | Location | Status | In-Service Date | Acquisition Cost |
|---|---|---|---|---|---|---|---|---|---|
| DoALL Band Saw | SAW-001 | Support Equipment | DoALL | DC-330NC | DA-330-20765 | Raw Material Storage | OPERATIONAL | 2018-04-12 | $28,000 |
| Eriez Coolant Recycler | COOL-001 | Support Equipment | Eriez | HydroFlow HF-10 | EZ-HF10-2021 | Coolant Mixing Area | OPERATIONAL | 2021-08-22 | $18,000 |
| Atlas Copco Compressor | COMP-001 | Facility Equipment | Atlas Copco | GA37 VSD+ | AC-GA37-20198 | Compressor Room | OPERATIONAL | 2019-05-30 | $42,000 |
| Donaldson Mist Collector #1 | MIST-001 | Facility Equipment | Donaldson Torit | WSO 25-2 | DT-WSO-2019-431 | VMC Area | OPERATIONAL | 2019-03-15 | $8,500 |
| Donaldson Mist Collector #2 | MIST-002 | Facility Equipment | Donaldson Torit | WSO 25-2 | DT-WSO-2020-612 | Lathe Area | OPERATIONAL | 2020-02-14 | $8,500 |
| LNS Bar Feeder | FEED-001 | Support Equipment | LNS | Alpha SL65 S | LNS-SL65-21443 | Lathe Area | OPERATIONAL | 2020-02-14 | $35,000 |
| Toyota Forklift | FORK-001 | Facility Equipment | Toyota | 8FBE18U | TM-8FBE-2020-987 | Loading Dock | OPERATIONAL | 2020-01-10 | $32,000 |

#### Parent-Child Asset Relationships

Set these parent relationships after creating all assets:

| Child Asset | Parent Asset |
|---|---|
| Donaldson Mist Collector #1 | Haas VF-2 #1 |
| Donaldson Mist Collector #2 | Mazak QTN-200M |
| LNS Bar Feeder | Mazak QTN-200M |

---

## 4. Part Categories & Parts

### Part Categories

| Category Name | Description |
|---|---|
| Spindle Components | Bearings, drawbar springs, spindle seals |
| Ball Screws & Linear Guides | Ball screws, nuts, linear guide blocks/rails |
| Way Covers & Enclosure | Telescoping covers, wipers, door switches |
| Tool Holders | CAT40, BT40, collet chucks, retention knobs |
| Collets | ER32, ER40 collets |
| Cutting Tools - Milling | End mills, face mills, indexable inserts |
| Cutting Tools - Turning | Turning inserts, grooving, threading |
| Cutting Tools - Drilling | Carbide drills, indexable drills, center drills |
| Filters & Consumables | Coolant filters, oil mist filters, air filters |
| Fluids & Lubricants | Coolant, way lube, hydraulic oil, spindle oil |
| Electrical & Servo | Servo motors, drives, encoders, sensors |
| Belts & Seals | Drive belts, wiper seals, O-rings |

### Parts Inventory

#### Spindle Components

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor | Assets |
|---|---|---|---|---|---|---|---|---|
| Spindle Bearing Set - Front (CAT40) | NSK-7014CTYNDBLP5 | Spindle Components | 1,500 | 2 | 1 | set | NSK Precision | Haas VF-2 #1, Haas VF-2 #2, Haas VF-4 |
| Spindle Bearing Set - Rear (CAT40) | FAG-B7012CTP4SUL | Spindle Components | 950 | 2 | 1 | set | Schaeffler Group | Haas VF-2 #1, Haas VF-2 #2, Haas VF-4 |
| Spindle Drawbar Spring Set (Haas) | HAAS-93-0333 | Spindle Components | 225 | 4 | 2 | set | Haas Automation | Haas VF-2 #1, Haas VF-2 #2, Haas VF-4 |
| Spindle Drawbar Spring Set (DMG) | DMG-SPR-CMX800 | Spindle Components | 310 | 2 | 1 | set | DMG MORI Parts | DMG MORI CMX 800 V |

#### Ball Screws & Linear Guides

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor | Assets |
|---|---|---|---|---|---|---|---|---|
| Ball Screw X-Axis (Haas VF-2) | THK-W3212-944RCX | Ball Screws & Linear Guides | 3,200 | 1 | 1 | ea | THK America | Haas VF-2 #1, Haas VF-2 #2 |
| Ball Screw Nut (Haas VF) | NSK-BSN4010 | Ball Screws & Linear Guides | 1,100 | 2 | 1 | ea | NSK Precision | Haas VF-2 #1, Haas VF-2 #2, Haas VF-4 |
| Linear Guide Block (THK SSR25) | THK-SSR25XW | Ball Screws & Linear Guides | 320 | 4 | 2 | ea | THK America | Haas VF-2 #1, Haas VF-2 #2, Haas VF-4 |
| Ball Screw Support Bearing | NACHI-25TAB06DF | Ball Screws & Linear Guides | 425 | 2 | 1 | ea | NACHI America | All VMCs |

#### Way Covers & Enclosure

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor | Assets |
|---|---|---|---|---|---|---|---|---|
| X-Axis Way Cover (Haas VF-2) | HAAS-93-3015 | Way Covers & Enclosure | 1,050 | 1 | 1 | ea | Haas Automation | Haas VF-2 #1, Haas VF-2 #2 |
| Z-Axis Wiper Kit (Haas) | HAAS-93-0959 | Way Covers & Enclosure | 110 | 4 | 2 | kit | Haas Automation | All Haas VMCs |
| Spindle Window Wiper Seal | HAAS-58-4026 | Way Covers & Enclosure | 55 | 6 | 3 | ea | Haas Automation | All Haas machines |
| Door Interlock Switch | OMRON-D4NS4CF | Way Covers & Enclosure | 60 | 4 | 2 | ea | Omron Industrial | All CNC machines |

#### Tool Holders

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor | Assets |
|---|---|---|---|---|---|---|---|---|
| CAT40 End Mill Holder 3/4" | PARLEC-C40-75EM4 | Tool Holders | 100 | 12 | 6 | ea | Parlec / Techniks | All VMCs |
| CAT40 End Mill Holder 1/2" | PARLEC-C40-50EM3 | Tool Holders | 95 | 15 | 8 | ea | Parlec / Techniks | All VMCs |
| CAT40 ER32 Collet Chuck | TECH-04522-CAT40 | Tool Holders | 150 | 10 | 5 | ea | Techniks | All VMCs |
| CAT40 Shell Mill Arbor | KMT-A4016CSS075 | Tool Holders | 185 | 4 | 2 | ea | Kennametal | All VMCs |
| CAT40 Pull Stud (Retention Knob) | TECH-PS-CAT40-A1 | Tool Holders | 15 | 50 | 20 | ea | Techniks | All VMCs |
| BT40 ER32 Collet Chuck | NIKKEN-BT40-C32 | Tool Holders | 140 | 4 | 2 | ea | Lyndex-Nikken | DMG MORI CMX 800 V |

#### Collets

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| ER32 Collet 1/2" | REGO-1132-50000 | Collets | 35 | 10 | 5 | ea | Rego-Fix |
| ER32 Collet 3/8" | REGO-1132-37500 | Collets | 35 | 10 | 5 | ea | Rego-Fix |
| ER32 Collet 1/4" | REGO-1132-25000 | Collets | 35 | 10 | 5 | ea | Rego-Fix |
| ER32 Collet 3/4" | REGO-1132-75000 | Collets | 38 | 8 | 4 | ea | Rego-Fix |
| ER40 Collet 3/4" | TECH-04216-34 | Collets | 42 | 6 | 3 | ea | Techniks |

#### Cutting Tools - Milling

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| 1/2" 4-Flute Carbide End Mill | KMT-F4AE0500AWL | Cutting Tools - Milling | 55 | 20 | 10 | ea | Kennametal |
| 3/4" 4-Flute Carbide End Mill | HELICAL-07584 | Cutting Tools - Milling | 85 | 12 | 6 | ea | Harvey / Helical |
| 1/2" Ball Nose End Mill | SGS-36528 | Cutting Tools - Milling | 62 | 8 | 4 | ea | SGS Tool |
| 2" Indexable Face Mill Body | SAND-R245-050Q22 | Cutting Tools - Milling | 425 | 3 | 1 | ea | Sandvik Coromant |
| R245 Face Mill Insert | SAND-R245-1204-PM4340 | Cutting Tools - Milling | 15 | 50 | 20 | ea | Sandvik Coromant |
| 3" Shell Mill Body | KMT-KSSR300SE125 | Cutting Tools - Milling | 550 | 2 | 1 | ea | Kennametal |

#### Cutting Tools - Turning

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| CNMG 432 Turning Insert (Gen Purpose) | SAND-CNMG120408-PM4325 | Cutting Tools - Turning | 14 | 60 | 30 | ea | Sandvik Coromant |
| WNMG 432 Turning Insert | KMT-WNMG080408-MP | Cutting Tools - Turning | 12 | 40 | 20 | ea | Kennametal |
| DNMG 432 Finishing Insert | ISCAR-DNMG150408-TF | Cutting Tools - Turning | 13 | 30 | 15 | ea | Iscar |
| CCMT 32.51 Light Turning Insert | MIT-CCMT09T304 | Cutting Tools - Turning | 10 | 40 | 20 | ea | Mitsubishi Materials |
| Grooving Insert 0.125" | ISCAR-GRIP4004Y-IC354 | Cutting Tools - Turning | 16 | 20 | 10 | ea | Iscar |
| Threading Insert (External) | SAND-266RG16MM01A | Cutting Tools - Turning | 21 | 20 | 10 | ea | Sandvik Coromant |

#### Cutting Tools - Drilling

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| 1/2" Carbide Drill 5xD | SAND-860-0500-040A1 | Cutting Tools - Drilling | 102 | 6 | 3 | ea | Sandvik Coromant |
| 3/8" Carbide Drill 3xD | KMT-B105A09525 | Cutting Tools - Drilling | 75 | 8 | 4 | ea | Kennametal |
| 1" Indexable U-Drill Body | SAND-880-D1000L25 | Cutting Tools - Drilling | 340 | 2 | 1 | ea | Sandvik Coromant |
| U-Drill Insert | SAND-880-040305H-CLM | Cutting Tools - Drilling | 12 | 30 | 15 | ea | Sandvik Coromant |
| Center Drill #3 | CL-69103 | Cutting Tools - Drilling | 8 | 20 | 10 | ea | Chicago-Latrobe |

#### Filters & Consumables

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| Coolant Filter Bag | ERIEZ-HF1020-05 | Filters & Consumables | 24 | 20 | 10 | ea | Eriez |
| Oil Mist Filter Cartridge | DONALDSON-P191280 | Filters & Consumables | 80 | 6 | 3 | ea | Donaldson |
| Air Compressor Intake Filter | AC-1613940000 | Filters & Consumables | 45 | 3 | 2 | ea | Atlas Copco |
| Air/Oil Separator Element | AC-2901053200 | Filters & Consumables | 100 | 2 | 1 | ea | Atlas Copco |
| Spindle Air Purge Filter | PARKER-025AA | Filters & Consumables | 20 | 10 | 5 | ea | Parker Hannifin |

#### Fluids & Lubricants

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| Coolant - TRIM MicroSol 685 (55 gal) | MFS-MICROSOL685-55 | Fluids & Lubricants | 1,050 | 2 | 1 | drum | Master Fluid Solutions |
| Spindle Oil - Mobil Velocite No.10 (5 gal) | MOBIL-VELOCITE10-5 | Fluids & Lubricants | 70 | 3 | 2 | pail | ExxonMobil |
| Way Lube - Mobil Vactra No.2 (5 gal) | MOBIL-VACTRA2-5 | Fluids & Lubricants | 85 | 4 | 2 | pail | ExxonMobil |
| Hydraulic Oil - Mobil DTE 25 (5 gal) | MOBIL-DTE25-5 | Fluids & Lubricants | 65 | 3 | 2 | pail | ExxonMobil |

#### Electrical & Servo

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor | Assets |
|---|---|---|---|---|---|---|---|---|
| Servo Motor X-Axis (Haas) | HAAS-30-3010A | Electrical & Servo | 2,750 | 1 | 1 | ea | Haas Automation | All Haas VMCs |
| Spindle Encoder (Heidenhain) | HEIDENHAIN-ROD486 | Electrical & Servo | 1,100 | 1 | 1 | ea | Heidenhain | DMG MORI CMX 800 V, DMG MORI DMU 50 |
| Axis Encoder (Heidenhain) | HEIDENHAIN-ERN1387 | Electrical & Servo | 800 | 2 | 1 | ea | Heidenhain | DMG MORI CMX 800 V |
| Proximity Sensor | OMRON-E2EX5ME1Z | Electrical & Servo | 45 | 6 | 3 | ea | Omron Industrial | All CNC machines |
| Coolant Pump Motor | GRAYMILLS-IMV50F | Electrical & Servo | 400 | 2 | 1 | ea | Graymills | All CNC machines |
| Spindle Cooling Unit | HABOR-HBO250P | Electrical & Servo | 2,000 | 1 | 0 | ea | Habor | DMG MORI DMU 50 |

#### Belts & Seals

| Name | Part Number | Category | Cost ($) | Qty | Min Qty | Unit | Vendor |
|---|---|---|---|---|---|---|---|
| Spindle Drive Belt (Haas) | GATES-8MGT1600-36 | Belts & Seals | 105 | 3 | 2 | ea | Gates |
| ATC Belt (Haas) | HAAS-93-0616 | Belts & Seals | 65 | 3 | 2 | ea | Haas Automation |
| Linear Guide Wiper Seal Kit | THK-SSR25-WIPER | Belts & Seals | 28 | 10 | 5 | kit | THK America |

---

## 5. Sets of Parts (MultiParts)

| Set Name | Parts Included |
|---|---|
| Haas VF-2 Spindle Rebuild Kit | Spindle Bearing Set - Front (CAT40), Spindle Bearing Set - Rear (CAT40), Spindle Drawbar Spring Set (Haas), Spindle Window Wiper Seal |
| Haas Annual PM Kit | Z-Axis Wiper Kit (Haas), Spindle Drive Belt (Haas), ATC Belt (Haas), Spindle Air Purge Filter, Way Lube - Mobil Vactra No.2, Hydraulic Oil - Mobil DTE 25 |
| CAT40 Tooling Starter Set | CAT40 End Mill Holder 3/4" (x2), CAT40 End Mill Holder 1/2" (x3), CAT40 ER32 Collet Chuck (x2), CAT40 Shell Mill Arbor, CAT40 Pull Stud (x10) |
| ER32 Collet Set | ER32 Collet 1/4", ER32 Collet 3/8", ER32 Collet 1/2", ER32 Collet 3/4" |
| Turning Insert Starter Pack | CNMG 432 Turning Insert (x10), WNMG 432 Turning Insert (x10), DNMG 432 Finishing Insert (x10), CCMT 32.51 Insert (x10) |
| Compressor Annual Service Kit | Air Compressor Intake Filter, Air/Oil Separator Element, Hydraulic Oil - Mobil DTE 25 |
| Coolant System Refresh Kit | Coolant Filter Bag (x5), Coolant - TRIM MicroSol 685, Spindle Air Purge Filter (x2) |

---

## 6. Meters & Initial Readings

### Meter Categories

| Category Name | Description |
|---|---|
| Runtime | Operational hours, cycle counts |
| Vibration | Vibration levels and spectral data |
| Fluid Condition | Coolant, oil, and hydraulic fluid readings |
| Temperature | Bearing, oil, and spindle temperatures |
| Precision | Backlash, runout, positioning accuracy |
| Pressure | Air, hydraulic, differential pressure |

### Meters (create one per asset where applicable)

| Meter Name | Asset | Unit | Category | Update Freq (days) | Assigned Users |
|---|---|---|---|---|---|
| Spindle Hours - VF2 #1 | Haas VF-2 #1 | hours | Runtime | 7 | Tommy Nguyen |
| Spindle Hours - VF2 #2 | Haas VF-2 #2 | hours | Runtime | 7 | Angela Martinez |
| Spindle Hours - VF4 | Haas VF-4 | hours | Runtime | 7 | Angela Martinez |
| Spindle Hours - CMX800 | DMG MORI CMX 800 V | hours | Runtime | 7 | Carlos Ruiz |
| Spindle Hours - RoboDrill | Fanuc RoboDrill | hours | Runtime | 7 | Tommy Nguyen |
| Spindle Hours - QTN200 | Mazak QTN-200M | hours | Runtime | 7 | Rick Hoffman |
| Spindle Hours - ST20 #1 | Haas ST-20 #1 | hours | Runtime | 7 | Rick Hoffman |
| Spindle Hours - ST20 #2 | Haas ST-20 #2 | hours | Runtime | 7 | Jake Williams |
| Spindle Hours - DMU50 | DMG MORI DMU 50 | hours | Runtime | 7 | Jake Williams |
| Spindle Hours - UMC750 | Haas UMC-750 | hours | Runtime | 7 | Jake Williams |
| Spindle Vibration - VF2 #1 | Haas VF-2 #1 | mm/s RMS | Vibration | 7 | Sarah Chen |
| Spindle Vibration - VF4 | Haas VF-4 | mm/s RMS | Vibration | 7 | Sarah Chen |
| Spindle Vibration - CMX800 | DMG MORI CMX 800 V | mm/s RMS | Vibration | 7 | Sarah Chen |
| Spindle Vibration - DMU50 | DMG MORI DMU 50 | mm/s RMS | Vibration | 7 | Sarah Chen |
| Coolant Concentration - VMC Area | Haas VF-2 #1 | % Brix | Fluid Condition | 1 | Tommy Nguyen |
| Coolant Concentration - Lathe Area | Mazak QTN-200M | % Brix | Fluid Condition | 1 | Rick Hoffman |
| Coolant pH - VMC Area | Haas VF-2 #1 | pH | Fluid Condition | 1 | Tommy Nguyen |
| Coolant pH - Lathe Area | Mazak QTN-200M | pH | Fluid Condition | 1 | Rick Hoffman |
| X-Axis Backlash - VF2 #1 | Haas VF-2 #1 | mm | Precision | 90 | Sarah Chen |
| Y-Axis Backlash - VF2 #1 | Haas VF-2 #1 | mm | Precision | 90 | Sarah Chen |
| Z-Axis Backlash - VF2 #1 | Haas VF-2 #1 | mm | Precision | 90 | Sarah Chen |
| Spindle Runout - VF2 #1 | Haas VF-2 #1 | mm | Precision | 30 | Carlos Ruiz |
| Spindle Runout - DMU50 | DMG MORI DMU 50 | mm | Precision | 30 | Carlos Ruiz |
| Shop Air Pressure | Atlas Copco Compressor | PSI | Pressure | 1 | Sarah Chen |
| Oil Mist Filter DP - VMC | Donaldson Mist Collector #1 | in WG | Pressure | 7 | Marcus Johnson |
| Oil Mist Filter DP - Lathe | Donaldson Mist Collector #2 | in WG | Pressure | 7 | Marcus Johnson |
| Hydraulic Oil Temp - QTN200 | Mazak QTN-200M | C | Temperature | 1 | Rick Hoffman |
| Spindle Bearing Temp - DMU50 | DMG MORI DMU 50 | C | Temperature | 1 | Jake Williams |

### Sample Initial Readings

After creating meters, log these readings to populate the history:

| Meter | Date | Value | Notes |
|---|---|---|---|
| Spindle Hours - VF2 #1 | 2026-03-01 | 12,450 | |
| Spindle Hours - VF2 #1 | 2026-03-15 | 12,680 | |
| Spindle Hours - VF2 #1 | 2026-04-01 | 12,910 | |
| Spindle Hours - VF2 #2 | 2026-04-01 | 8,320 | |
| Spindle Hours - VF4 | 2026-04-01 | 10,150 | |
| Spindle Hours - CMX800 | 2026-04-01 | 6,780 | |
| Spindle Hours - DMU50 | 2026-04-01 | 3,250 | |
| Spindle Hours - QTN200 | 2026-04-01 | 9,430 | |
| Spindle Vibration - VF2 #1 | 2026-03-01 | 1.8 | Within spec |
| Spindle Vibration - VF2 #1 | 2026-04-01 | 2.1 | Slight increase - monitor |
| Spindle Vibration - VF4 | 2026-04-01 | 1.2 | Good |
| Spindle Vibration - CMX800 | 2026-04-01 | 0.9 | Excellent |
| Spindle Vibration - DMU50 | 2026-04-01 | 0.7 | Excellent |
| Coolant Concentration - VMC Area | 2026-04-01 | 7.5 | Target: 7-9% |
| Coolant Concentration - Lathe Area | 2026-04-01 | 8.2 | Good |
| Coolant pH - VMC Area | 2026-04-01 | 9.1 | Good |
| Coolant pH - Lathe Area | 2026-04-01 | 9.0 | Good |
| X-Axis Backlash - VF2 #1 | 2026-01-15 | 0.008 | Within tolerance |
| Y-Axis Backlash - VF2 #1 | 2026-01-15 | 0.010 | Approaching limit |
| Z-Axis Backlash - VF2 #1 | 2026-01-15 | 0.005 | Good |
| Spindle Runout - VF2 #1 | 2026-03-15 | 0.008 | Within spec |
| Spindle Runout - DMU50 | 2026-03-15 | 0.003 | Excellent |
| Shop Air Pressure | 2026-04-01 | 112 | Normal range |
| Oil Mist Filter DP - VMC | 2026-04-01 | 2.8 | OK (change at 4.0) |
| Hydraulic Oil Temp - QTN200 | 2026-04-01 | 42 | Normal |
| Spindle Bearing Temp - DMU50 | 2026-04-01 | 35 | Normal |

---

## 7. Vendors

| Name | Company Name | Vendor Type | Phone | Email | Website |
|---|---|---|---|---|---|
| Haas Parts Dept | Haas Automation | OEM Parts | (805) 278-1800 | parts@haascnc.com | haascnc.com |
| DMG MORI Parts | DMG MORI USA | OEM Parts | (847) 593-5400 | parts@dmgmori.com | dmgmori.com |
| Kennametal | Kennametal Inc. | Cutting Tools | (800) 446-7738 | orders@kennametal.com | kennametal.com |
| Sandvik Coromant | Sandvik Coromant | Cutting Tools | (800) 726-3845 | us.coromant@sandvik.com | sandvik.coromant.com |
| Iscar USA | Iscar Metals Inc. | Cutting Tools | (817) 258-3200 | info@iscarusa.com | iscar.com |
| Techniks / Parlec | Techniks Inc. | Toolholding | (800) 597-3921 | sales@techniks.com | techniks.com |
| Rego-Fix | Rego-Fix AG | Collets & Holders | (317) 870-5959 | info@regofix.com | rego-fix.com |
| THK America | THK Co., Ltd. | Linear Motion | (847) 310-1111 | sales@thk.com | thk.com |
| NSK Precision | NSK Ltd. | Bearings | (888) 675-2675 | info@nskprecision.com | nskamericas.com |
| Master Fluid Solutions | Master Fluid Solutions | Coolant | (800) 537-3365 | info@masterfluid.com | masterfluid.com |
| Atlas Copco | Atlas Copco USA | Compressed Air | (800) 232-3234 | info@atlascopco.com | atlascopco.com |
| Donaldson Filtration | Donaldson Company | Filtration | (800) 365-1331 | shop@donaldson.com | donaldson.com |
| GTI Spindle Technology | GTI Spindle Technology | Spindle Repair | (603) 669-5993 | service@gtispindle.com | gtispindle.com |
| Precision Machine Service | Precision Machine Service LLC | Machine Repair | (713) 555-0142 | service@precisionmachinesvs.com | _(local vendor)_ |

---

## 8. Preventive Maintenance (Scheduled Work Orders)

### Daily PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Daily Coolant Check - VMC Area | Check coolant concentration with refractometer (target 7-9% Brix), check pH (target 8.5-9.5), top off as needed. Log readings in meters. | Haas VF-2 #1 | Preventive | MEDIUM | Daily | Tommy Nguyen | 0.25 |
| Daily Coolant Check - Lathe Area | Check coolant concentration and pH, top off sump. Log readings. | Mazak QTN-200M | Preventive | MEDIUM | Daily | Rick Hoffman | 0.25 |
| Daily Machine Inspection - VMC | Wipe way covers, check for leaks (coolant/hydraulic/way lube), verify chip conveyor running, check hydraulic oil level, check air pressure at FRL. | Haas VF-2 #1 | Preventive | LOW | Daily | Tommy Nguyen | 0.25 |
| Daily Machine Inspection - Lathe | Wipe machine, check for leaks, verify chip conveyor, check hydraulic level, empty chip bin. | Mazak QTN-200M | Preventive | LOW | Daily | Rick Hoffman | 0.25 |

### Weekly PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Weekly Coolant Filter Clean | Clean coolant tank strainer and filter basket on all machines. Replace filter bags if fouled. | Eriez Coolant Recycler | Preventive | MEDIUM | Weekly (Monday) | Sarah Chen | 0.5 |
| Weekly Tool Holder Inspection | Inspect CAT40 holders for fretting, clean tapers with Scotch-Brite, check pull stud torque. | _(no specific asset)_ | Preventive | MEDIUM | Weekly (Wednesday) | Lisa Park | 0.5 |
| Weekly Way Cover Inspection | Inspect bellows and telescoping covers for tears, chip accumulation, or damage. Report issues immediately. | Haas VF-2 #1 | Preventive | MEDIUM | Weekly (Friday) | Sarah Chen | 0.25 |
| Weekly Compressor Drain | Drain water from air compressor receiver tank and check dryer operation. | Atlas Copco Compressor | Preventive | LOW | Weekly (Monday) | Marcus Johnson | 0.1 |
| Weekly Oil Mist Filter Check | Check differential pressure on oil mist collectors. Record in meters. Change filter if DP > 4 in. WG. | Donaldson Mist Collector #1 | Preventive | LOW | Weekly (Thursday) | Marcus Johnson | 0.15 |

### Monthly PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Monthly Coolant Sump Cleanout - VMC | Fully drain sump, remove tramp oil and sludge, clean strainers, recharge with fresh TRIM MicroSol 685 at 8% concentration. | Haas VF-2 #1 | Preventive | HIGH | Monthly | Sarah Chen | 2.0 |
| Monthly Spindle Runout Check | Check spindle TIR with test indicator in spindle. Max 0.0005" (0.012mm). Record in meters. | Haas VF-2 #1 | Preventive | HIGH | Monthly | Carlos Ruiz | 0.25 |
| Monthly ATC Inspection | Inspect auto tool changer arm/carousel, clean tool pockets, check tool change alignment, lubricate per OEM. | Haas VF-2 #1 | Preventive | MEDIUM | Monthly | Sarah Chen | 0.75 |
| Monthly Way Lube Top-Off | Check and top off way lube reservoir on all machines. Use Mobil Vactra No. 2 only. | Haas VF-2 #1 | Preventive | MEDIUM | Monthly | Marcus Johnson | 0.25 |
| Monthly Safety Interlock Test | Test all door interlocks, chuck guards, and E-stop circuits. Document results. | Haas VF-2 #1 | Preventive | HIGH | Monthly | Sarah Chen | 0.35 |
| Monthly Drive Belt Inspection | Inspect spindle drive belt and ATC belt for wear, cracking, or glazing. Check tension per OEM spec. | Haas VF-2 #1 | Preventive | MEDIUM | Monthly | Sarah Chen | 0.25 |
| Monthly Chip Conveyor Clean | Remove and clean chip conveyor chain/belt, inspect for wear, clean coolant return trough. | Haas VF-2 #1 | Preventive | LOW | Monthly | Marcus Johnson | 0.5 |

### Quarterly PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Quarterly Backlash Measurement | Measure backlash on X, Y, Z axes with dial indicator per Haas service procedure. Record in meters. If any axis >0.015mm, schedule ball screw adjustment. | Haas VF-2 #1 | Preventive | HIGH | Every 3 months | Sarah Chen | 1.0 |
| Quarterly Oil Mist Filter Replace | Replace oil mist filter cartridge (Donaldson P191280). Reset differential pressure baseline. | Donaldson Mist Collector #1 | Preventive | MEDIUM | Every 3 months | Marcus Johnson | 0.5 |
| Quarterly Coolant Filter Replace | Replace coolant filter bags on Eriez HydroFlow system. Inspect housing for buildup. | Eriez Coolant Recycler | Preventive | MEDIUM | Every 3 months | Sarah Chen | 0.5 |
| Quarterly Hydraulic Oil Sample | Pull hydraulic oil sample and send to lab for analysis (viscosity, water content, particle count). | Mazak QTN-200M | Preventive | MEDIUM | Every 3 months | Sarah Chen | 0.25 |
| Quarterly Electrical Cabinet Inspection | Open and inspect electrical cabinet: clean fan filters, check for loose connections, verify drive temperatures, look for discoloration. | Haas VF-2 #1 | Preventive | HIGH | Every 3 months | Sarah Chen | 0.75 |
| Quarterly E-Stop Circuit Test | Full test of emergency stop circuit on all machines. Verify all E-stops kill motion within spec. Document test results. | Haas VF-2 #1 | Preventive | HIGH | Every 3 months | Sarah Chen | 0.25 |
| Quarterly Machine Leveling Check | Check machine level with precision level (0.0005"/ft). Adjust leveling pads if needed. | DMG MORI DMU 50 | Preventive | HIGH | Every 3 months | Sarah Chen | 1.0 |

### Semi-Annual PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Semi-Annual Geometry Check | Full geometric alignment: spindle squareness to table (X and Y), tramming, parallelism. Correct if out of spec. | DMG MORI DMU 50 | Preventive | HIGH | Every 6 months | Carlos Ruiz, Sarah Chen | 4.0 |
| Semi-Annual Spindle Belt Replacement | Replace spindle drive belt (Gates 8MGT-1600-36). Check pulley alignment and tension. | Haas VF-2 #1 | Preventive | MEDIUM | Every 6 months | Sarah Chen | 1.5 |
| Semi-Annual Compressor Filter Replace | Replace air intake filter. Check oil level. Inspect hoses and connections. | Atlas Copco Compressor | Preventive | MEDIUM | Every 6 months | Marcus Johnson | 0.5 |
| Semi-Annual Linear Guide Wiper Replace | Inspect and replace wiper seals on all linear guide blocks. Clean and re-lubricate rails. | Haas VF-2 #1 | Preventive | MEDIUM | Every 6 months | Sarah Chen | 2.0 |

### Annual PMs

| Title | Description | Asset(s) | Category | Priority | Frequency | Assigned To | Est. Duration (hrs) |
|---|---|---|---|---|---|---|---|
| Annual Accuracy Audit (Ballbar Test) | Full machine accuracy audit using Renishaw ballbar. Test circularity, backlash, squareness, servo mismatch. Compare to baseline. Schedule corrections as needed. | DMG MORI DMU 50 | Preventive | HIGH | Yearly | External Vendor (Precision Machine Service) | 8.0 |
| Annual Hydraulic Oil Change | Drain and replace hydraulic oil. Replace suction strainer. Clean reservoir. Use Mobil DTE 25. | Mazak QTN-200M | Preventive | HIGH | Yearly | Sarah Chen | 2.0 |
| Annual Way Lube Oil Change | Drain and replace way lube oil on all machines. Use Mobil Vactra No. 2. | Haas VF-2 #1 | Preventive | MEDIUM | Yearly | Marcus Johnson | 1.0 |
| Annual Drawbar Spring Inspection | Remove and inspect spindle drawbar springs. Replace if compressed height is out of spec or if >8,000 spindle hours since last replacement. | Haas VF-2 #1 | Preventive | HIGH | Yearly | Sarah Chen | 2.0 |
| Annual Compressor Full Service | Full compressor service: oil change, air/oil separator, intake filter, belt inspection, drain valve check, safety valve test. | Atlas Copco Compressor | Preventive | HIGH | Yearly | Atlas Copco (vendor) | 4.0 |
| Annual CMM Calibration | Full CMM calibration by Zeiss certified technician. Update calibration certificate. | Zeiss Contura CMM | Preventive | HIGH | Yearly | External Vendor | 8.0 |

---

## 9. Sample Work Orders

Seed a mix of open, in-progress, and completed work orders to show the system in use.

### Open Work Orders

| Title | Description | Asset | Priority | Status | Category | Assigned To | Due Date |
|---|---|---|---|---|---|---|---|
| Investigate vibration increase on VF-2 #1 | Spindle vibration reading increased from 1.8 to 2.1 mm/s RMS over last month. Not critical yet but trending upward. Perform detailed vibration analysis, check bearing condition, inspect belt tension. | Haas VF-2 #1 | MEDIUM | OPEN | Corrective | Sarah Chen | 2026-04-10 |
| Replace torn Y-axis way cover - VF-4 | Operator reported torn accordion bellows on Y-axis way cover. Chips getting past cover onto linear guides. Order Hennig replacement and schedule install during next planned downtime. | Haas VF-4 | HIGH | OPEN | Corrective | Sarah Chen | 2026-04-07 |
| Coolant pump making noise - ST-20 #2 | Grinding/whining noise from coolant pump on Haas ST-20 #2. Coolant flow appears reduced. Inspect pump impeller and motor bearings. May need pump replacement (Graymills IMV50-F on shelf). | Haas ST-20 #2 | HIGH | OPEN | Corrective | Marcus Johnson | 2026-04-06 |

### In-Progress Work Orders

| Title | Description | Asset | Priority | Status | Category | Assigned To | Due Date |
|---|---|---|---|---|---|---|---|
| Replace door interlock switch - RoboDrill | Door interlock switch intermittently failing to register closed. Machine faults randomly during cycle. Replacement Omron D4NS-4CF in stock. | Fanuc RoboDrill | URGENT | IN_PROGRESS | Corrective | Sarah Chen | 2026-04-04 |
| Quarterly backlash check - DMU 50 | Scheduled quarterly backlash measurement on all 5 axes. Ball bar test also due. Coordinate with Jake Williams for machine availability. | DMG MORI DMU 50 | HIGH | IN_PROGRESS | Preventive | Sarah Chen | 2026-04-05 |

### Completed Work Orders (recent history)

| Title | Description | Asset | Priority | Status | Completed On | Completed By | Feedback |
|---|---|---|---|---|---|---|---|
| Monthly ATC inspection - VF-2 #1 | Cleaned tool pockets, verified tool change alignment, lubricated arm pivot. All within spec. | Haas VF-2 #1 | MEDIUM | COMPLETED | 2026-03-28 | Sarah Chen | ATC operating normally. Tool pocket #14 slightly worn - monitor next month. |
| Replace coolant filter bags | Replaced 5 coolant filter bags on Eriez HydroFlow. Previous bags heavily loaded with aluminum fines. | Eriez Coolant Recycler | LOW | COMPLETED | 2026-03-25 | Marcus Johnson | Bags were at capacity. Consider increasing change frequency to every 2 months during heavy aluminum jobs. |
| Emergency spindle repair - Doosan Lynx | Spindle seized during production. Bearings failed catastrophically. Removed spindle, sent to GTI Spindle Technology for emergency rebuild. Reinstalled and tested. Total downtime: 5 days. | Doosan Lynx 2100LB | URGENT | COMPLETED | 2026-03-20 | Sarah Chen | Root cause: coolant ingress through worn labyrinth seal. Added monthly seal inspection to PM checklist. GTI rebuild cost: $6,800. |
| Annual compressor service | Full annual service performed by Atlas Copco technician. Oil change, separator, intake filter, belt check. All passed. Next service due March 2027. | Atlas Copco Compressor | MEDIUM | COMPLETED | 2026-03-15 | Dave Kowalski | Atlas Copco tech noted compressor running slightly warm. Recommended cleaning radiator more frequently (quarterly vs semi-annual). |
| Install new bar feeder - Mazak QTN | Installed LNS Alpha SL65 S bar feeder on Mazak QTN-200M. Configured for 2" bar stock. Tested with production run - feeding smoothly. | Mazak QTN-200M | LOW | COMPLETED | 2026-03-10 | Sarah Chen, Marcus Johnson | Bar feeder commissioning complete. Operator training done for Day shift. Swing shift training scheduled for next week. |

---

## 10. Files & Attachments

Upload these types of files and attach to the corresponding entities. Use real manuals/documents where available.

### Machine Manuals (attach to Assets)

| File Name | Type | Attach To | Source / Notes |
|---|---|---|---|
| Haas-VF2-Operators-Manual.pdf | DOCUMENT | Haas VF-2 #1, Haas VF-2 #2 | Download from [Haas Resource Center](https://www.haascnc.com/service/manuals.html) |
| Haas-Mill-Maintenance-Guide.pdf | DOCUMENT | All Haas VMCs | Available from Haas service portal |
| DMG-MORI-CMX800V-Manual.pdf | DOCUMENT | DMG MORI CMX 800 V | Request from DMG MORI service |
| Mazak-QTN200M-Operation-Manual.pdf | DOCUMENT | Mazak QTN-200M | Available from Mazak support portal |
| FANUC-RoboDrill-Maintenance.pdf | DOCUMENT | Fanuc RoboDrill | FANUC America support downloads |
| Atlas-Copco-GA37-Service-Manual.pdf | DOCUMENT | Atlas Copco Compressor | Atlas Copco documentation portal |

### Safety Data Sheets (attach to Parts)

| File Name | Type | Attach To | Source |
|---|---|---|---|
| TRIM-MicroSol-685-SDS.pdf | DOCUMENT | Coolant - TRIM MicroSol 685 | masterfluid.com - SDS downloads |
| Mobil-Vactra-No2-SDS.pdf | DOCUMENT | Way Lube - Mobil Vactra No.2 | mobil.com - product data sheets |
| Mobil-DTE-25-SDS.pdf | DOCUMENT | Hydraulic Oil - Mobil DTE 25 | mobil.com - product data sheets |
| Mobil-Velocite-10-SDS.pdf | DOCUMENT | Spindle Oil - Mobil Velocite No.10 | mobil.com - product data sheets |

### PM Checklists (attach to Preventive Maintenance)

| File Name | Type | Attach To |
|---|---|---|
| Daily-Machine-Inspection-Checklist.pdf | DOCUMENT | Daily Machine Inspection - VMC, Daily Machine Inspection - Lathe |
| Monthly-PM-Checklist-VMC.pdf | DOCUMENT | Monthly ATC Inspection, Monthly Safety Interlock Test |
| Quarterly-Backlash-Measurement-Form.pdf | DOCUMENT | Quarterly Backlash Measurement |
| Annual-Accuracy-Audit-Template.pdf | DOCUMENT | Annual Accuracy Audit (Ballbar Test) |
| Coolant-Management-Log.pdf | DOCUMENT | Daily Coolant Check - VMC Area, Daily Coolant Check - Lathe Area |

### Work Order Photos (attach to completed Work Orders)

| File Name | Type | Attach To |
|---|---|---|
| doosan-spindle-damage-01.jpg | IMAGE | Emergency spindle repair - Doosan Lynx |
| doosan-spindle-damage-02.jpg | IMAGE | Emergency spindle repair - Doosan Lynx |
| bar-feeder-install-complete.jpg | IMAGE | Install new bar feeder - Mazak QTN |
| vf4-way-cover-torn.jpg | IMAGE | Replace torn Y-axis way cover - VF-4 |

---

## 11. Reference Videos

Attach these as links in work order descriptions, asset notes, or PM instructions.

### Spindle Repair & Service

| Title | Channel | URL | Use With |
|---|---|---|---|
| CNC Spindle Repair - Complete Teardown | GTI Spindle Technology | https://www.youtube.com/watch?v=3ZGJxFNfDQs | Emergency spindle repair WOs |
| Haas Mill Spindle Service | Haas Automation | https://www.youtube.com/@HaasAutomation (search: spindle service) | Annual Drawbar Spring Inspection |
| How to Check Spindle Runout | Haas Tip of the Day | https://www.youtube.com/@HaasAutomation (search: spindle runout) | Monthly Spindle Runout Check |

### Way Covers & Machine Maintenance

| Title | Channel | URL | Use With |
|---|---|---|---|
| Haas Way Cover Replacement | Haas Automation | https://www.youtube.com/@HaasAutomation (search: way cover) | Way cover replacement WOs |
| CNC Machine Leveling Procedure | Haas Tip of the Day | https://www.youtube.com/@HaasAutomation (search: machine leveling) | Quarterly Machine Leveling Check |
| How to Level a CNC Machine | Edge Precision | https://www.youtube.com/watch?v=qTJXnHsR2XA | Quarterly Machine Leveling Check |

### Coolant System

| Title | Channel | URL | Use With |
|---|---|---|---|
| CNC Coolant Maintenance Best Practices | Master Fluid Solutions | https://www.youtube.com/@MasterFluidSolutions | Daily Coolant Checks, Monthly Sump Cleanout |
| How to Clean a CNC Coolant Tank | TITANS of CNC | https://www.youtube.com/@TitansofCNC (search: coolant) | Monthly Coolant Sump Cleanout |
| Coolant Concentration Testing | NYC CNC | https://www.youtube.com/@NYC-CNC (search: coolant) | Daily Coolant Check PMs |

### Ball Screw & Alignment

| Title | Channel | URL | Use With |
|---|---|---|---|
| Renishaw Ballbar Testing Explained | Renishaw | https://www.youtube.com/@RenishawPlc (search: ballbar) | Annual Accuracy Audit |
| Backlash Measurement on CNC | Haas Tip of the Day | https://www.youtube.com/@HaasAutomation (search: backlash) | Quarterly Backlash Measurement |

### General CNC Maintenance Channels

| Channel | URL | Description |
|---|---|---|
| Haas Automation | https://www.youtube.com/@HaasAutomation | Official service videos, Tip of the Day series (hundreds of maintenance topics) |
| TITANS of CNC | https://www.youtube.com/@TitansofCNC | Shop tours, machining, maintenance best practices |
| NYC CNC | https://www.youtube.com/@NYC-CNC | Practical Haas shop operations and maintenance |
| This Old Tony | https://www.youtube.com/@ThisOldTony | Machine maintenance, repair, and shop tips |
| Abom79 | https://www.youtube.com/@Abom79 | Large machine maintenance and manual machining |

---

## Data Entry Order

To avoid foreign key issues, create data in this order:

1. **Locations** (parent locations first, then children)
2. **Asset Categories**, **Part Categories**, **Meter Categories**, **Work Order Categories**
3. **Vendors**
4. **People (Users)** and **Teams**
5. **Assets** (assign locations, categories, primary users)
6. **Parent-child asset relationships**
7. **Parts** (assign categories, vendors, assets)
8. **Sets of Parts (MultiParts)**
9. **Meters** (assign to assets, assign users)
10. **Initial Meter Readings**
11. **Files** (upload and attach to assets, parts, etc.)
12. **Preventive Maintenance** (with schedules, assigned users/teams, assets)
13. **Work Orders** (open, in-progress, and completed)

---

## Work Order Categories

Create these categories for work order classification:

| Category Name | Description |
|---|---|
| Preventive | Scheduled preventive maintenance |
| Corrective | Reactive repairs and fixes |
| Emergency | Urgent breakdown repairs |
| Inspection | Audits, measurements, quality checks |
| Installation | New equipment or component installation |
| Calibration | Precision calibration and alignment |
| Safety | Safety-related inspections and repairs |
