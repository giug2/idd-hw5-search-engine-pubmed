package it.uniroma3.idd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;


@Data @NoArgsConstructor @AllArgsConstructor
public class GetDocumentResponse {

    private String id;
    private String title;
    private String authors;
    
    /* Contiene i campi specifici del documento (articoli, tabelle e img) */
    private Map<String, String> results; 
}
