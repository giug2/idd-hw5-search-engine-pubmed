'''
Programma per recuperare articoli scientifici da https://pmc.ncbi.nlm.nih.gov/search/?filter=collections.open_access 
In particolare si recuperano articoli: 
  - disponibili in formato HTML 
  - che contengono specifiche parole chiave nel titolo o nell'abstract.
'''

import requests
import time
import os

# Query
QUERY = '"ultra-processed foods" AND "cardiovascular risk"'
MAX_DOCS = 500

# Cartella output
OUTPUT_DIR = "pm_html_articles"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Endpoint NCBI
ESEARCH = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi"
EFETCH = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi"


def search_pmc_ids(query, retmax):
    """Cerca su PMC gli articoli che matchano la query e restituisce gli ID PMC."""
    params = {
        "db": "pmc",
        "term": query,
        "retmax": retmax,
        "retmode": "json"
    }
    r = requests.get(ESEARCH, params=params)
    r.raise_for_status()
    data = r.json()
    return data["esearchresult"]["idlist"]


def download_html(pmcid, idx):
    """Scarica l'HTML completo dell'articolo PMC"""
    params = {
        "db": "pmc",
        "id": pmcid,
        "rettype": "full",
        "retmode": "html"
    }
    r = requests.get(EFETCH, params=params)
    r.raise_for_status()

    path = os.path.join(OUTPUT_DIR, f"article_{idx:04d}_{pmcid}.html")
    with open(path, "w", encoding="utf-8") as f:
        f.write(r.text)
    
    return path


def compute_stats(folder):
    """Calcola statistiche sulla cartella scaricata."""
    files = [os.path.join(folder, f) for f in os.listdir(folder) if f.endswith(".html")]
    sizes = [os.path.getsize(f) for f in files]

    if not files:
        return None

    total_size = sum(sizes)

    return {
        "num_files": len(files),
        "total_size_mb": total_size / (1024 * 1024),
    }


def main():
    start_time = time.time()
    errors = 0

    print("Recupero lista ID da PMC tramite eSearch...")
    ids = search_pmc_ids(QUERY, MAX_DOCS)

    if not ids:
        print("Nessun articolo trovato.")
        return

    print(f"Trovati {len(ids)} articoli. Inizio download...\n")

    downloaded = 0

    for idx, pmcid in enumerate(ids, start=1):
        if idx > MAX_DOCS:
            break
        try:
            path = download_html(pmcid, idx)
            downloaded += 1
            print(f"Scaricato {path}")
            time.sleep(0.34)  # evitare rate limit NCBI (~3 richieste/sec)
        except Exception as e:
            errors += 1
            print(f"Errore su {pmcid}: {e}")

    total_time = time.time() - start_time

    # Statistiche sui file scaricati
    stats = compute_stats(OUTPUT_DIR)

    print("\n==============================")
    print("     STATISTICHE FINALI")
    print("==============================")

    print(f"Tempo totale: {total_time:.2f} sec")
    if downloaded > 0:
        print(f"Tempo medio per articolo: {total_time / downloaded:.2f} sec")
    print(f"Articoli trovati (esearch): {len(ids)}")
    print(f"Articoli scaricati: {downloaded}")
    print(f"Errori: {errors}")

    if stats:
        print(f"Dimensione totale cartella: {stats['total_size_mb']:.2f} MB")
    else:
        print("Nessun file HTML trovato, impossibile calcolare statistiche.")

    print("\nCOMPLETATO! Articoli salvati in:", OUTPUT_DIR)
    print("==============================\n")


if __name__ == "__main__":
    main()
