package it.uniroma3.idd.config; 

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${data.articles.path}") 
    private String articlesPath; // Ora è "pm_html_articles"

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        
        // 1. Costruisci l'URI nel formato corretto, utilizzando 'file:./' per indicare 
        // la directory di lavoro corrente (che è la radice del progetto)
        String fileUri = "file:./" + articlesPath + "/";
        
        System.out.println("DEBUG MAPPING URI ATTIVO: " + fileUri); 
        
        registry.addResourceHandler("/raw_articles/**")
                .addResourceLocations(fileUri);
        
    }
}