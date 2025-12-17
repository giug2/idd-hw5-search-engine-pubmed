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
INPUT_DIR = "input/pmc_html_articles"
OUTPUT_DIR = "input/img"
HTML_EXTS = (".html", ".htm", ".xhtml", ".xml")

# =========================
# ELEMENTI DA ESCLUDERE (header, footer, modals, disclaimer PMC)
# =========================
EXCLUDED_SELECTORS = [
    "header", "footer", "nav", 
    ".usa-modal", ".usa-banner", ".usa-nav",
    "[role='banner']", "[role='navigation']", "[role='contentinfo']",
    "#ncbi-header", "#ncbi-footer", ".ncbi-header", ".ncbi-footer",
    ".pmc-sidebar", ".article-details", ".article-actions"
]

# Testi da escludere completamente (disclaimer NLM, etc.)
EXCLUDED_TEXT_PATTERNS = [
    "PERMALINK",
    "As a library, NLM provides access to scientific literature",
    "Inclusion in an NLM database does not imply endorsement",
    "Copy As a library",
    "Open in a new tab",
    "Google Scholar",
    "Go to:"
]


def should_exclude_paragraph(text):
    """Verifica se un paragrafo contiene testo da escludere."""
    if not text:
        return True
    for pattern in EXCLUDED_TEXT_PATTERNS:
        if pattern in text:
            return True
    return False

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

    # Rileva se è una pagina web PMC (HTML) o un file XML
    is_web_page = soup.find("html") is not None and soup.find("head") is not None
    
    # Se è una pagina web PMC, rimuovi elementi non desiderati
    if is_web_page:
        for selector in EXCLUDED_SELECTORS:
            for element in soup.select(selector):
                element.decompose()
        
        # Cerca il contenuto principale dell'articolo
        article_content = (
            soup.find("main") or 
            soup.find("article") or 
            soup.find(class_="article") or
            soup.find(id="mc") or
            soup
        )
    else:
        article_content = soup

    # Estrai paragrafi solo dal contenuto dell'articolo, escludendo testi problematici
    paragraphs = []
    for p in article_content.find_all(["p"]):  # Solo <p>, non <div>
        txt = clean_text(p.get_text(" ", strip=True))
        if txt and not should_exclude_paragraph(txt):
            paragraphs.append(txt)

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
# Statistiche immagini
# =========================
def summarize_images(output_folder=OUTPUT_DIR):
    files = [f for f in os.listdir(output_folder) if f.endswith(".json")]

    summary = {
        "total_articles": len(files),
        "total_images": 0,
        "field_counts": {
            "caption": 0,
            "alt": 0,
            "context_paragraphs": 0,
            "saved_path": 0
        }
    }

    for f in files:
        json_path = os.path.join(output_folder, f)
        try:
            with open(json_path, encoding="utf-8") as fh:
                images = json.load(fh)
        except Exception as e:
            print(f"[ERROR] Impossibile leggere {json_path}: {e}")
            continue

        summary["total_images"] += len(images)
        for img in images:
            for field in summary["field_counts"].keys():
                value = img.get(field)
                if value:
                    if isinstance(value, list) and len(value) == 0:
                        continue
                    summary["field_counts"][field] += 1

    # Stampa risultati
    print("===== IMAGE SUMMARY =====")
    print(f"Articoli processati: {summary['total_articles']}")
    print(f"Immagini estratte: {summary['total_images']}")
    print("Campi pieni per immagine:")
    for field, count in summary["field_counts"].items():
        print(f"  {field}: {count}")
    print("=========================")
    return summary


# =========================
# Esecuzione
# =========================
if __name__ == "__main__":
    import sys
    
    input_dir = INPUT_DIR
    output_dir = OUTPUT_DIR

    if len(sys.argv) >= 2:
        input_dir = sys.argv[1]
    if len(sys.argv) >= 3:
        output_dir = sys.argv[2]

    # Gestione path se eseguiti da dentro la cartella scripts/
    if not os.path.exists(input_dir) and os.path.exists("../" + input_dir):
        input_dir = "../" + input_dir
        output_dir = "../" + output_dir

    print(f"Input folder: {input_dir}")
    print(f"Output folder: {output_dir}")

    if not os.path.exists(input_dir):
        print(f"ERRORE: La cartella di input '{input_dir}' non esiste.")
        exit(1)

    process_folder(input_dir, output_dir)
    summarize_images(output_dir)
