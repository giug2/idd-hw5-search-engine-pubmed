package it.uniroma3.idd.controller;

import it.uniroma3.idd.dto.SearchResultDTO;
import it.uniroma3.idd.service.Searcher;
import org.apache.lucene.queryparser.classic.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


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
        return "index";
    }


    /**
     * Gestisce l'invio del form di ricerca dal front-end (index.html).
     * * Il parametro 'query' contiene la sintassi Lucene completa (es. 'title:term OR term').
     * Il parametro 'campoScelto' viene ignorato o rimosso dall'HTML per semplificazione.
     */
    @PostMapping("/search")
    public String search(
            @RequestParam("query") String query,
            // Riceve i valori multipli delle checkbox degli indici
            @RequestParam(name = "indices", required = false) List<String> indicesScelti,
            Model model) { 

        // Validazione base
        if (query == null || query.trim().isEmpty()) {
            model.addAttribute("error", "Inserisci una query valida.");
            return "index";
        }
        
        // Deve essere selezionato almeno un indice
        if (indicesScelti == null || indicesScelti.isEmpty()) {
             model.addAttribute("error", "Seleziona almeno un indice di ricerca (Articoli, Tabelle, etc.).");
             // Passa la query corrente per non perderla
             model.addAttribute("query", query); 
             return "index";
        }

        // Nella modalità "input libero", passiamo SEMPRE NULL come campo specifico.
        // Il Searcher interpreterà la stringa 'query' interamente, usando QueryParser 
        // per la ricerca singola (se la query contiene 'campo:parola') o MultiFieldQueryParser 
        // per la ricerca combinata (se la query contiene solo 'parola').
        String campo = null; 

        try {
            // Chiama il metodo di ricerca scalabile nel Searcher
            Map<String, List<SearchResultDTO>> risultati = searcher.search(
                    query.trim(), 
                    indicesScelti, 
                    campo 
            );

            // Passa tutti i dati e lo stato al front-end
            model.addAttribute("risultatiTotali", risultati); 
            model.addAttribute("query", query);
            model.addAttribute("indicesScelti", indicesScelti);

        } catch (ParseException e) {
            model.addAttribute("error", "Errore di sintassi nella query Lucene. Controlla il formato (es. title:term AND term): " + e.getMessage());
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Errore di configurazione dell'indice: " + e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "Si è verificato un errore inatteso: " + e.getMessage());
            e.printStackTrace();
        }

        return "index"; // Ritorna al template 'index.html'
    }
}
