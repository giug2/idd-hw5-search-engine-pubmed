# extract_tables.py
import nltk
nltk.download("punkt")
nltk.download("stopwords")


import os
import json
from bs4 import BeautifulSoup
import re
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize

# ---------------------------------------------------------
# CONFIG
# ---------------------------------------------------------
STOPWORDS = set(stopwords.words("english"))
TABLE_REF_RE = re.compile(r'\btable\s*\d+\b', flags=re.IGNORECASE)
MIN_CONTEXT_TERMS = 2   # quanti termini comuni servono per contesto?


# ---------------------------------------------------------
# UTILITIES
# ---------------------------------------------------------
def clean_text(s):
    """Rimuove whitespace e normalizza il testo."""
    if not s:
        return ""
    return " ".join(s.split())


def tokenize_terms(text):
    """Tokenizza il testo e restituisce solo termini informativi."""
    tokens = word_tokenize(text.lower())
    return [
        t for t in tokens
        if t.isalpha() and t not in STOPWORDS and len(t) >= 3
    ]


# ---------------------------------------------------------
# ESTRAZIONE DA UN SINGOLO ARTICOLO HTML
# ---------------------------------------------------------
def extract_tables_from_html(html_string, paper_id):
    soup = BeautifulSoup(html_string, "lxml")

    # Tutti i paragrafi dell'articolo
    paragraphs = []
    for p in soup.find_all(["p", "div"]):
        txt = clean_text(p.get_text(" ", strip=True))
        if txt:
            paragraphs.append(txt)

    tables_output = []
    tables = soup.find_all("table")

    # Possibile fallback se le tabelle sono wrappate in <figure>
    if not tables:
        tables = soup.find_all(lambda tag:
            tag.name in ["figure", "div"] and tag.find("table")
        )

    for idx, table in enumerate(tables, start=1):

        # ---- TABLE ID ----
        table_id = table.get("id")
        if not table_id:
            table_id = f"{paper_id}_table_{idx}"

        # ---- CAPTION ----
        caption = ""
        cap_tag = table.find("caption")
        if cap_tag:
            caption = clean_text(cap_tag.get_text(" ", strip=True))
        else:
            # cerca figcaption / tag vicino
            parent = table.parent
            figcap = parent.find("figcaption") if parent else None
            if figcap:
                caption = clean_text(figcap.get_text(" ", strip=True))

        # ---- BODY (TESTO) ----
        rows = []
        for tr in table.find_all("tr"):
            cells = [
                clean_text(td.get_text(" ", strip=True))
                for td in tr.find_all(["td", "th"])
            ]
            if cells:
                rows.append(cells)

        if rows:
            body = "\n".join([" | ".join(r) for r in rows])
        else:
            body = clean_text(table.get_text(" ", strip=True))

        # ---- TERMINI INFORMALI ----
        all_text = caption + " " + body
        terms = set(tokenize_terms(all_text))

        # ---- MENTIONS ----
        mentions = []
        for p in paragraphs:
            if TABLE_REF_RE.search(p):
                mentions.append(p)

        # ---- CONTEXT PARAGRAPHS ----
        context = []
        for p in paragraphs:
            p_terms = set(tokenize_terms(p))
            if len(p_terms.intersection(terms)) >= MIN_CONTEXT_TERMS:
                context.append(p)

        # ---- CREA OUTPUT ----
        tables_output.append({
            "paper_id": paper_id,
            "table_id": table_id,
            "caption": caption,
            "body": body,
            "mentions": mentions,
            "context_paragraphs": context,
            "terms": list(terms)
        })

    return tables_output


# ---------------------------------------------------------
# PROCESSA TUTTA LA CARTELLA pm_html_articles
# ---------------------------------------------------------
def process_folder(input_folder, output_folder="tables"):
    os.makedirs(output_folder, exist_ok=True)

    files = [f for f in os.listdir(input_folder) if f.endswith(".html")]
    print(f"Trovati {len(files)} articoli.")

    for filename in files:
        path = os.path.join(input_folder, filename)
        paper_id = os.path.splitext(filename)[0]

        with open(path, "r", encoding="utf-8") as f:
            html = f.read()

        tables = extract_tables_from_html(html, paper_id)

        out_path = os.path.join(output_folder, f"{paper_id}.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(tables, f, indent=2, ensure_ascii=False)

        print(f"[OK] {filename} → {out_path} (tabelle: {len(tables)})")


# ---------------------------------------------------------
# MAIN
# ---------------------------------------------------------
if __name__ == "__main__":
    import sys
    if len(sys.argv) < 2:
        print("USO: python table_extraction.py pm_html_articles/")
        exit()

    input_folder = sys.argv[1]
    process_folder(input_folder)
