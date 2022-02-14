import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

import opennlp.tools.sentdetect.*;
import packages.prt.TextSanitized;

public class PlainText extends PATServlet {

    private final int LINE_LENGTH_THRESHOLD = 750;
    private SentenceDetectorME detector = new SentenceDetectorME(sentenceModel);  

    public PlainText(File file) {
        if (verbose) printParams();
        System.out.println("Parsing Text...");
        //Instantiating the SentenceDetectorME class 
        

        if (fromSerial) {
            readMPTRSerializedFile();
        } else { // otherwise populate from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                // String[] splitMsg;
                int lineCount = 0;
                int wordThreshold = 100;
                int charThreshold = 500;
                int wordCount = 0;
                int charCount = 0;
                StringBuilder stringBuilder = new StringBuilder();
    
                long startTime = System.currentTimeMillis();

                while ((row = inputReader.readLine()) != null) {
                    lineCount++;
                    if (lineCount % 1000 == 0) {
                        System.out.println("line: " + lineCount);
                    }
                    if (row.isEmpty()) {
                        if (stringBuilder.length() > 0) {
                            splitAndProcessChunk(stringBuilder.toString());
                            stringBuilder.setLength(0);
                        }
                        wordCount = 0;
                        charCount = 0;
                    }
                    else if (!row.equals("") && !row.isEmpty()) {
                        if (wordCount < wordThreshold && charCount < charThreshold) {
                            stringBuilder.append(" " + row.trim());
                            wordCount += row.split(" ").length;
                            charCount += row.length();
                        } else {
                            splitAndProcessChunk(stringBuilder.toString());
                            stringBuilder.setLength(0);
                            wordCount = 0;
                            charCount = 0;  
                        }
                    }
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

            try {
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));
                String row;
                while ((row = inputReader.readLine()) != null) {
                    // tokenizer to maintain word position within sentence
                    writeBigrams(row);
                }
                inputReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            writeSerialized();
        }
    }

    public void splitAndProcessChunk(String chunk) {
        try {
            if (chunk.length() < LINE_LENGTH_THRESHOLD) { // if chunk is less than threshold, add terms
                addTerms(detector.sentDetect(chunk));
            } else { // otherwise split in half and recurse
                if (chunk.length() > 5000) System.out.println("larger chunk detected of size: " + chunk.length());
                int splitIndex = chunk.indexOf(".", chunk.length() / 2);
                if (splitIndex == -1) addTerms(detector.sentDetect(chunk)); // full stop doesn't exist
                else {
                    String prefix = chunk.substring(0, splitIndex);
                    String suffix = chunk.substring(splitIndex);
                    // System.out.println("prefix: " + prefix);
                    // System.out.println("splitIndex: " + splitIndex);
                    // addTerms(detector.sentDetect(prefix));
                    splitAndProcessChunk(prefix);
                    splitAndProcessChunk(suffix);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addTerms(String[] splitmsg) {
        for (String sentence : splitmsg) {
            if (!sentence.equals("") && !sentence.isEmpty()) {
                addTermByChunks(processSentence(sentence));
            }
        }
    }
}