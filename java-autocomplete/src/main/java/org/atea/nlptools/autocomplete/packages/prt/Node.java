package packages.prt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap.SimpleImmutableEntry;

//Trie node class
public class Node implements Serializable {
    private List<NodeChild> children;

    //Does this node represent the last character in a word? 
    //0: no word; >0: is word (termFrequencyCount)
    private double termFrequencyCount;
    private double termFrequencyCountChildMax;
    private Map<String, Double> termFrequencyDistrib = new HashMap<String, Double>();

    public Node(String originalText, double termFrequencyCount) {
        if (originalText != null) {
            termFrequencyDistrib.put(originalText, termFrequencyCount);
        }
        this.termFrequencyCount = termFrequencyCount;
    }

    public List<SimpleImmutableEntry<String, Double>> getTermFrequencyDistrib() {
        List<SimpleImmutableEntry<String, Double>> keyValList = new ArrayList<SimpleImmutableEntry<String, Double>>();
        Set<String> keySet = termFrequencyDistrib.keySet();
        for (String key : keySet) {
            Double value = termFrequencyDistrib.get(key);
            SimpleImmutableEntry<String, Double> keyVal = new SimpleImmutableEntry<>(key, value);
            keyValList.add(keyVal);
        }
        return keyValList;
    }

    @Override
    public String toString() {
        return "Node [children=" + children + ", termFrequencyCount=" + termFrequencyCount
                + ", termFrequencyCountChildMax=" + termFrequencyCountChildMax + "]";
    }

    public List<NodeChild> getChildren() {
        return children;
    }

    public void setChildren(List<NodeChild> children) {
        this.children = children;
    }

    public double getTermFrequencyCount() {
        return termFrequencyCount;
    }

    // public void setTermFrequencyCount(double d) {
    //     this.termFrequencyCount = d;
    // }

    public void incTermFrequencyCount(String originalText, double d) {
        double origD = termFrequencyDistrib.containsKey(originalText) ? termFrequencyDistrib.get(originalText) : 0;
        double newD = origD + d;
        termFrequencyDistrib.put(originalText, newD);
        this.termFrequencyCount += d;
    }

    public double getTermFrequencyCountChildMax() {
        return termFrequencyCountChildMax;
    }

    public void setTermFrequencyCountChildMax(double d) {
        this.termFrequencyCountChildMax = d;
    }

    public void printTermFrequencyDistrib() {
        for (String key : termFrequencyDistrib.keySet()) {
            System.out.println(key + " = " + termFrequencyDistrib.get(key));
        }
        System.out.println("===========");
    }

    public String getTermFrequencyDistribMaxKey() {
        double max = 0;
        String maxKey = null;

        for (String key : termFrequencyDistrib.keySet()) {
            double f = termFrequencyDistrib.get(key);
            if (f > max) {
                max = f;
                maxKey = key;
            }
        }
        return maxKey;
    }
}