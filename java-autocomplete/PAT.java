import java.awt.BorderLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import prt.PruningRadixTrie;
import prt.TermAndFrequency;

public class PAT extends JFrame{

    final int YEAR_MIN = 2008; // oldest message post year
    final int YEAR_MAX = LocalDate.now().getYear(); // today's year
    static boolean FROM_PERSIST = false; // if tries should populate from persistence file (50% faster) or from original dataset
    static int TOPK = 5; // number of top phrases the ranking system should extract
    static int BIGRAM_WEIGHT = 10; // weight of bigram ranking value
    static double TR_WEIGHT = 0.8; // weight of time ranking value
    static String MPTR_EXPORT = "MPTR_terms.txt"; // most popular time ranker persistence file
    static String BIGRAM_EXPORT = "bigram_terms.txt"; // bigram ranker persistence file
    static String MPTR_SPLIT = "\\?|\\.|!"; // regex values to split sentences 
    static String BIGRAM_SPLIT = ",|\\.|!|\\?"; // regex values to split sentences
    static String DATA = "src/rmt_corpus_cleaned.csv"; // location of data with date
    

    JTextField textfield; // input field
    JFrame f;
    JLabel label; // label for suggestion
    String final_out = "";
    Double final_value;
    HashMap<String, Double> output;

	
//	  long startTime = System.currentTimeMillis();
//    long endTime = System.currentTimeMillis();
//    System.out.println("Total execution time: " + (endTime - startTime));

    public static void main(String[] args) {
        PAT pat = new PAT();
        pat.Start();    
    }

    public void Start() {

        // forces UTF-8 encoding on output to support macrons
        PrintStream out = null;
        try {
            out = new PrintStream(System.out, true, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // most popular time ranker (MPTR) pruning radix trie (PRT)
        PruningRadixTrie pruningRadixTrie_MPTR = new PruningRadixTrie();
        // bigram ranker PRT
        PruningRadixTrie pruningRadixTrie_Bigram = new PruningRadixTrie();
        
        if (FROM_PERSIST) {
            // populate PRTs from tab-delimited persistence files
            pruningRadixTrie_MPTR.readTermsFromFile(MPTR_EXPORT, "\t");
            pruningRadixTrie_Bigram.readTermsFromFile(BIGRAM_EXPORT, "\t");
        } else {
            // populate PRTs from original dataset
            try { // MPTR PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(DATA, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d/MM/yyyy");
                System.out.println("Building MPTR Tree...");

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
                    String[] data = msg.split(MPTR_SPLIT); // split tweet into phrases by allocated delimiters
                    for (String sentence : data) {
                        if (!sentence.equals("") && !sentence.isEmpty()) { // prune empty phrases
                            pruningRadixTrie_MPTR.addTerm(processSentence(sentence), 1 + (TR_WEIGHT * getNormalized(msgYear, YEAR_MIN, YEAR_MAX))); // add phrase to tree with MPTR value
                        } 
                    }
                }
                inputReader.close();
            }
            catch (Exception e) {
                out.println("ERROR: " + e.getMessage());
            }
            
            try { // Bigram PRT indexing
                BufferedReader inputReader = new BufferedReader(new FileReader(DATA, StandardCharsets.UTF_8)); // create reader interface with UTF-8 encoding for macron support
                String row;
                System.out.println("Building Bigram Tree...");

                while ((row = inputReader.readLine()) != null) {
                    String[] data = row.split("\t");
                    for (String sentence : data) {
                        if (!Character.isDigit(sentence.charAt(0))) {
                            StringTokenizer itr = new StringTokenizer(sentence.toLowerCase().trim().replace("\"", "")); // tokenizer to maintain word position within sentence
                            if (itr.countTokens() > 1) {
                                String s1 = "";
                                String s2 = "";
                                while (itr.hasMoreTokens())
                                {
                                    if (s1.isEmpty())
                                        s1 = itr.nextToken();
                                    s2 = itr.nextToken();
                                    pruningRadixTrie_Bigram.addTerm(s1 + " " + s2, 1); // add words to PRT and increment count
                                    s1 = s2;
                                    s2 = "";
                                }
                            }
                        }
                    }
                }
                inputReader.close();
            }
            catch (Exception e) {
                out.println("ERROR: " + e.getMessage());
            }
            // save PRTs to persistence files
            pruningRadixTrie_MPTR.writeTermsToFile(MPTR_EXPORT);
            pruningRadixTrie_Bigram.writeTermsToFile(BIGRAM_EXPORT);   
        }
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
        f.setVisible(true);

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
                System.out.println("-------------------------------------");
                output = new HashMap<String, Double>();
                int maxFreq = 0;
                int minFreq = 0;
                Pattern pat = Pattern.compile("[^.]+$"); // return text following a fullstop
                Matcher mat = pat.matcher(textfield.getText().toLowerCase()); // retrieve text from text field, strip casing
                String text_in = "";
                if (mat.find()) {
                    text_in = mat.group(0).replaceAll("^\\s+",""); // remove leading spaces of text following fullstop
                } else {
                    text_in = textfield.getText().toLowerCase().replaceAll("^\\s+",""); // remove leading spaces
                }
                String lastwords = text_in; // stores last two words for bigram prefix search
                String[] words = text_in.split(" ");
                if (words.length > 2) { // if more than two words are present, retrieve last two
                    lastwords = words[words.length - 2] + " " + words[words.length - 1];
                    System.out.println("Bigram text in: " + lastwords);
                }
                
                System.out.println("Text in: " + text_in);
                
                List<TermAndFrequency> results_MPTR = pruningRadixTrie_MPTR.getTopkTermsForPrefix(text_in, TOPK); // retrieve top-k phrases
                List<TermAndFrequency> results_Bigram = pruningRadixTrie_Bigram.getTopkTermsForPrefix(lastwords.trim(), 0, false); // retrieve all matching bigrams
                System.out.println("TOP-K: " + (TOPK));
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

                if (!output.isEmpty()) {
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
                        label.setText(final_out);
                    } else {
                        out.println("Pass threshold: no");
                        label.setText("");
                    }
                }
            }
        });
    }

    private double getNormalized(double val, int min, int max) { // returns normalized value (0..1) when given min and max
        return (val - min) / (max - min);
    }

    private String processSentence(String val) { // removes various punctuation from phrase 
        return val.toLowerCase().trim().replaceAll(",","").replaceAll("\"","").replaceAll("'","");
    }    
    
    private boolean passThreshold(String currInput, String suggestion, Double rankingMetric) { // determines whether or not a phrase should be pushed to user
        System.out.println("Levenshtein distance to target: " + LevenshteinDistance(currInput, suggestion));
        // various limitations based on ranking metric and levenshtein distance
        if (rankingMetric > 100.0 && LevenshteinDistance(currInput, suggestion) < 10) {
            return true;
        } else if (rankingMetric > 15.0 && LevenshteinDistance(currInput, suggestion) < 6) {
            return true;
        }else if (rankingMetric > 5.0 && LevenshteinDistance(currInput, suggestion) < 4) {
            return true;
        }
        return false;
    }

    // calculates number of single character edits required to change one word to another
    // source: https://www.baeldung.com/java-levenshtein-distance
    static int LevenshteinDistance(String x, String y) {
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
}