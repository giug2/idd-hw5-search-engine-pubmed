package it.uniroma3.idd.controller;

import it.uniroma3.idd.dto.GetDocumentResponse;
import it.uniroma3.idd.service.Searcher;
import org.apache.lucene.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Value;
import java.util.HashMap;
import java.util.Map;


@Controller
public class DetailController {
    
    private final Searcher searcher;

    @Value("${data.img.path}")
    private String dataImgPath;


    @Autowired
    public DetailController(Searcher searcher) {
        this.searcher = searcher;
    }


    /**
     * Gestisce la visualizzazione dei dettagli per Articoli, Tabelle, Immagini, ecc.
     * URL: /dettaglio/{indexKey}/{id} (Esempio: /dettaglio/articoli/12345)
     * * @param indexKey Identificatore dell'indice ("articoli", "tabelle", ecc.)
     * @param id ID univoco del documento (campo "id" in Lucene)
     */
    @GetMapping("/dettaglio/{indexKey}/{id}")
    public String viewDetails(
            @PathVariable String indexKey,
            @PathVariable String id,
            @RequestParam(name = "articleId", required = false) String articleId, 
            Model model) {
        
        System.out.println("DEBUG: Richiesta dettaglio Articolo. ID ricevuto: '" + id + 
                   "', Indice: " + indexKey);
        try {
            
            Document luceneDoc = searcher.getDocumentById(id, indexKey);

            if (luceneDoc == null) {
                model.addAttribute("error", "Documento non trovato con ID: " + id + " nell'indice: " + indexKey);
                return "error_page"; 
            }
            
            GetDocumentResponse responseDTO = mapDocumentToResponse(luceneDoc, indexKey);
            
            model.addAttribute("document", responseDTO);
            model.addAttribute("indexKey", indexKey);

            return indexKey + "_detail"; 
            
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Errore: Indice specificato non valido o non caricato. " + e.getMessage());
            return "error_page";
        } catch (Exception e) {
            model.addAttribute("error", "Errore nel recupero dei dettagli: " + e.getMessage());
            e.printStackTrace();
            return "error_page";
        }
    }


    /**
     * Mappa il Documento Lucene al DTO GetDocumentResponse, gestendo i campi specifici
     * in modo scalabile (switch basato sulla chiave dell'indice).
     */
    private GetDocumentResponse mapDocumentToResponse(Document doc, String indexKey) {
        
        String id = doc.get("id");
        String title = "N/A";
        String authors = "N/A";
        Map<String, String> results = new HashMap<>();

        switch (indexKey.toLowerCase()) {
            case "articoli":
                title = doc.get("title");
                authors = doc.get("authors");
                String articleId = doc.get("id");
                String rawHtmlUrl = "/raw_articles/" + articleId + ".html";
                results.put("URL Articolo Originale", rawHtmlUrl);
                results.put("Abstract", doc.get("articleAbstract"));
                results.put("Testo", doc.get("paragraphs"));
                results.put("Data di Pubblicazione", doc.get("publicationDate"));
                break;
                
            case "tabelle":
                title = "Tabella: " + doc.get("caption");
                results.put("HTML Tabella", doc.get("html_table")); 
                results.put("Contesto", doc.get("context_paragraphs"));
                results.put("ID Articolo Padre", doc.get("fileName")); 
                results.put("Menzioni", doc.get("mentions")); 
                results.put("Termini Chiave", doc.get("terms")); 
                break;
            case "immagini":
                title = "Immagine: " + (doc.get("caption") != null ? doc.get("caption") : doc.get("id"));
                // Proviamo a costruire un URL servibile per l'immagine salvata sotto /saved_images/
                String rawSaved = doc.get("saved_path") != null ? doc.get("saved_path") : "";
                String servedUrl = "";
                if (!rawSaved.isEmpty()) {
                    try {
                        java.io.File base = new java.io.File(dataImgPath).getCanonicalFile();
                        java.io.File target = new java.io.File(rawSaved).getCanonicalFile();
                        java.net.URI baseUri = base.toURI();
                        java.net.URI targetUri = target.toURI();
                        java.net.URI rel = baseUri.relativize(targetUri);
                        String relPath = rel.getPath();
                        if (relPath == null || relPath.isEmpty() || relPath.equals(targetUri.getPath())) {
                            // Se relativize non funziona, tentiamo di cercare la sottostringa "img" nel percorso
                            int idx = rawSaved.indexOf("img/");
                            if (idx >= 0) {
                                relPath = rawSaved.substring(idx + 4); // dopo 'img/'
                            } else {
                                relPath = target.getName();
                            }
                        }
                        servedUrl = "/saved_images/" + relPath.replace("\\\\", "/");
                    } catch (Exception e) {
                        // fallback: usa solo il nome file
                        servedUrl = "/saved_images/" + new java.io.File(rawSaved).getName();
                    }
                }

                // salva il percorso servito (vuoto se non disponibile) e il percorso sorgente originale
                results.put("Percorso salvato", servedUrl);
                results.put("Src (original)", doc.get("src"));
                results.put("Caption", doc.get("caption"));
                results.put("Alt", doc.get("alt"));
                results.put("Contesto", doc.get("context_paragraphs"));
                results.put("ID Articolo Padre", doc.get("fileName"));
                break;
    
            default:
                results.put("Raw Data", doc.toString()); 
        }   

        return new GetDocumentResponse(id, title, authors, results);
    }


    @GetMapping("/dettaglio/articoli/{id}")
    public String viewArticle(@PathVariable("id") String id, Model model) {
        try {
            Document luceneDoc = searcher.getDocumentById(id, "articoli");
            if (luceneDoc == null) {
                model.addAttribute("error", "Articolo non trovato: " + id);
                return "error_page";
            }

            GetDocumentResponse responseDTO = mapDocumentToResponse(luceneDoc, "articoli");
            model.addAttribute("document", responseDTO);
            model.addAttribute("indexKey", "articoli");

            return "articoli_detail"; // template Thymeleaf per articoli
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "error_page";
        }
    }
}
