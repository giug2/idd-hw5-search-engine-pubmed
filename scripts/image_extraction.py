"""
image_extraction_auto.py

Estrae immagini (tag <img> e <figure>) da tutti i file HTML in pm_html_articles/
Salva JSON e immagini in images_output/
"""

from bs4 import BeautifulSoup
import os
import json
import re
import shutil
import base64
import urllib.request

# =========================
# CONFIG
# =========================
INPUT_DIR = "pm_html_articles"
OUTPUT_DIR = "images_output"
HTML_EXTS = (".html", ".htm", ".xhtml", ".xml")

# =========================
# Utility
# =========================
def clean_text(s):
    if not s:
        return ""
    return " ".join(s.split())

def resolve_src(base_file_path, src):
    if not src:
        return ""
    src = src.strip()
    if src.startswith("data:") or re.match(r'^https?://', src):
        return src
    base_dir = os.path.dirname(os.path.abspath(base_file_path))
    return os.path.normpath(os.path.join(base_dir, src))

def extract_from_file(path, output_folder=None):
    try:
        with open(path, "r", encoding="utf-8") as f:
            html = f.read()
    except Exception as e:
        print(f"[ERROR] Impossibile leggere {path}: {e}")
        return []

    parser = 'lxml-xml' if html[:400].lstrip().startswith('<?xml') or '<article' in html[:400].lower() else 'lxml'
    try:
        soup = BeautifulSoup(html, parser)
    except Exception:
        soup = BeautifulSoup(html, 'lxml')

    paragraphs = [clean_text(p.get_text(" ", strip=True)) for p in soup.find_all(["p", "div"]) if clean_text(p.get_text(" ", strip=True))]

    images_out = []
    imgs = []

    # Figure / fig
    for fig in soup.find_all(["figure", "fig"]):
        img_tag = fig.find("img")
        if img_tag:
            imgs.append((img_tag, fig))
            continue
        graphic_tag = fig.find(["graphic", "inline-graphic"]) if hasattr(fig, 'find') else None
        if graphic_tag:
            imgs.append((graphic_tag, fig))

    # Tag <img> standalone
    for img_tag in soup.find_all("img"):
        parent_fig = img_tag.find_parent(["figure", "fig"])
        if parent_fig is None:
            imgs.append((img_tag, None))

    # JATS graphic standalone
    for graphic_tag in soup.find_all(["graphic", "inline-graphic"]):
        parent_fig = graphic_tag.find_parent(["figure", "fig"])
        if parent_fig is None:
            imgs.append((graphic_tag, None))

    paper_id = os.path.splitext(os.path.basename(path))[0]

    for idx, (img_tag, fig_parent) in enumerate(imgs, start=1):
        src = ""
        alt = ""
        if getattr(img_tag, 'name', '') == 'img':
            src = img_tag.get("src") or img_tag.get("data-src") or ""
            alt = clean_text(img_tag.get("alt", ""))
        else:
            src = img_tag.get("xlink:href") or img_tag.get("href") or img_tag.get("src") or ""
            alt = clean_text(img_tag.get("alt", ""))

        caption = ""
        if fig_parent is not None:
            figcap = fig_parent.find("figcaption") or fig_parent.find("caption")
            if figcap:
                caption = clean_text(figcap.get_text(" ", strip=True))

        context = []
        filename = os.path.basename(src) if src else ""
        for p in paragraphs:
            if filename and filename in p:
                context.append(p)
            elif re.search(r'figure\s*\d+', p, flags=re.IGNORECASE):
                context.append(p)
        context = list(dict.fromkeys(context))

        src_resolved = resolve_src(path, src)

        image_obj = {
            "paper_id": paper_id,
            "image_id": f"{paper_id}_img_{idx}",
            "src": src,
            "src_resolved": src_resolved,
            "alt": alt,
            "caption": caption,
            "context_paragraphs": context,
            "fileName": paper_id,
            "saved_path": ""
        }

        # Salvataggio immagini
        if output_folder and src_resolved:
            try:
                dest_dir = os.path.join(output_folder, paper_id)
                os.makedirs(dest_dir, exist_ok=True)
                fname = os.path.basename(src_resolved) if not src_resolved.startswith('data:') else f"{image_obj['image_id']}.bin"
                dest_path = os.path.join(dest_dir, fname)
                saved = False
                if src_resolved.startswith('data:'):
                    header, _, data = src_resolved.partition(',')
                    if ';base64' in header:
                        with open(dest_path, 'wb') as wf:
                            wf.write(base64.b64decode(data))
                        saved = True
                elif re.match(r'^https?://', src_resolved):
                    try:
                        urllib.request.urlretrieve(src_resolved, dest_path)
                        saved = True
                    except Exception:
                        saved = False
                else:
                    if os.path.exists(src_resolved):
                        try:
                            shutil.copy2(src_resolved, dest_path)
                            saved = True
                        except Exception:
                            saved = False

                image_obj['saved_path'] = os.path.relpath(dest_path) if saved else ""
            except Exception:
                image_obj['saved_path'] = ""

        images_out.append(image_obj)

    return images_out

# =========================
# Processamento cartella
# =========================
def process_folder(input_folder=INPUT_DIR, output_folder=OUTPUT_DIR):
    files = []
    for root, _, filenames in os.walk(input_folder):
        for fname in sorted(filenames):
            if fname.lower().endswith(HTML_EXTS):
                files.append(os.path.join(root, fname))

    print(f"Trovati {len(files)} file HTML da processare.")
    total_images = 0
    for f in files:
        images = extract_from_file(f, output_folder=output_folder)
        total_images += len(images)
        json_out_path = os.path.join(output_folder, os.path.splitext(os.path.basename(f))[0] + ".json")
        os.makedirs(output_folder, exist_ok=True)
        with open(json_out_path, "w", encoding="utf-8") as out_f:
            json.dump(images, out_f, ensure_ascii=False, indent=2)
        print(f"[OK] {os.path.basename(f)} -> {json_out_path} (immagini: {len(images)})")

    print(f"Processati {len(files)} file, immagini totali estratte: {total_images}")

# =========================
# Esecuzione
# =========================
if __name__ == "__main__":
    process_folder()
