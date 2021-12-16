import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;
import opennlp.tools.sentdetect.*;

public class Tweet extends PAT {
    static String MPTR_EXPORT = "serialized/rmt_terms.txt"; // most popular time ranker persistence file
    static String BIGRAM_EXPORT = "serialized/rmt_bigrams.txt"; // bigram ranker persistence file

    public Tweet() {
        if (verbose) printParams();
        System.out.println("Parsing Tweets...");
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
        } else { // otherwise populate from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(DATA, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
    
                while ((row = inputReader.readLine()) != null) {
                    String[] dateSplit = row.split("\t"); // phrase / date delimiter
                    String date = dateSplit[0];
                    String msg = dateSplit[1];
                    int msgYear;
                    double timeRank;
                    if (date.charAt(0) <= '9') { // remove leading 0s of date to match dateFormat
                        msgYear = LocalDate.parse(date, dateFormat).getYear();
                    } else {
                        msgYear = LocalDate.parse(date.substring(1), dateFormat).getYear();
                    }
                    timeRank = 1 + TR_WEIGHT * getNormalized(msgYear, YEAR_MIN, YEAR_MAX);

                    //String[] splitMsg = msg.split(MPTR_SPLIT);
                    String[] splitMsg = detector.sentDetect(msg);
                    for (String sentence : splitMsg) {
                        if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                            addTermByChunks(processSentence(sentence), timeRank);
                        } 
                    }
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " terms written from " + DATA);
                
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
            }
        }

        if (fromSerial) {
            currBigramPRT.readTermsFromFile(BIGRAM_EXPORT, "\t");
        } else {
            try { // Bigram PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(DATA, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
    
                while ((row = inputReader.readLine()) != null) {
                    String[] data = row.split("\t");
                    for (String sentence : data) {
                        if (!Character.isDigit(sentence.charAt(0))) {
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
                                    currBigramPRT.addTerm(s1 + " " + s2, 1); // add words to PRT and increment count
                                    s1 = s2;
                                    s2 = "";
                                }
                            }
                        }
                    }
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " terms written from " + DATA);
    
                // save PRTs to persistence files
                System.out.println("Writing serialized files...");
                currPRT.writeTermsToFile(MPTR_EXPORT.replace("terms", "terms-n" + chunkSize));
                currBigramPRT.writeTermsToFile(BIGRAM_EXPORT); 
                System.out.println("Files written.");
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
            }
        }
    }
}