package it.uniroma3.idd.config; 

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${data.articles.path}") 
    private String articlesPath; 
    
    @Value("${data.img.path}")
    private String imagesPath; 


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        
        // Costruisci l'URI nel formato corretto, utilizzando 'file:./' per indicare 
        // la directory di lavoro corrente (che è la radice del progetto)
        String fileUri = "file:./" + articlesPath + "/";
        
        System.out.println("DEBUG MAPPING URI ATTIVO: " + fileUri); 
        
        registry.addResourceHandler("/raw_articles/**")
                .addResourceLocations(fileUri);
        
        // Mappa le immagini salvate (es. ../input/img/...) sotto /saved_path/**
        String imagesFileUri = "file:./" + imagesPath + "/";
        System.out.println("DEBUG MAPPING IMAGES URI ATTIVO: " + imagesFileUri);
        registry.addResourceHandler("/saved_path/**")
            .addResourceLocations(imagesFileUri);
    }
}
