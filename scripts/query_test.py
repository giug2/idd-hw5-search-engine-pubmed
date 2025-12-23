import os
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time

# --- CONFIGURAZIONE ---
URL_PAGINA = "http://localhost:8080/" 
FILE_OUTPUT = "report_test.txt"

# --- DEFINIZIONE TEST CASES BASATI SUL FILE "CHRONIC KIDNEY DISEASE" ---
TEST_CASES = [
# ---------------------------------------------------------
    # 1 ricerche su articoli
    # ---------------------------------------------------------
    {
        "query": "title:Kidney", 
        "filtri": ["articoli"], 
        "desc": "[Articoli] Ricerca nel Titolo (Aggiornato)"
    },
    {
        "query": "authors:Kim", 
        "filtri": ["articoli"], 
        "desc": "[Articoli] Ricerca per Autore"
    },
    {
        "query": "articleAbstract:\"dietary fiber\"", 
        "filtri": ["articoli"], 
        "desc": "[Articoli] Ricerca Frase Esatta nell'Abstract (Aggiornato)"
    },

    # ---------------------------------------------------------
    # 2 ricerche su tabelle
    # ---------------------------------------------------------
    {
        "query": "caption:statistics", 
        "filtri": ["tabelle"], 
        "desc": "[Tabelle] Ricerca nella Caption"
    },
    {
        "query": "body:\"confidence interval\"", 
        "filtri": ["tabelle"], 
        "desc": "[Tabelle] Ricerca nel Body"
    },

    # ---------------------------------------------------------
    # 3 ricerche su immagini
    # ---------------------------------------------------------
    {
        "query": "caption:Hamburger", 
        "filtri": ["immagini"], 
        "desc": "[Immagini] Ricerca nella Caption"
    },
    {
        "query": "alt:europe", 
        "filtri": ["immagini"], 
        "desc": "[immagini] alt"
    },

    # ---------------------------------------------------------
    # 4 ricerche combinate
    # ---------------------------------------------------------
    {
        "query": "nutrition",
        "filtri": ["articoli", "tabelle"], 
        "desc": "[Combo] Articoli + Tabelle (nutrition)"
    },
    {
        "query": "mortality",
        "filtri": ["articoli", "immagini"], 
        "desc": "[Combo] Articoli + Immagini (mortality)"
    },
    {
        "query": "accuracy", 
        "filtri": ["tabelle", "immagini"], 
        "desc": "Ricerca Combinata (Tabelle + Immagini)"
    },
    {
        "query": "diet quality", 
        "filtri": ["articoli", "tabelle", "immagini"], 
        "desc": "[Full] Tutti gli indici (diet quality)"
    },

    # ---------------------------------------------------------
    # 5 casi particolari
    # ---------------------------------------------------------
    {
        "query": "sdfgbhsfdhbwrghbrfgbrfbrbr", 
        "filtri": ["articoli", "tabelle", "immagini"], 
        "desc": "Ricerca Zero Risultati (Controllo gestione vuoto)"
    },
]

def esegui_test():
    if not os.path.exists(os.path.dirname(FILE_OUTPUT)) and os.path.dirname(FILE_OUTPUT) != "":
        os.makedirs(os.path.dirname(FILE_OUTPUT))

    options = webdriver.ChromeOptions()
    # options.add_argument("--headless") 
    
    print("Avvio Browser con i nuovi Test Case...")
    driver = webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=options)
    
    with open(FILE_OUTPUT, "w", encoding="utf-8") as f:
        f.write("=== REPORT TEST AGGIORNATO (DATI KIDNEY DISEASE) ===\n\n")

        try:
            for i, test in enumerate(TEST_CASES):
                print(f"[{i+1}/{len(TEST_CASES)}] Eseguo: {test['desc']}")
                
                driver.get(URL_PAGINA)
                query = test["query"]
                filtri = test["filtri"]

                f.write(f"TEST #{i+1}: {test['desc']}\n")
                f.write(f"QUERY: '{query}'\n")
                f.write(f"INDICI: {', '.join(filtri)}\n")
                f.write("-" * 40 + "\n")
                
                # 1. Imposta Filtri
                for tipo in ["articoli", "tabelle", "immagini"]:
                    try:
                        checkbox = driver.find_element(By.CSS_SELECTOR, f"input[type='checkbox'][value='{tipo}']")
                        is_selected = checkbox.is_selected()
                        should_be_selected = tipo in filtri
                        
                        if should_be_selected != is_selected:
                            checkbox.click()
                    except:
                        pass # Ignora errori checkbox se non critici

                # 2. Esegui Ricerca
                try:
                    input_query = driver.find_element(By.NAME, "query")
                    input_query.clear()
                    input_query.send_keys(query)
                    driver.find_element(By.CSS_SELECTOR, "button[type='submit']").click()
                except Exception as e:
                    f.write(f" [Errore] Submit: {e}\n")
                    continue
                
                # 3. Attesa Risultati
                try:
                    WebDriverWait(driver, 5).until(EC.presence_of_element_located((By.TAG_NAME, "body")))
                except:
                    f.write(" [Warn] Timeout.\n")

                # 4. Analisi Risultati
                f.write("RISULTATI:\n")
                try:
                    result_links = driver.find_elements(By.CSS_SELECTOR, "a[href*='/dettaglio/']")
                    
                    if not result_links:
                        f.write(" > Nessun risultato trovato.\n")
                    else:
                        for idx, link in enumerate(result_links):
                            if idx >= 5: break 
                            f.write(f"  - {link.text.strip()}\n")
                        f.write(f" (Totale: {len(result_links)})\n")
                        
                except Exception as e:
                    f.write(f" [Errore] Lettura risultati: {e}\n")

                f.write("\n" + "="*40 + "\n\n")
                time.sleep(0.5)

        except Exception as e:
            f.write(f"\nERRORE: {str(e)}\n")
        finally:
            driver.quit()

    print(f"\n Test completati. Report: {FILE_OUTPUT}")

if __name__ == "__main__":
    esegui_test()