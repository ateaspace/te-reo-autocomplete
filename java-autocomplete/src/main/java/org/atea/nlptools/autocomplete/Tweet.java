import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringTokenizer;
import opennlp.tools.sentdetect.*;
import packages.prt.PruningRadixTrie;
import packages.prt.TextSanitized;

public class Tweet extends PATServlet {

    public Tweet(File file) {
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
            long startTime = System.currentTimeMillis();
            if (serType == serializeType.text) {
                System.out.println("Reading terms from: " + SAVE_DIR + MPTR_EXPORT);
                currPRT.readTermsFromFile(SAVE_DIR + MPTR_EXPORT, "\t");
            } else if (serType == serializeType.binary) {
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
            long stopTime = System.currentTimeMillis();
            long elapsedTime = stopTime - startTime;
            System.out.println("===== build time ===== " + elapsedTime);
        } else { // otherwise populate from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
                // int lineCount = 0;
    
                long startTime = System.currentTimeMillis();
                while ((row = inputReader.readLine()) != null) {
                    // lineCount++;
                    // System.out.println(lineCount);
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
                        for (String subSentence : sentence.split("((?<=[!?]))")) {
                            if (!subSentence.equals("") && !subSentence.isEmpty() && subSentence.split(" ").length < MAX_WORDS_IN_SUGGESTION) { // prune empty phrases
                                //System.out.println(subSentence);
                                addTermByChunks(processSentence(subSentence), round(timeRank, 4));
                            } 
                        }
                        
                    }
                }
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                System.out.println("===== build time ===== " + elapsedTime);
                inputReader.close();
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + file);
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }

        if (fromSerial) {
            currBigramPRT.readTermsFromFile(SAVE_DIR + BIGRAM_EXPORT, "\t");
        } else {
            try { // Bigram PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
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
                                    String termOriginal = s1 + " " + s2;
                                    TextSanitized term = new TextSanitized(termOriginal);
                                    currBigramPRT.addTerm(term, 1); // add words to PRT and increment count
                                    s1 = s2;
                                    s2 = "";
                                }
                            }
                        }
                    }
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " bigram terms written from " + file);

                if (serType == serializeType.text) {
                    writeTextSerializedFile(MPTR_EXPORT, BIGRAM_EXPORT);
                } else if (serType == serializeType.binary) {
                    writeBinarySerializedFile(MPTR_SER, BIGRAM_EXPORT);
                }
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}