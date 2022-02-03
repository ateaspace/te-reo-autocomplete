import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;

import opennlp.tools.sentdetect.*;
import packages.prt.TextSanitized;

public class PlainText extends PATServlet {

    private final int LINE_LENGTH_THRESHOLD = 250;
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
                // int trueLineCount = 0;
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

                // while ((row = inputReader.readLine()) != null) {
                //     row = row.trim();
                //     if (!row.equals("") && !row.isEmpty()) {
                //         if (row.length() < LINE_LENGTH_THRESHOLD) { // if row contains less than x chars, split with OpenNLP, otherwise regex split
                //             splitMsg = detector.sentDetect(row);
                //         } else {
                //             splitMsg = row.split(MPTR_SPLIT);
                //         }
                        
                //         for (String sentence : splitMsg) {
                //             if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty and large phrases
                //                 //System.out.println(sentence);
                //                 addTermByChunks(processSentence(sentence), 1);
                //             } 
                //         }
                //         lineCount++;
                //         if (lineCount % 1000 == 0) {
                //             System.out.println("processing line: " + trueLineCount + " line length: " + row.length());
                //         }
                //     }
                //     trueLineCount++;
                // }
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
                BufferedReader inputReader;
                inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8));
                String row;

                while ((row = inputReader.readLine()) != null) {
                    // tokenizer to maintain word position within sentence
                    StringTokenizer itr = new StringTokenizer(row.toLowerCase().trim().replace("\"", ""));
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
                inputReader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            writeSerialized();
        }
    }

    public void splitAndProcessChunk(String chunk) {
        String[] splitMsg;
        if (chunk.length() < LINE_LENGTH_THRESHOLD) { // if row contains less than x chars, split with OpenNLP, otherwise regex split
            splitMsg = detector.sentDetect(chunk);
        } else {
            splitMsg = chunk.split(MPTR_SPLIT);
        }
                    
        for (String sentence : splitMsg) {
            if (!sentence.equals("") && !sentence.isEmpty()) {
                addTermByChunks(processSentence(sentence), 1);
            }
        }
    }
}