package it.uniroma3.idd.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import java.util.List;


public class Table {
    @Id
    @NotNull
    private String id;               

    @NotNull
    private String caption;           

    @NotNull
    private String body;               

    @NotNull
    private String bodyCleaned;        

    private List<String> mentions;    

    private List<String> context_paragraphs;

    private List<String> terms;   

    private String fileName;           

    public Table(String id, String caption, String body, String bodyCleaned, List<String> mentions, List<String> context_paragraphs, List<String> terms, String fileName) {
        this.id = id;
        this.caption = caption;
        this.body = body;
        this.bodyCleaned = bodyCleaned;
        this.mentions = mentions;
        this.context_paragraphs = context_paragraphs;
        this.fileName = fileName;
        this.terms = terms;
    }

    public @NotNull String getId() {
        return id;
    }

    public void setId(@NotNull String id) {
        this.id = id;
    }

    public @NotNull String getCaption() {
        return caption;
    }

    public void setCaption(@NotNull String caption) {
        this.caption = caption;
    }

    public @NotNull String getBody() {
        return body;
    }

    public void setBody(@NotNull String body) {
        this.body = body;
    }

    public @NotNull String getBodyCleaned() {
        return bodyCleaned;
    }

    public void setBodyCleaned(@NotNull String bodyCleaned) {
        this.bodyCleaned = bodyCleaned;
    }

    public List<String> getMentions() {
        return mentions;
    }

    public void setMentions(List<String> mentions) {
        this.mentions = mentions;
    }

    public List<String> getContext_paragraphs() {
        return context_paragraphs;
    }

    public void setContext_paragraphs(List<String> context_paragraphs) {
        this.context_paragraphs = context_paragraphs;
    }

    public List<String> getTerms() {
        return terms;
    }

    public void setTerms(List<String> terms) {
        this.terms = terms;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    //if mentions is null, return empty string
    public String getMentionsString() {
        if (mentions == null) {
            return "";
        }
        return String.join(" ", mentions);
    }

    //if references is null, return empty string
    public String getContext_paragraphsString() {
        if (context_paragraphs == null) {
            return "";
        }
        return String.join(" ", context_paragraphs);
    }

    //if terms is null, return empty string
    public String getTermsString() {
        if (terms == null) {
            return "";
        }
        return String.join(" ", terms);
    }

    @Override
    public String toString() {
        return "Table{" +
                "id='" + id + '\'' +
                ", caption='" + caption + '\'' +
                ", body='" + body + '\'' +
                ", mentions=" + mentions +
                ", context_paragraphs=" + context_paragraphs +
                ", terms=" + terms +
                '}';
    }
}
