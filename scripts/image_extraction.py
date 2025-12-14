#!/usr/bin/env python3
"""
image_extraction.py

Estrae immagini (tag <img> e <figure> con <img>) da file HTML.
Input: singolo file HTML o cartella contenente file .html
Output: per ogni file viene creato un JSON in `images/` (default) con un array di immagini:
  {
    "paper_id": "article_0001_...",
    "image_id": "article_0001_img_1",
    "src": "images/fig1.png",
    "src_resolved": "pm_html_articles/article_0001/images/fig1.png",  # percorso assoluto o relativo risolto
    "alt": "...",
    "caption": "...",
    "context_paragraphs": [...],
    "fileName": "article_0001_..."
  }

Uso:
  python scripts/image_extraction.py <input_path> [output_folder]

Esempio:
  python scripts/image_extraction.py pm_html_articles/ images_output/

Dipendenze: beautifulsoup4, lxml

"""

from bs4 import BeautifulSoup
import os
import json
import sys
import re
import shutil
import base64
import urllib.request
import mimetypes
from urllib.parse import urljoin
import argparse

# Config
DEFAULT_OUTPUT = "images"
HTML_EXTS = (".html", ".htm", ".xhtml", ".xml")

# Utility


def find_input_path(candidates=None):
    """Prova a trovare automaticamente una cartella di input comune.

    Restituisce il primo percorso esistente tra i candidati forniti o None.
    """
    if candidates is None:
        candidates = [
            "input/pm_html_articles",
            "pm_html_articles",
            "input",
            "input/articles",
            "articles",
        ]
    for p in candidates:
        if os.path.isdir(p):
            return p
    return None

def clean_text(s):
    if not s:
        return ""
    return " ".join(s.split())


def resolve_src(base_file_path, src):
    """Ritorna un percorso risolto su disco (se possibile) a partire da src relativo.
    Se src è URL assoluto (http://...), lo restituisce così com'è.
    """
    if not src:
        return ""
    src = src.strip()
    # leave data: URIs as-is
    if src.startswith("data:"):
        return src
    # If it looks like an absolute URL, return unchanged
    if re.match(r'^https?://', src):
        return src
    # Otherwise join with the base file directory
    base_dir = os.path.dirname(os.path.abspath(base_file_path))
    resolved = os.path.normpath(os.path.join(base_dir, src))
    return resolved


def extract_from_file(path, output_folder=None):
    """Estrae immagini da un file HTML o JATS/XML e ritorna lista di dicts.

    Il parser viene scelto automaticamente: se il file sembra essere XML/JATS
    (inizia con '<?xml' o contiene il tag '<article'), utilizziamo il parser
    XML ('lxml-xml') per una parsing più corretta; altrimenti usiamo 'lxml'.
    """
    try:
        with open(path, "r", encoding="utf-8") as f:
            html = f.read()
    except Exception as e:
        print(f"[ERROR] Impossibile leggere {path}: {e}")
        return []

    # Scegli il parser in base al contenuto (semplice euristica)
    small = html[:400].lower()
    if small.lstrip().startswith('<?xml') or '<article' in small:
        parser = 'lxml-xml'
    else:
        parser = 'lxml'

    try:
        soup = BeautifulSoup(html, parser)
    except Exception:
        # fallback conservative
        soup = BeautifulSoup(html, 'lxml')

    # raccogli tutti i paragrafi per contesto
    paragraphs = [clean_text(p.get_text(" ", strip=True)) for p in soup.find_all(["p", "div"]) if clean_text(p.get_text(" ", strip=True))]

    images_out = []
    imgs = []

    # Prima, figure/fig (incluso JATS <fig>) che contengono immagini (fornisce accesso alla didascalia)
    for fig in soup.find_all(["figure", "fig"]):
    # preferisci <img> HTML
        img_tag = fig.find("img")
        if img_tag:
            imgs.append((img_tag, fig))
            continue
    # gestisci stile JATS <graphic> o <inline-graphic>
        graphic_tag = fig.find(["graphic", "inline-graphic"]) if hasattr(fig, 'find') else None
        if graphic_tag:
            imgs.append((graphic_tag, fig))

    # Poi, tag HTML <img> standalone non dentro figure/fig
    for img_tag in soup.find_all("img"):
        parent_fig = img_tag.find_parent(["figure", "fig"])
        if parent_fig is None:
            imgs.append((img_tag, None))

    # Gestisci anche JATS <graphic>/<inline-graphic> standalone non dentro fig
    for graphic_tag in soup.find_all(["graphic", "inline-graphic"]):
        parent_fig = graphic_tag.find_parent(["figure", "fig"])
        if parent_fig is None:
            imgs.append((graphic_tag, None))

    paper_id = os.path.splitext(os.path.basename(path))[0]

    for idx, (img_tag, fig_parent) in enumerate(imgs, start=1):
    # determina l'attributo src a seconda del tipo di tag
        src = ""
        alt = ""
    # HTML <img>
        if getattr(img_tag, 'name', '') == 'img':
            src = img_tag.get("src") or img_tag.get("data-src") or ""
            alt = clean_text(img_tag.get("alt", ""))
        else:
            # JATS/altro: cerca xlink:href, href
            src = img_tag.get("xlink:href") or img_tag.get("href") or img_tag.get("src") or ""
            # il testo alternativo può essere in un <caption> circostante o nell'attributo @alt
            alt = clean_text(img_tag.get("alt", ""))

        caption = ""
        if fig_parent is not None:
            # prova figcaption (HTML)
            figcap = fig_parent.find("figcaption")
            if figcap:
                caption = clean_text(figcap.get_text(" ", strip=True))
            else:
                # JATS: potrebbe essere usato <caption>
                cap = fig_parent.find("caption")
                if cap:
                    caption = clean_text(cap.get_text(" ", strip=True))
                else:
                    # prova il primo <p> o header in figure/fig
                    p_tag = fig_parent.find(["p", "h1", "h2", "h3", "h4"]) 
                    if p_tag:
                        caption = clean_text(p_tag.get_text(" ", strip=True))
        else:
            # fallback: cerca un paragrafo fratello vicino (precedente fino a 3)
            sibling = img_tag
            found = False
            # cerca all'indietro
            for _ in range(3):
                sibling = sibling.previous_sibling
                if sibling is None:
                    break
                if getattr(sibling, 'name', None) in ["p", "div"]:
                    txt = clean_text(sibling.get_text(" ", strip=True))
                    if txt:
                        caption = txt
                        found = True
                        break
            if not found:
                # cerca in avanti
                sibling = img_tag
                for _ in range(3):
                    sibling = sibling.next_sibling
                    if sibling is None:
                        break
                    if getattr(sibling, 'name', None) in ["p", "div"]:
                        txt = clean_text(sibling.get_text(" ", strip=True))
                        if txt:
                            caption = txt
                            break

    # paragrafi di contesto: euristica - paragrafi che contengono il nome file o sono vicini all'immagine nel DOM
        context = []
    # 1) paragrafi che fanno riferimento a 'Figure' o al nome del file immagine
        filename = os.path.basename(src) if src else ""
        for p in paragraphs:
            if filename and filename in p:
                context.append(p)
            elif re.search(r'figure\s*\d+', p, flags=re.IGNORECASE):
                context.append(p)
    # 2) limita i duplicati
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
    # se è fornita output_folder, prova a salvare il file immagine
        if output_folder and src_resolved:
            try:
                dest_dir = os.path.join(output_folder, paper_id)
                os.makedirs(dest_dir, exist_ok=True)
                # scegli il nome file
                fname = os.path.basename(src_resolved) if not src_resolved.startswith('data:') else f"{image_obj['image_id']}.bin"
                dest_path = os.path.join(dest_dir, fname)
                saved = False
                # data URI
                if src_resolved.startswith('data:'):
                    # formato: data:[<mediatype>][;base64],<data>
                    header, _, data = src_resolved.partition(',')
                    if ';base64' in header:
                        raw = base64.b64decode(data)
                        with open(dest_path, 'wb') as wf:
                            wf.write(raw)
                        saved = True
                # URL remoto
                elif re.match(r'^https?://', src_resolved):
                    try:
                        urllib.request.urlretrieve(src_resolved, dest_path)
                        saved = True
                    except Exception:
                        saved = False
                else:
                    # percorso file locale
                    if os.path.exists(src_resolved):
                        try:
                            shutil.copy2(src_resolved, dest_path)
                            saved = True
                        except Exception:
                            saved = False

                if saved:
                    image_obj['saved_path'] = os.path.relpath(dest_path)
                else:
                    # se il salvataggio è fallito, rimuoviamo la cartella creata se è vuota
                    try:
                        if os.path.isdir(dest_dir) and not os.listdir(dest_dir):
                            os.rmdir(dest_dir)
                    except Exception:
                        pass
                    image_obj['saved_path'] = ""
            except Exception:
                image_obj['saved_path'] = ""

        images_out.append(image_obj)

    return images_out


def process_path(input_path, output_folder=DEFAULT_OUTPUT, recursive=True, save_images=True):
    """Processa una cartella o file singolo. Se recursive=True, scansiona ricorsivamente.

    save_images: se False non salva i file immagine su disco (ma crea comunque i JSON)
    """
    if save_images:
        os.makedirs(output_folder, exist_ok=True)

    files = []
    if os.path.isdir(input_path):
        if recursive:
            for root, _, filenames in os.walk(input_path):
                for fname in sorted(filenames):
                    if fname.lower().endswith(HTML_EXTS):
                        files.append(os.path.join(root, fname))
        else:
            for fname in sorted(os.listdir(input_path)):
                if fname.lower().endswith(HTML_EXTS):
                    files.append(os.path.join(input_path, fname))
    elif os.path.isfile(input_path):
        files = [input_path]
    else:
        print(f"[ERROR] Input non trovato: {input_path}")
        return

    print(f"Trovati {len(files)} file HTML/XML da processare.")

    total_images = 0
    for f in files:
        # se non vogliamo salvare immagini, passiamo output_folder=None
        out_folder = output_folder if save_images else None
        images = extract_from_file(f, output_folder=out_folder)
        total_images += len(images)

        # scrivi JSON anche se non salviamo immagini
        json_out_dir = output_folder if output_folder else os.getcwd()
        os.makedirs(json_out_dir, exist_ok=True)
        out_path = os.path.join(json_out_dir, os.path.splitext(os.path.basename(f))[0] + ".json")
        try:
            with open(out_path, "w", encoding="utf-8") as out_f:
                json.dump(images, out_f, ensure_ascii=False, indent=2)
            print(f"[OK] {os.path.basename(f)} -> {out_path} (immagini: {len(images)})")
        except Exception as e:
            print(f"[ERROR] impossibile scrivere {out_path}: {e}")

    print(f"Processati {len(files)} file, immagini totali estratte: {total_images}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Estrai immagini da file HTML/JATS e salva metadata.")
    parser.add_argument("--input", "-i", help="File o cartella di input (se omesso prova a trovarlo automaticamente)")
    parser.add_argument("--output", "-o", default=DEFAULT_OUTPUT, help=f"Cartella output per JSON e immagini (default: {DEFAULT_OUTPUT})")
    parser.add_argument("--no-save-images", action="store_true", help="Non salvare i file immagine, solo JSON")
    parser.add_argument("--no-recursive", action="store_true", help="Non scansionare ricorsivamente le sottocartelle")
    parser.add_argument("--no-auto-detect", action="store_true", help="Disabilita la ricerca automatica della cartella di input")

    args = parser.parse_args()

    input_path = args.input
    if not input_path and not args.no_auto_detect:
        found = find_input_path()
        if found:
            input_path = found
            print(f"Input non specificato: trovato automaticamente '{input_path}'")

    if not input_path:
        parser.print_help()
        sys.exit(1)

    recursive = not args.no_recursive
    save_images = not args.no_save_images
    process_path(input_path, output_folder=args.output, recursive=recursive, save_images=save_images)
