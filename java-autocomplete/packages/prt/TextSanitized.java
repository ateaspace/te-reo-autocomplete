package packages.prt;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TextSanitized {
    private String originalText;
    private String sanitizedText;
    private Integer[] s2o_mapping = null;

    public TextSanitized(String originalText) {
        this.originalText = originalText;
        String[] originalTextChars = originalText.split("");
        ArrayList<String> sanitizedTextChars = new ArrayList<>(); 
        ArrayList<Integer> sanitizedTextPos = new ArrayList<>();
        for (int i = 0; i < originalTextChars.length; i++) {
            String s = originalTextChars[i];
            if (Pattern.matches("\\p{IsPunctuation}", s)) {
                continue;
            }
            //String s_af = accentFold(s);
            String s_af_lc = s.toLowerCase();
            sanitizedTextChars.add(s_af_lc);
            sanitizedTextPos.add(i);
        }
        sanitizedTextPos.add(originalTextChars.length);
        this.sanitizedText = String.join("", sanitizedTextChars);
        this.s2o_mapping = sanitizedTextPos.toArray(new Integer[0]);
    }

    // public static String transformToSanitized(String originalText) {
    //     String s_np = originalText.replaceAll("\\p{IsPunctuation}", "");
    //     String s_np_af = accentFold(s_np);
    //     String transformedText = s_np_af.toLowerCase();
    //     return transformedText;
    // }

    public static String accentFold(String src) {
		return Normalizer
				.normalize(src, Normalizer.Form.NFD)
                .replaceAll("\\p{Mn}", ""); // (\p{Nonspacing_Mark})
	}

    public String substringSanitized(int beginIndex) {
        // return originalText.substring(beginIndex);
        return sanitizedText.substring(beginIndex);
    }

    public String substringSanitized(int beginIndex, int endIndex) {
        // return originalText.substring(beginIndex, endIndex);
        return sanitizedText.substring(beginIndex, endIndex);
    }

    public String substringOriginal(int beginIndex) {
        int s2o_pos = s2o_mapping[beginIndex];
        return originalText.substring(s2o_pos);
    }

    public String substringOriginal(int beginIndex, int endIndex) {
        int s2o_beginIndex = s2o_mapping[beginIndex];
        int s2o_endIndex = s2o_mapping[endIndex];
        return originalText.substring(s2o_beginIndex, s2o_endIndex);
    }

    public String getOriginalText() {
        return originalText;
    }

    public String getSanitizedText() {
        return sanitizedText;
    }

    public static void main(String[] args) {
        TextSanitized t1 = new TextSanitized("A-bc,-, Def.:,.");
        TextSanitized t2 = new TextSanitized("Abc, Def");
        System.out.println("t1[sanitized 0..3] = " + t1.substringSanitized(0, 3));
        System.out.println("t1[original 0..3]  = " + t1.substringOriginal(0, 3));
        System.out.println("t2[sanitized 0..3] = " + t2.substringSanitized(0, 3));
        System.out.println("t2[original 0..3]  = " + t2.substringOriginal(0, 3));

        System.out.println("t1[sanitized 4..] = " + t1.substringSanitized(4));
        System.out.println("t1[original 4..]  = " + t1.substringOriginal(4));
        System.out.println("t2[sanitized 4..] = " + t2.substringSanitized(4));
        System.out.println("t2[original 4..]  = " + t2.substringOriginal(4));
    }

    @Override
    public String toString() {
        return "TextSanitize [originalText=" + originalText + ", sanitizedText=" + sanitizedText + "]";
    }

}