import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;
import com.google.gson.Gson;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import packages.prt.PruningRadixTrie;
import packages.prt.TextSanitized;

public class Mailbox extends PAT {
    static String MPTR_EXPORT = "serialized/mbox_terms.txt"; // most popular time ranker persistence file
    static String MPTR_SER = "serialized/rmt_terms.ser"; // most popular time ranker persistence file
    static String BIGRAM_EXPORT = "serialized/mbox_bigrams.txt"; // bigram ranker persistence file
    
    public Mailbox() {
        if (verbose) printParams();
        currTextExport = MPTR_EXPORT;
        currBinaryExport = MPTR_SER;
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
            if (sType == serializeType.text) {
                currPRT.readTermsFromFile(MPTR_EXPORT, "\t");
            } else if (sType == serializeType.binary) {
                FileInputStream fileInputStream;
                try {
                    fileInputStream = new FileInputStream(MPTR_SER);
                    ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                    currPRT = (PruningRadixTrie) objectInputStream.readObject();
                    objectInputStream.close(); 
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else { // populate PRTs from original dataset
            try { // MPTR PRT indexing
                FileReader fr = new FileReader(DATA);
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
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + DATA);
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
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " bigram terms written from " + DATA);
                // save trees to files using given serializeType
                if (sType == serializeType.text) {
                    writeTextSerializedFile(MPTR_EXPORT, BIGRAM_EXPORT);
                } else if (sType == serializeType.binary) {
                    writeBinarySerializedFile(MPTR_SER, BIGRAM_EXPORT);
                }
            } catch (Exception e) {
                printError("ERROR " + e);
            }
        }
    }
}