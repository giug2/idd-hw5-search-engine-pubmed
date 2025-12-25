package it.uniroma3.idd.controller;

import it.uniroma3.idd.dto.*;
import it.uniroma3.idd.service.Searcher;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping; 
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Collections; 


@Controller
public class SearchController {
        
        private final Searcher searcher;

        @Autowired
        public SearchController(Searcher searcher) {
                this.searcher = searcher;
        }


        @GetMapping("/")
        public String home(Model model) {
            // Inizializza i checkbox 'articoli' come predefinito
            model.addAttribute("indiceScelti", Arrays.asList("articoli"));
            // Aggiungiamo anche la query a null per evitare errori in Thymeleaf
            model.addAttribute("query", ""); 
            return "index";
        }


        /** Gestisce l'invio del form di ricerca dal front-end (index.html).
        * Il parametro 'query' contiene la sintassi Lucene completa 
        * (es. 'title:term OR publicationYear:[min TO max]').*/
        @PostMapping("/search")
        public String search(
                        @RequestParam("query") String query,
                        @RequestParam(name = "indices", required = false) List<String> indiceScelti,
                        Model model) { 
                
                // Assicuriamo che indiceScelti sia sempre nel Model.
                List<String> selectedIndices = (indiceScelti != null) ? indiceScelti : Collections.emptyList();
                model.addAttribute("indiceScelti", selectedIndices); 
                model.addAttribute("query", query); // Passa la query corrente al Model

                // Query Vuota
                if (query == null || query.trim().isEmpty()) {
                        model.addAttribute("error", "Inserisci una query valida.");
                        return "index"; 
                }
                // Nessun Indice Selezionato
                if (indiceScelti == null || indiceScelti.isEmpty()) {
                    model.addAttribute("error", "Seleziona almeno un indice di ricerca (Articoli, Tabelle, etc.).");
                    return "index";
                }

                // Campo è null, forzando la logica MultiField/QueryParser nel Searcher
                String campo = null; 

                try {
                    // Passa i risultati
                     SearchResponse response = searcher.search(query.trim(), indiceScelti, campo);
                     model.addAttribute("risultatiTotali", response.getRisultati()); 
                     model.addAttribute("metriche", response.getMetrichePerIndice()); 
                    // Query e indiceScelti sono già nel Model.

                } catch (ParseException e) {
                        model.addAttribute("error", "Errore di sintassi nella query Lucene. Controlla il formato (es. title:term AND publicationYear:[2015 TO 2020]): " + e.getMessage());
                } catch (IllegalArgumentException e) {
                        model.addAttribute("error", "Errore di configurazione: " + e.getMessage());
                } catch (Exception e) {
                        model.addAttribute("error", "Si è verificato un errore inatteso: " + e.getMessage());
                        e.printStackTrace();
                }

                return "index"; 
        }
}
