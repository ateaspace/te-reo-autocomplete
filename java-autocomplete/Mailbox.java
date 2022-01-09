import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;
import com.google.gson.Gson;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import packages.prt.TextSanitized;

public class Mailbox extends PAT {
    static String MPTR_EXPORT = "serialized/mbox_terms.txt"; // most popular time ranker persistence file
    static String BIGRAM_EXPORT = "serialized/mbox_bigrams.txt"; // bigram ranker persistence file
    
    public Mailbox() {
        if (verbose) printParams();
        System.out.println("-------------------------------------\nParsing Mailbox...");
        try {
            //Loading sentence detector model 
            InputStream inputStream = new FileInputStream(language); 
            sentenceModel = new SentenceModel(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Instantiating the SentenceDetectorME class 
        SentenceDetectorME detector = new SentenceDetectorME(sentenceModel); 
        if (fromSerial) {
            currPRT.readTermsFromFile(MPTR_EXPORT.replace("terms", "terms-n" + chunkSize), "\t");
        } else { // populate PRTs from original dataset
            try { // MPTR PRT indexing
                FileReader fr = new FileReader(DATA);
                MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class); // convert from .json file to Java object
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
                double timeRank;
                
                for (MailItem mailItem : mailitems) {
                    int date = LocalDate.parse(mailItem.getDate(), dateFormat).getYear();
                    String msg = mailItem.getBody();
                    timeRank = 1 + TR_WEIGHT * getNormalized(date, YEAR_MIN, YEAR_MAX);

                    //String[] splitMsg = msg.split(MPTR_SPLIT);
                    String[] splitMsg = detector.sentDetect(msg);
                    for (String sentence : splitMsg) {
                        if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                            addTermByChunks(processSentence(sentence), timeRank);
                        } 
                    }
                }
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " terms written from " + DATA);
            } catch (Exception e) {
                printError("ERROR: " + e);
            }
        }
        
        if (fromSerial) {
            currBigramPRT.readTermsFromFile(BIGRAM_EXPORT, "\t");
        } else {
            try { // Bigram PRT indexing
                FileReader fr = new FileReader(DATA);
                MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class);
    
                for (MailItem mailItem : mailitems) {
                    String msg = mailItem.getBody();
                    String[] splitMsg = msg.split(BIGRAM_SPLIT);
                    for (String sentence : splitMsg) {
                        // tokenizer to maintain word position within sentence
                        StringTokenizer itr = new StringTokenizer(sentence.toLowerCase().trim().replace("\"", "")); 
                        if (itr.countTokens() > 1) {
                            String s1 = "";
                            String s2 = "";
                            while (itr.hasMoreTokens())
                            {
                                if (s1.isEmpty())
                                    s1 = itr.nextToken();
                                s2 = itr.nextToken();
                                String termOriginal = s1 + " " + s2;
                                TextSanitized term = new TextSanitized(termOriginal);
                                currBigramPRT.addTerm(term, 1); // add words to PRT and increment count
                                s1 = s2;
                                s2 = "";
                            }
                        }
                    }
                }
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " terms written from " + DATA);
    
                // save PRTs to persistence files
                System.out.println("Writing serialized files...");
                currPRT.writeTermsToFile(MPTR_EXPORT.replace("terms", "terms-n" + chunkSize));
                currBigramPRT.writeTermsToFile(BIGRAM_EXPORT);  
                System.out.println("Files written.");
            } catch (Exception e) {
                printError("ERROR " + e);
            }
        }
    }
}