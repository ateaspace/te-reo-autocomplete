import java.awt.BorderLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.google.gson.Gson;

import java.awt.event.*;

import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.sentdetect.SentenceModel;
import packages.prt.PruningRadixTrie;
import packages.prt.TermAndFrequency;

import packages.CommandLine;
import packages.CommandLine.*;

@Command(name = "PAT", version = "TRMAC 0.1", mixinStandardHelpOptions = true)
public class PAT extends JFrame implements Runnable{

    final int YEAR_MIN = 2008; // oldest message post year
    final int YEAR_MAX = LocalDate.now().getYear(); // today's year
    final int TOPK = 5; // number of top phrases the ranking system should extract
    final int BIGRAM_WEIGHT = 10; // weight of bigram ranking value
    final double TR_WEIGHT = 0.8; // weight of time ranking value
    final String MPTR_SPLIT = ",|\\.|!|\\?"; // regex values to split sentences 
    final String BIGRAM_SPLIT = ",|\\.|!|\\?"; // regex values to split sentences
    final String SENT_MI = "src/mri-sent.bin"; // location of maori sentence tokenizer file
    final String SENT_EN = "src/en-sent.bin"; // location of english sentence tokenizer file
    static Boolean fromSerial = false; // if tries should be populated from serialized files
    static Tweet tweet; 
    static Mailbox mbox;
    static int exitCode;
    
    JTextField textfield; // input field
    JFrame f;
    JLabel label; // label for suggestion
    String final_out = "";
    Double final_value;
    HashMap<String, Double> output;
    SentenceModel sentenceModel = null;
    Boolean triggerSuggestion = false;
    
    @Option(names = {"-o", "--originaldata"}, defaultValue = "false", description = "Force re-generation of serial files by using original data source.")
    boolean serialized; 

    @Option(names = {"-a", "--anonymize"}, defaultValue = "false", description = "Anonymize suggestions to hide personal information.")
    static boolean anonymize;

    @Option(names = {"-v", "--verbose"}, defaultValue = "false", description = "Verbose mode. Provides more detail in console. Useful for troubleshooting.")
    static boolean verbose; 

    @Option(names = {"-l", "--language"}, arity="1", defaultValue = "default", description = "Language of text in file (mi or en). [default=auto]")
    static String language;

    @Option(names = {"-n", "--numchunks"}, arity="1..*", description = "Number of words per phrase to be used as input. Supports multiple ints seperated by a space. [default=4, 5, 6]")
    static List<Integer> chunkSize = Arrays.asList(4, 5, 6);

    @Parameters(paramLabel = "<fileType>", arity = "1", description = "Type of file. (rmt for Reo Maori Twitter corpus or mbox for Gmail Mailbox data).")
    static String filetype;

    @Parameters(paramLabel = "<file>", arity = "1", description = "File to read from.")
    static File DATA;
    
// 	  long startTime = System.currentTimeMillis();
//    long endTime = System.currentTimeMillis();
//    System.out.println("Total execution time: " + (endTime - startTime));

    @Override
    public void run() {

        if (Collections.min(chunkSize) < 1 || Collections.max(chunkSize) > 99) {
            printError("Please enter an n value between 1 and 99 inclusive.");
            System.exit(exitCode);
        } else {
            Collections.sort(chunkSize);
        }

        if (!DATA.exists() || DATA.isDirectory()) { 
            printError("Given file is either a directory or doesn't exist. Please enter a valid source file.");
            System.exit(exitCode);
        }

        if (filetype.equals("rmt")) {
            checkSerial();
            checkLanguage();
            tweet = new Tweet();
        } else if (filetype.equals("mbox")) {
            checkSerial();
            checkLanguage();
            mbox = new Mailbox();
        } else {
            printError("Given filetype is not supported. Please enter either rmt or mbox as the filetype.");
            System.exit(exitCode);
        }
        PAT p = new PAT();
        p.Start();
    }

    public static void main(String[] args) {
        exitCode = new CommandLine(new PAT()).execute(args); 
    }

    public void Start() {
        // jframe properties
        f = new JFrame("Te Reo M\u0101ori Auto-Complete"); 
        textfield = new JTextField();
        textfield.setFont(new Font(Font.SANS_SERIF, 1, 20));
        textfield.setEditable(true);
        label = new JLabel("suggestion: ");
        label.setFont(new Font(Font.SANS_SERIF, 1, 20));
        JPanel p = new JPanel(new BorderLayout());
        // jframe layout
        p.add(textfield, BorderLayout.NORTH);
        p.add(label, BorderLayout.SOUTH);
        f.add(p);
        f.setSize(400, 100);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setLocationRelativeTo(null); // jframe appear in center of monitor
        f.setVisible(true);

        textfield.setFocusTraversalKeysEnabled(false); // enables recognition of TAB
        textfield.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) 
                    if (triggerSuggestion) 
                        textfield.setText(final_out);
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) 
                    System.exit(0);
            }
            @Override
            public void keyTyped(KeyEvent e) {}
            @Override
            public void keyReleased(KeyEvent e) {}
        });

        textfield.getDocument().addDocumentListener(new DocumentListener() {
            // invoke inputChange() method on insertion, removal or altercation of text in text field
            public void changedUpdate(DocumentEvent e) {
                inputChange();
            }
            public void removeUpdate(DocumentEvent e) {
                inputChange();
            }
            public void insertUpdate(DocumentEvent e) {
                inputChange();
            }
            public void inputChange() {
                SwingUtilities.invokeLater( () -> process() ); 
            }

            public void process() {
                label.setText("");
                output = new HashMap<String, Double>();
                int maxFreq = 0;
                int minFreq = 0;
                String text_in = "";

                // process input text
                String[] splitInput = textfield.getText().split("\\.|\\?|\\!");
                if (splitInput.length > 1) {
                    text_in = splitInput[splitInput.length-1].toLowerCase().replaceAll("^\\s+","");
                } else {
                    text_in = textfield.getText().toLowerCase().replaceAll("^\\s+","");
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
                if (filetype.equals("rmt")) {
                    results_MPTR = tweet.MPTR_PRT.getTopkTermsForPrefix(text_in, TOPK); // retrieve top-k phrases
                    results_Bigram = tweet.Bigram_PRT.getTopkTermsForPrefix(lastTwoWords, 0, false); // retrieve all matching bigrams
                } else if (filetype.equals("mbox")) {
                    results_MPTR = mbox.MPTR_PRT.getTopkTermsForPrefix(text_in, TOPK); // retrieve top-k phrases
                    results_Bigram = mbox.Bigram_PRT.getTopkTermsForPrefix(lastTwoWords, 0, false); // retrieve all matching bigrams
                }
                //System.out.println("TOP-K: " + (TOPK));
                // System.out.println("results_MPTR_size: " + results_MPTR.size());
                // System.out.println("results_Bigram_size: " + results_Bigram.size());

                if (results_Bigram.size() > 1 && results_Bigram.size() > 0) { // calculate minimum and maximum bigram ranking values
                    maxFreq = (int)results_Bigram.get(0).getTermFrequencyCount();
                    minFreq = (int)results_Bigram.get(results_Bigram.size()-1).getTermFrequencyCount();
                } else {
                    System.out.println("No bigram results");
                    label.setText("-- no results!");
                }
                Map<String, Double> normalized_Bigrams = new HashMap<String, Double>();

                for (TermAndFrequency result : results_Bigram) { // normalize bigram ranking values based on min and max
                    normalized_Bigrams.put(result.getTerm(), getNormalized(result.getTermFrequencyCount(), minFreq, maxFreq));
                }

                Set<Entry<String, Double>> bigramFinal = normalized_Bigrams.entrySet(); // convert to set
                
                for (TermAndFrequency result : results_MPTR) {
                    for (Entry<String, Double> result_bi : bigramFinal) {
                        if (result.getTerm().contains(result_bi.getKey())) { // if a bigram result exists, add phrase to PRT
                            double ranking_val = result.getTermFrequencyCount() + (BIGRAM_WEIGHT * result_bi.getValue());
                            output.put(result.getTerm(), ranking_val);
                            continue;
                        }
                    }
                    output.put(result.getTerm(), result.getTermFrequencyCount()); // if no bigram results, add with MPTR metric
                }
                if (text_in.equals("")) {
                    label.setText("suggestion:");
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
                    
                    final_out = sorted_output.keySet().stream().findFirst().get(); // get top ranking phrase
                    final_value = sorted_output.get(sorted_output.keySet().toArray()[0]); // get ranking metric of top ranking phrase
                    out.println("Suggestion: " + final_out); 
                    out.println("Ranking value: " + round(final_value, 2));
                    if (passThreshold(text_in, final_out, final_value)) { // if phrase passes threshold limitations, push to UI
                        out.println("Pass threshold: yes");
                        triggerSuggestion = true;
                        label.setText(final_out);
                    } else {
                        out.println("Pass threshold: no");
                        triggerSuggestion = false;
                        label.setText("");
                    }
                }
            }
        });
    }

    // splits string into n chunks and adds to PRT
    public void addTermByChunks(PruningRadixTrie prt, String input, double timeRank) {
        String[] splitInput = input.split(" "); // tokenizer
        for (int i = 0; i < (splitInput.length - Collections.max(chunkSize) + 1); i++) {
            for (int chunk : chunkSize) {
                prt.addTerm(String.join(" ", Arrays.copyOfRange(splitInput, i, i + chunk)).trim(), 2+timeRank); // offset to 'prefer' longer suggestions 
            }
            // prt.addTerm(String.join(" ", Arrays.copyOfRange(splitInput, i, i + chunkSize)).trim(), 2+timeRank); // offset to 'prefer' longer suggestions
            // prt.addTerm(String.join(" ", Arrays.copyOfRange(splitInput, i, i + chunkSize-1)).trim(), 1+timeRank);
            // prt.addTerm(String.join(" ", Arrays.copyOfRange(splitInput, i, i + chunkSize-2)).trim(), timeRank);
        }
        prt.addTerm(input.trim(), 2+timeRank); // add full phrase
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
            return val.toLowerCase().replaceAll("\"","").replaceAll("'","").replaceAll("\\s+", " ").trim();
        } else {
            return val.toLowerCase().replaceAll("\"","").replaceAll("'","").replaceAll("\\s+", " ").trim();
        }
    }    
    
    // determines whether or not a phrase should be pushed to user
    private boolean passThreshold(String currInput, String suggestion, Double rankingMetric) { 
        System.out.println("Levenshtein distance to target: " + LevenshteinDistance(removeAccents(currInput), removeAccents(suggestion)));
        // various limitations based on ranking metric and levenshtein distance
        //if (currInput.split(" ").length < 2) return false; // don't make suggestions if only one word exists
        if (rankingMetric > 100.0 && LevenshteinDistance(currInput, suggestion) < 10) {
            return true;
        } else if (rankingMetric > 15.0 && LevenshteinDistance(currInput, suggestion) < 6) {
            return true;
        } else if (rankingMetric > 5.0 && LevenshteinDistance(currInput, suggestion) < 4) {
            return true;
        } else {
            return false;
        }
    }

    private void checkLanguage() {
        if (language.equals("mi")) {
            language = SENT_MI;
        } else if (language.equals("en")) {
            language = SENT_EN;
        } else if (language.equals("default")){
            File modelFile = new File("src/langdetect-183.bin");
            LanguageDetectorModel trainedModel;
            try {
                trainedModel = new LanguageDetectorModel(modelFile);
                LanguageDetectorME languageDetector = new LanguageDetectorME(trainedModel);
                Language[] languages = languageDetector.predictLanguages(getSnippet(filetype, DATA));
                System.out.println("Predicted language: " + languages[0].getLang() + " with " + round(languages[0].getConfidence(), 2) + " confidence.");
                if (languages[0].getLang().equals("mri")) {
                    language = SENT_MI;
                } else if (languages[0].getLang().equals("eng")) {
                    language = SENT_EN;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            printError("Given language is not supported. Please enter either 'mi' for Maori or 'en' for English.");
            System.exit(exitCode);
        }
    }

    private String getSnippet(String s, File f) throws IOException {
        String row;
        String out = "";
        if (s.equals("rmt")) {
            BufferedReader inputReader = new BufferedReader(new FileReader(DATA, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
            for (int i = 0; i < 15; i++) {
                row = inputReader.readLine();
                out = out + " " + row.split("\t")[1];
            }
            inputReader.close();
            return out;
        } else if (s.equals("mbox")) {
            FileReader fr = new FileReader(DATA);
            MailItem[] mailitems = new Gson().fromJson(fr, MailItem[].class);
            for (int i = 0; i < 15; i++) {
                out = out + " " + mailitems[i].getBody();
            }
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
    private void checkSerial() {
        File f1 = null;
        File f2 = null;
        if (filetype.equals("rmt")) {
            f1 = new File(Tweet.MPTR_EXPORT.replace("terms", "terms-n" + chunkSize));
            f2 = new File(Tweet.BIGRAM_EXPORT);
        } else if (filetype.equals("mbox")) {
            f1 = new File(Mailbox.MPTR_EXPORT.replace("terms", "terms-n" + chunkSize));
            f2 = new File(Mailbox.BIGRAM_EXPORT);
        } else {
            System.exit(exitCode);
        }

        if (serialized) {
            System.out.println("-------------------------------------\nForcing use of original data source.");
        } else if (f1.exists() && f2.exists() && !f1.isDirectory() && !f2.isDirectory()) {
            try {
                if (firstIsRecent(f1.getPath(), DATA.getPath())) {
                    fromSerial = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }    
        } else {
            System.out.println("-------------------------------------\nSerial file(s) not found.");
        }

        if (fromSerial && anonymize) {
            printError("Anonymize flag ignored due to present serial files. Use -o to force use of original data source.");
        } else if (anonymize) {
            System.out.println("Anonymizing data...");
        }
    }

    public void printParams() {
        System.out.println("----------");
        System.out.println("Original data: " + DATA);
        System.out.println("Read from serial? " + fromSerial);
        System.out.println("Data type: " + filetype);
        System.out.println("Sentence chunk size: " + chunkSize);
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
    public static String removeAccents(String src) {
		return Normalizer
            .normalize(src, Normalizer.Form.NFD)
            .replaceAll("[^\\p{ASCII}]", "");
	}

    // prints error messages in bold red text
    public void printError(String s) {
        System.out.println("\033[0;1m" + "\u001b[31m" + s + "\u001B[0m");
    }
}