#!/usr/bin/env python3
"""Extrait du catalogue Kotlin les données du calendrier de semis publié sur le site (docs/quand-semer.html).

Usage : python3 scripts/calendrier-web.py  (réécrit le bloc entre les marqueurs DONNEES dans docs/quand-semer.html)
La page et l'appli lisent ainsi les mêmes semaines et les mêmes dates de gelée.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "shared/src/commonMain/kotlin/fr/pilmil/sillon/data"
PAGE = ROOT / "docs/quand-semer.html"

def vegetables():
    src = (DATA / "VegetableCatalog.kt").read_text(encoding="utf-8")
    order = re.findall(r"^\s{8}([A-Z_]+),", src.split("val ALL_VEGETABLES")[1].split(")")[0], re.M)
    blocks = dict(re.findall(r"val ([A-Z_]+) = Vegetable\((.*?)\n    \)", src, re.S))
    out = []
    for key in order:
        b = blocks[key]
        def field(name):
            m = re.search(rf"\b{name} = (-?\d+|null)", b)
            return None if not m or m.group(1) == "null" else int(m.group(1))
        climates = re.search(r"climates = setOf\(([^)]*)\)", b)
        cl = re.findall(r"Climate\.(\w+)", climates.group(1)) if climates else ["TEMPERATE"]
        out.append({
            "n": re.search(r'\bname = "([^"]+)"', b).group(1),
            "a": field("sowIndoorsWeeksBeforeFrost"),
            "t": field("sowOutdoorsWeeksAfterFrost"),
            "p": field("transplantWeeksAfterFrost"),
            "c": "".join("T" if c == "TEMPERATE" else "P" for c in cl),
            **({"i": 1} if re.search(r"indicative = true", b) else {}),
        })
    return [v for v in out if v["a"] is not None or v["t"] is not None or v["p"] is not None]

def departments():
    src = (DATA / "FrenchFrostCatalog.kt").read_text(encoding="utf-8")
    rows = re.findall(r'DepartmentFrost\("(\w+)", "([^"]+)", FrostDate\((\d+), (\d+)\), "[^"]+"(, Climate\.TROPICAL)?\)', src)
    return [{"k": k, "n": n, "m": int(m), "d": int(d), **({"p": 1} if t else {})} for k, n, m, d, t in rows]

def static_steps(veg):
    """Liste HTML des repères en semaines pour la métropole, lisible sans JavaScript."""
    steps = {}
    for v in veg:
        if "T" not in v["c"]:
            continue
        for w, kind in ((None if v["a"] is None else -v["a"], "À l'abri"), (v["t"], "En terre"), (v["p"], "Planter")):
            if w is not None:
                steps.setdefault(w, {}).setdefault(kind, []).append(v["n"])
    def when(w):
        if w == 0:
            return "Semaine des gelées"
        return f"{abs(w)} sem. {'avant' if w < 0 else 'après'}"
    lines = ['<ol class="steps">']
    frost_done = False
    for w in sorted(steps):
        if w >= 0 and not frost_done:
            lines.append('  <li><span class="w frost">Dernières gelées</span><span class="frost">Date médiane du département</span></li>')
            frost_done = True
        order = ["À l'abri", "En terre", "Planter"]
        parts = " ".join(f"<strong>{k}&nbsp;:</strong> {', '.join(steps[w][k]).lower().replace("d'inde", "d'Inde")}." for k in order if k in steps[w])
        lines.append(f'  <li><span class="w">{when(w)}</span><span>{parts}</span></li>')
    lines.append("</ol>")
    return "\n".join(lines)

def main():
    veg, dep = vegetables(), departments()
    blob = json.dumps({"veg": veg, "dep": dep}, ensure_ascii=False, separators=(",", ":"))
    page = PAGE.read_text(encoding="utf-8")
    new = re.sub(r"(/\* DONNEES \*/).*?(/\* FIN DONNEES \*/)", lambda m: f"{m.group(1)}{blob}{m.group(2)}", page, flags=re.S)
    new = re.sub(r"(<!-- STATIQUE -->).*?(<!-- FIN STATIQUE -->)", lambda m: f"{m.group(1)}\n{static_steps(veg)}\n{m.group(2)}", new, flags=re.S)
    if "--check" in sys.argv:
        sys.exit(0 if new == page else "docs/quand-semer.html n'est pas à jour : lancez scripts/calendrier-web.py")
    PAGE.write_text(new, encoding="utf-8")
    print(f"{len(veg)} plantes, {len(dep)} départements")

if __name__ == "__main__":
    main()
