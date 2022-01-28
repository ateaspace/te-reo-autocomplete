import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.sentdetect.SentenceModel;
import packages.prt.PruningRadixTrie;
import packages.prt.TermAndFrequency;
import packages.prt.TextSanitized;
import packages.CommandLine.*;

@Command(name = "PAT", version = "TRMAC 0.1", mixinStandardHelpOptions = true)
public class PATServlet extends HttpServlet {

    final int YEAR_MIN = 2008; // oldest message post year
    final int YEAR_MAX = LocalDate.now().getYear(); // today's year
    final int TOPK = 3; // number of top phrases the ranking system should extract
    final int BIGRAM_WEIGHT = 10; // weight of bigram ranking value
    final int MAX_WORDS_IN_SUGGESTION = 15; // maximum number of words in suggestion
    final double TR_WEIGHT = 1.0; // weight of time ranking value
    final double THRESHOLD_WEIGHT = 0.75; // weight of ranking threshold
    final String MPTR_SPLIT = ",|\\.|!|\\?"; // regex values to split sentences 
    final String BIGRAM_SPLIT = ",|\\.|!|\\?"; // regex values to split sentences
    
    static Boolean fromSerial = false; // if tree should be populated from serialized file

    static PATServlet inputCorpus;
    static int exitCode;
    static PruningRadixTrie currPRT = new PruningRadixTrie();
    static PruningRadixTrie currBigramPRT = new PruningRadixTrie();
    static String currTextExport;
    static String currBinaryExport;
    static String currBigramExport;
    static String SENT_MI; // location of maori sentence tokenizer file
    static String SENT_EN; // location of english sentence tokenizer file
    static String LANG_DETECT; // location of language detection model
    static String SAVE_DIR;
    static String MPTR_EXPORT = "/terms.txt"; // most popular time ranker persistence file
    static String MPTR_SER = "/terms.ser"; // most popular time ranker persistence file
    static String BIGRAM_EXPORT = "/bigrams.txt"; // bigram ranker persistence file
    static String POS_SAVE = "/positivePhrases.txt";
    static String NEG_SAVE = "/negativePhrases.txt";
    
    String final_out = "";
    Double final_value;
    HashMap<String, Double> output;
    SentenceModel sentenceModel = null;
    Boolean triggerSuggestion = false;
    HashMap<String, Integer> triggerCount = new HashMap<>();
    enum serializeType { text, binary, none };
    enum dataType {rmt, mbox, txt }; 
    
    @Option(names = {"-o", "--originaldata"}, defaultValue = "false", description = "Force re-generation of serial files by using original data source.")
    static boolean forceOriginal; 
    @Option(names = {"-a", "--anonymize"}, defaultValue = "false", description = "Anonymize suggestions to hide personal information.")
    static boolean anonymize;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = "Verbose mode. Provides more detail in console. Useful for troubleshooting.")
    static boolean verbose; 
    @Option(names = {"-l", "--language"}, arity="1", defaultValue = "auto", description = "Language of text in file (mi or en). [default=auto]")
    static String language;
    @Option(names = {"-n", "--numchunks"}, arity="1..*", description = "Number of words per phrase to be used as input. Supports multiple ints seperated by a space. [default=4, 5, 6]")
    static List<Integer> chunkSize = Arrays.asList(4, 5, 6);
    @Option(names = {"-s", "--serializetype"}, arity = "1", description = "Method of tree serialization. Options: ${COMPLETION-CANDIDATES} [default=text]")
    static serializeType serType = serializeType.text;
    @Parameters(paramLabel = "<fileType>", arity = "1", description = "Type of file. Options: ${COMPLETION-CANDIDATES} [default=rmt]")
    static dataType fileType = dataType.rmt;
    @Parameters(paramLabel = "<file>", arity = "1", description = "File to read from.")
    static File DATA;

    private final Gson gsonInstance = new Gson(); // instance of gson for parsing response

    // depreciated
    // @Override
    // public void run() {
    //     triggerCount.put("100",0);
    //     triggerCount.put("15",0);
    //     triggerCount.put("5",0);

    //     if (Collections.min(chunkSize) < 1 || Collections.max(chunkSize) > 99) {
    //         printError("Please enter an n value between 1 and 99 inclusive.");
    //         System.exit(exitCode);
    //     } else {
    //         Collections.sort(chunkSize);
    //     }

    //     if (!DATA.exists() || DATA.isDirectory()) { 
    //         printError("Given file: " + DATA + " is either a directory or doesn't exist. Please enter a valid source file.");
    //         System.exit(exitCode);
    //     }
    // }

    @Override
    public void init() {
        // get parameters from web.xml
        ServletConfig config = getServletConfig();
        String fileTypeString = config.getInitParameter("dataType");
        String corpusInput = config.getInitParameter("corpus");
        String corpusDir = config.getInitParameter("corpusHome");
        
        SENT_EN = corpusDir + "mri-sent.bin";
        SENT_MI = corpusDir + "en-sent.bin";
        LANG_DETECT = corpusDir + "langdetect-183.bin";
        SAVE_DIR = corpusInput.replace(".csv", "");
        
        DATA = new File(corpusInput);
        if (DATA.isDirectory() || !DATA.exists()) {
            printError("Given file: " + DATA + "is either a directory or doesn't exist.");
        }

        // forceOriginal = true;
        language = "auto";

        checkSerial(serType);
        checkLanguage(DATA);

        if (fileTypeString.equals("rmt")) {
            fileType = dataType.rmt;
            inputCorpus = new Tweet(DATA);  
        } else if (fileTypeString.equals("mbox")) {
            fileType = dataType.mbox;
            inputCorpus = new Mailbox(DATA);
        } else if (fileTypeString.equals("txt")) {
            fileType = dataType.txt;
            inputCorpus = new PlainText(DATA);
        } else {
            System.err.println("Unrecognized data type: " + fileTypeString);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json; charset=UTF-8");
        final JsonWriter writer = gsonInstance.newJsonWriter(response.getWriter());
        // get input parameter
        if (request == null) {
            response.sendError(400, "The 'inputString' parameter must be supplied.");
        }
        String inputStringParameter = request.getParameter("inputString");
        if (inputStringParameter == null) {
            response.sendError(400, "The 'inputString' parameter must be non-empty.");
        }
        String[] outputStrings = process(inputStringParameter);
        // write outputString to json obj

        writer.beginObject();
        if (outputStrings.length == 0 || outputStrings[0].equals("ns")) { // if all suggestions have been removed due to negativePhrase list
            writer.name("state");
            writer.value("ns");
        } else {
            if (!triggerSuggestion){
                writer.name("state");
                writer.value("ft");
            } else {
                writer.name("state");
                writer.value("pt");
            }
            for (int i = 0; i < outputStrings.length; i++) {
                writer.name("sg" + i);
                writer.value(outputStrings[i]);
            }
        }
        writer.endObject();
        writer.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (request == null) {
            response.sendError(400, "The 'inputString' parameter must be supplied.");
        }
        String positiveParameter = request.getParameter("positive");
        String negativeParameter = request.getParameter("negative");

        if (positiveParameter != null && negativeParameter == null) {
            try {
                String filename = SAVE_DIR + POS_SAVE;
                FileWriter fw = new FileWriter(filename, true); // true: append
                if (!lineExistsInFile(positiveParameter, filename)) {
                    fw.write(positiveParameter + "\n");
                    fw.close();
                    System.out.println("\"" + positiveParameter + "\" added to positivePhrases.txt");
                } else {
                    System.err.println("\"" + positiveParameter + "\" already exists in positivePhrases file");
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        } else if (positiveParameter == null && negativeParameter != null) {
            try {
                String filename = SAVE_DIR + NEG_SAVE;
                FileWriter fw = new FileWriter(filename, true); // true: append
                if (!lineExistsInFile(negativeParameter, filename)) {
                    fw.write(negativeParameter + "\n");
                    fw.close();
                    System.out.println("\"" + negativeParameter + "\" added to negativePhrases.txt");
                } else {
                    System.err.println("\"" + negativeParameter + "\" already exists in negativePhrases file");
                }
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("POST request recieved with incorrect parameters.");
        }
        
    }

    public boolean lineExistsInFile(String line, String filename) {
        String row;
        File file = new File(filename);
        if (file.exists()) {
            try (BufferedReader inputReader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                while ((row = inputReader.readLine()) != null) {
                    if (row.trim().equals(line.trim())) return true;
                }
                inputReader.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // System.err.println("File doesn't exist: " + file);
        }
        
        return false;
    }

    public String[] process(String input) {
        output = new HashMap<String, Double>();
        int maxFreq = 0;
        int minFreq = 0;
        String text_in = "";

        // process input text
        String[] splitInput = input.split("\\.|\\?|\\!");
        if (splitInput.length > 1) {
            text_in = splitInput[splitInput.length-1].toLowerCase().replaceAll("^\\s+","");
        } else {
            text_in = input.toLowerCase().replaceAll("^\\s+","");
        }

        String lastTwoWords = text_in.trim(); // stores last two words for bigram prefix search
        String lastThreeWords = text_in.trim(); // stores last two words for bigram prefix search
        String[] words = text_in.split(" ");
        if (words.length > 2) { // if more than two words are present, retrieve last two
            lastTwoWords = words[words.length - 2] + " " + words[words.length - 1].trim();
        }
        if (words.length > 3) {
            lastThreeWords = String.join(" ", Arrays.copyOfRange(words, words.length-3, words.length));
        }
        if (verbose) {
            System.out.println("Bigram text in: " + lastTwoWords);
            System.out.println("Last three words: " + lastThreeWords);
        }
        
        System.out.println("-------------------------------------\nText in: " + text_in);

        // retrieve top ranking phrases that contain text_in as prefix
        List<TermAndFrequency> results_MPTR = null;
        List<TermAndFrequency> results_Bigram = null;
        
        results_MPTR = currPRT.getTopkTermsForPrefix(text_in, TOPK); // retrieve top-k phrases
        results_Bigram = currBigramPRT.getTopkTermsForPrefix(lastTwoWords, 0, false); // retrieve all bigrams that match the users input so far

        //System.out.println("TOP-K: " + (TOPK));
        // System.out.println("results_MPTR_size: " + results_MPTR.size());
        // System.out.println("results_Bigram_size: " + results_Bigram.size());

        if (results_Bigram.size() > 1) { // calculate minimum and maximum bigram ranking values
            maxFreq = (int)results_Bigram.get(0).getTermFrequencyCount();
            minFreq = (int)results_Bigram.get(results_Bigram.size()-1).getTermFrequencyCount();
        } else {
            System.out.println("No bigram results");
        }

        Map<String, Double> normalized_Bigrams = new HashMap<String, Double>();
        for (TermAndFrequency result : results_Bigram) { // normalize bigram ranking values based on min and max
            normalized_Bigrams.put(result.getTerm(), getNormalized(result.getTermFrequencyCount(), minFreq, maxFreq));
        }

        Set<Entry<String, Double>> bigramFinal = normalized_Bigrams.entrySet(); // convert to set
        
        for (TermAndFrequency result : results_MPTR) {
            for (Entry<String, Double> result_bi : bigramFinal) {
                if (result.getTerm().trim().endsWith(result_bi.getKey().trim())) { // if a bigram result exists, add phrase to output
                    double ranking_val = result.getTermFrequencyCount() + (BIGRAM_WEIGHT * result_bi.getValue());
                    output.put(result.getTerm(), ranking_val);
                }
            }
            output.put(result.getTerm(), result.getTermFrequencyCount()); // if no bigram results, add with MPTR metric
        }
        if (text_in.equals("")) {
            // label.setText("Suggestion:");
        } else if (!output.isEmpty()) {
            // forces UTF-8 encoding on output to support macrons
            PrintStream out = null;
            try {
                out = new PrintStream(System.out, true, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            // sorts output based on ranking metric
            Map<String, Double> sorted_output =
            output.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            List<String> topk_out = Arrays.asList(sorted_output.keySet().toArray(new String[sorted_output.size()]));
            final_out = sorted_output.keySet().stream().findFirst().get(); // get top ranking phrase
            final_value = sorted_output.values().stream().findFirst().get(); // get ranking metric of top ranking phrase
            
            out.println("Top-ranking suggestions:");
            for (Entry<String,Double> m : sorted_output.entrySet()) {
                out.println("\t" + m.getKey() + " : " + round(m.getValue(), 2));
            }
            // out.println("Ranking value (top): " + round(final_value, 2));

            // move suggestion to top if exists in positive list

            List<String> adjusted_topk_out = new ArrayList<>();

            for (String s : topk_out) {
                if (!lineExistsInFile(s, SAVE_DIR + NEG_SAVE)) {
                    if (!lineExistsInFile(s, SAVE_DIR + POS_SAVE)) {
                        adjusted_topk_out.add(s);
                    } else {
                        adjusted_topk_out.add(0, s);
                        System.err.println("\"" + s + "\" moved to top of suggestions");
                    }
                } else {
                    System.err.println("\"" + s + "\" removed from suggestions");
                }
            }

            if (passThreshold(text_in, final_out, final_value)) { // if phrase passes threshold limitations, push to UI
                out.println("Pass threshold: yes");
                triggerSuggestion = true;
            } else {
                out.println("Pass threshold: no");
                triggerSuggestion = false;
            }
            return adjusted_topk_out.toArray(new String[adjusted_topk_out.size()]);
        }
        return new String[]{"ns"};
    }

    // splits string into n chunks and adds to PRT
    public void addTermByChunks(String input, double timeRank) {
        String[] splitInput = input.split(" "); // tokenizer
        for (int i = 0; i < (splitInput.length - Collections.min(chunkSize) + 1); i++) { // number of slides required by minimum chunk size
            for (int chunk : chunkSize) { // for each window size
                if (i + chunk < splitInput.length) { // if window fits in frame
                    String termOriginal = String.join(" ", Arrays.copyOfRange(splitInput, i, i + chunk)).trim();
                    TextSanitized term = new TextSanitized(termOriginal);
                    currPRT.addTerm(term, timeRank); // add window to PRT
                }
            }
        }
        String termOriginal = input.trim();
        TextSanitized term = new TextSanitized(termOriginal);
        currPRT.addTerm(term, timeRank); // add full phrase
    }

    // returns normalized value (0..1) when given min and max
    public double getNormalized(double val, int min, int max) { 
        return (val - min) / (max - min);
    }

    // removes various punctuation from phrase 
    public String processSentence(String val) { 
        if (anonymize) {
            // val = val.replaceAll("(On)\\s.{0,200}(wrote:)", ""); // remove 'On ... wrote:'
            // val = val.replaceAll("(\\*From:\\*).*(\\*Subject:\\*)", ""); // remove '*From:* etc.' conversation headers
            // val = val.replaceAll("(From:).*(Subject:)", "");
            // val = val.replaceAll("(\\*From:\\*).*(\\*To:\\*)", "");
            // val = val.replaceAll("\\-{8,12}\\s(Forwarded).*(Subject:)",""); // remove ----- Forwarded Message ----- etc.
            val = val.replaceAll("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)", ""); // remove URLs https://stackoverflow.com/questions/3809401/what-is-a-good-regular-expression-to-match-a-url
            val = val.replaceAll("(www)\\..{0,100}(\\.nz)|(\\.com)",""); // remove URLs not caught by above expression
            val = val.replaceAll("[\\d|-|(|)]{5,14}",""); // remove phone numbers
        } 
        return val.replaceAll("\"","").replaceAll("'","").replaceAll("\\s+", " ").trim();
    }    
    
    // determines whether or not a phrase should be pushed to user
    private boolean passThreshold(String currInput, String suggestion, Double rankingMetric) { 
        System.out.println("Levenshtein distance to target: " + LevenshteinDistance(accentFold(currInput), accentFold(suggestion)));
        String foldedCurrInput = accentFold(currInput);
        String foldedSuggestion = accentFold(suggestion);
        // various limitations based on ranking metric and levenshtein distance
        //if (currInput.split(" ").length < 2) return false; // don't make suggestions if only one word exists
        System.out.println("Threshold weight: " + THRESHOLD_WEIGHT);
        if (rankingMetric > (THRESHOLD_WEIGHT*100.0) && LevenshteinDistance(foldedCurrInput, foldedSuggestion) < 10) {
            triggerCount.merge("100", 1, Integer::sum);
            return true;
        } else if (rankingMetric > (THRESHOLD_WEIGHT*15.0) && LevenshteinDistance(foldedCurrInput, foldedSuggestion) < 8) {
            triggerCount.merge("15", 1, Integer::sum);
            return true;
        } else if (rankingMetric > (THRESHOLD_WEIGHT*5.0) && LevenshteinDistance(foldedCurrInput, foldedSuggestion) < 5) {
            triggerCount.merge("5", 1, Integer::sum);
            return true;
        } else {
            return false;
        }
    }

    // derives language of text using OpenNLP langdetect model
    private void checkLanguage(File f) {
        if (language.equals("mi")) {
            language = SENT_MI;
        } else if (language.equals("en")) {
            language = SENT_EN;
        } else if (language.equals("auto")){
            File modelFile = new File(LANG_DETECT);
            LanguageDetectorModel trainedModel;
            try {
                trainedModel = new LanguageDetectorModel(modelFile);
                LanguageDetectorME languageDetector = new LanguageDetectorME(trainedModel);
                Language[] languages = languageDetector.predictLanguages(getSnippet(fileType, f));
                System.out.println("Predicted language: " + languages[0].getLang() + " with " + round(languages[0].getConfidence(), 2) + " confidence.");
                if (languages[0].getLang().equals("mri")) {
                    language = SENT_MI;
                } else if (languages[0].getLang().equals("eng")) {
                    language = SENT_EN;
                } else {
                    System.err.println("Unrecognized language detected: " + languages[0].getLang());
                    System.out.println("Defaulting to mri...");
                    language = SENT_MI;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            printError("Given language is not supported. Please enter either 'mi' for Maori or 'en' for English.");
            System.exit(exitCode);
        }
    }

    // returns first numLines from dataset for language prediction
    private String getSnippet(dataType s, File f) throws IOException {
        String row;
        String out = "";
        int numLines = 15;
        if (s == dataType.rmt) {
            BufferedReader inputReader = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
            for (int i = 0; i < numLines; i++) {
                row = inputReader.readLine();
                out = out + " " + row.split("\t")[1];
            }
            inputReader.close();
            return out;
        } else if (s == dataType.mbox) {
            FileReader fr = new FileReader(f);
            MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class);
            for (int i = 0; i < numLines; i++) {
                out = out + " " + mailitems[i].getBody();
            }
            return out;
        } else if (s == dataType.txt) {
            BufferedReader inputReader = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
            for (int i = 0; i < numLines; i++) {
                row = inputReader.readLine();
                out = out + " " + row;
            }
            inputReader.close();
            return out;
        } else {
            printError("File type does not exist.");
            System.exit(0);
            return null;
        }
    }

    // returns true if first file path was modified more recently than second
    private boolean firstIsRecent(String s1, String s2) throws IOException {
        BasicFileAttributes s1Attr = Files.readAttributes(Paths.get(s1), BasicFileAttributes.class);
        BasicFileAttributes s2Attr = Files.readAttributes(Paths.get(s2), BasicFileAttributes.class);
        FileTime s1Time = s1Attr.lastModifiedTime();
        FileTime s2Time = s2Attr.lastModifiedTime();
        if (s1Time.toInstant().isAfter(s2Time.toInstant())) { // if serial file is newer than source file
            System.out.println("-------------------------------------\nRecent serial file(s) detected.");
            return true;
        } else {
            System.out.println("-------------------------------------\nSerial file(s) are older than data source.");
            return false;
        }
    }

    // checks if serial file exists for given filetype
    private void checkSerial(serializeType st) {
        File f1 = null;
        File f2 = new File(SAVE_DIR + BIGRAM_EXPORT);
        new File(SAVE_DIR).mkdir();
        if (st == serializeType.text) {
            MPTR_EXPORT = MPTR_EXPORT.replace("terms", "terms-n" + chunkSize);
            f1 = new File(SAVE_DIR + MPTR_EXPORT);
        } else if (st == serializeType.binary) {
            MPTR_SER = MPTR_SER.replace("terms", "terms-n" + chunkSize);
            f1 = new File(SAVE_DIR + MPTR_SER);
        } else {
            System.err.println("Serialize Type: " + st + " does not exist.");
        }

        if (forceOriginal) {
            System.out.println("-------------------------------------\nForcing use of original data source.");
        } else if (f1.exists() && !f1.isDirectory() && f2.exists() && !f2.isDirectory()) {
            try {
                if (firstIsRecent(f1.getPath(), DATA.getPath())) {
                    fromSerial = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }    
        } else {
            System.out.println("-------------------------------------\nSerial file(s) not found at " + f1.getAbsolutePath() + " and " + f2.getAbsolutePath());
        }

        if (fromSerial && anonymize) {
            printError("Anonymize flag ignored due to present serial files.");
        }
        if (anonymize) {
            System.out.println("Anonymizing data...");
        }
    }

    // writes serialized file using Java Serializable interface
    public void writeBinarySerializedFile(String path, String bigramPath) {
        System.out.println("Writing serialized binary file...");
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(SAVE_DIR + path);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(currPRT);
            objectOutputStream.flush();
            objectOutputStream.close();
            currBigramPRT.writeTermsToFile(SAVE_DIR + bigramPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("File written to: " + SAVE_DIR + path);
    }

    // writes serialized file using PRT method (term frequency)
    public void writeTextSerializedFile(String prtPath, String bigramPath) {
        System.out.println("Writing serialized text file...");
        currPRT.writeTermsToFile(SAVE_DIR + prtPath);
        currBigramPRT.writeTermsToFile(SAVE_DIR + bigramPath);
        System.out.println("File written to: " + SAVE_DIR + prtPath);
    }

    // prints numerous parameters for verbose option
    public void printParams() {
        System.out.println("----------");
        System.out.println("Original data: " + DATA);
        System.out.println("Read from serial? " + fromSerial);
        System.out.println("Data type: " + fileType);
        System.out.println("Sentence chunk size: " + chunkSize);
        System.out.println("File serialization type: " + serType);
        System.out.println("Top-K: " + TOPK);
        System.out.println("----------");
    }

    // calculates number of single character edits required to change one word to another
    // source: https://www.baeldung.com/java-levenshtein-distance
    private int LevenshteinDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];
        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                }
                else if (j == 0) {
                    dp[i][j] = i;
                }
                else {
                    dp[i][j] = min(dp[i - 1][j - 1] 
                     + costOfSubstitution(x.charAt(i - 1), y.charAt(j - 1)), 
                      dp[i - 1][j] + 1, 
                      dp[i][j - 1] + 1);
                }
            }
        }
        return dp[x.length()][y.length()];
    }

    public static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 1;
    }

    public static int min(int... numbers) {
        return Arrays.stream(numbers).min().orElse(Integer.MAX_VALUE);
    }

    // rounds double numbers safely
    // https://stackoverflow.com/questions/2808535/round-a-double-to-2-decimal-places
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.DOWN);
        return bd.doubleValue();
    }
    
    // removes accents from string
    // https://stackoverflow.com/questions/3322152/is-there-a-way-to-get-rid-of-accents-and-convert-a-whole-string-to-regular-lette
    public static String accentFold(String src) {
		return Normalizer
            .normalize(src, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "");
	}

    // executes on escape key press or window cloase
    private void closeProgram() {
        System.out.println("Trigger Counts: " + triggerCount);
        System.exit(0);
    }

    // prints error messages in bold red text
    public void printError(String s) {
        System.out.println("\033[0;1m" + "\u001b[31m" + s + "\u001B[0m");
    }
}