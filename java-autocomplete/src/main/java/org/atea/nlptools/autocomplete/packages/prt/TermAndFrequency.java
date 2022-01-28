package packages.prt;

import java.io.Serializable;

public class TermAndFrequency implements Serializable {
    private String term;
    private double termFrequencyCount;
    
    public String getTerm() {
        return term;
    }
    public void setTerm(String term) {
        this.term = term;
    }
    public double getTermFrequencyCount() {
        return termFrequencyCount;
    }
    public void setTermFrequencyCount(double termFrequencyCount) {
        this.termFrequencyCount = termFrequencyCount;
    }
    @Override
    public String toString() {
        return "TermAndFrequency [term=" + term + ", termFrequencyCount=" + termFrequencyCount + "]";
    } 
    public TermAndFrequency(String term, double d) {
        super();
        this.term = term;
        this.termFrequencyCount = d;
    }
    
    // @Override
    // public int hashCode() {
    //     final int prime = 31;
    //     int result = 1;
    //     result = prime * result + ((term == null) ? 0 : term.hashCode());
    //     result = prime * result + (int) (termFrequencyCount ^ (termFrequencyCount >>> 32));
    //     return result;
    // }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TermAndFrequency other = (TermAndFrequency) obj;
        if (term == null) {
            if (other.term != null)
                return false;
        } else if (!term.equals(other.term))
            return false;
        if (termFrequencyCount != other.termFrequencyCount)
            return false;
        return true;
    }
}