package it.uniroma3.idd.service;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.event.IndexingCompleteEvent;
import it.uniroma3.idd.model.Article;
import it.uniroma3.idd.model.Table;
import it.uniroma3.idd.model.Image;
import it.uniroma3.idd.utils.Parser;
import jakarta.annotation.PostConstruct;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;


@Component
public class LuceneIndexer {

    private final LuceneConfig luceneConfig;
    private final ApplicationEventPublisher eventPublisher;
    private final Analyzer perFieldAnalyzer;
    private final Parser parser;


    @Autowired
    public LuceneIndexer(LuceneConfig luceneConfig, ApplicationEventPublisher eventPublisher, Analyzer perFieldAnalyzer, Parser parser) {
        this.luceneConfig = luceneConfig;
        this.eventPublisher = eventPublisher;
        this.perFieldAnalyzer = perFieldAnalyzer;
        this.parser = parser;
    }


    @PostConstruct
    public void init() {
        try {
            System.out.println("Index initialization in progress...");
            if (luceneConfig.isShouldInitializeIndex()) {
                System.out.println("Deleting the index directory...");
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getIndexDirectory())); // Delete the index directory
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getTableDirectory()));
                deleteNonEmptyDirectory(Paths.get(luceneConfig.getImgDirectory()));
                indexArticles(luceneConfig.getIndexDirectory(), Codec.getDefault()); // Initialize the index
                indexTables(luceneConfig.getTableDirectory(), Codec.getDefault());
                indexImages(luceneConfig.getImgDirectory(), Codec.getDefault());
            }
            System.out.println("Table Index initialized, publishing event.");
            eventPublisher.publishEvent(new IndexingCompleteEvent(this)); 
            System.out.println("IndexingComplete event published.");
        } catch (Exception e) {
            throw new RuntimeException("Error initializing the index", e);
        }
    }


    public void indexArticles(String Pathdir, Codec codec) throws IOException {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);
        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);

        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);
        List<Article> articles = parser.articleParser();

        for (Article article : articles) {
            Document doc = new Document();
            doc.add(new StringField("id", article.getId(), Field.Store.YES));
            doc.add(new TextField("title", article.getTitle(), TextField.Store.YES));
            doc.add(new TextField("authors", String.join(" ", article.getAuthors()), TextField.Store.YES));
            doc.add(new TextField("paragraphs", String.join(" ", article.getParagraphs()), TextField.Store.YES));
            doc.add(new TextField("articleAbstract", article.getArticleAbstract(), TextField.Store.YES));
            doc.add(new StringField("publicationDate",article.getPublicationDate(),Field.Store.YES));
            String date = article.getPublicationDate();
            if (date.length() >= 4) {
                int year = Integer.parseInt(date.substring(0, 4));
                doc.add(new IntPoint("publicationYear", year));
                doc.add(new StoredField("publicationYear", year));
            }

            if (date.length() == 10) { // YYYY-MM-DD
                long epoch = toEpoch(date);
                doc.add(new LongPoint("publicationDate_ts", epoch));
                doc.add(new StoredField("publicationDate_ts", epoch));
            }
            writer.addDocument(doc);
        }
        writer.commit();
        writer.close();
    }


    private long toEpoch(String date) {
        // date = YYYY-MM-DD
        LocalDate localDate = LocalDate.parse(date);
        return localDate.atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
    }


    public void indexTables(String Pathdir, Codec codec) throws Exception {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);
        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);

        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);
        List<Table> tables = parser.tableParser();

        for (Table table : tables) {
            Document doc = new Document();
            doc.add(new StringField("id", table.getId(), Field.Store.YES));
            doc.add(new TextField("caption", table.getCaption(), Field.Store.YES)); 
            doc.add(new StoredField("html_table", table.getHtmlBody()));
            doc.add(new TextField("body", table.getBodyCleaned(), Field.Store.YES)); 
            doc.add(new TextField("mentions", table.getMentionsString(), Field.Store.YES)); 
            doc.add(new TextField("context_paragraphs", table.getContext_paragraphsString(), Field.Store.YES)); 
            doc.add(new TextField("terms", table.getTermsString(), Field.Store.YES));
            doc.add(new StringField("fileName", table.getFileName(), Field.Store.YES)); 
            writer.addDocument(doc);
        }
        writer.commit();
        writer.close();
    }


    public void indexImages(String Pathdir, Codec codec) throws Exception {
        Path path = Paths.get(Pathdir);
        Directory dir = FSDirectory.open(path);
        IndexWriterConfig config = new IndexWriterConfig(perFieldAnalyzer);

        config.setCodec(codec);

        IndexWriter writer = new IndexWriter(dir, config);
        List<Image> images = parser.imageParser();

        for (Image image : images) {
            Document doc = new Document();
            doc.add(new StringField("id", image.getId(), Field.Store.YES));
            doc.add(new TextField("caption", image.getCaption(), Field.Store.YES));
            doc.add(new TextField("alt", image.getAlt(), Field.Store.YES));
            doc.add(new StoredField("src", image.getSrc()));
            doc.add(new StoredField("src_resolved", image.getSrcResolved()));
            doc.add(new StoredField("saved_path", image.getSavedPath()));
            doc.add(new TextField("context_paragraphs", image.getContext_paragraphsString(), Field.Store.YES));
            doc.add(new StringField("fileName", image.getFileName(), Field.Store.YES));
            writer.addDocument(doc);
        }
        writer.commit();
        writer.close();
    }


    public void deleteNonEmptyDirectory(Path directory) throws IOException {
        // Verifica se la directory esiste
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            // Rimuove ricorsivamente i file e le sottocartelle
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);  // Elimina il file
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);  // Elimina la directory dopo aver cancellato i suoi contenuti
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Directory and its contents deleted.");
        } else {
            System.out.println("Directory does not exist or is not a directory.");
        }
    }
}
