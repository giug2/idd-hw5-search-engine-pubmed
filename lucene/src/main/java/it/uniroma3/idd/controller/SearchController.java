package it.uniroma3.idd.controller;

import it.uniroma3.idd.dto.SearchResultDTO;
import it.uniroma3.idd.service.Searcher;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping; // Manteniamo solo le importazioni necessarie
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Collections; // Utile per liste vuote

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
            model.addAttribute("indicesScelti", Arrays.asList("articoli"));
            // Aggiungiamo anche la query a null per evitare errori in Thymeleaf
            model.addAttribute("query", ""); 
            return "index";
        }


        /**
            * Gestisce l'invio del form di ricerca dal front-end (index.html).
            * Il parametro 'query' contiene la sintassi Lucene completa (es. 'title:term OR publicationYear:[min TO max]').
            */
        @PostMapping("/search")
        public String search(
                        @RequestParam("query") String query,
                        @RequestParam(name = "indices", required = false) List<String> indicesScelti,
                        Model model) { 
                
                // [Correzione bug Thymeleaf]: Assicuriamo che indicesScelti sia sempre nel Model.
                List<String> selectedIndices = (indicesScelti != null) ? indicesScelti : Collections.emptyList();
                model.addAttribute("indicesScelti", selectedIndices); 
                model.addAttribute("query", query); // Passa la query corrente al Model

                // Validazione 1: Query Vuota
                if (query == null || query.trim().isEmpty()) {
                        model.addAttribute("error", "Inserisci una query valida.");
                        return "index"; 
                }
                
                // Validazione 2: Nessun Indice Selezionato
                if (indicesScelti == null || indicesScelti.isEmpty()) {
                    model.addAttribute("error", "Seleziona almeno un indice di ricerca (Articoli, Tabelle, etc.).");
                    return "index";
                }

                // Campo è null, forzando la logica MultiField/QueryParser nel Searcher
                String campo = null; 

                try {
                    // Chiama il metodo di ricerca (che ora gestisce i range tramite QueryParser)
                    Map<String, List<SearchResultDTO>> risultati = searcher.search(
                                            query.trim(), indicesScelti, campo );

                    // Passa i risultati
                    model.addAttribute("risultatiTotali", risultati); 
                    // Query e indicesScelti sono già nel Model.

                } catch (ParseException e) {
                        model.addAttribute("error", "Errore di sintassi nella query Lucene. Controlla il formato (es. title:term AND publicationYear:[2015 TO 2020]): " + e.getMessage());
                } catch (IllegalArgumentException e) {
                        model.addAttribute("error", "Errore di configurazione: " + e.getMessage());
                } catch (Exception e) {
                        model.addAttribute("error", "Si è verificato un errore inatteso: " + e.getMessage());
                        e.printStackTrace();
                }

                return "index"; // Ritorna al template 'index.html'
        }
}