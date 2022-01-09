package packages.prt;

import java.util.List;

//Trie node class
public class Node
{
    private List<NodeChild> children;

    //Does this node represent the last character in a word? 
    //0: no word; >0: is word (termFrequencyCount)
    private double termFrequencyCount;
    private double termFrequencyCountChildMax;

    public Node(double termFrequencyCount2) {
        this.termFrequencyCount = termFrequencyCount2;
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

    public void setTermFrequencyCount(double d) {
        this.termFrequencyCount = d;
    }

    public double getTermFrequencyCountChildMax() {
        return termFrequencyCountChildMax;
    }

    public void setTermFrequencyCountChildMax(double d) {
        this.termFrequencyCountChildMax = d;
    }
}