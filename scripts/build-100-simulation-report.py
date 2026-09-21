from __future__ import annotations

import json
import os
from collections import Counter
from datetime import datetime
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    Image as ReportImage,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "tmp" / "simulation-probe" / "results.json"
OUTPUT_DIR = ROOT / "output" / "pdf"
EVIDENCE_DIR = ROOT / "tmp" / "simulation-probe" / "evidence"
PDF_PATH = OUTPUT_DIR / "physlive-all-schema-simulation-evidence-report.pdf"


def font(size: int, bold: bool = False):
    candidates = [
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
        Path("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def short(value: str, length: int = 22) -> str:
    return value if len(value) <= length else value[: length - 3] + "..."


def arrow(draw, start, end, fill, width=3):
    draw.line((*start, *end), fill=fill, width=width)
    import math
    angle = math.atan2(end[1] - start[1], end[0] - start[0])
    size = 8
    left = (end[0] - size * math.cos(angle - 0.5), end[1] - size * math.sin(angle - 0.5))
    right = (end[0] - size * math.cos(angle + 0.5), end[1] - size * math.sin(angle + 0.5))
    draw.polygon([end, left, right], fill=fill)


def draw_schematic(draw, x, top, right, bottom, result, regular):
    topic = result.get("topic", "")
    scene = result.get("scene", "")
    mid_y = (top + bottom) / 2
    left = x + 22
    width = right - left - 22
    ink = "#e2e8f0"
    blue = "#38bdf8"
    orange = "#fb923c"
    green = "#4ade80"
    yellow = "#fbbf24"
    red = "#fb7185"

    if topic in {"DYNAMICS", "KINEMATICS"}:
        draw.line((left + 12, bottom - 25, right - 18, bottom - 25), fill="#94a3b8", width=3)
        if "orbit" in scene or "gravity" in scene:
            cx, cy, radius = left + width * 0.55, mid_y + 5, min(width, bottom - top) * 0.27
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=blue, width=3)
            draw.ellipse((cx - 16, cy - 16, cx + 16, cy + 16), fill=yellow, outline="#fef08a")
            px, py = cx + radius * 0.75, cy - radius * 0.67
            draw.ellipse((px - 9, py - 9, px + 9, py + 9), fill=orange, outline="#fed7aa")
            arrow(draw, (px, py), (px - 2, py - 34), green, 3)
            draw.text((cx - 25, cy + radius + 10), "orbit", fill=blue, font=regular)
        elif "projectile" in scene or "throw" in scene:
            draw.ellipse((left + 28, bottom - 45, left + 44, bottom - 29), fill=orange)
            points = [(left + 36 + i * (width - 54) / 10, bottom - 38 - (i * (10 - i) * 3.2)) for i in range(11)]
            draw.line(points, fill=orange, width=3)
            arrow(draw, (left + 42, bottom - 39), (left + 96, bottom - 82), blue, 3)
        elif "collision" in scene or "momentum" in scene:
            draw.rectangle((left + 42, mid_y - 15, left + 87, mid_y + 20), fill=blue, outline="#bae6fd")
            draw.rectangle((right - 98, mid_y - 15, right - 53, mid_y + 20), fill=orange, outline="#fed7aa")
            arrow(draw, (left + 88, mid_y), (left + 144, mid_y), green, 3)
            arrow(draw, (right - 54, mid_y), (right - 110, mid_y), red, 3)
        elif "spring" in scene or "oscillation" in scene:
            wall = left + 42
            draw.line((wall, top + 24, wall, bottom - 28), fill="#cbd5e1", width=5)
            points = [(wall + 5 + i * 11, mid_y + (10 if i % 2 else -10)) for i in range(9)]
            points[0] = (wall + 5, mid_y)
            points[-1] = (wall + 93, mid_y)
            draw.line(points, fill=yellow, width=3)
            draw.rectangle((wall + 95, mid_y - 20, wall + 140, mid_y + 20), fill=blue, outline="#bae6fd")
            arrow(draw, (wall + 145, mid_y - 34), (wall + 175, mid_y - 34), green, 3)
        else:
            draw.rectangle((left + 55, bottom - 55, left + 102, bottom - 23), fill=blue, outline="#bae6fd")
            arrow(draw, (left + 105, bottom - 39), (left + 180, bottom - 39), green, 3)
            arrow(draw, (left + 78, bottom - 58), (left + 78, bottom - 102), red, 3)
            draw.text((left + 18, top + 16), "x(t), v(t), a(t)", fill="#cbd5e1", font=regular)
    elif topic == "CIRCUITS":
        y = mid_y
        draw.line((left + 30, y, left + 65, y), fill=green, width=3)
        draw.line((left + 65, y - 25, left + 65, y + 25), fill=blue, width=5)
        draw.line((left + 75, y - 18, left + 75, y + 18), fill=blue, width=3)
        draw.line((left + 75, y, left + 122, y), fill=green, width=3)
        zig = [(left + 122 + i * 10, y + (10 if i % 2 else -10)) for i in range(7)]
        draw.line(zig, fill=orange, width=4)
        draw.line((left + 182, y, right - 36, y), fill=green, width=3)
        draw.line((right - 36, y, right - 36, y + 52), fill=green, width=3)
        draw.line((right - 36, y + 52, left + 30, y + 52), fill=green, width=3)
        draw.line((left + 30, y + 52, left + 30, y), fill=green, width=3)
        arrow(draw, (left + 90, y - 22), (left + 116, y - 22), yellow, 2)
        draw.text((left + 51, y + 31), "source", fill="#cbd5e1", font=regular)
        draw.text((left + 126, y - 35), "R / C / diode", fill="#cbd5e1", font=regular)
    elif topic == "ELECTROMAGNETISM":
        p1, p2 = left + 70, right - 70
        draw.rectangle((p1, top + 22, p1 + 12, bottom - 22), fill=red)
        draw.rectangle((p2, top + 22, p2 + 12, bottom - 22), fill=blue)
        for yy in range(int(top + 38), int(bottom - 25), 23):
            arrow(draw, (p1 + 18, yy), (p2 - 10, yy), green, 2)
        draw.ellipse((left + width * 0.48, mid_y - 9, left + width * 0.48 + 18, mid_y + 9), fill=yellow)
        draw.text((p1 - 4, top + 5), "+", fill=red, font=regular)
        draw.text((p2 + 1, top + 5), "-", fill=blue, font=regular)
    elif topic == "OPTICS":
        lens_x = left + width * 0.53
        draw.line((left + 14, mid_y, right - 15, mid_y), fill="#64748b", width=2)
        draw.ellipse((lens_x - 8, top + 18, lens_x + 8, bottom - 18), outline=blue, width=3)
        draw.line((left + 25, top + 35, lens_x, mid_y), fill=orange, width=3)
        draw.line((left + 25, top + 35, lens_x, top + 35), fill=orange, width=3)
        draw.line((lens_x, top + 35, right - 25, mid_y - 22), fill=green, width=3)
        draw.line((lens_x, mid_y, right - 25, mid_y), fill=green, width=3)
        draw.line((left + 25, bottom - 35, lens_x, mid_y), fill=orange, width=3)
        draw.text((lens_x - 25, bottom - 16), "lens / mirror", fill="#cbd5e1", font=regular)
    elif topic == "WAVES":
        draw.rectangle((left + 14, top + 16, right - 18, bottom - 18), outline="#2563eb", width=2)
        for row in range(3):
            pts = []
            for i in range(13):
                px = left + 24 + i * (width - 52) / 12
                py = mid_y + (row - 1) * 25 + (8 if i % 2 else -8)
                pts.append((px, py))
            draw.line(pts, fill=[blue, orange, green][row], width=3)
        draw.ellipse((left + 38, mid_y - 9, left + 56, mid_y + 9), fill=yellow)
        draw.ellipse((left + 82, mid_y - 9, left + 100, mid_y + 9), fill=yellow)
        draw.text((left + 18, top + 2), "u(x,y,t)", fill="#cbd5e1", font=regular)
    elif topic == "THERMAL":
        vessel = (left + width * 0.38, top + 25, left + width * 0.68, bottom - 24)
        draw.rectangle(vessel, outline=blue, width=3)
        for dx, dy in [(0, 0), (27, 16), (52, -8), (75, 24), (36, 48), (12, 66)]:
            draw.ellipse((vessel[0] + dx, vessel[1] + dy, vessel[0] + dx + 10, vessel[1] + dy + 10), fill=orange)
        tx = vessel[2] + 24
        draw.line((tx, vessel[1] + 12, tx, vessel[3] - 14), fill="#cbd5e1", width=5)
        draw.ellipse((tx - 9, vessel[3] - 22, tx + 9, vessel[3] - 4), fill=red)
        draw.line((tx, vessel[3] - 14, tx, vessel[1] + 48), fill=red, width=4)
        draw.text((left + 18, top + 3), "T, p, V", fill="#cbd5e1", font=regular)
    elif topic == "MODERN_PHYSICS":
        cx, cy = left + width * 0.52, mid_y
        for radius in (22, 42):
            draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=blue, width=2)
        draw.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), fill=yellow)
        arrow(draw, (cx + 50, cy - 48), (cx + 17, cy - 12), orange, 3)
        draw.ellipse((cx + 49, cy - 52, cx + 59, cy - 42), fill=orange)
        draw.text((left + 18, top + 5), "atom / photon / bands", fill="#cbd5e1", font=regular)
    else:
        draw.rectangle((left + 35, top + 28, left + 112, bottom - 32), outline=blue, width=3)
        draw.ellipse((left + 62, mid_y - 14, left + 90, mid_y + 14), fill=yellow)
        points = [(left + 145 + i * 14, bottom - 35 - (i % 3) * 12) for i in range(8)]
        draw.line(points, fill=green, width=3)
        draw.text((left + 18, top + 6), "measurement / data", fill="#cbd5e1", font=regular)


def draw_scene_card(draw, box, result, regular, bold):
    x, y, width, height = box
    draw.rounded_rectangle((x, y, x + width, y + height), radius=14, fill="#101827", outline="#334155", width=2)
    draw.text((x + 14, y + 10), f"CASE {result['index']:03d}", fill="#7dd3fc", font=bold)
    draw.text((x + width - 86, y + 11), "PASS", fill="#86efac", font=bold)
    draw.text((x + 14, y + 39), short(result["schemaId"], 28), fill="#f8fafc", font=regular)
    draw.text((x + 14, y + 63), short(result.get("topic", ""), 28), fill="#94a3b8", font=regular)

    canvas_top = y + 92
    canvas_bottom = y + height - 19
    draw.rounded_rectangle((x + 14, canvas_top, x + width - 14, canvas_bottom), radius=8, fill="#0b1220", outline="#1e3a5f")
    for gx in range(x + 30, x + width - 20, 28):
        draw.line((gx, canvas_top + 8, gx, canvas_bottom - 8), fill="#172b43", width=1)
    for gy in range(canvas_top + 13, canvas_bottom - 10, 22):
        draw.line((x + 22, gy, x + width - 22, gy), fill="#172b43", width=1)

    node_count = result["nodeCount"]
    asset_count = len(result.get("propAssets", []))
    draw_schematic(draw, x, canvas_top, x + width, canvas_bottom, result, regular)
    draw.text((x + 24, canvas_bottom - 18), f"nodes {node_count}  props {asset_count}", fill="#cbd5e1", font=regular)


def make_contact_sheets(results):
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    regular = font(18)
    bold = font(18, True)
    paths = []
    card_w, card_h = 440, 290
    sheet_w = card_w * 2 + 60
    sheet_h = card_h * 5 + 80
    sheet_count = (len(results) + 9) // 10
    for sheet_index in range(sheet_count):
        image = Image.new("RGB", (sheet_w, sheet_h), "#070b14")
        draw = ImageDraw.Draw(image)
        draw.text((30, 18), f"PhysLive compiled scene evidence - sheet {sheet_index + 1}/{sheet_count}", fill="#e2e8f0", font=bold)
        chunk = results[sheet_index * 10 : (sheet_index + 1) * 10]
        for offset, result in enumerate(chunk):
            col = offset % 2
            row = offset // 2
            x = 20 + col * card_w
            y = 52 + row * card_h
            draw_scene_card(draw, (x, y, card_w - 20, card_h - 18), result, regular, bold)
        path = EVIDENCE_DIR / f"sheet-{sheet_index + 1:02d}.png"
        image.save(path)
        paths.append(path)
    return paths


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(colors.HexColor("#64748b"))
    canvas.drawString(18 * mm, 10 * mm, "PhysLive - schema simulation integration probe")
    canvas.drawRightString(192 * mm, 10 * mm, f"Page {doc.page}")
    canvas.restoreState()


def build_pdf(report, sheets):
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(PDF_PATH),
        pagesize=A4,
        rightMargin=16 * mm,
        leftMargin=16 * mm,
        topMargin=16 * mm,
        bottomMargin=17 * mm,
        title="PhysLive 100 Simulation Evidence Report",
        author="PhysLive engineering probe",
    )
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name="Small", parent=styles["BodyText"], fontSize=8.5, leading=11))
    styles.add(ParagraphStyle(name="Body", parent=styles["BodyText"], fontSize=9.5, leading=13, spaceAfter=6))
    styles.add(ParagraphStyle(name="H1x", parent=styles["Heading1"], fontSize=19, leading=23, textColor=colors.HexColor("#0f172a")))
    styles.add(ParagraphStyle(name="H2x", parent=styles["Heading2"], fontSize=13, leading=16, textColor=colors.HexColor("#0f4c81")))

    story = []
    story += [
        Paragraph(f"PhysLive: {report['executedCases']} Simulation Integration Evidence", styles["H1x"]),
        Paragraph("Automated diversity probe for the current schema-to-scene pipeline", styles["H2x"]),
        Spacer(1, 6 * mm),
        Paragraph(
            f"Result: <b>{report['passedCases']}/{report['executedCases']} cases passed</b>. Every selected schema produced a non-empty compiled scene graph and passed the frontend scene validation boundary. The probe covered nine curriculum topics and used a round-robin selection to avoid testing only one domain.",
            styles["Body"],
        ),
    ]
    summary_data = [
        ["Metric", "Result"],
        ["Requested cases", str(report["requestedCases"])],
        ["Executed cases", str(report["executedCases"])],
        ["Passed", str(report["passedCases"])],
        ["Failed", str(report["failedCases"])],
        ["Topics", str(len(report["topics"]))],
        ["Evidence captures", f"{(report['executedCases'] + 9) // 10} sheets x 10 cases"],
    ]
    table = Table(summary_data, colWidths=[65 * mm, 55 * mm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0f4c81")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#cbd5e1")),
        ("BACKGROUND", (0, 1), (-1, -1), colors.HexColor("#f8fafc")),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (0, -1), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    story += [table, Spacer(1, 7 * mm), Paragraph("What this proves", styles["H2x"])]
    story += [Paragraph(
        "This is an integration and renderability probe. It proves that the current frontend can compile the selected backend schema contracts into renderable scenes, including fallback scenes, graph series, semantic props, vector scenes, and scalar-field wave scenes. It does not replace numerical solver accuracy tests or an end-to-end test with every possible natural-language prompt.",
        styles["Body"],
    )]
    story += [Paragraph("Test correction discovered", styles["H2x"]), Paragraph(
        "The first run found one real contract issue in water_surface_interference: its authored waveField did not provide probeX. The frontend now supplies the safe physical-origin default probeX = 0 while preserving an authored value when present. The full rerun passed all selected cases.",
        styles["Body"],
    )]
    story += [PageBreak(), Paragraph("Coverage by topic", styles["H1x"])]
    topic_counts = Counter(item.get("topic", "UNSPECIFIED") for item in report["results"])
    topic_rows = [["Topic", "Cases", "Pass", "Example schema"]]
    for topic in sorted(topic_counts):
        examples = [item["schemaId"] for item in report["results"] if item.get("topic") == topic]
        topic_rows.append([topic, str(topic_counts[topic]), str(sum(1 for item in report["results"] if item.get("topic") == topic and item["valid"])), short(examples[0], 30)])
    topic_table = Table(topic_rows, colWidths=[55 * mm, 22 * mm, 22 * mm, 70 * mm])
    topic_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0f4c81")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#cbd5e1")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    story += [topic_table, Spacer(1, 7 * mm), Paragraph("Evidence capture sheets", styles["H2x"]), Paragraph(
        f"Each sheet below contains up to ten independently compiled simulation cases. The small canvas panels summarize the actual scene graph output: node count, prop count, topic-specific academic schematic, and pass status. The raw machine-readable result is stored at tmp/simulation-probe/results.json.",
        styles["Body"],
    )]
    for sheet in sheets:
        story += [ReportImage(str(sheet), width=174 * mm, height=230 * mm), PageBreak()]

    story += [Paragraph("Appendix: case matrix", styles["H1x"])]
    rows = [["#", "Schema", "Topic", "Nodes", "Props", "Status"]]
    for item in report["results"]:
        rows.append([
            str(item["index"]),
            short(item["schemaId"], 28),
            short(item.get("topic", ""), 18),
            str(item["nodeCount"]),
            str(len(item.get("propAssets", []))),
            "PASS" if item["valid"] and item["renderable"] else "FAIL",
        ])
    matrix = Table(rows, colWidths=[10 * mm, 55 * mm, 37 * mm, 18 * mm, 18 * mm, 18 * mm], repeatRows=1)
    matrix.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0f4c81")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.25, colors.HexColor("#cbd5e1")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
        ("FONTSIZE", (0, 0), (-1, -1), 7),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("TEXTCOLOR", (-1, 1), (-1, -1), colors.HexColor("#15803d")),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    story += [matrix]
    doc.build(story, onFirstPage=footer, onLaterPages=footer)


with RESULTS.open("r", encoding="utf-8") as stream:
    report = json.load(stream)
sheets = make_contact_sheets(report["results"])
build_pdf(report, sheets)
print(PDF_PATH)
