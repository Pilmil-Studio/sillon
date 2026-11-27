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

SITE = "https://pilmil-studio.github.io/sillon/"
MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]
MOIS_C = ["janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."]

def shell(title, desc, path, css, body, crumbs=()):
    """Page complète avec l'en-tête, la navigation et le pied de page du site."""
    t = TEMPLATE.read_text(encoding="utf-8")
    head = t[:t.index("</style>")]
    head = re.sub(r"<title>.*?</title>", f"<title>{e(title)} : Sillon</title>", head)
    for k, v in [('name="description"', desc), ('property="og:description"', desc), ('property="og:title"', title)]:
        head = re.sub(rf'<meta {k} content="[^"]*">', f'<meta {k} content="{html.escape(v)}">', head)
    head = re.sub(r'<meta property="og:url" content="[^"]*">', f'<meta property="og:url" content="{SITE}{path}">', head)
    head = re.sub(r'\n<link rel="canonical"[^>]*>', "", head)
    head = head.replace("<meta name=\"description\"", f'<link rel="canonical" href="{SITE}{path}">\n<meta name="description"', 1)
    rest = t[t.index("</style>"):t.index("<main")].replace(' aria-current="page"', '')
    rest = re.sub(r'<link rel="canonical"[^>]*>\n?', "", rest)
    rest = re.sub(r'<script type="application/ld\+json" data-ref="app">.*?</script>\n?', "", rest, flags=re.S)
    if crumbs:
        import json
        items = [{"@type": "ListItem", "position": i + 1, "name": n, "item": SITE + u} for i, (n, u) in enumerate(crumbs)]
        ld = json.dumps({"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": items}, ensure_ascii=False)
        rest = rest.replace("</head>", f'<script type="application/ld+json">{ld}</script>\n</head>', 1)
    foot = t[t.index("<footer"):t.index("<script>")]
    return head + css + rest + body + foot + "</body>\n</html>\n"

def slug(s):
    import unicodedata
    s = unicodedata.normalize("NFD", s.lower()).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")

def page_name(v):
    return f"legume-{v['id'].replace('_', '-')}.html"

def departments():
    src = (ROOT / "shared/src/commonMain/kotlin/fr/pilmil/sillon/data/FrenchFrostCatalog.kt").read_text(encoding="utf-8")
    rows = re.findall(r'DepartmentFrost\("(\w+)", "([^"]+)", FrostDate\((\d+), (\d+)\), "[^"]+"(, Climate\.TROPICAL)?\)', src)
    return [{"k": k, "n": n, "m": int(m), "d": int(d), "p": bool(t)} for k, n, m, d, t in rows]

def dep_page(d):
    return f"semis-{d['k'].lower()}-{slug(d['n'])}.html"

def day(anchor, weeks_off):
    import datetime
    x = anchor + datetime.timedelta(weeks=weeks_off)
    return x

def fmt(x):
    return f"{x.day}{'er' if x.day == 1 else ''} {MOIS_C[x.month - 1]}"

def steps_for(v, anchor):
    out = []
    if v["abri"] is not None: out.append(("Semer à l'abri", day(anchor, -v["abri"])))
    if v["terre"] is not None: out.append(("Semer en terre", day(anchor, v["terre"])))
    if v["plant"] is not None: out.append(("Planter au potager", day(anchor, v["plant"])))
    return out

PAGE_CSS = """
.veg h1 i { display:block; font-size:.55em; font-weight:400; color:var(--muted); margin-top:4px; }
.crumbs { font-size:.9rem; margin:6px 0 14px; color:var(--muted); } .crumbs a { color:inherit; }
.veg dl { display:grid; grid-template-columns:8.5em 1fr; gap:6px 14px; margin:14px 0; }
.veg dt { font-weight:700; color:var(--fg); } .veg dd { margin:0; color:var(--muted); }
.sow { margin:6px 0; padding-left:1.1em; } .advice { color:var(--fg); font-size:1.05rem; }
.rel li { margin:8px 0; } .src { color:var(--muted); font-size:.88em; }
.ev { display:inline-block; font-size:.72rem; font-weight:700; padding:1px 7px; border-radius:99px; margin-left:4px; vertical-align:1px; }
.ev-etaye { background:color-mix(in srgb,var(--accent-solid) 25%,transparent); }
.ev-plausible { background:color-mix(in srgb,var(--warm) 22%,transparent); }
.ev-tradition { background:color-mix(in srgb,var(--muted) 18%,transparent); }
table.dates { width:100%; border-collapse:collapse; margin:12px 0; font-size:.95rem; }
table.dates th, table.dates td { text-align:left; padding:8px 10px 8px 0; border-bottom:1px solid var(--line); vertical-align:top; }
table.dates th { color:var(--fg); }
.month h3 { margin:18px 0 6px; text-transform:capitalize; }
.task { display:grid; grid-template-columns:9em 1fr; gap:4px 14px; padding:8px 0; border-top:1px solid var(--line); }
.task .when { font-weight:700; color:var(--fg); }
.links { columns:2 14em; font-size:.95rem; } .links a { display:block; padding:3px 0; }
.callout { margin:36px 0 8px; padding:24px; border-radius:22px; background:linear-gradient(120deg,var(--accent-solid),color-mix(in srgb,var(--accent-solid) 70%,#0c2a1c)); color:var(--on-accent); }
.callout h2 { margin:0 0 8px; color:inherit; } .callout p { color:inherit !important; margin:0 0 14px; }
.callout .btn { background:var(--on-accent); color:var(--accent-solid); border-color:transparent; } .callout .btn.ghost { background:transparent; color:var(--on-accent); border-color:color-mix(in srgb,var(--on-accent) 60%,transparent); }
@media (max-width:520px) { .veg dl, .task { grid-template-columns:1fr; } }
"""

def callout(utm, title="Les dates de votre jardin, au jour près"):
    return f'''<aside class="callout">
  <h2>{title}</h2>
  <p>Sillon calcule ces dates pour votre commune, vous rappelle chaque quinzaine quoi semer, planter ou récolter, et garde la mémoire de vos parcelles pour la rotation. Gratuit, sans compte, sans réseau.</p>
  <div class="cta" style="margin-top:0"><a class="btn" href="https://play.google.com/store/apps/details?id=fr.pilmil.sillon&amp;referrer=utm_source%3Dsite%26utm_content%3D{utm}">Google Play</a><a class="btn ghost" href="./">Découvrir Sillon</a></div>
</aside>'''

REF_DEPS = ["13", "33", "44", "75", "69", "67", "73"]

def veg_page(v, names, fam, water, sun, ev, deps, all_veg):
    import datetime
    card = fiche(v, names, fam, water, sun, ev)
    card = card.replace(f'<h2>{e(v["name"])} <i>', f'<h1>{e(v["name"])} <i>', 1).replace("</i></h2>", "</i></h1>", 1)
    card = card.replace('<details><summary>Bons voisins</summary>', '<h2>Bons voisins</h2><div>').replace('<details><summary>À éloigner</summary>', '<h2>À éloigner</h2><div>').replace("</ul></details>", "</ul></div>")
    card = re.sub(r'\s*<p class="cal">.*?</p>', "", card)
    rows = []
    for k in REF_DEPS:
        d = next((x for x in deps if x["k"] == k), None)
        if not d: continue
        a = datetime.date(2027, d["m"], d["d"])
        st = steps_for(v, a)
        if not st: continue
        cells = "".join(f"<td>{fmt(x)}</td>" for _, x in st)
        rows.append(f'<tr><th><a href="{dep_page(d)}">{e(d["n"])}</a></th>{cells}</tr>')
    heads = "".join(f"<th>{lbl}</th>" for lbl, _ in steps_for(v, datetime.date(2027, 4, 15)))
    table = (f'<h2>Les dates selon la région</h2><p>Dates indicatives, à une semaine près, à partir de la date médiane des dernières gelées de quelques départements.</p>'
             f'<table class="dates"><thead><tr><th>Département</th>{heads}</tr></thead><tbody>{"".join(rows)}</tbody></table>'
             f'<p><a href="quand-semer.html">Calculer pour mon département</a></p>') if rows and heads else ""
    same = [o for o in all_veg if o["family"] == v["family"] and o is not v][:6]
    sib = ("<h2>Même famille</h2><p>" + " · ".join(f'<a href="{page_name(o)}">{e(o["name"])}</a>' for o in same) + "</p>") if same else ""
    nm = v["name"]
    body = f'''<main class="wrap doc veg" id="contenu">
<p class="crumbs"><a href="guides.html">Guides</a> › <a href="legumes.html">Fiches légumes</a> › {e(nm)}</p>
{card.replace('<article class="veg"', '<article').replace("</article>", "")}
{table}
{sib}
{callout(page_name(v)[:-5])}
<p>Données issues du catalogue de Sillon. D'où elles viennent et ce qu'elles ne disent pas : <a href="sources.html">Sources et limites</a>.</p>
</article>
</main>
'''
    sow = steps_for(v, datetime.date(2027, 4, 15))
    desc = f"{nm} : quand semer et planter selon les gelées, espacement ({v['inRow']} cm sur le rang), germination, récolte et bons voisins avec le niveau de preuve."
    return shell(f"{nm} : quand semer, espacement, bons voisins", desc, page_name(v), PAGE_CSS, body,
                 crumbs=[("Guides", "guides.html"), ("Fiches légumes", "legumes.html"), (nm, page_name(v))])

def dep_html(d, all_veg):
    import datetime
    a = datetime.date(2027, d["m"], d["d"])
    pool = [v for v in all_veg if (v["tropical"] if d["p"] else v["temperate"])]
    by = {}
    for v in pool:
        for lbl, x in steps_for(v, a):
            by.setdefault(x, {}).setdefault(lbl, []).append(v)
    order = ["Semer à l'abri", "Semer en terre", "Planter au potager"]
    months = {}
    for x in sorted(by):
        months.setdefault((x.year, x.month), []).append(x)
    parts = []
    for (y, mo), xs in months.items():
        items = []
        for x in xs:
            s0, s1 = x - datetime.timedelta(days=7), x + datetime.timedelta(days=7)
            lines = "".join(f"<span><b>{lbl}</b>&nbsp;: " + ", ".join(f'<a href="{page_name(v)}">{e(v["name"].lower())}</a>' for v in by[x][lbl]) + ".</span><br>" for lbl in order if lbl in by[x])
            items.append(f'<div class="task"><span class="when">{fmt(s0)} → {fmt(s1)}</span><span>{lines}</span></div>')
        parts.append(f'<section class="month"><h3>{MOIS[mo - 1]}</h3>{"".join(items)}</section>')
    name = d["n"]; code = d["k"]
    if d["p"]:
        lead = f"Repères indicatifs pour {e(name)}, calés sur un début de saison retenu au {d['d']} {MOIS[d['m'] - 1]}. Outre-mer, ce début de saison est une convention propre au territoire, pas une date de gelée."
        h1 = f"Calendrier du potager : {e(name)}"
    else:
        lead = f"En {e(name)} ({code}), les dernières gelées tombent en moyenne vers le <b>{d['d']} {MOIS[d['m'] - 1]}</b> (date médiane : une année sur deux, il gèle encore après). Voici, mois par mois, quoi semer à l'abri, semer en terre et planter, avec une fenêtre d'une semaine avant et après."
        h1 = f"Calendrier des semis : {e(name)} ({code})"
    body = f'''<main class="wrap doc" id="contenu">
<p class="crumbs"><a href="guides.html">Guides</a> › <a href="quand-semer.html">Quand semer ?</a> › {e(name)}</p>
<h1>{h1}</h1>
<p class="lead">{lead}</p>
<p>Un jardin en altitude, en fond de vallée ou loin de la mer gèle souvent plus tard : décalez d'une ou deux semaines. Pour une date au jour près, <a href="quand-semer.html?dep={code}">ouvrez le calendrier interactif</a>.</p>
{"".join(parts)}
{callout("semis-" + code.lower())}
<h2>Les autres départements</h2>
<p><a href="quand-semer.html#departements">Tous les départements</a></p>
</main>
'''
    desc = f"Quand semer et planter en {name} ({code}) : calendrier du potager mois par mois, calé sur la date des dernières gelées." if not d["p"] else f"Calendrier du potager pour {name} : repères de semis et de plantation."
    return shell(f"Calendrier des semis en {name} ({code})", desc, dep_page(d), PAGE_CSS, body,
                 crumbs=[("Guides", "guides.html"), ("Quand semer ?", "quand-semer.html"), (name, dep_page(d))])

def write_pages():
    allv = vegetables()
    for v in allv:
        v["tropical"] = False
    src = SRC.read_text(encoding="utf-8")
    for key, b in re.findall(r"val ([A-Z_]+) = Vegetable\((.*?)\n    \)", src, re.S):
        idm = re.search(r'\bid = "([^"]+)"', b).group(1)
        for v in allv:
            if v["id"] == idm:
                v["tropical"] = "TROPICAL" in (re.search(r"climates = setOf\(([^)]*)\)", b) or [None, ""])[1] if re.search(r"climates = setOf", b) else False
    veg = [v for v in allv if v["temperate"]]
    names = {v["id"]: v["name"] for v in allv}
    fam = families(); water = enum_labels("Vegetable.kt", "WaterNeed"); sun = enum_labels("Vegetable.kt", "SunExposure")
    ev = enum_labels("CompanionRelation.kt", "EvidenceLevel")
    deps = departments()
    out = {}
    for v in veg:
        out[page_name(v)] = veg_page(v, names, fam, water, sun, ev, deps, veg)
    for d in deps:
        out[dep_page(d)] = dep_html(d, allv)
    return out

def build():
    veg = [v for v in vegetables() if v["temperate"]]
    names = {v["id"]: v["name"] for v in vegetables()}
    fam = families(); water = enum_labels("Vegetable.kt", "WaterNeed"); sun = enum_labels("Vegetable.kt", "SunExposure")
    ev = enum_labels("CompanionRelation.kt", "EvidenceLevel")
    import unicodedata
    veg.sort(key=lambda v: unicodedata.normalize("NFD", v["name"].lower()).encode("ascii", "ignore"))
    used_f = sorted({v["family"] for v in veg}, key=lambda k: fam.get(k, (k, 0))[0])
    opts = "".join(f'<option value="{k}">{e(fam.get(k, (k, 0))[0])}</option>' for k in used_f)
    index = "".join(f'<a href="{page_name(v)}">{e(v["name"])}</a>' for v in veg)
    def short(v):
        st = []
        if v["abri"] is not None: st.append("à l'abri " + weeks(-v["abri"], "après").replace(" les dernières gelées", ""))
        if v["terre"] is not None: st.append("en terre " + weeks(v["terre"], "après").replace(" les dernières gelées", ""))
        if v["plant"] is not None: st.append("plantation " + weeks(v["plant"], "après").replace(" les dernières gelées", ""))
        return " ; ".join(st)
    cards = "\n".join(f'''<article class="veg" id="{v['id']}" data-n="{e(v['name'].lower())} {e(v['latin'].lower())}" data-f="{v['family']}">
  <h2><a href="{page_name(v)}">{e(v['name'])}</a> <i>{e(v['latin'])}</i></h2>
  <p>Semis ({e(short(v))}, par rapport aux dernières gelées). Espacement {v['inRow']} × {v['rows']} cm. Récolte {v['harvest'][0]} à {v['harvest'][1]} jours après le semis.</p>
  <p class="cal"><a href="{page_name(v)}">Fiche complète : voisins, conseils, dates par région</a></p>
</article>''' for v in veg)
    t = TEMPLATE.read_text(encoding="utf-8")
    head = t[:t.index("</style>")]
    head = re.sub(r"<title>.*?</title>", "<title>Fiches légumes : semis, espacements, bons voisins : Sillon</title>", head)
    d = f"{len(veg)} fiches légumes gratuites : quand semer, à quel espacement, à quelle température, les bons et mauvais voisins avec le niveau de preuve."
    head = re.sub(r'<meta name="description" content="[^"]*">', f'<meta name="description" content="{d}">', head)
    head = re.sub(r'<meta property="og:description" content="[^"]*">', f'<meta property="og:description" content="{d}">', head)
    head = re.sub(r'<meta property="og:title" content="[^"]*">', '<meta property="og:title" content="Fiches légumes du potager">', head)
    head = head.replace("sillon/quand-semer.html", "sillon/legumes.html")
    head = re.sub(r'<link rel="canonical"[^>]*>', f'<link rel="canonical" href="{SITE}legumes.html">', head)
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

def keep_extras(new):
    """Conserve l'encart « autres guides » ajouté par l'espace Guides du site."""
    if not PAGE.exists():
        return new
    m = re.search(r'<section class="g-more".*?</section>\n', PAGE.read_text(encoding="utf-8"), re.S)
    if not m:
        return new
    i = new.index("<footer")
    return new[:i] + m.group(0) + new[i:]

def main():
    pages = write_pages()
    new = keep_extras(build())
    new = re.sub(r'<link rel="canonical"[^>]*>', f'<link rel="canonical" href="{SITE}legumes.html">', new)
    new = re.sub(r'<script type="application/ld\+json" data-ref="app">.*?</script>\n?', "", new, flags=re.S)
    deps = departments()
    links = '<div class="links">' + "".join(f'<a href="{dep_page(d)}">{d["k"]} · {e(d["n"])}</a>' for d in deps) + "</div>"
    qs_path = PAGE.parent / "quand-semer.html"
    qs = qs_path.read_text(encoding="utf-8")
    qs_new = re.sub(r"(<!-- DEPARTEMENTS -->).*?(<!-- FIN DEPARTEMENTS -->)", lambda m: m.group(1) + links + m.group(2), qs, flags=re.S)
    if "--check" not in sys.argv:
        qs_path.write_text(qs_new, encoding="utf-8")
        for old in list(PAGE.parent.glob("legume-*.html")) + list(PAGE.parent.glob("semis-*.html")):
            if old.name not in pages:
                old.unlink()
        for name, content in pages.items():
            (PAGE.parent / name).write_text(content, encoding="utf-8")
    else:
        stale = [n for n, c in pages.items() if not (PAGE.parent / n).exists() or (PAGE.parent / n).read_text(encoding="utf-8") != c]
        if qs_new != qs:
            stale.append("quand-semer.html")
        if stale:
            sys.exit(f"{len(stale)} pages à régénérer : lancez scripts/fiches-web.py")
    if "--check" in sys.argv:
        old = PAGE.read_text(encoding="utf-8") if PAGE.exists() else ""
        sys.exit(0 if old == new else "docs/legumes.html n'est pas à jour : lancez scripts/fiches-web.py")
    PAGE.write_text(new, encoding="utf-8")
    print(f"docs/legumes.html et {len(pages)} pages écrites")

if __name__ == "__main__":
    main()
