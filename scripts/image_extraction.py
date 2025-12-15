import json
import os
import string
import re
from lxml import etree

# ---------------------------------------------------------
# CONFIGURAZIONE STOP WORDS PER FIGURE
# ---------------------------------------------------------
# Lista di parole non informative da ignorare nell'estrazione dei termini dalle caption.
STOP_WORDS = {
    # Articoli e preposizioni inglesi
    'the', 'a', 'an', 'and', 'or', 'but', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by',
    'is', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'do', 'does', 'did',
    'this', 'that', 'these', 'those', 'it', 'we', 'they', 'them', 'their', 'its', 'our', 'your',
    'can', 'may', 'should', 'would', 'could', 'will', 'shall', 'must',
    # Termini generici di riferimento
    'table', 'figure', 'fig', 'section', 'eq', 'equation', 'et', 'al', 'shown', 'using', 'used',
    'show', 'shows', 'see', 'refer', 'reference', 'caption', 'image', 'picture', 'illustration',
    'above', 'below', 'left', 'right', 'top', 'bottom', 'as', 'also', 'here', 'there',
    # Verbi comuni
    'present', 'presents', 'presented', 'display', 'displays', 'displayed', 'depict', 'depicts',
    'illustrate', 'illustrates', 'illustrated', 'demonstrate', 'demonstrates', 'demonstrated',
    # Aggettivi/avverbi comuni
    'different', 'various', 'several', 'many', 'some', 'each', 'all', 'both', 'such', 'other',
    'first', 'second', 'third', 'new', 'proposed', 'respectively', 'corresponding',
    # Articoli e preposizioni italiane
    'il', 'lo', 'la', 'i', 'gli', 'le', 'un', 'uno', 'una', 'di', 'da', 'con', 'su', 'per',
    'tra', 'fra', 'è', 'sono', 'del', 'della', 'dei', 'delle', 'nel', 'nella', 'nei', 'nelle'
}


def extract_informative_terms(text):
    """
    Pulisce il testo e restituisce un set di termini unici "informativi".
    Rimuove punteggiatura, converte in minuscolo e filtra le stop words.
    """
    if not text:
        return set()
    
    # Rimuove la punteggiatura e converte in minuscolo
    translator = str.maketrans('', '', string.punctuation)
    clean_text = text.lower().translate(translator)
    
    # Divide in parole
    tokens = clean_text.split()
    
    # Filtra parole corte (< 3 caratteri), stop words e numeri puri
    informative_terms = {
        word for word in tokens 
        if word not in STOP_WORDS and len(word) > 2 and not word.isdigit()
    }
    
    return informative_terms


def get_node_text(node):
    """
    Estrae tutto il testo visibile da un nodo e dai suoi figli, pulito.
    """
    return "".join(node.itertext()).strip()


def get_node_html(node):
    """
    Restituisce la rappresentazione HTML (stringa) del nodo.
    """
    return etree.tostring(node, pretty_print=True).decode()


def build_full_image_url(src, base_url, article_id):
    """
    Costruisce l'URL completo dell'immagine partendo dal src relativo.
    Adattato per PMC (PubMed Central).
    """
    if not src:
        return ""
    
    # Se è già un URL completo, restituiscilo
    if src.startswith('http://') or src.startswith('https://'):
        return src
    
    # Base URL di PMC
    pmc_base = "https://pmc.ncbi.nlm.nih.gov"
    
    # Se il src inizia con /, è relativo alla root
    if src.startswith('/'):
        return f"{pmc_base}{src}"
        
    # Altrimenti proviamo a costruire un URL basato sull'ID articolo se disponibile
    if article_id:
        # Esempio: https://pmc.ncbi.nlm.nih.gov/articles/PMC12345/bin/image.jpg
        # Rimuoviamo eventuali prefissi "article_" o suffissi ".html" dall'ID se presenti
        clean_id = article_id.replace('article_', '').split('_')[-1] # Prende l'ultimo pezzo che dovrebbe essere il PMCID
        
        # Assicuriamoci che inizi con PMC
        if not clean_id.startswith('PMC') and clean_id.isdigit():
            clean_id = f"PMC{clean_id}"
            
        if clean_id.startswith('PMC'):
             # Aggiungiamo l'estensione .jpg se manca (spesso nei file XML manca l'estensione nel href, ma qui sembra esserci)
             return f"{pmc_base}/articles/{clean_id}/bin/{src}"
    
    return src


def process_figures_from_file(html_path, output_dir='output_figures'):
    """
    Funzione principale per estrarre le figure da un singolo file XML (PMC Article Set).
    """
    
    # Verifica esistenza file
    if not os.path.exists(html_path):
        print(f"Errore: Il file {html_path} non esiste.")
        return

    filename = os.path.basename(html_path)
    article_id = filename.replace('.html', '')
    print(f"--- Elaborazione figure dal file: {filename} ---")

    # Parsing XML
    try:
        with open(html_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Il parser XML di lxml è più adatto per questi file
        # Usiamo recover=True per essere più tolleranti
        parser = etree.XMLParser(recover=True)
        root = etree.fromstring(content.encode('utf-8'), parser=parser)
    except Exception as e:
        print(f"Errore nella lettura/parsing del file {filename}: {e}")
        return
    
    # Namespace map per XPath
    ns = {'xlink': 'http://www.w3.org/1999/xlink'}
    
    # Struttura dati finale
    extracted_data = {}

    # Estrazione del Titolo del documento
    title_nodes = root.xpath("//article-title")
    
    source_identifier = filename
    if title_nodes:
        page_title = get_node_text(title_nodes[0])
        if page_title:
            source_identifier = page_title

    # Trova tutte le figure (tag <fig>)
    found_figure_nodes = root.xpath("//fig")
    
    if not found_figure_nodes:
        print(f"Nessuna figura trovata nel file {filename}.")

    # Trova tutti i paragrafi del documento (escludendo quelli dentro figure/tabelle)
    # In PMC XML i paragrafi sono <p>
    all_paragraphs = root.xpath("//p[not(ancestor::fig) and not(ancestor::table-wrap)]")

    for figure_node in found_figure_nodes:
        
        # --- IDENTIFICAZIONE ID ---
        figure_id = figure_node.get('id')
        
        if not figure_id:
            unnamed_count = sum(1 for k in extracted_data.keys() if k.startswith('unnamed_fig_'))
            figure_id = f"unnamed_fig_{unnamed_count + 1}"

        print(f" -> Trovata figura ID: {figure_id}")

        # --- A. Estrazione URL Immagine e Link Href ---
        # Cerca tag <graphic>
        graphic_nodes = figure_node.xpath(".//graphic")
        image_url = ""
        image_alt = ""
        link_href = "" # In XML spesso non c'è un link href separato, ma usiamo l'immagine stessa
        
        if graphic_nodes:
            graphic_node = graphic_nodes[0]
            # L'attributo è xlink:href
            src = graphic_node.get(f"{{{ns['xlink']}}}href")
            if src:
                image_url = build_full_image_url(src, None, article_id)
                # Usiamo lo stesso URL per il link se non c'è altro
                link_href = image_url

        # --- B. Estrazione Caption ---
        # <caption/p> o solo <caption>
        caption_node = figure_node.xpath(".//caption")
        caption_text = ""
        if caption_node:
            caption_text = get_node_text(caption_node[0])

        # --- C. Analisi Termini Informativi dalla Caption ---
        caption_terms = extract_informative_terms(caption_text)
        
        # --- D. Scansione Paragrafi ---
        citing_paragraphs = []
        contextual_paragraphs = []

        for p in all_paragraphs:
            p_text = get_node_text(p)
            # Per XML, tostring potrebbe includere namespace, ma va bene
            p_html = etree.tostring(p, encoding='unicode', method='xml')
            
            # 1. Controllo Citazione Esplicita
            # Cerca <xref ref-type="fig" rid="figure_id">
            refs = p.xpath(f".//xref[@ref-type='fig' and @rid='{figure_id}']")
            
            is_citing = False
            if refs:
                citing_paragraphs.append(p_html)
                is_citing = True
            
            # 2. Controllo Termini (solo se non è già un paragrafo citante)
            if not is_citing and caption_terms:
                p_terms = extract_informative_terms(p_text)
                common_terms = caption_terms.intersection(p_terms)
                
                # SOGLIA: almeno 2 termini in comune per essere considerato rilevante
                if len(common_terms) >= 2:
                    contextual_paragraphs.append({
                        "html": p_html,
                        "matched_terms": list(common_terms)
                    })

        # --- E. Salvataggio Dati Figura ---
        extracted_data[figure_id] = {
            "source_file": source_identifier,
            "image_url": image_url,
            "link_href": link_href,
            "caption": caption_text,
            "citing_paragraphs": citing_paragraphs,
            "contextual_paragraphs": contextual_paragraphs
        }

    # Salvataggio su file JSON dedicato per questo articolo
    output_filename = f"{article_id}_figures.json"
    output_path = os.path.join(output_dir, output_filename)
    
    try:
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(extracted_data, f, ensure_ascii=False, indent=4)
        # print(f"Dati salvati in: {output_path}")
    except Exception as e:
        print(f"Errore nel salvataggio del JSON {output_path}: {e}")


# ---------------------------------------------------------
# ESECUZIONE
# ---------------------------------------------------------
if __name__ == '__main__':
    # -----------------------------------------------------
    # CONFIGURAZIONE DINAMICA PATH (Gerarchia Progetto)
    # -----------------------------------------------------
    
    # 1. Identifica la cartella dove si trova QUESTO script
    SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

    # 2. Identifica la cartella genitore (root del progetto)
    PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

    # 3. Definisci i percorsi relativi alla radice del progetto
    RESOURCES_DIR = os.path.join(PROJECT_ROOT, 'lucene', 'src', 'main', 'resources')

    # Input: cartella con i file HTML (usa 'input/pm_html_articles' nella root)
    SOURCE_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'pm_html_articles')
    
    # Output: cartella dove salvare i JSON delle figure
    # Salviamo in input/img nella root del progetto (come atteso da application.properties: data.img.path=../input/img)
    OUTPUT_DIRECTORY = os.path.join(PROJECT_ROOT, 'input', 'img')

    # Numero massimo di file da processare (None = tutti)
    NUM_FILES_TO_PROCESS = None
    
    # -----------------------------------------------------
    # LOGICA DI ESECUZIONE SU CARTELLA
    # -----------------------------------------------------
    
    print("-" * 60)
    print(f"Script Directory: {SCRIPT_DIR}")
    print(f"Project Root:     {PROJECT_ROOT}")
    print(f"Resources Dir:    {RESOURCES_DIR}")
    print(f"Input Folder:     {SOURCE_DIRECTORY}")
    print(f"Output Folder:    {OUTPUT_DIRECTORY}")
    print("-" * 60)

    if not os.path.exists(SOURCE_DIRECTORY):
        print(f"ERRORE: La cartella sorgente non esiste: {SOURCE_DIRECTORY}")
        print(f"Verifica che la cartella '{os.path.basename(SOURCE_DIRECTORY)}' esista nel percorso indicato.")
    else:
        # 1. Recupera tutti i file .html nella cartella
        all_files = [f for f in os.listdir(SOURCE_DIRECTORY) if f.endswith(".html")]
        all_files.sort()
        
        total_found = len(all_files)
        print(f"Totale file HTML trovati nella cartella: {total_found}")

        # 2. Applica il limite se impostato
        files_to_process = all_files
        if isinstance(NUM_FILES_TO_PROCESS, int) and NUM_FILES_TO_PROCESS > 0:
            print(f"Limite attivato: verranno processati solo i primi {NUM_FILES_TO_PROCESS} file.")
            files_to_process = all_files[:NUM_FILES_TO_PROCESS]
        else:
            print("Nessun limite impostato: verranno processati tutti i file.")

        # 3. Ciclo di elaborazione
        for i, filename in enumerate(files_to_process, 1):
            full_path = os.path.join(SOURCE_DIRECTORY, filename)
            
            print(f"\n[{i}/{len(files_to_process)}] Inizio elaborazione...")
            process_figures_from_file(full_path, output_dir=OUTPUT_DIRECTORY)

        print("\n--- Processo estrazione figure completato ---")


#    STRUTTURA DEL JSON GENERATO:
#
#    {
#        "id_figura_1": {
#            "source_file": "Titolo del paper o nome file",
#            "image_url": "https://arxiv.org/html/.../figs/image.png",
#            "caption": "Figure 1: Descrizione della figura...",
#            "informative_terms_identified": ["model", "architecture", "network", ...],
#            "citing_paragraphs": [
#                "<p>Paragrafo che cita esplicitamente la figura...</p>",
#                ...
#            ],
#            "contextual_paragraphs": [
#                {
#                    "html": "<p>Paragrafo con termini correlati...</p>",
#                    "matched_terms": ["model", "architecture"]
#                },
#                ...
#            ]
#        },
#        "id_figura_2": {
#            ...
#        }
#    }
