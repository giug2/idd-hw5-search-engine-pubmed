# extract_tables.py
import nltk
nltk.download("punkt")
nltk.download("stopwords")

'''
tables_output.append({
            "paper_id": paper_id,
            "table_id": table_id,
            "caption": caption,
            "body": body,
            "html_body": html_body,
            "mentions": mentions,
            "context_paragraphs": context,
            "terms": list(terms)
        })
'''

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

        html_body = str(table)

        # ---- TABLE ID ----
        table_id = table.get("id")
        if not table_id:
            table_id = f"{paper_id}_table_{idx}"

        # ---- CAPTION ----
        caption = ""
        
        # 1. CERCA <caption/> (Figlio diretto di <table>)
        cap_tag = table.find("caption")
        if cap_tag:
            caption = clean_text(cap_tag.get_text(" ", strip=True))
        
        # 2. CERCA NEL PARENT (Spesso <figure> o <table-wrap>)
        if not caption:
            parent = table.parent
            if parent:
                # Cerca figcaption o un elemento <title> o <p> all'inizio del contenitore
                # Usiamo select_one per prendere il primo elemento trovato
                figcap = parent.find("figcaption")
                title_tag = parent.find("title") 
                p_tag = parent.find("p") # A volte è un paragrafo
                
                if figcap:
                    caption = clean_text(figcap.get_text(" ", strip=True))
                elif title_tag:
                    caption = clean_text(title_tag.get_text(" ", strip=True))
                elif p_tag and p_tag.get_text(" ", strip=True):
                    # Solo se il paragrafo precede la tabella (euristica debole)
                    caption = clean_text(p_tag.get_text(" ", strip=True))
        
        # 3. Pulizia finale: Rimuovi la label (es. "Table 1:")
        if caption:
            caption = re.sub(r'^\s*Table\s*\d+\s*:\s*', '', caption, flags=re.IGNORECASE).strip()

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
            "html_body": html_body,
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
# STATISTICHE
# ---------------------------------------------------------
def summarize_tables(output_folder="tables"):
    files = [f for f in os.listdir(output_folder) if f.endswith(".json")]
    summary = {
        "total_articles": len(files),
        "total_tables": 0,
        "field_counts": {
            "paper_id": 0,
            "table_id": 0,
            "caption": 0,
            "body": 0,
            "html_body": 0,
            "mentions": 0,
            "context_paragraphs": 0,
            "terms": 0
        }
    }

    for filename in files:
        path = os.path.join(output_folder, filename)
        with open(path, "r", encoding="utf-8") as f:
            tables = json.load(f)
            summary["total_tables"] += len(tables)

            for table in tables:
                for field in summary["field_counts"]:
                    value = table.get(field)
                    if value:
                        # consideriamo anche liste non vuote come “piene”
                        if isinstance(value, list):
                            if len(value) > 0:
                                summary["field_counts"][field] += 1
                        else:
                            summary["field_counts"][field] += 1

    print("===== SUMMARY =====")
    print(f"Articoli processati: {summary['total_articles']}")
    print(f"Tabelle estratte: {summary['total_tables']}")
    print("Campi pieni per campo JSON:")
    for field, count in summary["field_counts"].items():
        print(f"  {field}: {count}")
    print("===================")
    return summary


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
    
    # Statistiche
    summarize_tables("tables")
