# 🔎 Search Engine 
Quinto homework del corso di Ingegneria dei Dati dell'A.A. 2025/2026.  
  
Il progetto implementa uno strumento di recupero e indicizzazione di articoli medici da [PubMed](https://pmc.ncbi.nlm.nih.gov/search/?filter=collections.open_access): scarica documenti HTML corrispondenti alla query di ricerca "ultra-processed foods AND cardiovascular risk" e costruisce un corpus strutturato contenente testi, metadati e contenuti presenti nei documenti, quali tabelle e immagini, per successive attività di ricerca.

## 🎯 Obiettivo 
L’obiettivo del progetto è:
- Scaricare automaticamente articoli da pubmed.gov che soddisfano una query predefinita.
- Estrarre e normalizzare i contenuti rilevanti da ciascun documento.
- Organizzare il tutto in un corpus pronto per essere indicizzato.

## 🛠️ Tecnologie
Il progetto è sviluppato con:
- Python – script di supporto e post-processing.
- HTML / CSS – per la visualizzazione e gestione dei contenuti scaricati.
- Java 21 - linguaggio principale del progetto.
- Apache Lucene 10.3.1 (presente nella cartella lucene/) – motore di indicizzazione/search engine usato.
- Spring Boot 3.3.5 - framework principale per la configurazione e l’avvio dell’applicazione.

## 🖥️ Output e Statistiche
La cartella images/ contiene le statistiche riguardanti le estarzioni e le indicizzazioni.  
La cartella output/ conviene i risultati delle query di test che sono state lanciate.

## 🖊️ Autori
[Gaglione Giulia](https://github.com/giug2)  
[Pentimalli Gabriel](https://github.com/GabrielPentimalli)  
[Peroni Alessandro](https://github.com/smixale)  
[Tony Troy](https://github.com/troylion56)
