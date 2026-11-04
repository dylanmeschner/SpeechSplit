#!/usr/bin/env python3
"""
Turns a meeting workbook PDF ("Livet og tjenesten som kristne – arbeidshefte",
"Our Christian Life and Ministry – Meeting Workbook", "Unser Leben und Dienst als Christ –
Arbeitsheft") into the weekly programs Speech Split loads in Meeting mode.

    python3 tools/workbook_to_json.py no mwb_N_202611.pdf [more.pdf ...]

Adds the weeks to docs/meetings/<lang>.json (weeks already there are replaced, weeks that
ended more than 8 weeks ago are dropped). Only titles and times are stored.
Needs: pip install pdfplumber
"""
import json, re, sys, datetime, pathlib, unicodedata
import pdfplumber

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "meetings"

MONTHS = {
    "no": ["januar", "februar", "mars", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "desember"],
    "en": ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"],
    "de": ["januar", "februar", "märz", "april", "mai", "juni", "juli", "august", "september", "oktober", "november", "dezember"],
}
WORDS = {
    "no": {"song": "Sang", "prayer": "bønn", "min": "min", "intro": "Innledende kommentarer", "closing": "Avsluttende kommentarer"},
    "en": {"song": "Song", "prayer": "Prayer", "min": "min.", "intro": "Opening Comments", "closing": "Concluding Comments"},
    "de": {"song": "Lied", "prayer": "Gebet", "min": "Min.", "intro": "Einleitende Worte", "closing": "Schlussworte"},
}

# Accents the PDFs draw as a separate glyph above the letter: the ring of å, the dots of ä/ö/ü
MARKS = {
    "˚": {"a": "å", "A": "Å"}, "°": {"a": "å", "A": "Å"},
    "¨": {"a": "ä", "o": "ö", "u": "ü", "A": "Ä", "O": "Ö", "U": "Ü", "e": "ë", "i": "ï"},
    "´": {"e": "é", "E": "É", "a": "á", "o": "ó"}, "`": {"e": "è", "a": "à"},
}

def lines_of_rows(chars):
    """Groups chars into rows (rings of å stay in, lines_of merges them)."""
    rows = []
    for c in sorted(chars, key=lambda c: (c["top"], c["x0"])):
        if c["text"].strip() == "" or c["text"].startswith("(cid"):
            continue
        if c["text"] in MARKS:
            # belongs to the row of the letter below it
            continue
        for row in rows:
            if abs(row[0]["top"] - c["top"]) < 3:
                row.append(c)
                break
        else:
            rows.append([c])
    rings = [c for c in chars if c["text"] in MARKS]
    for r in rings:
        for row in rows:
            if 0 < row[0]["top"] - r["top"] < 12 and any(c["x0"] - 1 <= (r["x0"] + r["x1"]) / 2 <= c["x1"] + 1 for c in row):
                row.append(r)
                break
    return rows

def lines_of(chars):
    """Chars to text lines: merges the ring of å (drawn as a separate glyph) and puts spaces back."""
    rings = [c for c in chars if c["text"] in MARKS]
    rest = [c for c in chars if c["text"] not in MARKS and c["text"].strip() != "" and not c["text"].startswith("(cid")]
    for r in rings:
        rx = (r["x0"] + r["x1"]) / 2
        table = MARKS[r["text"]]
        best = None
        for c in rest:
            if c["text"] in table and c["x0"] - 1 <= rx <= c["x1"] + 1 and 0 < c["top"] - r["top"] < 12:
                if best is None or c["top"] < best["top"]:
                    best = c
        if best is not None:
            best["text"] = table[best["text"]]
    rows = []
    for c in sorted(rest, key=lambda c: (round(c["top"]), c["x0"])):
        for row in rows:
            if abs(row["top"] - c["top"]) < 3:
                row["chars"].append(c)
                break
        else:
            rows.append({"top": c["top"], "chars": [c]})
    out = []
    for row in sorted(rows, key=lambda r: r["top"]):
        cs = sorted(row["chars"], key=lambda c: c["x0"])
        text, prev = "", None
        for c in cs:
            if prev is not None:
                gap = c["x0"] - prev["x1"]
                if gap > c["size"] * 0.18:
                    text += " "
            text += c["text"]
            prev = c
        out.append({"top": row["top"], "x0": cs[0]["x0"], "text": re.sub(r"\s+", " ", text).strip()})
    return out

def chunks_of(chars):
    """Like lines_of, but a line is cut where there's a wide gap (two columns side by side)."""
    out = []
    for row in lines_of_rows(chars):
        cs = sorted(row, key=lambda c: c["x0"])
        group = [cs[0]]
        for c in cs[1:]:
            if c["x0"] - group[-1]["x1"] > 24:
                out.append(group)
                group = []
            group.append(c)
        out.append(group)
    res = []
    for g in out:
        l = lines_of(g)
        if l:
            res.append(l[0])
    return sorted(res, key=lambda l: (round(l["top"]), l["x0"]))

def parse(lang, path):
    w = WORDS[lang]
    months = MONTHS[lang]
    pdf = pdfplumber.open(path)
    weeks, cur = [], None
    page_no = 0
    year_match = None
    for page in pdf.pages:
        page_no += 1
        mid = page.width / 2
        order = lambda l: (page_no, 0 if l["x0"] < mid else 1, l["top"])
        full = lines_of(list(page.chars))
        header = next((l for l in full if l["top"] < 20 and re.search(r"\d", l["text"])), None)
        # Week header, e.g. "2.–8. NOVEMBER | JEREMIA 49–50" or "30. NOVEMBER–6. DESEMBER | ..."
        if header:
            m = re.search(r"(\d{1,2})\.?\s*(?:([A-ZÆØÅÄÖÜ]+)\s*)?[–-]\s*(\d{1,2})\.?\s*([A-ZÆØÅÄÖÜ]+)", header["text"].upper())
            if lang == "en":
                m = re.search(r"([A-Z]+)\s+(\d{1,2})\s*[–-]\s*(?:([A-Z]+)\s+)?(\d{1,2})", header["text"].upper())
            if m:
                cur = {"header": header["text"], "m": m.groups(), "parts": [], "songs": []}
                weeks.append(cur)
        if cur is None:
            continue
        text_all = "\n".join(l["text"] for l in full)
        for sm in re.finditer(rf"{w['song']}\s*(\d+)", text_all):
            cur["songs"].append(int(sm.group(1)))
        # Parts: "N. Title (X min)", a title may wrap onto the next line (same column)
        ch = [l for l in chunks_of(list(page.chars)) if l["top"] > 18]
        # The middle song ("Sang 44", without "og bønn") opens the last section
        for l in ch:
            if re.fullmatch(rf"{w['song']}\s*\d+", l["text"]):
                cur["song2_at"] = order(l)
        minre = r"\(\s*(\d+)\s*" + re.escape(w["min"].rstrip(".")) + r"\.?\s*\)"
        for i, l in enumerate(ch):
            pm = re.match(r"^(\d{1,2})\.\s*(.*)$", l["text"])
            if not pm:
                continue
            title, last_top = pm.group(2), l["top"]
            for _ in range(3):
                if re.search(minre, title):
                    break
                same = sorted([c for c in ch if abs(c["top"] - last_top) < 4 and l["x0"] < c["x0"] < l["x0"] + 230 and c is not l],
                              key=lambda c: (not re.search(minre, c["text"]), c["x0"]))
                nxt = same or [c for c in ch if 6 < c["top"] - last_top < 18 and l["x0"] - 2 <= c["x0"] <= l["x0"] + 24]
                if not nxt:
                    break
                more = nxt[0]["text"]
                # A word split over two lines ("Völ-" + "kern") is joined again
                title = title[:-1] + more if re.search(r"[a-zæøåäöüß]-$", title) and more[:1].islower() else title + " " + more
                last_top = nxt[0]["top"]
            tm = re.search(minre, title)
            if tm:
                cur["parts"].append({"n": int(pm.group(1)), "title": title[: tm.start()].strip(), "min": int(tm.group(1)), "at": order(l)})
    return weeks

def iso_weeks(lang, weeks, issue_year, issue_month):
    months = MONTHS[lang]
    result = []
    for wk in weeks:
        g = wk["m"]
        if lang == "en":
            m1, d1, m2, d2 = g[0], int(g[1]), g[2] or g[0], int(g[3])
        else:
            d1, m1, d2, m2 = int(g[0]), g[1] or g[3], int(g[2]), g[3]
        mi = [x.upper() for x in months].index(m1.upper()) + 1
        year = issue_year + (1 if mi < issue_month - 6 else 0)
        start = datetime.date(year, mi, d1)
        # Sanity: every week starts on a Monday
        assert start.weekday() == 0, (wk["header"], start)
        h = re.sub(r"^\d+\s+", "", wk["header"])            # page number on the left
        h = re.sub(r"\s+\d+$", "", h)                        # page number on the right
        reading = re.split("|".join(m.upper() for m in months), h.upper())[-1]
        reading = h[len(h) - len(reading):].strip()
        if lang == "en":
            reading = re.sub(r"^.*?\d+\s+", "", h).strip()
        reading = re.sub(r"(\d)\s*-\s*(\d)", "\\1–\\2", unicodedata.normalize("NFKC", reading))
        parts = {}
        for p in wk["parts"]:
            # "ﬁ" ligatures to plain letters, "49-50" with a proper dash
            p["title"] = unicodedata.normalize("NFKC", p["title"])
            parts.setdefault(p["n"], p)
        songs = wk["songs"][:3]
        living = [p["n"] for p in wk["parts"] if "song2_at" in wk and p["at"] > wk["song2_at"]]
        same = next((r for r in result if r["start"] == start.isoformat()), None)
        if same is not None:
            # A later page about the same week (e.g. a discussion on the last page): fill gaps only
            have = {p["n"] for p in same["parts"]}
            # "Continued on page 16": the songs and the last section come from that page
            if len(same["songs"]) < 3:
                same["songs"] = (same["songs"] + songs)[:3]
            if living and not same.pop("_living", False):
                same["livingFrom"] = min(living)
            same["parts"] = sorted(same["parts"] + [{"n": n, "title": parts[n]["title"], "min": parts[n]["min"]} for n in parts if n not in have], key=lambda p: p["n"])
            continue
        result.append({
            "start": start.isoformat(),
            "reading": reading.title() if reading.isupper() else reading,
            "songs": songs,
            "parts": [{"n": n, "title": parts[n]["title"], "min": parts[n]["min"]} for n in sorted(parts)],
            # First part of "Our Christian Life" (the middle song comes right before it)
            "livingFrom": min(living) if living else 7,
            "_living": bool(living),
        })
    return result

def main():
    lang, files = sys.argv[1], sys.argv[2:]
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"{lang}.json"
    data = json.loads(target.read_text()) if target.exists() else {"format": 1, "lang": lang, "weeks": []}
    by_start = {w["start"]: w for w in data["weeks"]}
    for f in files:
        ym = re.search(r"(20\d\d)(\d\d)", pathlib.Path(f).name)
        year, month = int(ym.group(1)), int(ym.group(2))
        for w in iso_weeks(lang, parse(lang, f), year, month):
            if not w["parts"]:
                continue
            by_start[w["start"]] = w
            print(w["start"], w["reading"], w["songs"], [(p["n"], p["min"], p["title"]) for p in w["parts"]])
    cutoff = (datetime.date.today() - datetime.timedelta(weeks=8)).isoformat()
    for w in by_start.values():
        w.pop("_living", None)
    data["weeks"] = [by_start[k] for k in sorted(by_start) if k >= cutoff]
    data["updated"] = datetime.date.today().isoformat()
    target.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(data['weeks'])} weeks in {target}")

if __name__ == "__main__":
    main()
