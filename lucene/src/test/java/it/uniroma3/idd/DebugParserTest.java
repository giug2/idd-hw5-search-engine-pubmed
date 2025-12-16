package it.uniroma3.idd;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;

public class DebugParserTest {

    @Test
    public void findArticlesWithNoTitle() throws IOException {
        File dir = new File("../input/pm_html_articles");
        if (!dir.exists()) {
            System.out.println("Directory not found: " + dir.getAbsolutePath());
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".html"));
        if (files == null) return;

        int noTitleCount = 0;
        for (File file : files) {
            // Detect if file is HTML or XML
            boolean isHtml = false;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                for (int i = 0; i < 5; i++) {
                    String line = br.readLine();
                    if (line == null) break;
                    line = line.trim().toLowerCase();
                    if (line.isEmpty()) continue;
                    
                    if (line.startsWith("<!doctype html") || line.startsWith("<html")) {
                        isHtml = true;
                        break;
                    }
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

            String title = "No Title Found";
            if (isHtml) {
                Element metaTitle = document.selectFirst("meta[name=citation_title]");
                if (metaTitle != null) title = metaTitle.attr("content");
                else if (document.title() != null && !document.title().isEmpty()) title = document.title();
                else {
                    Element h1 = document.selectFirst("h1.content-title");
                    if (h1 != null) title = h1.text();
                }
            } else {
                title = document.select("article-title").first() != null ? document.select("article-title").first().text() : "No Title Found";
            }

            if (title == null || title.equals("No Title Found") || title.isEmpty()) {
                System.out.println("NO TITLE: " + file.getName());
                noTitleCount++;
            }
        }
        System.out.println("Total files with no title: " + noTitleCount);
    }
}
