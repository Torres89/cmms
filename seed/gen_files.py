# -*- coding: utf-8 -*-
"""Generate realistic-looking demo attachments: equipment manuals & SDS & PM
checklists as PDFs, and work-order photos as JPGs. Output -> seed/files/."""
import os
from fpdf import FPDF
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "files")
os.makedirs(OUT, exist_ok=True)


def _clean(s):
    # fpdf core fonts are latin-1 only
    return (s.replace("’", "'").replace("‘", "'")
             .replace("“", '"').replace("”", '"')
             .replace("–", "-").replace("—", "-")
             .replace("…", "...").encode("latin-1", "replace").decode("latin-1"))


class Doc(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120)
        self.cell(0, 8, _clean(self.title or ""), 0, 1, "R")
        self.set_text_color(0)

    def footer(self):
        self.set_y(-15)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(120)
        self.cell(0, 10, f"Page {self.page_no()}  -  Atlas CMMS Demo Document (sample content)", 0, 0, "C")
        self.set_text_color(0)


def pdf(filename, title, subtitle, sections, doc_type="MANUAL"):
    d = Doc()
    d.title = title
    d.set_auto_page_break(True, margin=20)
    # cover
    d.add_page()
    d.ln(40)
    d.set_font("Helvetica", "B", 13)
    d.set_text_color(30, 60, 120)
    d.cell(0, 10, _clean(doc_type), 0, 1, "C")
    d.set_text_color(0)
    d.ln(6)
    d.set_font("Helvetica", "B", 24)
    d.multi_cell(0, 12, _clean(title), 0, "C")
    d.ln(4)
    d.set_font("Helvetica", "", 13)
    d.set_text_color(80)
    d.multi_cell(0, 8, _clean(subtitle), 0, "C")
    d.set_text_color(0)
    d.ln(30)
    d.set_font("Helvetica", "I", 10)
    d.multi_cell(0, 6, _clean(
        "This is sample documentation generated for an Atlas CMMS demonstration "
        "environment. It mirrors the structure of a real OEM document but does not "
        "contain proprietary manufacturer content."), 0, "C")
    # body
    d.add_page()
    for i, (head, body) in enumerate(sections, 1):
        d.set_font("Helvetica", "B", 14)
        d.set_text_color(30, 60, 120)
        d.set_x(d.l_margin)
        d.multi_cell(0, 9, _clean(f"{i}. {head}"))
        d.set_text_color(0)
        d.ln(1)
        d.set_font("Helvetica", "", 11)
        for line in body:
            d.set_x(d.l_margin)
            if line.startswith("- "):
                d.multi_cell(0, 6, _clean("   -  " + line[2:]))
            else:
                d.multi_cell(0, 6, _clean(line))
        d.ln(4)
    path = os.path.join(OUT, filename)
    d.output(path)
    return path


def photo(filename, label, sublabel, base_color, kind="machine"):
    W, H = 1000, 750
    img = Image.new("RGB", (W, H), base_color)
    dr = ImageDraw.Draw(img)
    # crude "industrial" texture / shapes
    for x in range(0, W, 40):
        dr.line([(x, 0), (x, H)], fill=tuple(max(0, ch - 12) for ch in base_color), width=1)
    if kind == "damage":
        dr.ellipse([300, 220, 700, 540], outline=(40, 40, 40), width=8)
        dr.line([360, 280, 640, 500], fill=(180, 30, 30), width=10)
        dr.line([640, 280, 360, 500], fill=(180, 30, 30), width=10)
    elif kind == "install":
        dr.rectangle([200, 250, 800, 520], outline=(30, 30, 30), width=8)
        dr.rectangle([260, 300, 740, 470], fill=tuple(min(255, ch + 25) for ch in base_color))
    else:  # cover tear
        dr.rectangle([150, 200, 850, 560], outline=(30, 30, 30), width=6)
        pts = [(300, 200), (340, 320), (300, 400), (380, 520), (430, 560)]
        dr.line(pts, fill=(20, 20, 20), width=9)
    # caption bar
    dr.rectangle([0, H - 110, W, H], fill=(0, 0, 0))
    try:
        f1 = ImageFont.truetype("arialbd.ttf", 40)
        f2 = ImageFont.truetype("arial.ttf", 26)
    except Exception:
        f1 = ImageFont.load_default()
        f2 = ImageFont.load_default()
    dr.text((30, H - 95), label, fill=(255, 255, 255), font=f1)
    dr.text((30, H - 45), sublabel, fill=(200, 200, 200), font=f2)
    path = os.path.join(OUT, filename)
    img.save(path, "JPEG", quality=85)
    return path


# ------------------------------------------------------------------ MANUALS
def gen_manuals():
    safety = ("Safety Precautions", [
        "Read and understand all safety information before operating or servicing this machine.",
        "- Always lock out / tag out (LOTO) the main disconnect before maintenance.",
        "- Wait for all spindle and axis motion to stop before opening guards.",
        "- Wear approved eye protection and safety footwear in the machining area.",
        "- Keep the work envelope clear of tools, fixtures, and personnel during operation.",
        "- Never defeat door interlocks or safety light curtains.",
    ])
    maint = ("Routine Maintenance Schedule", [
        "Daily:",
        "- Check coolant level and concentration; top off as required.",
        "- Wipe down way covers and inspect for chips and leaks.",
        "- Verify air pressure at the FRL and drain the air filter bowl.",
        "Weekly:",
        "- Clean the chip conveyor and coolant tank strainer.",
        "- Inspect way wipers and telescoping covers for damage.",
        "Monthly:",
        "- Check and top off the way lube reservoir (use OEM-specified oil only).",
        "- Inspect the spindle drawbar and ATC operation.",
        "Annually:",
        "- Replace way lube and hydraulic oil; service the spindle as required.",
    ])
    lube = ("Lubrication Specifications", [
        "Way lube: ISO VG 68 way oil (e.g. Mobil Vactra No. 2).",
        "Hydraulic: ISO VG 32 (e.g. Mobil DTE 25).",
        "Spindle oil: ISO VG 10 spindle oil (e.g. Mobil Velocite No. 10).",
        "- Follow the lubrication chart and intervals in the maintenance schedule.",
    ])
    tshoot = ("Troubleshooting", [
        "Alarm: low lubrication pressure -> check reservoir level and pump operation.",
        "Symptom: poor surface finish -> check spindle runout, tool holders, and way lube.",
        "Symptom: axis following error -> inspect ball screw, bearings, and servo drive.",
        "Symptom: coolant flow reduced -> inspect pump impeller and filter screens.",
    ])

    gen = [
        ("Haas-VF2-Operators-Manual.pdf", "Haas VF-2 Operator's Manual",
         "Vertical Machining Center - Operation & Setup", "OPERATOR MANUAL"),
        ("Haas-Mill-Maintenance-Guide.pdf", "Haas Mill Maintenance Guide",
         "Preventive Maintenance for VF-Series Mills", "MAINTENANCE GUIDE"),
        ("DMG-MORI-CMX800V-Manual.pdf", "DMG MORI CMX 800 V Manual",
         "Vertical Machining Center - Service Reference", "SERVICE MANUAL"),
        ("Mazak-QTN200M-Operation-Manual.pdf", "Mazak QUICK TURN NEXUS 200-M",
         "CNC Turning Center - Operation Manual", "OPERATOR MANUAL"),
        ("FANUC-RoboDrill-Maintenance.pdf", "FANUC ROBODRILL a-D21MiB5",
         "Compact Machining Center - Maintenance", "MAINTENANCE GUIDE"),
        ("Atlas-Copco-GA37-Service-Manual.pdf", "Atlas Copco GA37 VSD+",
         "Rotary Screw Air Compressor - Service Manual", "SERVICE MANUAL"),
    ]
    for fn, title, sub, dt in gen:
        secs = [safety, maint, lube, tshoot]
        if "Compressor" in title:
            secs = [safety,
                    ("Routine Maintenance Schedule", [
                        "Daily: check for leaks and abnormal noise; verify display for alarms.",
                        "Weekly: drain condensate from the receiver tank; check the dryer.",
                        "Every 4000 h: replace oil filter and air/oil separator; change oil.",
                        "Annually: replace intake filter; test safety valve; inspect belts/coupling.",
                    ]),
                    ("Fluid Specifications", [
                        "Use OEM Roto-Z / equivalent screw compressor oil.",
                        "- Check oil level with the unit stopped and depressurized.",
                    ]),
                    tshoot]
        pdf(fn, title, sub, secs, dt)
        print("manual:", fn)


# ------------------------------------------------------------------ SDS
def gen_sds():
    def sds_sections(product, maker):
        return [
            ("Identification", [
                f"Product name: {product}",
                f"Manufacturer: {maker}",
                "Recommended use: metalworking / machine lubrication.",
                "Emergency phone (CHEMTREC): 1-800-424-9300.",
            ]),
            ("Hazards Identification", [
                "Classification: not classified as hazardous under OSHA HCS (sample data).",
                "- Prolonged skin contact may cause mild irritation.",
                "- Avoid contact with eyes; use in well-ventilated areas.",
            ]),
            ("First-Aid Measures", [
                "Skin: wash with soap and water. Eyes: flush 15 minutes with water.",
                "Inhalation: move to fresh air. Ingestion: do NOT induce vomiting; seek advice.",
            ]),
            ("Handling & Storage", [
                "Store in a cool, dry, well-ventilated area away from oxidizers.",
                "- Keep containers closed; avoid mist generation.",
            ]),
            ("Disposal", [
                "Dispose of in accordance with local, state, and federal regulations.",
            ]),
        ]
    items = [
        ("TRIM-MicroSol-685-SDS.pdf", "TRIM MicroSol 685", "Master Fluid Solutions",
         "Semi-synthetic metalworking coolant"),
        ("Mobil-Vactra-No2-SDS.pdf", "Mobil Vactra No. 2", "ExxonMobil",
         "Way lubricant (ISO VG 68)"),
        ("Mobil-DTE-25-SDS.pdf", "Mobil DTE 25", "ExxonMobil",
         "Hydraulic oil (ISO VG 32)"),
        ("Mobil-Velocite-10-SDS.pdf", "Mobil Velocite No. 10", "ExxonMobil",
         "Spindle oil (ISO VG 10)"),
    ]
    for fn, prod, maker, sub in items:
        pdf(fn, f"Safety Data Sheet - {prod}", sub, sds_sections(prod, maker), "SAFETY DATA SHEET")
        print("sds:", fn)


# ------------------------------------------------------------------ CHECKLISTS
def gen_checklists():
    items = [
        ("Daily-Machine-Inspection-Checklist.pdf", "Daily Machine Inspection Checklist",
         "Per-shift CNC machine checks", [
             ("Inspection Items", [
                 "- [ ] Coolant level and concentration checked / topped off",
                 "- [ ] Way covers wiped; no chips on guides",
                 "- [ ] No coolant / hydraulic / way-lube leaks",
                 "- [ ] Chip conveyor running freely; bin emptied",
                 "- [ ] Hydraulic oil level within sight glass range",
                 "- [ ] Air pressure at FRL within spec; bowl drained",
                 "- [ ] No unusual noise or vibration on startup",
             ]),
             ("Sign-Off", ["Operator: ______________   Shift: ______   Date: __________",
                           "Notes / issues found: ____________________________________"]),
         ]),
        ("Monthly-PM-Checklist-VMC.pdf", "Monthly PM Checklist - VMC",
         "Monthly preventive maintenance for vertical machining centers", [
             ("ATC & Spindle", [
                 "- [ ] Tool pockets cleaned; tool change alignment verified",
                 "- [ ] ATC arm lubricated per OEM",
                 "- [ ] Spindle taper inspected; drawbar operation checked",
             ]),
             ("Safety", [
                 "- [ ] Door interlocks tested",
                 "- [ ] E-stop circuit tested",
                 "- [ ] Chuck / guard interlocks verified",
             ]),
             ("Sign-Off", ["Technician: ______________   Date: __________"]),
         ]),
        ("Quarterly-Backlash-Measurement-Form.pdf", "Quarterly Backlash Measurement Form",
         "Axis backlash record (limit 0.015 mm)", [
             ("Measurements", [
                 "X-axis backlash: ________ mm",
                 "Y-axis backlash: ________ mm",
                 "Z-axis backlash: ________ mm",
                 "- Record in CMMS meter. If any axis > 0.015 mm, schedule ball-screw adjustment.",
             ]),
             ("Sign-Off", ["Technician: ______________   Date: __________"]),
         ]),
        ("Annual-Accuracy-Audit-Template.pdf", "Annual Accuracy Audit Template",
         "Renishaw ballbar accuracy audit", [
             ("Test Results", [
                 "Circularity (XY): ________ um   Backlash: ________ um",
                 "Squareness: ________ um/m       Servo mismatch: ________ ms",
                 "- Compare to baseline; schedule corrections as needed.",
             ]),
             ("Sign-Off", ["Auditor: ______________   Date: __________"]),
         ]),
        ("Coolant-Management-Log.pdf", "Coolant Management Log",
         "Daily coolant concentration & pH record", [
             ("Daily Log", [
                 "Date | Machine | Concentration (% Brix) | pH | Top-off (gal) | Initials",
                 "- Target concentration 7-9% Brix; target pH 8.5-9.5.",
                 "- ____ | ______ | ______ | ____ | ____ | ____",
                 "- ____ | ______ | ______ | ____ | ____ | ____",
                 "- ____ | ______ | ______ | ____ | ____ | ____",
             ]),
         ]),
    ]
    for fn, title, sub, secs in items:
        pdf(fn, title, sub, secs, "PM CHECKLIST")
        print("checklist:", fn)


# ------------------------------------------------------------------ PHOTOS
def gen_photos():
    photo("doosan-spindle-damage-01.jpg", "Doosan Lynx - Spindle Damage",
          "Failed front bearing - coolant ingress", (70, 75, 85), "damage")
    photo("doosan-spindle-damage-02.jpg", "Doosan Lynx - Spindle Teardown",
          "Labyrinth seal wear detail", (60, 65, 72), "damage")
    photo("bar-feeder-install-complete.jpg", "LNS Bar Feeder Install",
          "Alpha SL65 S commissioned on Mazak QTN-200M", (90, 100, 110), "install")
    photo("vf4-way-cover-torn.jpg", "Haas VF-4 - Torn Way Cover",
          "Y-axis accordion bellows damage", (95, 90, 80), "cover")
    print("photos done")


if __name__ == "__main__":
    gen_manuals()
    gen_sds()
    gen_checklists()
    gen_photos()
    print("\nAll files generated in", OUT)
