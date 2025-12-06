package it.uniroma3.idd.utils;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.Table;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
public class Parser {

    private final LuceneConfig luceneConfig;


    @Autowired
    public Parser(LuceneConfig luceneConfig) {
        this.luceneConfig = luceneConfig;
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
                Document document = Jsoup.parse(file, "UTF-8");
                String id = file.getName();
                
                // Title
                String title = document.select("article-title").first() != null ? document.select("article-title").first().text() : "No Title Found";
                
                // Authors
                List<String> authors = new ArrayList<>();
                document.select("contrib[contrib-type=author] name").forEach(nameElement -> {
                    String surname = nameElement.select("surname").text();
                    String givenNames = nameElement.select("given-names").text();
                    authors.add(givenNames + " " + surname);
                });
                
                // Abstract
                String articleAbstract = document.select("abstract p").first() != null ? document.select("abstract p").text() : "No Abstract Found";
                
                // Date
                String publicationDate = "Unknown Date";
                org.jsoup.nodes.Element pubDateElement = document.select("pub-date").first();
                if (pubDateElement != null) {
                    String year = pubDateElement.select("year").text();
                    String month = pubDateElement.select("month").text();
                    String day = pubDateElement.select("day").text();
                    
                    if (!year.isEmpty()) {
                        publicationDate = year;
                        if (!month.isEmpty()) {
                            publicationDate += "-" + (month.length() == 1 ? "0" + month : month);
                            if (!day.isEmpty()) {
                                publicationDate += "-" + (day.length() == 1 ? "0" + day : day);
                            }
                        }
                    }
                }

                // Paragraphs (Body)
                List<String> paragraphs = new ArrayList<>();
                document.select("body p").forEach(paragraph -> paragraphs.add(paragraph.text()));

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

                //save the name of the file
                String fileName = file.getName().replaceFirst("\\.json$", "");
                
                if (!jsonNode.isArray()) {
                    System.err.println("ERROR PARSING JSON: File " + file.getName() + " is NOT a JSON Array. Skipping.");
                    continue; 
                }

                int tablesInFile = 0;
                for (JsonNode tableEntry : jsonNode) {
                    
                    // Costruiamo l'ID combinando paper_id e table_id
                    String paperId = tableEntry.get("paper_id").asText();
                    String tableId = tableEntry.get("table_id").asText();
                    String id = paperId + "-" + tableId; 

                    // Estrazione dei campi
                    String caption = tableEntry.get("caption") != null ? tableEntry.get("caption").asText("") : "";
                    String tableHtml = tableEntry.get("body") != null ? tableEntry.get("body").asText("") : "";
                    
                    // Gestisci i campi List<String>
                    List<String> mentions = extractStringList(tableEntry, "mentions");
                    List<String> context_paragraphs = extractStringList(tableEntry, "context_paragraphs");
                    List<String> terms = extractStringList(tableEntry, "terms");

                    Table table = new Table(id, caption, tableHtml, cleanHtml(tableHtml), mentions, context_paragraphs, terms, fileName);
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
    
    
    private List<String> extractStringList(JsonNode parentNode, String fieldName) {
        List<String> resultList = new ArrayList<>();
        if (parentNode.has(fieldName) && parentNode.get(fieldName).isArray()) {
            parentNode.get(fieldName).forEach(element -> {
                resultList.add(element.asText(""));
            });
        }
        return resultList;
    }


    public String cleanHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        return doc.text();
    }
}
