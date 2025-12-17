from Bio import Entrez
import requests
from bs4 import BeautifulSoup
from tqdm import tqdm
import time
import os
from time import time as now

# ==================================================
# CONFIGURAZIONE
# ==================================================
Entrez.email = "tuo_email@universita.it"   # OBBLIGATORIO
QUERY = '"ultra-processed foods" AND "cardiovascular risk"'
MAX_ARTICLES = 500
OUT_DIR = "pmc_html_articles"
BASE_URL = "https://www.ncbi.nlm.nih.gov"
DELAY = 0.4   # ~2.5 req/sec (safe)

HEADERS = {
    "User-Agent": "AcademicResearchBot/1.0 (tuo_email@universita.it)"
}

os.makedirs(OUT_DIR, exist_ok=True)

# ==================================================
# STATISTICHE
# ==================================================
start_time = now()
downloaded = 0
errors = 0

# ==================================================
# STEP 1 – Ricerca DIRETTA su PMC
# ==================================================
print("Ricerca articoli su PubMed Central (PMC)...")
handle = Entrez.esearch(
    db="pmc",
    term=QUERY,
    retmax=MAX_ARTICLES
)
record = Entrez.read(handle)
handle.close()

pmcids = record["IdList"]
print(f"Articoli PMC trovati: {len(pmcids)}")

# ==================================================
# Helper – URL relativi → assoluti
# ==================================================
def make_absolute_urls(soup):
    for tag in soup.find_all(["img", "a", "link", "script"]):
        attr = "src" if tag.name in ["img", "script"] else "href"
        if tag.get(attr) and tag[attr].startswith("/"):
            tag[attr] = BASE_URL + tag[attr]

# ==================================================
# STEP 2 – Download HTML full-text da PMC
# ==================================================
print("Download HTML da PMC...")
for pmcid in tqdm(pmcids[:MAX_ARTICLES]):
    url = f"{BASE_URL}/pmc/articles/PMC{pmcid}/?page=fulltext"

    try:
        response = requests.get(url, headers=HEADERS, timeout=15)
        if response.status_code != 200:
            print(f"⚠ Errore HTTP PMC{pmcid}")
            errors += 1
            continue

        soup = BeautifulSoup(response.text, "html.parser")
        make_absolute_urls(soup)

        filepath = os.path.join(OUT_DIR, f"PMC{pmcid}.html")
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(str(soup))

        downloaded += 1
        time.sleep(DELAY)

    except Exception as e:
        errors += 1
        print(f"Errore PMC{pmcid}: {e}")

# ==================================================
# STATISTICHE FINALI
# ==================================================
def folder_stats(path):
    total_size = 0
    for root, _, files in os.walk(path):
        for f in files:
            total_size += os.path.getsize(os.path.join(root, f))
    return {
        "total_size_mb": total_size / (1024 * 1024)
    }

end_time = now()
total_time = end_time - start_time
stats = folder_stats(OUT_DIR) if downloaded > 0 else None

print("\n==============================")
print("     STATISTICHE FINALI")
print("==============================")

print(f"Tempo totale: {total_time:.2f} sec")
if downloaded > 0:
    print(f"Tempo medio per articolo: {total_time / downloaded:.2f} sec")

print(f"Articoli trovati (PMC esearch): {len(pmcids)}")
print(f"Articoli scaricati: {downloaded}")
print(f"Errori: {errors}")

if stats:
    print(f"Dimensione totale cartella: {stats['total_size_mb']:.2f} MB")
else:
    print("Nessun file HTML trovato.")

print("\nCOMPLETATO! Articoli salvati in:", OUT_DIR)
print("==============================\n")
