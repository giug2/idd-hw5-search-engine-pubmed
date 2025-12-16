package it.uniroma3.idd;

import it.uniroma3.idd.config.LuceneConfig;
import it.uniroma3.idd.model.Table;
import it.uniroma3.idd.utils.Parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

public class TableParserTest {

    @Test
    public void testTableParserFiltersPermalink() {
        LuceneConfig config = new LuceneConfig();
        config.setTablePath("../input/tables"); 
        
        Parser parser = new Parser(config);
        List<Table> tables = parser.tableParser();
        
        boolean foundPermalink = false;
        for (Table table : tables) {
            if (table.getMentions() != null) {
                for (String mention : table.getMentions()) {
                    if (mention.startsWith("PERMALINK Copy As a library")) {
                        foundPermalink = true;
                        System.out.println("Found PERMALINK in table: " + table.getId());
                    }
                }
            }
        }
        
        assertFalse(foundPermalink, "Should not find 'PERMALINK Copy As a library' in mentions");
    }
}
