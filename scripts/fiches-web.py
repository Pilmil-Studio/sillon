#!/usr/bin/env python3
"""Génère docs/legumes.html (fiches légumes) depuis le catalogue Kotlin de l'appli.

Usage : python3 scripts/fiches-web.py [--check]
La page et l'appli partagent ainsi espacements, températures, compagnonnages et conseils.
"""
import html, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MODEL = ROOT / "shared/src/commonMain/kotlin/fr/pilmil/sillon/model"
SRC = ROOT / "shared/src/commonMain/kotlin/fr/pilmil/sillon/data/VegetableCatalog.kt"
TEMPLATE = ROOT / "docs/quand-semer.html"
PAGE = ROOT / "docs/legumes.html"

e = lambda s: html.escape(s, quote=False)

def enum_labels(file, name):
    s = (MODEL / file).read_text(encoding="utf-8")
    body = s.split(f"enum class {name}")[1].split("}")[0]
    return {k: v for k, v in re.findall(r'(\w+)\("([^"]+)"', body)}

def families():
    s = (MODEL / "BotanicalFamily.kt").read_text(encoding="utf-8")
    return {k: (n, int(y)) for k, n, y in re.findall(r'(\w+)\("([^"]+)", NutrientRequirement\.\w+, (\d+)\)', s)}

def relations(block, field):
    m = re.search(rf"{field} = listOf\((.*?)\n        \)", block, re.S)
    if not m:
        return []
    out = []
    for call in re.findall(r"CompanionRelation\((.*?)\),?\n", m.group(1)):
        strs = re.findall(r'"((?:[^"\\]|\\.)*)"', call)
        ev = re.search(r"EvidenceLevel\.(\w+)", call)
        out.append({"id": strs[0], "why": strs[1], "src": strs[2] if len(strs) > 2 else None, "ev": ev.group(1) if ev else "TRADITION"})
    return out

def vegetables():
    s = SRC.read_text(encoding="utf-8")
    blocks = re.findall(r"val ([A-Z_]+) = Vegetable\((.*?)\n    \)", s, re.S)
    out = []
    for key, b in blocks:
        def f(name, kind=int):
            if kind == "str":
                m = re.search(rf'\b{name} =\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', b)
                return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))).replace('\\"', '"') if m else None
            m = re.search(rf"\b{name} = ([^,\n]+)", b)
            if not m or m.group(1).strip() == "null":
                return None
            v = m.group(1).strip()
            if kind is int:
                return int(v)
            if kind == "str":
                return re.match(r'"((?:[^"\\]|\\.)*)"', v).group(1)
            if kind == "range":
                a, z = v.split("..")
                return (int(a), int(z))
            if kind == "enum":
                return v.split(".")[-1]
            return v
        climates = re.search(r"climates = setOf\(([^)]*)\)", b)
        out.append({
            "id": f("id", "str"), "name": f("name", "str"), "latin": f("latinName", "str"),
            "family": f("family", "enum"), "inRow": f("spacingInRowCm"), "rows": f("spacingBetweenRowsCm"),
            "tGerm": f("tempGerminationMin"), "tMin": f("tempOptimalMin"), "tMax": f("tempOptimalMax"),
            "emerge": f("daysToEmerge", "range"), "harvest": f("daysToHarvest", "range"),
            "abri": f("sowIndoorsWeeksBeforeFrost"), "terre": f("sowOutdoorsWeeksAfterFrost"), "plant": f("transplantWeeksAfterFrost"),
            "harvestWeeks": f("harvestDurationWeeks"), "water": f("waterNeed", "enum"), "sun": f("sunExposure", "enum"),
            "advice": f("advice", "str"), "pot": f("minContainerLiters"), "sunHours": f("minDirectSunHours"),
            "temperate": not climates or "TEMPERATE" in climates.group(1),
            "good": relations(b, "goodCompanions"), "bad": relations(b, "badCompanions"),
            "indicative": "indicative = true" in b,
        })
    return out

def weeks(w, after_word):
    if w == 0:
        return "la semaine des dernières gelées"
    n = abs(w)
    return f"{n} semaine{'s' if n > 1 else ''} {'avant' if w < 0 else after_word} les dernières gelées"

def fiche(v, names, fam, water, sun, ev):
    fname, pause = fam.get(v["family"], (v["family"].title(), 3))
    sow = []
    if v["abri"] is not None:
        sow.append(f"<li><b>À l'abri</b> : {weeks(-v['abri'], 'après')}.</li>")
    if v["terre"] is not None:
        sow.append(f"<li><b>En terre</b> : {weeks(v['terre'], 'après')}.</li>")
    if v["plant"] is not None:
        sow.append(f"<li><b>Plantation</b> : {weeks(v['plant'], 'après')}.</li>")
    def rel(lst):
        items = []
        for r in lst:
            nm = names.get(r["id"], r["id"].replace("_", " ").capitalize())
            src = f" <span class=\"src\">({e(r['src'])})</span>" if r["src"] else ""
            items.append(f'<li><b>{e(nm)}</b> <span class="ev ev-{r["ev"].lower()}">{e(ev[r["ev"]])}</span><br>{e(r["why"])}{src}</li>')
        return "".join(items)
    facts = [
        ("Famille", f"{e(fname)} (attendre {pause} ans avant d'y revenir au même endroit)"),
        ("Espacement", f"{v['inRow']} cm sur le rang, {v['rows']} cm entre les rangs"),
        ("Germination", f"sol à {v['tGerm']} °C minimum, idéal {v['tMin']} à {v['tMax']} °C ; levée en {v['emerge'][0]} à {v['emerge'][1]} jours"),
        ("Récolte", f"{v['harvest'][0]} à {v['harvest'][1]} jours après le semis, pendant environ {v['harvestWeeks']} semaines"),
        ("Eau", e(water.get(v["water"], v["water"]))),
        ("Exposition", e(sun.get(v["sun"], v["sun"])) + (f", au moins {v['sunHours']} h de soleil direct" if v["sunHours"] else "")),
    ]
    if v["pot"]:
        facts.append(("En pot", f"possible, {v['pot']} litres minimum"))
    dl = "".join(f"<dt>{k}</dt><dd>{val}</dd>" for k, val in facts)
    return f'''<article class="veg" id="{v['id']}" data-n="{e(v['name'].lower())} {e(v['latin'].lower())}" data-f="{v['family']}">
  <h2>{e(v['name'])} <i>{e(v['latin'])}</i></h2>
  {'<ul class="sow">' + ''.join(sow) + '</ul>' if sow else ''}
  <dl>{dl}</dl>
  <p class="advice">{e(v['advice'])}</p>
  {'<details><summary>Bons voisins</summary><ul class="rel">' + rel(v['good']) + '</ul></details>' if v['good'] else ''}
  {'<details><summary>À éloigner</summary><ul class="rel">' + rel(v['bad']) + '</ul></details>' if v['bad'] else ''}
  <p class="cal"><a href="quand-semer.html">Voir les dates pour mon département</a></p>
</article>'''

def build():
    veg = [v for v in vegetables() if v["temperate"]]
    names = {v["id"]: v["name"] for v in vegetables()}
    fam = families(); water = enum_labels("Vegetable.kt", "WaterNeed"); sun = enum_labels("Vegetable.kt", "SunExposure")
    ev = enum_labels("CompanionRelation.kt", "EvidenceLevel")
    import unicodedata
    veg.sort(key=lambda v: unicodedata.normalize("NFD", v["name"].lower()).encode("ascii", "ignore"))
    used_f = sorted({v["family"] for v in veg}, key=lambda k: fam.get(k, (k, 0))[0])
    opts = "".join(f'<option value="{k}">{e(fam.get(k, (k, 0))[0])}</option>' for k in used_f)
    index = "".join(f'<a href="#{v["id"]}">{e(v["name"])}</a>' for v in veg)
    cards = "\n".join(fiche(v, names, fam, water, sun, ev) for v in veg)
    t = TEMPLATE.read_text(encoding="utf-8")
    head = t[:t.index("</style>")]
    head = re.sub(r"<title>.*?</title>", "<title>Fiches légumes : semis, espacements, bons voisins : Sillon</title>", head)
    d = f"{len(veg)} fiches légumes gratuites : quand semer, à quel espacement, à quelle température, les bons et mauvais voisins avec le niveau de preuve."
    head = re.sub(r'<meta name="description" content="[^"]*">', f'<meta name="description" content="{d}">', head)
    head = re.sub(r'<meta property="og:description" content="[^"]*">', f'<meta property="og:description" content="{d}">', head)
    head = re.sub(r'<meta property="og:title" content="[^"]*">', '<meta property="og:title" content="Fiches légumes du potager">', head)
    head = head.replace("sillon/quand-semer.html", "sillon/legumes.html")
    css = """
.filters { display:flex; flex-wrap:wrap; gap:10px; margin:18px 0 6px; }
.filters input, .filters select { font:inherit; padding:10px 13px; border-radius:12px; border:1.5px solid var(--line); background:var(--bg); color:var(--fg); }
.filters input { flex:1 1 14em; }
.index { display:flex; flex-wrap:wrap; gap:6px 14px; margin:12px 0 18px; font-size:.95rem; }
.veg { padding:20px 0; border-top:1px solid var(--line); }
.veg h2 { margin:0 0 8px; } .veg h2 i { font-weight:400; font-size:.8em; color:var(--muted); }
.veg dl { display:grid; grid-template-columns:8.5em 1fr; gap:4px 14px; margin:10px 0; }
.veg dt { font-weight:700; color:var(--fg); } .veg dd { margin:0; color:var(--muted); }
.sow { margin:6px 0; padding-left:1.1em; } .advice { color:var(--fg); }
details { margin:8px 0; } summary { cursor:pointer; font-weight:700; color:var(--fg); }
.rel li { margin:6px 0; } .src { color:var(--muted); font-size:.88em; }
.ev { display:inline-block; font-size:.72rem; font-weight:700; padding:1px 7px; border-radius:99px; margin-left:4px; vertical-align:1px; }
.ev-etaye { background:color-mix(in srgb,var(--accent-solid) 25%,transparent); }
.ev-plausible { background:color-mix(in srgb,var(--warm) 22%,transparent); }
.ev-tradition { background:color-mix(in srgb,var(--muted) 18%,transparent); }
.cal { margin:6px 0 0; font-size:.95rem; }
.callout { margin:36px 0 8px; padding:24px; border-radius:22px; background:linear-gradient(120deg,var(--accent-solid),color-mix(in srgb,var(--accent-solid) 70%,#0c2a1c)); color:var(--on-accent); }
.callout h2 { margin:0 0 8px; color:inherit; } .callout p { color:inherit !important; margin:0 0 14px; }
.callout .btn { background:var(--on-accent); color:var(--accent-solid); border-color:transparent; } .callout .btn.ghost { background:transparent; color:var(--on-accent); border-color:color-mix(in srgb,var(--on-accent) 60%,transparent); }
@media (max-width:520px) { .veg dl { grid-template-columns:1fr; } .veg dt { margin-top:6px; } }
"""
    nav_start = t.index("</style>")
    rest = t[nav_start:t.index('<main')]
    rest = rest.replace(' aria-current="page"', '').replace('\n    <a href="legumes.html">Fiches légumes</a>', '').replace('<a href="quand-semer.html">Quand semer ?</a>', '<a href="quand-semer.html">Quand semer ?</a>\n    <a href="legumes.html" aria-current="page">Fiches légumes</a>')
    foot = t[t.index("<footer"):t.index("<script>")]
    body = f'''<main class="wrap doc" id="contenu">
<p class="kicker">Fiches du potager</p>
<h1>{len(veg)} fiches légumes</h1>
<p class="lead">Pour chaque légume : quand le semer par rapport aux dernières gelées, à quel espacement, à quelle température il germe, quand il se récolte, et quels voisins lui conviennent. Pour les voisins, on indique ce qui est <b>étayé par des essais</b>, ce qui est <b>plausible</b> et ce qui relève de la <b>tradition</b>.</p>
<div class="filters"><input id="q" type="search" placeholder="Chercher un légume…" aria-label="Chercher un légume"><select id="fam" aria-label="Famille"><option value="">Toutes les familles</option>{opts}</select></div>
<nav class="index" aria-label="Liste des légumes">{index}</nav>
{cards}
<aside class="callout">
  <h2>Le potager qui se souvient pour vous</h2>
  <p>Sillon reprend ces fiches, calcule les dates pour votre commune, vous rappelle quoi faire chaque quinzaine et garde la mémoire de vos parcelles pour la rotation. Gratuit, sans compte, sans réseau.</p>
  <div class="cta" style="margin-top:0"><a class="btn" href="https://play.google.com/store/apps/details?id=fr.pilmil.sillon&amp;referrer=utm_source%3Dsite%26utm_content%3Dlegumes">Google Play</a><a class="btn ghost" href="./">Découvrir Sillon</a></div>
</aside>
<p>Données issues du catalogue de Sillon. D'où elles viennent et ce qu'elles ne disent pas : <a href="sources.html">Sources et limites</a>.</p>
</main>
'''
    js = '''<script>
(function(){
  var q=document.getElementById('q'), f=document.getElementById('fam'), cards=[].slice.call(document.querySelectorAll('.veg'));
  function norm(s){return s.toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'');}
  function run(){var t=norm(q.value.trim()), fam=f.value;
    cards.forEach(function(c){c.hidden=!((!t||norm(c.dataset.n).indexOf(t)>=0)&&(!fam||c.dataset.f===fam));});}
  q.addEventListener('input',run); f.addEventListener('change',run);
})();
</script>
</body>
</html>
'''
    return head + css + rest + body + foot + js

def main():
    new = build()
    if "--check" in sys.argv:
        old = PAGE.read_text(encoding="utf-8") if PAGE.exists() else ""
        sys.exit(0 if old == new else "docs/legumes.html n'est pas à jour : lancez scripts/fiches-web.py")
    PAGE.write_text(new, encoding="utf-8")
    print("docs/legumes.html écrit")

if __name__ == "__main__":
    main()
