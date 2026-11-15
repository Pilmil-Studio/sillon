#!/usr/bin/env python3
"""
Préparation et entraînement du détecteur temps réel Sillon YOLO v2.0.

Combine :
- PlantDoc Object Detection (CC BY 4.0) : boîtes englobantes sur feuilles potagères de terrain.
- PlantSeg (CC BY-NC 4.0 + accord écrit du 9 nov. 2026) : extraction automatique de boîtes
  englobantes à partir des masques de segmentation sur les cultures potagères uniquement.

Produit un modèle léger YOLO11n / YOLOv8n exporté en TFLite pour la détection en direct
dans le viseur CameraX de Sillon.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import re
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

YOLO_CLASSES = [
    "sain",
    "mildiou",
    "oidium",
    "alternariose",
    "rouille",
    "tetranyques",
    "autre",
]
CLASS_TO_ID = {name: i for i, name in enumerate(YOLO_CLASSES)}

# Mapping explicite des 29 classes de PlantDoc Object Detection (yusufmurtaza01/plantdoc-object-detection-dataset)
PLANTDOC_OD_MAP: dict[int, str | None] = {
    0: None,          # Cherry leaf
    1: None,          # Peach leaf
    2: None,          # Corn leaf blight
    3: None,          # Apple rust leaf
    4: "mildiou",     # Potato leaf late blight
    5: "sain",        # Strawberry leaf
    6: None,          # Corn rust leaf
    7: "mildiou",     # Tomato leaf late blight
    8: "autre",       # Tomato mold leaf
    9: "alternariose",# Potato leaf early blight
    10: None,         # Apple leaf
    11: "autre",      # Tomato leaf yellow virus
    12: None,         # Blueberry leaf
    13: "autre",      # Tomato leaf mosaic virus
    14: None,         # Raspberry leaf
    15: "autre",      # Tomato leaf bacterial spot
    16: "oidium",     # Squash Powdery mildew leaf
    17: None,         # grape leaf
    18: None,         # Corn Gray leaf spot
    19: "alternariose",# Tomato Early blight leaf
    20: None,         # Apple Scab Leaf
    21: "autre",      # Tomato Septoria leaf spot
    22: "sain",       # Tomato leaf
    23: None,         # Soyabean leaf
    24: "autre",      # Bell_pepper leaf spot
    25: "sain",       # Bell_pepper leaf
    26: None,         # grape leaf black rot
    27: "sain",       # Potato leaf
    28: "tetranyques",# Tomato two spotted spider mites leaf
}

VEGETABLE_PLANTS = {
    "basil", "bean", "bell pepper", "broccoli", "cabbage", "carrot", "cauliflower",
    "celery", "cucumber", "eggplant", "garlic", "lettuce", "potato", "squash",
    "strawberry", "tomato", "zucchini",
}


def map_plantseg_disease(plant: str, disease: str) -> str | None:
    p = plant.strip().lower()
    if p not in VEGETABLE_PLANTS:
        return None
    d = disease.strip().lower()
    if "late blight" in d or "downy mildew" in d or "phytophthora" in d:
        return "mildiou"
    if "powdery mildew" in d:
        return "oidium"
    if "early blight" in d or "alternaria" in d:
        return "alternariose"
    if "rust" in d:
        return "rouille"
    if "healthy" in d:
        return "sain"
    return "autre"


def mask_to_yolo_boxes(mask_path: Path, min_box_area_ratio: float = 0.005) -> list[tuple[float, float, float, float]]:
    """Extrait une ou plusieurs boîtes normalisées (cx, cy, w, h) depuis un masque PNG binaire."""
    arr = np.asarray(Image.open(mask_path).convert("L"))
    h_img, w_img = arr.shape
    if h_img == 0 or w_img == 0:
        return []
    fg = arr > 0
    if not fg.any():
        return []

    # Boîte englobante globale des lésions (ou composantes connexes si scipy est disponible)
    try:
        from scipy.ndimage import label as cc_label
        labeled, num_features = cc_label(fg)
        boxes: list[tuple[float, float, float, float]] = []
        for idx in range(1, num_features + 1):
            ys, xs = np.where(labeled == idx)
            if len(ys) == 0:
                continue
            x0, x1 = xs.min(), xs.max()
            y0, y1 = ys.min(), ys.max()
            bw = (x1 - x0 + 1) / w_img
            bh = (y1 - y0 + 1) / h_img
            if bw * bh < min_box_area_ratio:
                continue
            cx = ((x0 + x1) / 2.0) / w_img
            cy = ((y0 + y1) / 2.0) / h_img
            boxes.append((cx, cy, bw, bh))
        if boxes:
            return boxes
    except ImportError:
        pass

    ys, xs = np.where(fg)
    x0, x1 = xs.min(), xs.max()
    y0, y1 = ys.min(), ys.max()
    bw = (x1 - x0 + 1) / w_img
    bh = (y1 - y0 + 1) / h_img
    if bw * bh < min_box_area_ratio:
        return []
    cx = ((x0 + x1) / 2.0) / w_img
    cy = ((y0 + y1) / 2.0) / h_img
    return [(cx, cy, bw, bh)]


def prepare_plantdoc_od(root: Path, out_root: Path) -> dict[str, int]:
    """Remappe les labels de PlantDoc OD vers les 7 classes Sillon YOLO."""
    counts = {"train": 0, "val": 0}
    for split in ("train", "val"):
        img_dir = root / "images" / split
        lbl_dir = root / "labels" / split
        if not img_dir.is_dir() or not lbl_dir.is_dir():
            continue
        dst_img = out_root / "images" / split
        dst_lbl = out_root / "labels" / split
        dst_img.mkdir(parents=True, exist_ok=True)
        dst_lbl.mkdir(parents=True, exist_ok=True)
        for lbl_file in sorted(lbl_dir.glob("*.txt")):
            lines_out = []
            for raw_line in lbl_file.read_text(encoding="utf-8").splitlines():
                parts = raw_line.strip().split()
                if len(parts) != 5:
                    continue
                orig_cls = int(float(parts[0]))
                mapped = PLANTDOC_OD_MAP.get(orig_cls)
                if mapped is None:
                    continue
                lines_out.append(f"{CLASS_TO_ID[mapped]} {' '.join(parts[1:])}")
            if not lines_out:
                continue
            img_match = next((img_dir / f"{lbl_file.stem}{ext}" for ext in (".jpg", ".jpeg", ".png") if (img_dir / f"{lbl_file.stem}{ext}").is_file()), None)
            if not img_match:
                continue
            target_stem = f"pdoc_{lbl_file.stem}"
            shutil.copy2(img_match, dst_img / f"{target_stem}{img_match.suffix.lower()}")
            (dst_lbl / f"{target_stem}.txt").write_text("\n".join(lines_out) + "\n", encoding="utf-8")
            counts[split] += 1
    return counts


def prepare_plantseg_od(root: Path, out_root: Path) -> dict[str, int]:
    """Convertit les masques potagers de PlantSeg en boîtes YOLO."""
    counts = {"train": 0, "val": 0, "test": 0}
    base = root / "plantsegv2" if (root / "plantsegv2").is_dir() else root
    meta = base / "Metadatav2.csv"
    if not meta.is_file():
        return counts

    split_map = {"training": "train", "train": "train", "validation": "val", "val": "val", "test": "test", "testing": "test"}
    # Indexation rapide des images et masques
    img_index = {p.name: p for p in (base / "images").rglob("*") if p.is_file()} if (base / "images").is_dir() else {}
    mask_index = {p.name: p for p in (base / "annotations").rglob("*") if p.is_file()} if (base / "annotations").is_dir() else {}

    with meta.open(encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            mapped = map_plantseg_disease(row.get("Plant", ""), row.get("Disease", ""))
            if mapped is None:
                continue
            split = split_map.get(row.get("Split", "").strip().lower(), "train")
            img_path = img_index.get(row.get("Name", ""))
            mask_path = mask_index.get(row.get("Label file", ""))
            if not img_path or not mask_path:
                continue
            boxes = mask_to_yolo_boxes(mask_path)
            if not boxes:
                continue
            dst_img = out_root / "images" / split
            dst_lbl = out_root / "labels" / split
            dst_img.mkdir(parents=True, exist_ok=True)
            dst_lbl.mkdir(parents=True, exist_ok=True)
            stem = f"pseg_{img_path.stem}"
            shutil.copy2(img_path, dst_img / f"{stem}{img_path.suffix.lower()}")
            cls_id = CLASS_TO_ID[mapped]
            lbl_text = "\n".join(f"{cls_id} {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}" for cx, cy, w, h in boxes) + "\n"
            (dst_lbl / f"{stem}.txt").write_text(lbl_text, encoding="utf-8")
            counts[split] += 1
    return counts


def write_dataset_yaml(dataset_root: Path) -> Path:
    yaml_path = dataset_root / "sillon_yolo.yaml"
    lines = [
        f"path: {dataset_root.resolve()}",
        "train: images/train",
        "val: images/val",
    ]
    if (dataset_root / "images" / "test").is_dir():
        lines.append("test: images/test")
    lines.append(f"nc: {len(YOLO_CLASSES)}")
    lines.append("names:")
    for idx, name in enumerate(YOLO_CLASSES):
        lines.append(f"  {idx}: {name}")
    yaml_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return yaml_path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plantdoc-od", type=Path, help="Dossier PlantDoc Object Detection (CC BY 4.0)")
    parser.add_argument("--plantseg", type=Path, help="Dossier PlantSeg v2 (autorisation écrite 9 nov. 2026)")
    parser.add_argument("--work-dir", type=Path, default=Path("/tmp/sillon_yolo_data"))
    parser.add_argument("--out", type=Path, default=Path("out_yolo"))
    parser.add_argument("--model", type=str, default="yolo11n.pt")
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--imgsz", type=int, default=512)
    parser.add_argument("--batch", type=int, default=16)
    parser.add_argument("--prepare-only", action="store_true", help="Prépare le jeu YOLO sans lancer l'entraînement.")
    args = parser.parse_args()

    if args.work_dir.exists():
        shutil.rmtree(args.work_dir)
    args.work_dir.mkdir(parents=True, exist_ok=True)

    if args.plantdoc_od:
        c = prepare_plantdoc_od(args.plantdoc_od, args.work_dir)
        print(f"PlantDoc OD intégré : {c}")
    if args.plantseg:
        c = prepare_plantseg_od(args.plantseg, args.work_dir)
        print(f"PlantSeg (boîtes depuis masques) intégré : {c}")

    yaml_path = write_dataset_yaml(args.work_dir)
    print(f"Configuration YOLO écrite : {yaml_path}")

    if args.prepare_only:
        return 0

    from ultralytics import YOLO

    args.out.mkdir(parents=True, exist_ok=True)
    model = YOLO(args.model)
    model.train(
        data=str(yaml_path),
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        cos_lr=True,
        patience=20,
        close_mosaic=10,
        project=str(args.out),
        name="run",
        exist_ok=True,
    )

    best_pt = args.out / "run" / "weights" / "best.pt"
    best_model = YOLO(str(best_pt))
    val_metrics = best_model.val(data=str(yaml_path), split="val", imgsz=args.imgsz)
    test_metrics = best_model.val(data=str(yaml_path), split="test", imgsz=args.imgsz) if (args.work_dir / "images" / "test").is_dir() else None

    exported = best_model.export(format="tflite", imgsz=args.imgsz, int8=True, data=str(yaml_path))
    tflite_src = Path(str(exported))
    tflite_dst = args.out / "sillon_detector.tflite"
    if tflite_src.is_file():
        shutil.copy2(tflite_src, tflite_dst)
    elif tflite_src.is_dir():
        found = next(tflite_src.glob("*int8*.tflite"), None) or next(tflite_src.glob("*.tflite"), None)
        if found:
            shutil.copy2(found, tflite_dst)

    meta = {
        "format": 1,
        "created": dt.date.today().isoformat(),
        "model": args.model,
        "imgsz": args.imgsz,
        "classes": YOLO_CLASSES,
        "val_map50": float(val_metrics.box.map50),
        "val_map50_95": float(val_metrics.box.map),
        "test_map50": float(test_metrics.box.map50) if test_metrics else None,
        "test_map50_95": float(test_metrics.box.map) if test_metrics else None,
    }
    (args.out / "sillon_detector.json").write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(meta, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
