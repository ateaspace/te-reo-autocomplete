import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import com.google.gson.Gson;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

public class Mailbox extends PATServlet {  

    public Mailbox(File file) {
        if (verbose) printParams();
        System.out.println("Parsing Mailbox...");
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
            readMPTRSerializedFile();
        } else { // populate PRTs from original dataset
            try { // MPTR PRT indexing
                FileReader fr = new FileReader(file);
                MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class); // convert from .json file to Java object
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
                double timeRank;
                
                for (MailItem mailItem : mailitems) {
                    int date = LocalDate.parse(mailItem.getDate(), dateFormat).getYear();
                    String msg = mailItem.getBody();
                    timeRank = 1 + TR_WEIGHT * getNormalized(date, YEAR_MIN, YEAR_MAX); // rank based on 1 (frequency) + normalized timeRank value
                    //String[] splitMsg = msg.split(MPTR_SPLIT);
                    String[] splitMsg = detector.sentDetect(msg);
                    for (String sentence : splitMsg) {
                        if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                            addTermByChunks(processSentence(sentence), round(timeRank, 4));
                        } 
                    }
                }
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + file);
            } catch (Exception e) {
                printError("ERROR: " + e);
            }
        }
        
        if (fromSerial && availSer.equals("both")) {
            currBigramPRT.readTermsFromFile(SAVE_DIR + BIGRAM_EXPORT, "\t");
        } else {
            try { // Bigram PRT indexing
                FileReader fr = new FileReader(file);
                MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class);
    
                for (MailItem mailItem : mailitems) {
                    String msg = mailItem.getBody();
                    String[] splitMsg = msg.split(BIGRAM_SPLIT);
                    for (String sentence : splitMsg) {
                        writeBigrams(sentence);
                    }
                }
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " bigram terms written from " + file);
                
                writeSerialized();
            } catch (Exception e) {
                printError("ERROR " + e);
            }
        }
    }
}