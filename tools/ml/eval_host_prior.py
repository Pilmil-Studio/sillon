"""
Mesure de l'a priori de plausibilité de la plante sur le test de terrain, avec et sans a priori.

L'appli applique cet a priori quand le diagnostic part d'une plantation (voir HostPlausibility.kt et
PhotoDiagnosis.applyHostPrior) : chaque probabilité d'une maladie ou d'un ravageur est multipliée par la
plausibilité de l'hôte (plancher 0,05, jamais zéro), puis renormalisée. Les classes sans fiche (sain, autre,
hors_sujet) ne sont pas pondérées. Ce script rejoue ce calcul sur les photos de terrain dont la culture est
connue, et compare, par classe et au total : exactitude, couverture et précision au seuil du modèle.

Le seuil du modèle n'est PAS modifié : il a été calibré sans a priori. Ce script dit seulement ce que l'a priori
change à ce seuil ; recaler le seuil serait une décision à prendre après lecture des chiffres, jamais avant.

Culture connue :
  - PlantDoc / PlantVillage : la culture est dans le nom du dossier (« Tomato leaf late blight »).
  - iNaturalist (--classed) : colonne `host_taxon` de ATTRIBUTIONS.csv si elle existe ; sinon `source_taxon`
    quand c'est une plante cultivée connue (classes sain, mildiou...). Pour un ravageur, `source_taxon` est
    l'insecte : la culture est inconnue et la photo est écartée (jamais devinée).

Aucun chiffre n'est écrit en dur : tout sort des images que vous fournissez. Sans images, rien n'est inventé ;
`--self-test` vérifie seulement la lecture de la table et le calcul de l'a priori sur des vecteurs jouets.

Exemples :
    python eval_host_prior.py --self-test
    python eval_host_prior.py --plantdoc <PlantDoc> --classed dataset_raw --out rapport_a_priori.json
    python eval_host_prior.py --plantdoc <PlantDoc> --cache probas.npz      # relance sans refaire l'inférence

L'inférence reproduit celle de l'appli (carré central, taille du modèle, moyenne de l'image et de son miroir
horizontal) sans le pré-cadrage par `sillon_detector.tflite` : les chiffres sont comparables entre eux (avec et
sans a priori), pas identiques à ceux de l'appli.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import zlib
from pathlib import Path

import numpy as np

from train_sillon import CROPS, IMAGE_EXTENSIONS, LABELS, MIN_TEST_IMAGES, label_for_folder, normalize, wilson

ROOT = Path(__file__).resolve().parents[2]
KOTLIN = ROOT / "shared/src/commonMain/kotlin/fr/pilmil/sillon"
DEFAULT_MODEL = ROOT / "app/src/main/assets/models/sillon_disease.tflite"
DEFAULT_SPEC = ROOT / "app/src/main/assets/models/sillon_disease.json"
FLOOR = 0.05  # même valeur que HostPlausibility.FLOOR : vérifiée à chaque lancement

# Culture (mot du nom de dossier) -> famille botanique de BotanicalFamily.kt.
CROP_FAMILY = {
    "tomato": "SOLANACEAE",
    "potato": "SOLANACEAE",
    "pepper": "SOLANACEAE",
    "squash": "CUCURBITACEAE",
    "strawberry": "ROSACEAE",
}
# Début de nom de taxon iNaturalist (minuscules) -> culture, pour les photos dont le taxon est la plante cultivée.
TAXON_CROP = {
    "solanum lycopersicum": "tomato",
    "solanum tuberosum": "potato",
    "capsicum": "pepper",
    "cucurbita": "squash",
    "fragaria": "strawberry",
}
TEST_RATIO = 0.20  # même découpage que train_sillon.py pour --classed


def load_prior_table() -> tuple[dict[str, set[str]], set[str]]:
    """Lit la table de l'appli au lieu de la recopier : familles visées par fiche et causes à large spectre."""
    catalog = (KOTLIN / "data/DiseaseCatalog.kt").read_text(encoding="utf-8")
    targets: dict[str, set[str]] = {}
    for block in re.split(r"\n    val [A-Z_]+ = Disease\(", catalog)[1:]:
        ident = re.search(r'id = "([a-z_]+)"', block)
        if not ident:
            continue
        families = re.search(r"targetFamilies = (listOf\((.*?)\)|emptyList\(\))", block, re.S)
        names = set(re.findall(r"BotanicalFamily\.([A-Z_]+)", families.group(0))) if families else set()
        targets[ident.group(1)] = names
    text = (KOTLIN / "data/HostPlausibility.kt").read_text(encoding="utf-8")
    broad_block = re.search(r"BROAD_SPECTRUM\s*=\s*setOf\((.*?)\)", text, re.S)
    broad = set(re.findall(r'"([a-z_]+)"', broad_block.group(1))) if broad_block else set()
    floor = re.search(r"FLOOR\s*=\s*([0-9.]+)f", text)
    if not floor or abs(float(floor.group(1)) - FLOOR) > 1e-9:
        raise SystemExit("HostPlausibility.FLOOR ne correspond plus à FLOOR de ce script : mettez-les d'accord.")
    return targets, broad


def plausibility(catalog_id: str, family: str, targets: dict[str, set[str]], broad: set[str]) -> float:
    if catalog_id in broad:
        return 1.0
    families = targets.get(catalog_id)
    if not families:
        return 1.0
    return 1.0 if family in families else FLOOR


def apply_prior(probs: np.ndarray, classes: list[dict], family: str, targets, broad) -> np.ndarray:
    """Même calcul que PhotoDiagnosis.applyHostPrior : classes sans fiche non pondérées, puis renormalisation."""
    weights = np.ones(len(classes))
    for i, c in enumerate(classes):
        cid = c.get("catalogId")
        if cid:
            weights[i] = plausibility(cid, family, targets, broad)
    weighted = probs * weights
    total = weighted.sum()
    return probs if total <= 0 else weighted / total


def crop_of_folder(name: str) -> str | None:
    found = set(normalize(name).split()) & CROPS
    return next(iter(found)) if len(found) == 1 else None


def crop_of_taxon(taxon: str) -> str | None:
    t = taxon.strip().lower()
    return next((crop for prefix, crop in TAXON_CROP.items() if t.startswith(prefix)), None)


def collect(plantdoc: Path | None, classed: Path | None) -> tuple[list[tuple[Path, int, str]], dict[str, int]]:
    """Photos de test dont la culture est connue : (chemin, classe vraie, famille). Compte aussi les écartées."""
    items: list[tuple[Path, int, str]] = []
    skipped = {"culture inconnue": 0}
    if plantdoc:
        for folder in sorted(p for p in plantdoc.rglob("*") if p.is_dir()):
            if "test" not in {normalize(x) for x in folder.relative_to(plantdoc).parts}:
                continue
            label, crop = label_for_folder(folder.name), crop_of_folder(folder.name)
            if label is None or crop is None:
                continue
            for image in sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_EXTENSIONS):
                items.append((image, LABELS.index(label), CROP_FAMILY[crop]))
    if classed:
        table = classed / "ATTRIBUTIONS.csv"
        if not table.exists():
            table = Path(__file__).with_name("ATTRIBUTIONS.csv")
        hosts: dict[str, str] = {}
        with table.open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                taxon = row.get("host_taxon") or row.get("source_taxon") or ""
                crop = crop_of_taxon(taxon)
                if crop:
                    hosts[row["path"]] = crop
        for folder in sorted(p for p in classed.iterdir() if p.is_dir() and p.name in LABELS):
            for image in sorted(p for p in folder.iterdir() if p.suffix.lower() in IMAGE_EXTENSIONS):
                group = image.stem.split("_")[0]
                if zlib.crc32(f"{folder.name}/{group}".encode()) / 2**32 >= TEST_RATIO:
                    continue
                crop = hosts.get(f"{folder.name}/{image.name}")
                if crop is None:
                    skipped["culture inconnue"] += 1
                    continue
                items.append((image, LABELS.index(folder.name), CROP_FAMILY[crop]))
    return items, skipped


def make_interpreter(model: Path):
    try:
        import tensorflow as tf  # type: ignore

        return tf.lite.Interpreter(model_path=str(model))
    except ImportError:
        from tflite_runtime.interpreter import Interpreter  # type: ignore

        return Interpreter(model_path=str(model))


def infer(items, model: Path, spec: dict) -> np.ndarray:
    """Probabilités du modèle (moyenne image + miroir), une ligne par photo."""
    from PIL import Image

    size = spec["input"]["width"]
    interpreter = make_interpreter(model)
    interpreter.allocate_tensors()
    inp, out = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    rows = []
    for path, _, _ in items:
        with Image.open(path) as raw:
            img = raw.convert("RGB")
        side = min(img.size)
        left, top = (img.width - side) // 2, (img.height - side) // 2
        img = img.crop((left, top, left + side, top + side)).resize((size, size))
        arr = np.asarray(img, dtype=np.float32)
        views = []
        for view in (arr, arr[:, ::-1, :]):
            interpreter.set_tensor(inp["index"], np.ascontiguousarray(view)[None])
            interpreter.invoke()
            views.append(interpreter.get_tensor(out["index"])[0].astype(np.float64))
        rows.append(np.mean(views, axis=0))
    return np.stack(rows)


def metrics(probs: np.ndarray, truth: np.ndarray, threshold: float) -> dict:
    """Exactitude (sans seuil), couverture et précision au seuil, global et par classe vraie."""
    top, pred = probs.max(axis=1), probs.argmax(axis=1)
    kept = top >= threshold

    def block(mask: np.ndarray) -> dict:
        n, k = int(mask.sum()), int((mask & kept).sum())
        correct = int((mask & (pred == truth)).sum())
        kept_ok = int((mask & kept & (pred == truth)).sum())
        return {
            "n": n,
            "accuracy": round(correct / n, 3) if n else None,
            "accuracy_ci95": wilson(correct, n),
            "coverage": round(k / n, 3) if n else None,
            "precision_at_threshold": round(kept_ok / k, 3) if k else None,
            "precision_ci95": wilson(kept_ok, k),
        }

    per_class = {LABELS[i]: block(truth == i) for i in sorted(set(truth.tolist()))}
    return {"all": block(np.ones(len(truth), dtype=bool)), "per_class": per_class}


def format_report(result: dict) -> str:
    lines = [f"Seuil du modèle (inchangé) : {result['threshold']}", f"Photos : {result['n']}  écartées : {result['skipped']}", ""]
    for key, title in (("without_prior", "SANS a priori"), ("with_prior", "AVEC a priori")):
        m = result[key]
        a = m["all"]
        lines.append(f"{title} : exactitude {a['accuracy']}  couverture {a['coverage']}  précision {a['precision_at_threshold']}")
        for name, c in m["per_class"].items():
            warn = "  (effectif faible)" if c["n"] < MIN_TEST_IMAGES else ""
            lines.append(
                f"  {name:14s} n={c['n']:4d}  exactitude {c['accuracy']}  couverture {c['coverage']}  "
                f"précision {c['precision_at_threshold']} IC95 {c['precision_ci95']}{warn}"
            )
        lines.append("")
    return "\n".join(lines)


def self_test() -> int:
    """Vérifie la lecture de la table et le calcul sur des vecteurs jouets. Ne produit aucune performance."""
    targets, broad = load_prior_table()
    assert "doryphore" in targets and targets["doryphore"], "doryphore sans famille visée"
    assert "oidium" in broad and "mildiou" not in broad
    classes = [{"label": "sain", "catalogId": None}, {"label": "doryphore", "catalogId": "doryphore"}, {"label": "oidium", "catalogId": "oidium"}]
    probs = np.array([0.1, 0.6, 0.3])
    on_tomato = apply_prior(probs, classes, "SOLANACEAE", targets, broad)
    on_cabbage = apply_prior(probs, classes, "BRASSICACEAE", targets, broad)
    assert np.allclose(on_tomato, probs), "solanacée : doryphore plausible, rien ne change"
    assert abs(on_cabbage.sum() - 1) < 1e-9 and on_cabbage[1] > 0, "jamais zéro, somme 1"
    assert on_cabbage[1] < probs[1] and on_cabbage[2] > probs[2], "doryphore baisse sur chou, oïdium monte"
    assert crop_of_folder("Tomato leaf late blight") == "tomato" and crop_of_folder("Apple scab") is None
    assert crop_of_taxon("Solanum lycopersicum") == "tomato" and crop_of_taxon("Leptinotarsa decemlineata") is None
    m = metrics(np.array([[0.9, 0.1], [0.4, 0.6]]), np.array([0, 0]), 0.65)
    assert m["all"]["coverage"] == 0.5 and m["all"]["precision_at_threshold"] == 1.0
    print("auto-test réussi (aucune performance mesurée : il n'y a pas d'images).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--plantdoc", type=Path, help="dossier PlantDoc (sous-dossier test)")
    parser.add_argument("--classed", type=Path, help="dossier des photos rangées par classe (dataset_raw)")
    parser.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC)
    parser.add_argument("--cache", type=Path, help="fichier .npz de probabilités, créé s'il n'existe pas")
    parser.add_argument("--out", type=Path, help="rapport JSON")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if not (args.plantdoc or args.classed):
        parser.error("donnez --plantdoc et/ou --classed (ou --self-test)")

    spec = json.loads(args.spec.read_text(encoding="utf-8"))
    classes, threshold = spec["classes"], float(spec["threshold"])
    if [c["label"] for c in classes] != LABELS:
        raise SystemExit("Les classes du modèle ne correspondent pas à train_sillon.LABELS.")
    targets, broad = load_prior_table()
    items, skipped = collect(args.plantdoc, args.classed)
    if not items:
        raise SystemExit("Aucune photo de test avec culture connue : rien à mesurer.")
    truth = np.array([t for _, t, _ in items])

    if args.cache and args.cache.exists():
        probs = np.load(args.cache)["probs"]
        if len(probs) != len(items):
            raise SystemExit("Le cache ne correspond plus aux photos : supprimez-le.")
    else:
        probs = infer(items, args.model, spec)
        if args.cache:
            np.savez(args.cache, probs=probs)
    prior = np.stack([apply_prior(p, classes, fam, targets, broad) for p, (_, _, fam) in zip(probs, items)])

    result = {
        "threshold": threshold,
        "n": len(items),
        "skipped": skipped,
        "without_prior": metrics(probs, truth, threshold),
        "with_prior": metrics(prior, truth, threshold),
    }
    print(format_report(result))
    if args.out:
        args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
