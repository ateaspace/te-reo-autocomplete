package packages.prt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PruningRadixTrie implements Serializable {
        
    public double termCount = 0;
    public double termCountLoaded = 0;

    //The trie (root node)
    private final Node trie;

    public PruningRadixTrie() {
        this.trie = new Node(null, 0);
    }

    // Insert a word into the trie
    public void addTerm(TextSanitized term, double termFrequencyCount) {
        List<Node> nodeList = new ArrayList<>();
        addTerm(trie, term, termFrequencyCount, 0, 0, nodeList);
    }

    public String toLowerCase(String caseSensitive) {
        String caseInsensitive = caseSensitive.toLowerCase();
        return caseInsensitive;
    }

    public void updateMaxCounts(List<Node> nodeList, double termFrequencyCount) {
        for (Node node : nodeList) {
            if (termFrequencyCount > node.getTermFrequencyCountChildMax()) {
                node.setTermFrequencyCountChildMax(termFrequencyCount);
            }
        }
    }

    public void addTerm(Node curr, TextSanitized term, double termFrequencyCount, int id, int level, List<Node> nodeList)
    {
        try {
            //System.out.println("level: " + level + "\tadd term: " + term.toString());
            //String termSanitizedStr = term.getSanitizedText();
            nodeList.add(curr);

            //test for common prefix (with possibly different suffix)
            int common = 0;
            List<NodeChild> currChildren = curr.getChildren();
            if (currChildren != null) { 
                for (int j = 0; j < currChildren.size(); j++) {
                    TextSanitized key = currChildren.get(j).getKeySanitized();
                    //String keySanitizedStr = key.getSanitizedText();
                    Node node = currChildren.get(j).getNode();

                    //System.out.println("termSanitized= " + termSanitizedStr + "\tkeySanitized= " + keySanitizedStr);
                    for (int i = 0; i < Math.min(term.getSanitizedTextLength(), key.getSanitizedTextLength()); i++) {
                        if (term.getSanitizedCharAt(i) == key.getSanitizedCharAt(i)) common = i + 1;
                        else break;
                    }

                    if (common > 0) {
                        //term already existed
                        //existing ab
                        //new      ab
                        if ((common == term.getSanitizedTextLength()) && (common == key.getSanitizedTextLength())) {
                            if (node.getTermFrequencyCount() == 0) termCount++;
                            node.incTermFrequencyCount(term.getOriginalText(), termFrequencyCount);
                            updateMaxCounts(nodeList, node.getTermFrequencyCount());
                        }
                        //new is subkey
                        //existing abcd
                        //new      ab
                        //if new is shorter (== common), then node(count) and only 1. children add (clause2)
                        else if (common == term.getSanitizedTextLength()) {
                            //insert second part of oldKey as child 
                            Node child = new Node(term.getOriginalText(), termFrequencyCount);
                            List<NodeChild> l = new ArrayList<>();
                            // String keySuffixUnSanitized = key.suffixUnSanitized(common);
                            // TextSanitized keySuffix = new TextSanitized(keySuffixUnSanitized);

                            TextSanitized keySuffix = TextSanitized.suffixTextSanitized(key, common);
                            l.add(new NodeChild(keySuffix, node));
                            child.setChildren(l);
                            
                            child.setTermFrequencyCountChildMax( 
                                    Math.max(node.getTermFrequencyCountChildMax(), node.getTermFrequencyCount()));
                            updateMaxCounts(nodeList, termFrequencyCount);

                            //insert first part as key, overwrite old node
                            // String termPrefixUnSanitized = term.prefixUnSanitized(common);
                            // TextSanitized termPrefix = new TextSanitized(termPrefixUnSanitized);

                            TextSanitized termPrefix = TextSanitized.prefixTextSanitized(term, common);
                            
                            currChildren.set(j, new NodeChild(termPrefix, child));
                            //sort children descending by termFrequencyCountChildMax to start lookup with most promising branch
                            Collections.sort(currChildren, Comparator.comparing(
                                    (NodeChild e) -> e.getNode().getTermFrequencyCountChildMax()).reversed());
                            //increment termcount by 1
                            termCount++;
                        }
                        //if oldkey shorter (==common), then recursive addTerm (clause1)
                        //existing: te
                        //new:      test
                        else if (common == key.getSanitizedTextLength()) {
                            // String termSuffixUnSanitized = term.suffixUnSanitized(common);
                            // TextSanitized termSuffix = new TextSanitized(termSuffixUnSanitized);
                            TextSanitized termSuffix = TextSanitized.suffixTextSanitized(term, common);
                            addTerm(node, termSuffix, termFrequencyCount, id, level + 1, nodeList);
                        }
                        //old and new have common substrings
                        //existing: test
                        //new:      team
                        else {
                            //insert second part of oldKey and of s as child 
                            Node child = new Node(null, 0);//count
                            List<NodeChild> l = new ArrayList<>();
                            // String keySuffixUnSanitized = key.suffixUnSanitized(common);
                            // TextSanitized keySuffix = new TextSanitized(keySuffixUnSanitized);
                            TextSanitized keySuffix = TextSanitized.suffixTextSanitized(key, common);
                            // String termSuffixUnSanitized = term.suffixUnSanitized(common);
                            // TextSanitized termSuffix = new TextSanitized(termSuffixUnSanitized);
                            TextSanitized termSuffix = TextSanitized.suffixTextSanitized(term, common);
                            l.add(new NodeChild(keySuffix, node));
                            l.add(new NodeChild(termSuffix, new Node(term.getOriginalText(), termFrequencyCount)));
                            child.setChildren(l);

                            child.setTermFrequencyCountChildMax(
                                    Math.max(node.getTermFrequencyCountChildMax(), Math.max(termFrequencyCount, node.getTermFrequencyCount())));
                            updateMaxCounts(nodeList, termFrequencyCount);
                            
                            //insert first part as key, overwrite old node
                            // String termPrefixUnSanitized = term.prefixUnSanitized(common);
                            // TextSanitized termPrefix = new TextSanitized(termPrefixUnSanitized);
                            TextSanitized termPrefix = TextSanitized.prefixTextSanitized(term, common);
                            currChildren.set(j, new NodeChild(termPrefix, child));
                            //sort children descending by termFrequencyCountChildMax to start lookup with most promising branch
                            Collections.sort(currChildren, Comparator.comparing(
                                    (NodeChild e) -> e.getNode().getTermFrequencyCountChildMax()).reversed());
                            //increment termcount by 1
                            termCount++;
                        }
                        return;
                    }
                }
            }

            // initialize dictionary if first key is inserted 
            if (currChildren == null) {
                List<NodeChild> l = new ArrayList<>();
                l.add(new NodeChild(term, new Node(term.getOriginalText(), termFrequencyCount)));
                curr.setChildren(l);
            }
            else {
                currChildren.add(new NodeChild(term, new Node(term.getOriginalText(), termFrequencyCount)));
                //sort children descending by termFrequencyCountChildMax to start lookup with most promising branch
                Collections.sort(currChildren, Comparator.comparing(
                        (NodeChild e) -> e.getNode().getTermFrequencyCountChildMax()).reversed());
            }
            termCount++;
            updateMaxCounts(nodeList, termFrequencyCount);
        } catch (Exception e) { System.out.println("exception: " + term + " " + e.getMessage()); e.printStackTrace();}
    }

    public void findAllChildTerms(String prefix, int topK, List<TermAndFrequency> results, Boolean pruning) // Removed 3rd parameter: ref long termFrequencyCountPrefix
    {
        findAllChildTerms(prefix, trie, topK, results, null, pruning);
    }

    public void findAllChildTerms(String prefix, Node curr, int topK, List<TermAndFrequency> results, BufferedWriter file, Boolean pruning) // Removed 4th parameter: ref long termfrequencyCountPrefix
    {
        try {
            //pruning/early termination in radix trie lookup
            if (pruning && (topK > 0) && (results.size() == topK) && 
                    (curr.getTermFrequencyCountChildMax() <= results.get(topK - 1).getTermFrequencyCount())) { 
                return;
            }

            //test for common prefix (with possibly different suffix)
            Boolean noPrefix = (prefix.equals("") || prefix == null);

            if (curr.getChildren() != null) {
                for (NodeChild nodeChild : curr.getChildren()) {
                    // String key = nodeChild.getKey();
                    // String keySensitive = nodeChild.getKeySensitive();
                    TextSanitized keySanitized = nodeChild.getKeySanitized();
                    String keySanitizedStr = keySanitized.getSanitizedText();
                    // String keyUnSanitized = keyTS.getUnsanitizedText();
                    
                    Node node = nodeChild.getNode();
                    //pruning/early termination in radix trie lookup
                    if (pruning && (topK > 0) && (results.size() == topK) &&
                            (node.getTermFrequencyCount() <= results.get(topK - 1).getTermFrequencyCount()) && 
                            (node.getTermFrequencyCountChildMax() <= results.get(topK - 1).getTermFrequencyCount())) {
                        if (!noPrefix) break; 
                        else continue;
                    }                     
                    // prefix = Normalizer.normalize(prefix, Normalizer.Form.NFD);
                    // prefix = prefix.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                    if (noPrefix || unaccent(keySanitizedStr).startsWith(unaccent(prefix))) {
                        if (node.getTermFrequencyCount() > 0) {
                            String originalText = keySanitized.getOriginalText();
                            // if (prefix == key) termfrequencyCountPrefix = node.getTermFrequencyCount();

                            //candidate                              
                            if (file != null) file.write(originalText + "\t" + node.getTermFrequencyCount() + "\n");
                            else {
                                addTopKSuggestion(nodeChild, node.getTermFrequencyCount(), topK, results);
                                // if (topK > 0) addTopKSuggestion(newPrefixString, node.getTermFrequencyCount(), topK, results); 
                                // else results.add(new TermAndFrequency(newPrefixString, node.getTermFrequencyCount()));  
                            }
                        }

                        if ((node.getChildren() != null) && (node.getChildren().size() > 0)) { 
                            findAllChildTerms("", node, topK, results, file, pruning);
                        }
                        if (!noPrefix) break;
                    } else if (unaccent(prefix).startsWith(unaccent(keySanitizedStr))) {
                        if ((node.getChildren() != null) && (node.getChildren().size() > 0)) {
                            findAllChildTerms(prefix.substring(keySanitizedStr.length()), node, topK, results, file, pruning);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) { System.out.println("xxexception: " + prefix + " " + e.getMessage()); e.printStackTrace(); }
    }
    
    public List<TermAndFrequency> getTopkTermsForPrefix(String prefix, int topK) {
        return getTopkTermsForPrefix(prefix, topK, true);
    }
    
    public List<TermAndFrequency> getTopkTermsForPrefix(String prefix, int topK, Boolean pruning) { // Removed parameter 'out long termFrequencyCountPrefix' as returning it in Java would mean changing the return type of the method. 
        List<TermAndFrequency> results = new ArrayList<>();

        //termFrequency of prefix, if it exists in the dictionary (even if not returned in the topK results due to low termFrequency)
        // long termFrequencyCountPrefix = 0;

        // At the end of the prefix, find all child words
        findAllChildTerms(prefix, topK, results, pruning); 
        
        return results;
    }

    public void writeTermsToFile(String path) {
        //save only if new terms were added
        if (termCountLoaded == termCount) return;
        try (BufferedWriter file = new BufferedWriter(Files.newBufferedWriter(Paths.get(path),StandardCharsets.UTF_8))) {
            // long prefixCount = 0;
            findAllChildTerms("", trie, 0, null, file, true);
            //System.out.println(termCount + " terms written.");
        } catch (Exception e) {
            System.out.println("Writing terms exception: " + e.getMessage());
        }
    }

    public Boolean readTermsFromFile(String path, String delimiter) { // Introduced parameter fieldDelimiter, the string on each line that separates the word from the frequency. Eg use value "\t" for tab delimited dictionary files.
        try (BufferedReader sr = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            String line;
            
            //process a single line at a time only for memory efficiency  
            while ((line = sr.readLine()) != null) {
                String[] lineParts = line.split(delimiter);
                if (lineParts.length == 2) {
                    try { 
                        TextSanitized term = new TextSanitized(lineParts[0]);
                        double count = Double.parseDouble(lineParts[1]);
                        this.addTerm(term, count);
                    } catch (NumberFormatException e) {
                        System.out.println("Warning - frequency could not be extracted from a dictionary line. Skipping line.");
                    }
                }
            }
            termCountLoaded = termCount;
            System.out.println(String.format("%,d", (int)termCount) + " terms written from serialized file");
        } catch (FileNotFoundException e) {
            System.out.println("Could not find file " + path);
            return false;
        } catch (Exception e) {
            System.out.println("Loading terms exception: " + e.getMessage());
        }

        return true;
    }

    public void addTopKSuggestion(NodeChild nodeChild, double d, int topK, List<TermAndFrequency> results) 
    {
        // TextSanitized keySanitized = nodeChild.getKeySanitized();
        // String originalText = keySanitized.getOriginalText();
        String mostFrequentText = nodeChild.getNode().getTermFrequencyDistribMaxKey();

        if (topK > 0) {
            //at the end/highest index is the lowest value
            // >  : old take precedence for equal rank   
            // >= : new take precedence for equal rank 
            if ((results.size() < topK) || (d >= results.get(topK - 1).getTermFrequencyCount())) {
                TermAndFrequency termAndFrequency = new TermAndFrequency(mostFrequentText, d);
                int index = Collections.binarySearch(results, termAndFrequency, Comparator.comparing(
                        (TermAndFrequency e) -> e.getTermFrequencyCount()).reversed()); // descending order
                if (index < 0) results.add(~index, termAndFrequency); 
                else results.add(index, termAndFrequency); 

                if (results.size() > topK) results.remove(topK);
            }
        } else {
            results.add(new TermAndFrequency(mostFrequentText, d));
        }
    }
    
    // https://stackoverflow.com/questions/3322152/is-there-a-way-to-get-rid-of-accents-and-convert-a-whole-string-to-regular-lette
    public static String unaccent(String src) {
		return Normalizer
				.normalize(src, Normalizer.Form.NFD)
				.replaceAll("[^\\p{ASCII}]", "");
	}
}