import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.StringTokenizer;
import opennlp.tools.sentdetect.*;
import packages.prt.PruningRadixTrie;
import packages.prt.TextSanitized;

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
            if (serType == serType.text) {
                System.out.println("Reading terms from: " + SAVE_DIR + MPTR_EXPORT);
                currPRT.readTermsFromFile(SAVE_DIR + MPTR_EXPORT, "\t");
            } else if (serType == serType.binary) {
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
        } else { // otherwise populate from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                int lineCount = 0;
    
                while ((row = inputReader.readLine()) != null) {
                    //String[] splitMsg = msg.split(MPTR_SPLIT);
                    int phraseCount = 0;
                    String[] splitMsg = detector.sentDetect(row);
                    for (String sentence : splitMsg) {
                        if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                            //System.out.println(sentence);
                            addTermByChunks(processSentence(sentence), 1);
                            phraseCount++;
                            if (phraseCount % 10 == 0) {
                                System.out.println("phraseCount: " + phraseCount);
                            }
                        } 
                    }
                    lineCount++;
                    if (lineCount > 100000) {
                        break;
                    }
                    if (lineCount % 1000 == 0) {
                        System.out.println("processing line: " + lineCount);
                    }
                }
                inputReader.close();
                System.out.println(String.format("%,d", (int)currPRT.termCount) + " phrases written from " + file);
            }
            catch (Exception e) {
                printError("ERROR: " + e.getMessage());
                e.printStackTrace();
            }

            try { // Bigram PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
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
                System.out.println(String.format("%,d", (int)currBigramPRT.termCount) + " bigram terms written from " + file);

                if (serType == serType.text) {
                    writeTextSerializedFile(MPTR_EXPORT, BIGRAM_EXPORT);
                } else if (serType == serType.binary) {
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