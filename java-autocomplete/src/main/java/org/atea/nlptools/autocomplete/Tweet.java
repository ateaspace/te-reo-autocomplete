import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import opennlp.tools.sentdetect.*;

public class Tweet extends PATServlet {

    public Tweet(File file) {
        if (verbose) printParams();
        System.out.println("Parsing Tweets...");
        // initializing sentence detector 
        SentenceDetectorME detector = new SentenceDetectorME(sentenceModel);  

        if (fromSerial) { // if serial file(s) exist, read from serial
            readMPTRSerializedFile();
        } else { // otherwise populate from original dataset
            try { 
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
    
                long startTime = System.currentTimeMillis();
                while ((row = inputReader.readLine()) != null) {
                    String[] dateSplit = row.split("\t"); // phrase / date delimiter
                    String date = dateSplit[0];
                    String msg = dateSplit[1];
                    int msgYear;
                    if (date.charAt(0) <= '9') { // remove leading 0s of date to match dateFormat
                        msgYear = LocalDate.parse(date, dateFormat).getYear();
                    } else {
                        msgYear = LocalDate.parse(date.substring(1), dateFormat).getYear();
                    }
                    double timeRank = 1 + TR_WEIGHT * getNormalized(msgYear, YEAR_MIN, YEAR_MAX);

                    //String[] splitMsg = msg.split(MPTR_SPLIT);
                    String[] splitMsg = detector.sentDetect(msg);
                    for (String sentence : splitMsg) {
                        for (String subSentence : sentence.split("((?<=[!?]))")) {
                            if (!subSentence.equals("") && !subSentence.isEmpty()) { // prune empty and large phrases
                                addTermByChunks(processSentence(subSentence), round(timeRank, 4));
                            } 
                        }
                        
                    }
                }
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                System.out.println("===== build time: " + elapsedTime + "ms ===== ");                
                inputReader.close();
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + file);
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (fromSerial && availSer.equals("both")) {
            currBigramPRT.readTermsFromFile(SAVE_DIR + BIGRAM_EXPORT, "\t");
        } else {
            try { // Bigram PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                while ((row = inputReader.readLine()) != null) {
                    String[] data = row.split("\t");
                    for (String sentence : data) {
                        if (!Character.isDigit(sentence.charAt(0))) {
                            writeBigrams(sentence);
                        }
                    }
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " bigram terms written from " + file);
                writeSerialized();
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}