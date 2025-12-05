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

        File[] files = dir.listFiles();
        if (files == null) {
            System.err.println("Error listing files in: " + dir.getAbsolutePath());
            return new ArrayList<>();
        }

        System.out.println("Number of files in the directory: " + files.length);
        List<Table> tables = new ArrayList<>();

        for (File file : files) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonNode = objectMapper.readTree(file);

                //save the name of the file
                String fileName = file.getName().replaceFirst("\\.json$", "");

                jsonNode.fields().forEachRemaining(entry -> {
                    String id = entry.getKey();
                    JsonNode tableNode = entry.getValue().get("table");
                    String tableHtml = tableNode != null ? tableNode.asText("") : "";

                    // Controlla che il nodo "caption" esista prima di chiamare asText
                    String caption = entry.getValue().has("caption")
                            ? entry.getValue().get("caption").asText("")
                            : "";

                    // Gestisci il nodo "mentions"
                    List<String> mentions = new ArrayList<>();
                    if (entry.getValue().has("mentions")) {
                        // CORREZIONE: Usa mention.asText() per estrarre la stringa
                        entry.getValue().get("mentions").forEach(mention -> mentions.add(mention.asText()));
                    }

                    // Gestisci il nodo "context_paragraphs"
                    List<String> context_paragraphs = new ArrayList<>();
                    if (entry.getValue().has("context_paragraphs")) {
                        // CORREZIONE: Usa context_paragraph.asText()
                        entry.getValue().get("context_paragraphs").forEach(context_paragraph -> context_paragraphs.add(context_paragraph.asText()));
                    }

                    // Gestisci il nodo "terms"
                    List<String> terms = new ArrayList<>();
                    if (entry.getValue().has("terms")) {
                        // CORREZIONE: Usa term.asText()
                        entry.getValue().get("terms").forEach(term -> terms.add(term.asText()));
                    }

                    Table table = new Table(id, caption, tableHtml, cleanHtml(tableHtml), mentions, context_paragraphs, terms, fileName);
                    tables.add(table);
                });


            } catch (IOException e) {
                System.out.println("Error opening the file: " + file.getName());
                e.printStackTrace();
            }
        }

        return tables;
    }

    public String cleanHtml(String htmlContent) {
        Document doc = Jsoup.parse(htmlContent);
        return doc.text();
    }

}