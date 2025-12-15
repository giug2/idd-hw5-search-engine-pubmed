from Bio import Entrez
import requests
from bs4 import BeautifulSoup
from tqdm import tqdm
import time
import os

# ==================================================
# CONFIGURAZIONE
# ==================================================
Entrez.email = "tuo_email@universita.it"   # OBBLIGATORIO
QUERY = '"ultra-processed foods" AND "cardiovascular risk"'
MAX_ARTICLES = 500
OUT_DIR = "pm_html_articles"
BASE_URL = "https://www.ncbi.nlm.nih.gov"
DELAY = 0.4   # ~2.5 req/sec (safe)

HEADERS = {
    "User-Agent": "AcademicResearchBot/1.0 (tuo_email@universita.it)"
}

os.makedirs(OUT_DIR, exist_ok=True)

# ==================================================
# STEP 1 – Ricerca PubMed
# ==================================================
print("🔍 Ricerca articoli PubMed...")
handle = Entrez.esearch(
    db="pubmed",
    term=QUERY,
    retmax=MAX_ARTICLES
)
record = Entrez.read(handle)
handle.close()

pmids = record["IdList"]
print(f"PMID trovati: {len(pmids)}")

# ==================================================
# STEP 2 – PMID → PMCID (BATCH + RETRY)
# ==================================================
def chunks(lst, n):
    for i in range(0, len(lst), n):
        yield lst[i:i + n]

pmcids = []
print("🔗 Conversione PMID → PMCID (batch)...")

for batch in chunks(pmids, 50):
    attempts = 0
    success = False

    while not success and attempts < 3:
        try:
            handle = Entrez.elink(
                dbfrom="pubmed",
                db="pmc",
                id=batch
            )
            records = Entrez.read(handle)
            handle.close()

            for r in records:
                if r.get("LinkSetDb"):
                    for link in r["LinkSetDb"][0]["Link"]:
                        pmcids.append(link["Id"])

            success = True

        except Exception as e:
            attempts += 1
            print(f"⚠ Retry PMID→PMCID ({attempts}/3): {e}")
            time.sleep(2)

print(f"Articoli disponibili su PMC: {len(pmcids)}")

# ==================================================
# Helper – URL relativi → assoluti
# ==================================================
def make_absolute_urls(soup):
    for tag in soup.find_all(["img", "a", "link", "script"]):
        attr = "src" if tag.name in ["img", "script"] else "href"
        if tag.get(attr) and tag[attr].startswith("/"):
            tag[attr] = BASE_URL + tag[attr]

# ==================================================
# STEP 3 – Download HTML da PMC
# ==================================================
print("⬇️ Download HTML da PMC...")
for pmcid in tqdm(pmcids[:MAX_ARTICLES]):
    url = f"{BASE_URL}/pmc/articles/PMC{pmcid}/?page=fulltext"

    try:
        response = requests.get(url, headers=HEADERS, timeout=15)
        if response.status_code != 200:
            print(f"⚠ Errore HTTP PMC{pmcid}")
            continue

        soup = BeautifulSoup(response.text, "html.parser")
        make_absolute_urls(soup)

        filepath = os.path.join(OUT_DIR, f"PMC{pmcid}.html")
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(str(soup))

        time.sleep(DELAY)

    except Exception as e:
        print(f"❌ Errore PMC{pmcid}: {e}")

print("✅ Download completato.")
