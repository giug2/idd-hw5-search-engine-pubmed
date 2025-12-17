# extract_tables.py
import nltk
nltk.download("punkt", quiet=True)
nltk.download("punkt_tab", quiet=True)
nltk.download("stopwords", quiet=True)

import os
import json
import re
from bs4 import BeautifulSoup
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize

# ---------------------------------------------------------
# CONFIG
# ---------------------------------------------------------
STOPWORDS = set(stopwords.words("english"))
TABLE_REF_RE = re.compile(r'\btable\s*\d+\b', flags=re.IGNORECASE)
MIN_CONTEXT_TERMS = 2


# ---------------------------------------------------------
# UTILITIES
# ---------------------------------------------------------
def clean_text(s):
    if not s:
        return ""
    return " ".join(s.split())


def tokenize_terms(text):
    tokens = word_tokenize(text.lower())
    return [
        t for t in tokens
        if t.isalpha() and t not in STOPWORDS and len(t) >= 3
    ]


# ---------------------------------------------------------
# ELEMENTI DA ESCLUDERE
# ---------------------------------------------------------
EXCLUDED_SELECTORS = [
    "header", "footer", "nav",
    ".usa-modal", ".usa-banner", ".usa-nav",
    "[role='banner']", "[role='navigation']", "[role='contentinfo']",
    "#ncbi-header", "#ncbi-footer", ".ncbi-header", ".ncbi-footer",
    ".pmc-sidebar", ".article-details", ".article-actions"
]

EXCLUDED_TEXT_PATTERNS = [
    "PERMALINK",
    "As a library, NLM provides access to scientific literature",
    "Inclusion in an NLM database does not imply endorsement",
    "Open in a new tab",
    "Google Scholar",
    "Go to:"
]


def should_exclude_paragraph(text):
    if not text:
        return True
    return any(p in text for p in EXCLUDED_TEXT_PATTERNS)


# ---------------------------------------------------------
# ESTRAZIONE TABELLE DA HTML PMC
# ---------------------------------------------------------
def extract_tables_from_html(html_string, paper_id):
    soup = BeautifulSoup(html_string, "lxml")

    is_web_page = soup.find("html") and soup.find("head")

    if is_web_page:
        for selector in EXCLUDED_SELECTORS:
            for el in soup.select(selector):
                el.decompose()

        article_content = (
            soup.find("main")
            or soup.find("article")
            or soup.find(id="mc")
            or soup
        )
    else:
        article_content = soup

    paragraphs = []
    for p in article_content.find_all("p"):
        txt = clean_text(p.get_text(" ", strip=True))
        if txt and not should_exclude_paragraph(txt):
            paragraphs.append(txt)

    tables_output = []
    tables = soup.find_all("table")

    if not tables:
        tables = soup.find_all(lambda t: t.name in ["figure", "div"] and t.find("table"))

    for idx, table in enumerate(tables, start=1):

        html_body = str(table)

        table_id = table.get("id") or f"{paper_id}_table_{idx}"

        # -------------------------------------------------
        # CAPTION (PMC-AWARE)
        # -------------------------------------------------
        # ---- CAPTION ----
        caption = ""

        # 1. prova a prendere <caption> dentro table
        cap_tag = table.find("caption")
        if cap_tag:
            caption = clean_text(cap_tag.get_text(" ", strip=True))

        # 2. prova a prendere figcaption/title/p nel parent
        if not caption:
            parent = table.parent
            if parent:
                figcap = parent.find("figcaption")
                title_tag = parent.find("title")
                p_tag = parent.find("p")
                if figcap:
                    caption = clean_text(figcap.get_text(" ", strip=True))
                elif title_tag:
                    caption = clean_text(title_tag.get_text(" ", strip=True))
                elif p_tag and p_tag.get_text(" ", strip=True):
                    caption = clean_text(p_tag.get_text(" ", strip=True))

        # 3. nuovo: cerca il div.caption **precedente immediato** prima del tbl-box
        if not caption:
            prev_div = table.find_parent("div", class_="tbl-box")
            if prev_div:
                sibling = prev_div.find_previous_sibling("div", class_="caption")
                if sibling:
                    caption = clean_text(sibling.get_text(" ", strip=True))

        # 4. fallback: prendi il paragrafo immediatamente successivo
        if not caption:
            next_p = table.find_next("p")
            if next_p and next_p.get_text(strip=True):
                caption = clean_text(next_p.get_text(" ", strip=True))

        # 5. pulizia finale
        if caption:
            caption = re.sub(r'^\s*Table\s*\d+\s*[:.]?\s*', '', caption, flags=re.IGNORECASE).strip()


        # -------------------------------------------------
        # BODY
        # -------------------------------------------------
        rows = []
        for tr in table.find_all("tr"):
            cells = [
                clean_text(td.get_text(" ", strip=True))
                for td in tr.find_all(["td", "th"])
            ]
            if cells:
                rows.append(cells)

        body = "\n".join(" | ".join(r) for r in rows) if rows else clean_text(table.get_text(" ", strip=True))

        # -------------------------------------------------
        # TERMINI
        # -------------------------------------------------
        all_text = caption + " " + body
        terms = set(tokenize_terms(all_text))

        # -------------------------------------------------
        # MENTIONS
        # -------------------------------------------------
        mentions = [p for p in paragraphs if TABLE_REF_RE.search(p)]

        # -------------------------------------------------
        # CONTEXT
        # -------------------------------------------------
        context = []
        for p in paragraphs:
            if len(set(tokenize_terms(p)).intersection(terms)) >= MIN_CONTEXT_TERMS:
                context.append(p)

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
# PROCESS FOLDER
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
        "field_counts": {}
    }

    # campi da monitorare
    fields = ["paper_id", "table_id", "caption", "body", "html_body", "mentions", "context_paragraphs", "terms"]
    for field in fields:
        summary["field_counts"][field] = 0

    for f in files:
        with open(os.path.join(output_folder, f), encoding="utf-8") as fh:
            tables = json.load(fh)
            summary["total_tables"] += len(tables)

            for table in tables:
                for field in fields:
                    value = table.get(field)
                    if value:
                        # considera liste non vuote come "piene"
                        if isinstance(value, list):
                            if len(value) > 0:
                                summary["field_counts"][field] += 1
                        else:
                            summary["field_counts"][field] += 1

    # Stampa risultati
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

    input_folder = "input/pmc_html_articles"
    output_folder = "input/tables"

    if len(sys.argv) >= 2:
        input_folder = sys.argv[1]
    if len(sys.argv) >= 3:
        output_folder = sys.argv[2]

    if not os.path.exists(input_folder) and os.path.exists("../" + input_folder):
        input_folder = "../" + input_folder
        output_folder = "../" + output_folder

    print(f"Input folder: {input_folder}")
    print(f"Output folder: {output_folder}")

    process_folder(input_folder, output_folder)
    summarize_tables(output_folder)
