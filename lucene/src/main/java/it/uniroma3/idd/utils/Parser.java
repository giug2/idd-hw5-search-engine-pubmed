package it.uniroma3.idd.utils;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.Table;
import it.uniroma3.idd.model.Image;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.nodes.Element;
import java.util.Map;
import java.util.Iterator;


@Component
public class Parser {

    private final LuceneConfig luceneConfig;


    @Autowired
    public Parser(LuceneConfig luceneConfig) {
        this.luceneConfig = luceneConfig;
    }


    private String extractPublicationDate(Document document) {
        // 1. epub
        Element pubDate = document.selectFirst("pub-date[pub-type=epub]");
        // 2. ppub
        if (pubDate == null) {
            pubDate = document.selectFirst("pub-date[pub-type=ppub]");
        }
        // 3. qualsiasi pub-date
        if (pubDate == null) {
            pubDate = document.selectFirst("pub-date");
        }
        if (pubDate == null) return "Unknown Date";
        // 4. string-date
        Element stringDate = pubDate.selectFirst("string-date");
        if (stringDate != null) {
            return normalizeStringDate(stringDate.text());
        }
        // 5. anno / mese / giorno
        String year = pubDate.select("year").text();
        String month = normalizeMonth(pubDate.select("month").text());
        String day = pubDate.select("day").text();

        if (year.isEmpty()) return "Unknown Date";

        StringBuilder date = new StringBuilder(year);

        if (!month.isEmpty()) {
            date.append("-").append(month);
            if (!day.isEmpty()) {
                date.append("-").append(day.length() == 1 ? "0" + day : day);
            }
        }
        return date.toString();
    }


    private String normalizeMonth(String month) {
        if (month.isEmpty()) return "";

        Map<String, String> months = Map.ofEntries(
            Map.entry("jan", "01"), Map.entry("january", "01"),
            Map.entry("feb", "02"), Map.entry("february", "02"),
            Map.entry("mar", "03"), Map.entry("march", "03"),
            Map.entry("apr", "04"), Map.entry("april", "04"),
            Map.entry("may", "05"),
            Map.entry("jun", "06"), Map.entry("june", "06"),
            Map.entry("jul", "07"), Map.entry("july", "07"),
            Map.entry("aug", "08"), Map.entry("august", "08"),
            Map.entry("sep", "09"), Map.entry("september", "09"),
            Map.entry("oct", "10"), Map.entry("october", "10"),
            Map.entry("nov", "11"), Map.entry("november", "11"),
            Map.entry("dec", "12"), Map.entry("december", "12")
        );

        month = month.toLowerCase().trim();
        if (month.matches("\\d+")) {
            return month.length() == 1 ? "0" + month : month;
        }
        return months.getOrDefault(month, "");
    }
    

    private String normalizeStringDate(String text) {
        // es: "2021 Mar 15" → 2021-03-15
        String[] parts = text.split("\\s+");
        if (parts.length >= 2) {
            String year = parts[0];
            String month = normalizeMonth(parts[1]);
            String day = parts.length >= 3 ? parts[2] : "";
            return year + (month.isEmpty() ? "" : "-" + month) +
                (day.isEmpty() ? "" : "-" + day);
        }
        return text;
    }


    public List<Article> articleParser() {
        File dir = new File(luceneConfig.getArticlesPath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Articles directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".html"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Number of files in the directory: " + files.length);
        List<Article> articles = new ArrayList<>();

        for (File file : files) {
            try {
                // Rileva se il file è HTML o XML
                boolean isHtml = false;
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    // Leggi le prime righe per sicurezza, saltando le righe vuote
                    for (int i = 0; i < 5; i++) {
                        String line = br.readLine();
                        if (line == null) break;
                        line = line.trim().toLowerCase();
                        if (line.isEmpty()) continue;
                        
                        if (line.startsWith("<!doctype html") || line.startsWith("<html")) {
                            isHtml = true;
                            break;
                        }
                        // Se vediamo la dichiarazione XML o tag specifici JATS, è XML
                        if (line.startsWith("<?xml") || line.startsWith("<pmc-articleset") || line.startsWith("<article")) {
                            isHtml = false;
                            break;
                        }
                    }
                }

                Document document;
                if (isHtml) {
                    document = Jsoup.parse(file, "UTF-8");
                } else {
                    document = Jsoup.parse(new FileInputStream(file), "UTF-8", "", org.jsoup.parser.Parser.xmlParser());
                }

                String id = file.getName().replaceFirst("(?i)\\.html?$", "");
                
                // Titolo
                String title = "No Title Found";
                if (isHtml) {
                    Element metaTitle = document.selectFirst("meta[name=citation_title]");
                    if (metaTitle != null) title = metaTitle.attr("content");
                    else if (document.title() != null && !document.title().isEmpty()) title = document.title();
                    else {
                        // Fallback per PMC HTML: prova h1.content-title
                        Element h1 = document.selectFirst("h1.content-title");
                        if (h1 != null) title = h1.text();
                    }
                } else {
                    title = document.select("article-title").first() != null ? document.select("article-title").first().text() : "No Title Found";
                }
                
                // Autori
                List<String> authors = new ArrayList<>();
                if (isHtml) {
                    document.select("meta[name=citation_author]").forEach(meta -> {
                        authors.add(meta.attr("content"));
                    });
                } else {
                    document.select("contrib[contrib-type=author] name").forEach(nameElement -> {
                        String surname = nameElement.select("surname").text();
                        String givenNames = nameElement.select("given-names").text();
                        authors.add(givenNames + " " + surname);
                    });
                }
                
                // Abstract
                String articleAbstract = "No Abstract Found";
                if (isHtml) {
                    Element metaDesc = document.selectFirst("meta[name=description]");
                    if (metaDesc != null) articleAbstract = metaDesc.attr("content");
                    else {
                        Element ogDesc = document.selectFirst("meta[name=og:description]");
                        if (ogDesc != null) articleAbstract = ogDesc.attr("content");
                        else {
                            // Fallback: prova a trovare il div dell'abstract
                            Element absDiv = document.selectFirst("div.abstract-content, div#abstract-1");
                            if (absDiv != null) articleAbstract = absDiv.text();
                        }
                    }
                } else {
                    articleAbstract = document.select("abstract p").first() != null ? document.select("abstract p").text() : "No Abstract Found";
                }
                
                // Data
                String publicationDate = "Unknown Date";
                if (isHtml) {
                    Element metaDate = document.selectFirst("meta[name=citation_publication_date]");
                    if (metaDate != null) {
                        publicationDate = normalizeStringDate(metaDate.attr("content"));
                    }
                } else {
                    publicationDate = extractPublicationDate(document);
                }


                // Paragrafi (Corpo)
                List<String> paragraphs = new ArrayList<>();
                if (isHtml) {
                    // Per i file HTML, i paragrafi potrebbero trovarsi in contenitori diversi a seconda della struttura del sito
                    // Prova i tag p standard, ma forse restringi al contenuto principale se possibile
                    // L'HTML di PMC di solito ha il contenuto in .jig-ncbi-inp-strip o simile, ma 'body p' è un fallback sicuro
                    document.select("body p").forEach(paragraph -> {
                        String text = paragraph.text();
                        if (text.length() > 50) { // Filtra testo di navigazione breve
                            paragraphs.add(text);
                        }
                    });
                } else {
                    document.select("body p").forEach(paragraph -> {
                        String text = paragraph.text();
                        if (!text.isEmpty()) {
                            paragraphs.add(text);
                        }
                    });
                }

                if (articleAbstract.isEmpty() || articleAbstract.length() < 20) {
                    articleAbstract = "No Abstract Found";
                }

                Article article = new Article(id, title, authors, paragraphs, articleAbstract, publicationDate);
                articles.add(article);

            } catch (IOException e) {
                System.out.println("Error opening the file: " + file.getName());
                e.printStackTrace();
            }
        }
        return articles;
    }


    public List<Table> tableParser() {
        File dir = new File(luceneConfig.getTablePath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Tables directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".json"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Number of JSON files found: " + files.length);
        List<Table> tables = new ArrayList<>();

        for (File file : files) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(file);
                
                if (!jsonNode.isArray()) {
                    System.err.println("ERROR PARSING JSON: File " + file.getName() + " is NOT a JSON Array. Skipping.");
                    continue; 
                }

                int tablesInFile = 0;
                for (JsonNode tableEntry : jsonNode) {
                    
                    // Costruiamo l'ID combinando paper_id e table_id
                    String paperId = tableEntry.get("paper_id").asText("");
                    paperId = paperId.replaceFirst("(?i)\\.html?$", ""); 
                    String tableId = tableEntry.get("table_id").asText();
                    String id = paperId + "-" + tableId; 

                    // Estrazione dei campi
                    String caption = tableEntry.get("caption") != null ? tableEntry.get("caption").asText("") : "";
                    String tableHtml = tableEntry.get("body") != null ? tableEntry.get("body").asText("") : "";
                    String htmlBody = tableEntry.get("html_body") != null ? tableEntry.get("html_body").asText("") : "";
                    
                    // Gestisci i campi List<String>
                    List<String> mentions = extractStringList(tableEntry, "mentions");
                    List<String> context_paragraphs = extractStringList(tableEntry, "context_paragraphs");
                    List<String> terms = extractStringList(tableEntry, "terms");

                    Table table = new Table(id, caption, tableHtml, cleanHtml(tableHtml), mentions, context_paragraphs, terms, paperId, htmlBody);
                    tables.add(table);
                    tablesInFile++;
                }
                
                if (tablesInFile == 0) {
                     System.out.println("WARNING: File " + file.getName() + " was successfully read but contained 0 tables.");
                }


            } catch (IOException e) {
                System.err.println("CRITICAL JSON PARSING ERROR in file: " + file.getName() + ". Message: " + e.getMessage());
            }
        }
        System.out.println("Successfully parsed a total of " + tables.size() + " tables.");
        return tables;
    }


    public List<Image> imageParser() {
        File dir = new File(luceneConfig.getImgPath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Images directory not found: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        File[] files = dir.listFiles((dir1, name) -> name.endsWith(".json"));
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Number of image JSON files found: " + files.length);
        List<Image> images = new ArrayList<>();

        for (File file : files) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(file);

                if (jsonNode.isObject()) {
                    // Nuovo formato: Map<String, Object>
                    Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        String imageId = entry.getKey();
                        JsonNode imgData = entry.getValue();

                        String caption = imgData.has("caption") ? imgData.get("caption").asText("") : "";
                        String src = imgData.has("image_url") ? imgData.get("image_url").asText("") : "";
                        if (isJunkImage(src)) continue;
                        String linkHref = imgData.has("link_href") ? imgData.get("link_href").asText("") : "";
                        
                        // Estrai ID articolo dal nome file (es. article_0001_12345678_figures.json -> article_0001_12345678)
                        String fileName = file.getName().replace("_figures.json", "");
                        
                        // Paragrafi di contesto
                        List<String> contextParagraphs = new ArrayList<>();
                        
                        // 1. Paragrafi citanti (stringhe semplici)
                        if (imgData.has("citing_paragraphs") && imgData.get("citing_paragraphs").isArray()) {
                            imgData.get("citing_paragraphs").forEach(p -> {
                                String text = cleanHtml(p.asText());
                                if (!text.isEmpty() && text.length() >= 20) {
                                    contextParagraphs.add(text);
                                }
                            });
                        }
                        
                        // 2. Paragrafi contestuali (oggetti con campo "html")
                        if (imgData.has("contextual_paragraphs") && imgData.get("contextual_paragraphs").isArray()) {
                            imgData.get("contextual_paragraphs").forEach(pObj -> {
                                if (pObj.has("html")) {
                                    String text = cleanHtml(pObj.get("html").asText());
                                    if (!text.isEmpty() && text.length() >= 20) {
                                        contextParagraphs.add(text);
                                    }
                                }
                            });
                        }

                        // ID: Usa direttamente la chiave dalla mappa JSON
                        String id = imageId;

                        // Crea oggetto Image
                        Image image = new Image(id, caption, "", src, src, "", linkHref, contextParagraphs, fileName);
                        images.add(image);
                    }
                } else if (jsonNode.isArray()) {
                    // Vecchio formato: Array di Oggetti (mantenuto per retrocompatibilità se necessario)
                    for (JsonNode imgEntry : jsonNode) {
                        String paperId = imgEntry.get("paper_id").asText("");
                        paperId = paperId.replaceFirst("(?i)\\.html?$", "");
                        String imageId = imgEntry.get("image_id") != null ? imgEntry.get("image_id").asText() : "";
                        String id = paperId + "-" + imageId;

                        String caption = imgEntry.get("caption") != null ? imgEntry.get("caption").asText("") : "";
                        String alt = imgEntry.get("alt") != null ? imgEntry.get("alt").asText("") : "";
                        String src = imgEntry.get("src") != null ? imgEntry.get("src").asText("") : "";
                        if (isJunkImage(src)) continue;
                        String srcResolved = imgEntry.get("src_resolved") != null ? imgEntry.get("src_resolved").asText("") : "";
                        String savedPath = imgEntry.get("saved_path") != null ? imgEntry.get("saved_path").asText("") : "";
                        String linkHref = imgEntry.get("link_href") != null ? imgEntry.get("link_href").asText("") : "";

                        List<String> context_paragraphs = extractStringList(imgEntry, "context_paragraphs");
                        String fileName = imgEntry.get("fileName") != null ? imgEntry.get("fileName").asText("") : "";

                        Image image = new Image(id, caption, alt, src, srcResolved, savedPath, linkHref, context_paragraphs, fileName);
                        images.add(image);
                    }
                } else {
                    System.err.println("ERROR PARSING JSON: File " + file.getName() + " is neither Object nor Array. Skipping.");
                }

            } catch (IOException e) {
                System.err.println("CRITICAL JSON PARSING ERROR in file: " + file.getName() + ". Message: " + e.getMessage());
            }
        }

        System.out.println("Successfully parsed a total of " + images.size() + " images.");
        return images;
    }
    
    
    private List<String> extractStringList(JsonNode parentNode, String fieldName) {
        List<String> resultList = new ArrayList<>();
        if (parentNode.has(fieldName) && parentNode.get(fieldName).isArray()) {
            parentNode.get(fieldName).forEach(element -> {
                String text = element.asText("");
                if (!text.isEmpty() && text.length() >= 20) {
                    resultList.add(text);
                }
            });
        }
        return resultList;
    }


    public String cleanHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        return doc.text();
    }
    
    /**
     * Verifica se un'immagine è un'icona/logo/asset del sito da escludere.
     */
    private boolean isJunkImage(String src) {
        if (src == null) return true;
        String lower = src.toLowerCase();
        return lower.contains("icon") || 
               lower.contains("logo") || 
               lower.contains("flag") || 
               lower.contains("spinner") || 
               lower.contains("loader") ||
               lower.endsWith(".svg");
    }
}
