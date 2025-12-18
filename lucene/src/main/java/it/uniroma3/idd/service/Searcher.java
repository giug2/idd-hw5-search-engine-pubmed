package it.uniroma3.idd.service;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
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

import it.uniroma3.idd.dto.SearchResultDTO;


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
            try { reader.close(); } catch (IOException e) { System.err.println("Errore chiusura reader: " + e.getMessage()); }
        }
    }


    public Map<String, List<SearchResultDTO>> search(String queryText, List<String> indicesScelti, String campoScelto) throws Exception {
        Map<String, List<SearchResultDTO>> risultatiFinali = new HashMap<>();

        for (String indexKey : indicesScelti) {
            IndexSearcher currentSearcher = searcherMap.get(indexKey);
            if (currentSearcher == null) {
                System.err.println("Indice non trovato o non caricato: " + indexKey);
                continue;
            }

            Query query = buildQuery(queryText, indexKey, campoScelto);
            TopDocs hits = currentSearcher.search(query, 50); // limitiamo a 50 risultati
            risultatiFinali.put(indexKey, mapHitsToDTO(hits, currentSearcher, indexKey));
        }
        return risultatiFinali;
    }


    private Query buildQuery(String testoRicerca, String indexKey, String campoScelto) throws ParseException {
        List<Query> queries = new ArrayList<>();
        String[] defaultFields;

        switch (indexKey.toLowerCase()) {
            case "articoli":
                defaultFields = new String[]{"title", "authors", "articleAbstract", "paragraphs", "pubblicationDate"};
                break;
            case "tabelle":
                defaultFields = new String[]{"caption", "body", "mentions", "context_paragraphs"};
                break;
            case "immagini":
                defaultFields = new String[]{"caption", "alt", "mentions", "context_paragraphs", "fileName"};
                break;
            default:
                defaultFields = new String[]{};
                break;
        }

        if (defaultFields.length == 0) {
            throw new ParseException("Nessun campo di ricerca predefinito per l'indice: " + indexKey);
        }

        // --- Intercetta range su publicationYear ---
        Pattern yearRangePattern = Pattern.compile("publicationYear\\s*:\\s*\\[(\\d{4})\\s+TO\\s+(\\d{4})\\]");
        Matcher mYear = yearRangePattern.matcher(testoRicerca);
        if (mYear.find()) {
            int min = Integer.parseInt(mYear.group(1));
            int max = Integer.parseInt(mYear.group(2));
            queries.add(IntPoint.newRangeQuery("publicationYear", min, max));
            testoRicerca = mYear.replaceAll(""); // rimuovo la parte range dalla query testuale
        }

        // --- Query testuale residua ---
        if (!testoRicerca.trim().isEmpty()) {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(defaultFields, analyzer);
            queries.add(parser.parse(testoRicerca.trim()));
        }

        // Combina tutte le query
        if (queries.size() == 1) return queries.get(0);
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (Query q : queries) builder.add(q, BooleanClause.Occur.MUST);
        return builder.build();
    }


    private List<SearchResultDTO> mapHitsToDTO(TopDocs hits, IndexSearcher searcher, String indexKey) throws IOException {
        List<SearchResultDTO> results = new ArrayList<>();
        for (ScoreDoc sd : hits.scoreDocs) {
            Document doc = searcher.storedFields().document(sd.doc);
            String id = doc.get("id");
            float score = sd.score;
            String titolo, snippet, urlDettaglio;

            switch (indexKey.toLowerCase()) {
                case "articoli":
                    titolo = doc.get("title");
                    String abst = doc.get("articleAbstract");
                    snippet = (abst != null) ? abst.substring(0, Math.min(abst.length(), 150)) + "..." : "Abstract non disponibile.";
                    urlDettaglio = "/dettaglio/articoli/" + id;
                    break;
                case "tabelle":
                    titolo = doc.get("caption");
                    String context = doc.get("context_paragraphs");
                    snippet = (context != null) ? context.substring(0, Math.min(context.length(), 150)) + "..." : "Contesto non disponibile.";
                    String articleId = doc.get("fileName");
                    urlDettaglio = "/dettaglio/tabelle/" + id + "?articleId=" + articleId;
                    break;
                case "immagini":
                    titolo = doc.get("caption") != null ? doc.get("caption") : doc.get("id");
                    String contextImg = doc.get("context_paragraphs");
                    snippet = (contextImg != null) ? contextImg.substring(0, Math.min(contextImg.length(), 150)) + "..." : "Contesto non disponibile.";
                    String articleIdImg = doc.get("fileName");
                    urlDettaglio = "/dettaglio/immagini/" + id + "?articleId=" + articleIdImg;
                    break;
                default:
                    titolo = doc.get("title") != null ? doc.get("title") : doc.get("id");
                    snippet = "Dettagli non ancora mappati per questo tipo di indice.";
                    urlDettaglio = "/dettaglio/" + indexKey + "/" + id;
                    break;
            }

            results.add(new SearchResultDTO(indexKey.toUpperCase(), id, titolo, snippet, score, urlDettaglio));
        }
        return results;
    }


    public Document getDocumentById(String id, String indexKey) throws IOException {
        IndexSearcher targetSearcher = searcherMap.get(indexKey);
        if (targetSearcher == null) throw new IllegalArgumentException("Indice non valido o non caricato: " + indexKey);

        Query idQuery = new TermQuery(new Term("id", id));
        TopDocs hits = targetSearcher.search(idQuery, 1);
        if (hits.scoreDocs.length > 0) return targetSearcher.storedFields().document(hits.scoreDocs[0].doc);
        return null;
    }
}
