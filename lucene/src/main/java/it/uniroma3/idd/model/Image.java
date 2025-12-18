package it.uniroma3.idd.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.annotation.Id;
import java.util.List;


public class Image {
    @Id
    @NotNull
    private String id;

    private String caption;
    private String alt;
    private String src;
    private String srcResolved;
    private String savedPath;
    private String linkHref;
    private List<String> mentions;
    private List<String> context_paragraphs;
    private String fileName;

    public Image(String id, String caption, String alt, String src, String srcResolved, String savedPath, String linkHref, List<String> mentions, List<String> context_paragraphs, String fileName) {
        this.id = id;
        this.caption = caption;
        this.alt = alt;
        this.src = src;
        this.srcResolved = srcResolved;
        this.savedPath = savedPath;
        this.linkHref = linkHref;
        this.mentions = mentions;
        this.context_paragraphs = context_paragraphs;
        this.fileName = fileName;
    }

    public @NotNull String getId() { return id; }
    public void setId(@NotNull String id) { this.id = id; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getAlt() { return alt; }
    public void setAlt(String alt) { this.alt = alt; }

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }

    public String getSrcResolved() { return srcResolved; }
    public void setSrcResolved(String srcResolved) { this.srcResolved = srcResolved; }

    public String getSavedPath() { return savedPath; }
    public void setSavedPath(String savedPath) { this.savedPath = savedPath; }

    public String getLinkHref() { return linkHref; }
    public void setLinkHref(String linkHref) { this.linkHref = linkHref; }

     public List<String> getMentions() { return mentions; }
    public void setMentions(List<String> mentions) { this.mentions = mentions; }

    public List<String> getContext_paragraphs() { return context_paragraphs; }
    public void setContext_paragraphs(List<String> context_paragraphs) { this.context_paragraphs = context_paragraphs; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContext_paragraphsString() {
        if (context_paragraphs == null) return "";
        return String.join(" ", context_paragraphs);
    }

    public String getMentionsString() {
        if (mentions == null) return "";
        return String.join(" ", mentions);
    }

    @Override
    public String toString() {
        return "Image{" +
                "id='" + id + '\'' +
                ", caption='" + caption + '\'' +
                ", alt='" + alt + '\'' +
                ", src='" + src + '\'' +
                ", savedPath='" + savedPath + '\'' +
                '}';
    }
}
