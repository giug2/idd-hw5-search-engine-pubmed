package it.uniroma3.idd.service;

import it.uniroma3.idd.dto.SearchResultDTO;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term; 
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class Searcher {
    
    private final Analyzer analyzer;
    private final Map<String, IndexSearcher> searcherMap = new HashMap<>();
    private final Map<String, DirectoryReader> readerMap = new HashMap<>();

    @Value("#{${lucene.indices.map}}")
    private Map<String, String> indexPaths; 

    @Autowired
    public Searcher(Analyzer perFieldAnalyzer) {
        this.analyzer = perFieldAnalyzer;
    }


    @PostConstruct
    public void init() throws IOException {
        System.out.println("Inizializzazione dinamica degli Index Searcher...");
        
        for (Map.Entry<String, String> entry : indexPaths.entrySet()) {
            String indexKey = entry.getKey();
            String path = entry.getValue();
            
            try {
                DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(path)));
                IndexSearcher searcher = new IndexSearcher(reader);
                
                readerMap.put(indexKey, reader);
                searcherMap.put(indexKey, searcher);
                System.out.println("-> Caricato indice: " + indexKey + " da: " + path);
            } catch (IOException e) {
                System.err.println("Errore nel caricamento dell'indice '" + indexKey + "' dal percorso: " + path + ". " + e.getMessage());
            }
        }
    }


    @PreDestroy
    public void destroy() {
        System.out.println("Chiusura di tutti i DirectoryReader...");
        for (DirectoryReader reader : readerMap.values()) {
            try {
                reader.close();
            } catch (IOException e) {
                System.err.println("Errore durante la chiusura del reader: " + e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // METODO DI RICERCA PRINCIPALE
    // -------------------------------------------------------------------------
    public Map<String, List<SearchResultDTO>> search(
            String queryText, 
            List<String> indicesScelti, 
            String campoScelto) throws Exception {

        Map<String, List<SearchResultDTO>> risultatiFinali = new HashMap<>();
        
        for (String indexKey : indicesScelti) {
            IndexSearcher currentSearcher = searcherMap.get(indexKey);
            
            if (currentSearcher == null) {
                System.err.println("Indice non trovato o non caricato: " + indexKey);
                continue; 
            }

            Query query = buildQuery(queryText, indexKey, campoScelto);
            TopDocs hits = currentSearcher.search(query, 10);
            risultatiFinali.put(indexKey, mapHitsToDTO(hits, currentSearcher, indexKey));
        }
        return risultatiFinali;
    }


    // -------------------------------------------------------------------------
    // HELPER: COSTRUZIONE QUERY SCALABILE (Logica "campo:parola")
    // -------------------------------------------------------------------------
    private Query buildQuery(String testoRicerca, String indexKey, String campoScelto) throws ParseException {
        // Se campoScelto NON è nullo, l'utente vuole usare la sintassi completa (es. "title:term")
        if (campoScelto != null && !campoScelto.isEmpty()) {
            
            // Usiamo la sintassi Lucene "campo:query"
            String queryInSintassiLucene = campoScelto + ":" + testoRicerca;

            // Usiamo un QueryParser generico per interpretare la sintassi Lucene completa.
            QueryParser parser = new QueryParser("id", analyzer); 
            
            return parser.parse(queryInSintassiLucene); 
        }
        
        // Logica per Ricerca Combinata/Generica (MultiFieldQuery)
        String[] defaultFields;
        switch (indexKey.toLowerCase()) {
            case "articoli":
                defaultFields = new String[]{"title", "authors", "articleAbstract", "paragraphs"};
                break;
            case "tabelle":
                defaultFields = new String[]{"caption", "body", "mentions", "terms", "context_paragraphs"};
                break;
            case "immagini":
                defaultFields = new String[]{"caption", "alt", "context_paragraphs", "fileName"};
                break;
            default:
                defaultFields = new String[]{}; 
                break;
        }
        
        if (defaultFields.length == 0) {
            throw new ParseException("Nessun campo di ricerca predefinito trovato per l'indice: " + indexKey);
        }

        // L'utilizzo dell'istanza risolve l'errore di tipizzazione
        MultiFieldQueryParser multiParser = new MultiFieldQueryParser(defaultFields, analyzer);
        return multiParser.parse(testoRicerca);
    }
    

    // -------------------------------------------------------------------------
    // HELPER: MAPPATURA RISULTATI (Hits -> DTO)
    // -------------------------------------------------------------------------
    private List<SearchResultDTO> mapHitsToDTO(TopDocs hits, IndexSearcher searcher, String indexKey) throws IOException {
        List<SearchResultDTO> results = new ArrayList<>();
        
        for (ScoreDoc scoreDoc : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            
            String id = doc.get("id"); 
            float score = scoreDoc.score;
            String titolo, snippet, urlDettaglio;
            
            if ("articoli".equals(indexKey)) {
                titolo = doc.get("title");
                String abst = doc.get("articleAbstract");
                snippet = (abst != null) ? abst.substring(0, Math.min(abst.length(), 150)) + "..." : "Abstract non disponibile.";
                urlDettaglio = "/dettaglio/articoli/" + id;
            } else if ("tabelle".equals(indexKey)) { 
                titolo = doc.get("caption");
                String context = doc.get("context_paragraphs");
                snippet = (context != null) ? context.substring(0, Math.min(context.length(), 150)) + "..." : "Contesto non disponibile.";
                String articleId = doc.get("fileName");
                urlDettaglio = "/dettaglio/tabelle/" + id + "?articleId=" + articleId; 
            } else if ("immagini".equals(indexKey)) {
                titolo = doc.get("caption") != null ? doc.get("caption") : doc.get("id");
                String contextImg = doc.get("context_paragraphs");
                snippet = (contextImg != null) ? contextImg.substring(0, Math.min(contextImg.length(), 150)) + "..." : "Contesto non disponibile.";
                String articleIdImg = doc.get("fileName");
                urlDettaglio = "/dettaglio/immagini/" + id + "?articleId=" + articleIdImg;
            } else {
                titolo = doc.get("title") != null ? doc.get("title") : doc.get("id"); 
                snippet = "Dettagli non ancora mappati per questo tipo di indice.";
                urlDettaglio = "/dettaglio/" + indexKey + "/" + id;
            }

            results.add(new SearchResultDTO(indexKey.toUpperCase(), id, titolo, snippet, score, urlDettaglio));
        }
        return results;
    }
    

    // -------------------------------------------------------------------------
    // METODO PER RECUPERO SINGOLO DOCUMENTO 
    // ------------------------------------------------------------------------- 
    public Document getDocumentById(String id, String indexKey) throws IOException {
        IndexSearcher targetSearcher = searcherMap.get(indexKey);
        if (targetSearcher == null) {
            throw new IllegalArgumentException("Indice non valido o non caricato: " + indexKey);
        }
        
        Query idQuery = new TermQuery(new Term("id", id));
        
        TopDocs hits = targetSearcher.search(idQuery, 1);

        if (hits.scoreDocs.length > 0) {
            return targetSearcher.storedFields().document(hits.scoreDocs[0].doc);
        }
        return null;
    }
}
