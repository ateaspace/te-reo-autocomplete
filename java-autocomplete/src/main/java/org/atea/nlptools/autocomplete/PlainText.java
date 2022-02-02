import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import opennlp.tools.sentdetect.*;

public class PlainText extends PATServlet {

    public PlainText(File file) {
        if (verbose) printParams();
        System.out.println("Parsing Text...");
        try {
            //Loading sentence detector model 
            InputStream inputStream = new FileInputStream(language); 
            sentenceModel = new SentenceModel(inputStream);
        } catch (IOException e) {
            System.err.println("Failed to read in: " + language);
            e.printStackTrace();
        }
        //Instantiating the SentenceDetectorME class 
        SentenceDetectorME detector = new SentenceDetectorME(sentenceModel);  

        if (fromSerial) {
            readMPTRSerializedFile();
        } else { // otherwise populate from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                String[] splitMsg;
                int lineCount = 0;
                int trueLineCount = 0;
    
                long startTime = System.currentTimeMillis();
                while ((row = inputReader.readLine()) != null) {
                    row = row.trim();
                    if (!row.equals("") && !row.isEmpty()) {
                        if (row.length() < 150) { // if row contains less than x chars, run OpenNLP, otherwise regex split
                            splitMsg = detector.sentDetect(row);
                        } else {
                            splitMsg = row.split(MPTR_SPLIT);
                        }
                        
                        for (String sentence : splitMsg) {
                            if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                                //System.out.println(sentence);
                                addTermByChunks(processSentence(sentence), 1);
                            } 
                        }
                        lineCount++;
                        if (lineCount > 200000) {
                            break;
                        }
                        if (lineCount % 1000 == 0) {
                            System.out.println("processing line: " + trueLineCount + " line length: " + row.length());
                        }
                        // if (lineCount > 195000) {
                        //     System.out.println("processing line: " + trueLineCount + " line length: " + row.length());
                        // }
                    }
                    trueLineCount++;
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + file);
                long stopTime = System.currentTimeMillis();
                long elapsedTime = stopTime - startTime;
                System.out.println("===== build time: " + elapsedTime + "ms ===== ");            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }
            writeSerialized();
        }
    }
}