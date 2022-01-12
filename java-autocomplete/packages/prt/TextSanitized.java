package packages.prt;

import java.io.Serializable;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

public class TextSanitized implements Serializable {
    private String originalText;
    private String unSanitizedText;
    private String sanitizedText;
    private Integer[] s2o_mapping = null;

    public TextSanitized(String unSanitizedText) {
        this.originalText = unSanitizedText;
        this.unSanitizedText = unSanitizedText;
        String[] unSanitizedTextChars = unSanitizedText.split("");
        ArrayList<String> sanitizedTextChars = new ArrayList<>(); 
        ArrayList<Integer> sanitizedTextPos = new ArrayList<>();
        for (int i = 0; i < unSanitizedTextChars.length; i++) {
            String s = unSanitizedTextChars[i];
            if (Pattern.matches("\\p{IsPunctuation}", s)) {
                continue;
            }
            // accent fold currently disabled due to performance impact
            //String s_af = accentFold(s);
            String s_af_lc = s.toLowerCase();
            sanitizedTextChars.add(s_af_lc);
            sanitizedTextPos.add(i);
        }
        sanitizedTextPos.add(unSanitizedTextChars.length);
        this.sanitizedText = String.join("", sanitizedTextChars);
        this.s2o_mapping = sanitizedTextPos.toArray(new Integer[0]);
    }

    protected TextSanitized(TextSanitized textSanitized) {
        this.originalText = textSanitized.originalText;
        this.unSanitizedText = textSanitized.unSanitizedText;
        this.sanitizedText = textSanitized.sanitizedText;
        this.s2o_mapping = textSanitized.s2o_mapping.clone();
    }

    protected TextSanitized() {
        this.originalText = null;
        this.unSanitizedText = null;
        this.sanitizedText = null;
        this.s2o_mapping = null;
    }

    protected static TextSanitized prefixTextSanitized(TextSanitized parentTextSanitized, int sanitizedEndIndex) {
        TextSanitized childTextSanitized = new TextSanitized();
        childTextSanitized.originalText = parentTextSanitized.originalText;

        childTextSanitized.sanitizedText = parentTextSanitized.sanitizedText.substring(0, sanitizedEndIndex);

        int unSanitizedEndIndex = parentTextSanitized.s2o_mapping[sanitizedEndIndex];
        int unSanitizedBeginIndex = parentTextSanitized.s2o_mapping[0];
       
        childTextSanitized.unSanitizedText = parentTextSanitized.unSanitizedText.substring(unSanitizedBeginIndex, unSanitizedEndIndex);
        childTextSanitized.s2o_mapping = Arrays.copyOfRange(parentTextSanitized.s2o_mapping, 0, sanitizedEndIndex);

        return childTextSanitized;
    }

    protected static TextSanitized suffixTextSanitized(TextSanitized parentTextSanitized, int sanitizedBeginIndex) {
        TextSanitized childTextSanitized = new TextSanitized();
        childTextSanitized.originalText = parentTextSanitized.originalText;

        childTextSanitized.sanitizedText = parentTextSanitized.sanitizedText.substring(sanitizedBeginIndex);

        int unSanitizedBeginIndex = parentTextSanitized.s2o_mapping[sanitizedBeginIndex];
       
        childTextSanitized.unSanitizedText = parentTextSanitized.unSanitizedText.substring(unSanitizedBeginIndex);
        childTextSanitized.s2o_mapping = Arrays.copyOfRange(parentTextSanitized.s2o_mapping, sanitizedBeginIndex, parentTextSanitized.s2o_mapping.length);

        for (int i = 0; i < childTextSanitized.s2o_mapping.length; i++) {
            childTextSanitized.s2o_mapping[i] -= unSanitizedBeginIndex;
        }

        return childTextSanitized;
    }

    public static String accentFold(String src) {
		return Normalizer
				.normalize(src, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}", ""); // (\p{Nonspacing_Mark})
	}

    public String suffixSanitized(int beginIndex) {
        return sanitizedText.substring(beginIndex);
    }

    public String prefixSanitized(int endIndex) {
        return sanitizedText.substring(0, endIndex);
    }

    public String suffixUnSanitized(int beginIndex) {
        int s2o_pos = s2o_mapping[beginIndex];
        return unSanitizedText.substring(s2o_pos);
    }

    public String prefixUnSanitized(int endIndex) {
        int s2o_beginIndex = s2o_mapping[0];
        int s2o_endIndex = s2o_mapping[endIndex];
        return unSanitizedText.substring(s2o_beginIndex, s2o_endIndex);
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getUnsanitizedText() {
        return unSanitizedText;
    }

    public String getSanitizedText() {
        return sanitizedText;
    }

    public int getOriginalTextLength() {
        return originalText.length();
    }

    public int getSanitizedTextLength() {
        return sanitizedText.length();
    }

    public char getSanitizedCharAt(int p) {
        return sanitizedText.charAt(p);
    }

    public static void main(String[] args) {
        TextSanitized t1 = new TextSanitized("A-bc,-, Def.:,.");
        TextSanitized t2 = new TextSanitized("Abc, Def");
        System.out.println("t1[sanitized 0..3] = " + t1.prefixSanitized(3));
        System.out.println("t1[original 0..3]  = " + t1.prefixUnSanitized(3));
        System.out.println("t2[sanitized 0..3] = " + t2.prefixSanitized(3));
        System.out.println("t2[original 0..3]  = " + t2.prefixUnSanitized(3));

        System.out.println("t1[sanitized 4..] = " + t1.suffixSanitized(4));
        System.out.println("t1[original 4..]  = " + t1.suffixUnSanitized(4));
        System.out.println("t2[sanitized 4..] = " + t2.suffixSanitized(4));
        System.out.println("t2[original 4..]  = " + t2.suffixUnSanitized(4));
    }

    @Override
    public String toString() {
        return "TextSanitize [unSanitizedText=" + unSanitizedText + ", sanitizedText=" + sanitizedText + "]";
    }
}