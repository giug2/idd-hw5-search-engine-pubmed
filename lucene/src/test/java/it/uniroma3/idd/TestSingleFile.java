package it.uniroma3.idd;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;

public class TestSingleFile {
    @Test
    public void testSingleFile() throws Exception {
        System.out.println("Working Directory: " + new File(".").getAbsolutePath());
        File file = new File("../input/pm_html_articles/PMC11292914.html");
        System.out.println("Testing file: " + file.getAbsolutePath());
        
        boolean isHtml = false;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (int i = 0; i < 10; i++) {
                String line = br.readLine();
                if (line == null) break;
                System.out.println("Line " + i + ": '" + line + "'");
                line = line.trim().toLowerCase();
                if (line.isEmpty()) continue;
                
                if (line.startsWith("<!doctype html") || line.startsWith("<html")) {
                    isHtml = true;
                    System.out.println("Detected HTML at line " + i);
                    break;
                }
                if (line.startsWith("<?xml") || line.startsWith("<pmc-articleset") || line.startsWith("<article")) {
                    isHtml = false;
                    System.out.println("Detected XML at line " + i);
                    break;
                }
            }
        }
        
        System.out.println("Final Decision: isHtml=" + isHtml);
        
        Document document;
        if (isHtml) {
            document = Jsoup.parse(file, "UTF-8");
            System.out.println("Parsed as HTML");
        } else {
            document = Jsoup.parse(new FileInputStream(file), "UTF-8", "", org.jsoup.parser.Parser.xmlParser());
            System.out.println("Parsed as XML");
        }
        
        String title = "No Title Found";
        if (isHtml) {
            Element metaTitle = document.selectFirst("meta[name=citation_title]");
            if (metaTitle != null) {
                title = metaTitle.attr("content");
                System.out.println("Found title in meta: " + title);
            } else {
                System.out.println("Meta citation_title not found");
                if (document.title() != null && !document.title().isEmpty()) {
                    title = document.title();
                    System.out.println("Found title in document.title(): " + title);
                } else {
                    Element h1 = document.selectFirst("h1.content-title");
                    if (h1 != null) {
                        title = h1.text();
                        System.out.println("Found title in h1: " + title);
                    }
                }
            }
        } else {
            Element articleTitle = document.select("article-title").first();
            if (articleTitle != null) {
                title = articleTitle.text();
                System.out.println("Found title in article-title: " + title);
            }
        }
        
        System.out.println("Final Title: " + title);
    }
}
