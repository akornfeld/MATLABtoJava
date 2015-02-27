import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Ari
 */
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Simple program to
 *
 * @author Ari
 */
public class MATLABwordCount /* extends Application*/ {

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        // Start with args[0] being filename
        //String[] default_args = {"E:\\ari\\2012+ Carnegie\\Data\\Fluorescence\\functions"};//, "E:\\ari\\2012+ Carnegie\\Data\\Fluorescence"};
        String[] default_args = {"C:\\ari\\Data\\Fluorescence\\functions", "C:\\ari\\Data\\Fluorescence"};
        if (args.length == 0) {
            args = default_args;
            //System.out.println("Need one arg!");
            //System.exit(1);
        }
        String destDir = System.getProperty("user.dir");
        HashMap<String, Integer> words = new HashMap<>();

        /*
         String test = "end";
         System.out.println(words.getOrDefault(test, 0));
         words.put(test, 1);
         System.out.println(words.getOrDefault(test, 0));

         //String REanyWord = "\\b[^\\s()\\[\\]]+\\b";
         String REanyWord = "\\b[A-Za-z][A-Za-z0-9_]*";
         String manywords = "the.quick(brown) fox. the";
         ArrayList<String> result = REextractAll(manywords, REanyWord);
         for ( String word : result) {
         int wc = words.getOrDefault(word, 0) + 1;
         words.put(word, wc);
         }

         Map<String, Integer> byValue = sortByValues(words); 
         for(Map.Entry entry : byValue.entrySet() ) {
         System.out.println(entry.getKey() + ": " + entry.getValue());
         }
         //System.out.println(byValue);
         System.exit(0);
       
         Boolean result = test.matches(".*\\bend\\b.*");
         result = Pattern.matches("\\bend\\b", test);
         test = test.replaceAll("\\bend\\b", "}");
         test = "  asd %% asdf %";
         String[] cc = test.split("%", 2);
         test = test.replaceAll("([as]+)", "$1xx");
         test = "my code)";
         result = containsWord(test, "(code|continue)"); //|.*\\)$
         test = "   assert(false, 'Subclass must define this method')";
         test = test.replaceAll("assert\\s*[(]([^,]*),([^)]*)[)]", "assert $1 : $2;");
         String t2 = REextract(test, "^\\s*");
         test = "abc{1};";
         test = test.replaceAll("([^\\s])\\{(.*)\\}", "$1[$2]");
         /**/
        // if in JavaFX application:
        // File selectedFile = requestFileName((Window) null);
        //filename = selectedFile.getAbsolutePath();
        int codeLines = 0;
        int commentLines = 0;
        for (String sourceDir : args) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(sourceDir), "*.m")) {
                for (Path sourceFile : stream) {
                    int[] result;
                    System.out.println("Processing: " + sourceFile.getFileName());
                    result = countWords(sourceFile, destDir, words);
                    codeLines += result[0];
                    commentLines += result[1];
                }
            } catch (IOException x) {
                // IOException can never be thrown by the iteration.
                // In this snippet, it can // only be thrown by newDirectoryStream.
                //System.err.println(x);
                throw (x);
            }
        }

        //BufferedWriter writer = Files.newBufferedWriter(jfilePath, charset)
        //writer.write("");
        //writer.newLine();
        
        Map<String, Integer> byValue = sortByValues(words);
        for (Map.Entry entry : byValue.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        
        System.out.println();
        System.out.println("Code lines: " + codeLines);
        System.out.println("Comment lines: " + commentLines);

    }

    public static int[] countWords(Path mfilePath, String destDir, HashMap<String, Integer> words) throws IOException {
        //Path mfilePath = Paths.get(filename);
        String javaFilename = mfilePath.getFileName().toString().replaceFirst("\\.m$", ".java");
        Path jfilePath = Paths.get(destDir, javaFilename);

        int[] wc = {0, 0};
        int lineType;
        Charset charset = Charset.forName("US-ASCII");
        int lineNum = 0;
        try (BufferedReader reader = Files.newBufferedReader(mfilePath, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                // translate the line into Java syntax
                lineType = wordCountinFile(line, words);
                if (lineType >= 0) {
                    wc[lineType]++;
                }
            }

        } catch (IOException e) {
            //System.err.format("IOException: %s%n", x);
            throw e;
        }
        return wc;
    }

    /**
     * The "guts" of the translator: parse out a single line of code and and
     * make a good attempt to translate the syntax.
     * <P>
     * No attempt is made to span multiple lines or to translate built-in
     * functions.
     *
     * @param line
     * @param parseInfo
     * @return  0 = blank line; -1 = comment line; 1 = code line
     */
    static int wordCountinFile(String line, HashMap<String, Integer> words) {
        if (line.trim().isEmpty()) {
            return -1;
        } // else:

        // *********** PARSE CODE : COMMENT
        // 1. separte code from comments  (tolerating % inside quotes)
        String code = line;
        int cIdx = findChar(line, '%');
        if (cIdx >= 0) {
            code = code.substring(0, cIdx);
        }

        // regex for any word of code
        String REanyWord = "\\b[A-Za-z][A-Za-z0-9_]*";

        ArrayList<String> wordsInCode = REextractAll(code, REanyWord);
        for (String word : wordsInCode) {
            int wc = words.getOrDefault(word, 0) + 1;
            words.put(word, wc);
        }

        return (code.isEmpty() ? 1 : 0);  // -1 == comment line
    }

    /**
     * Find an "exposed" character in a line, honoring MATLAB syntax: look only
     * <BR> (a) outside <I>single</I> quotes (the only valid MATLAB quotes)<BR>
     * (b) unescaped (backslash) <BR>
     * and (c) if trackParens = true, outside parenthesized expressions. <BR>
     * <BR>
     * Thus findChar("f(a,b), c", ',', true) will return the second comma
     * <BR> and findChar("f(a,b), '%' % comment", "%") returns the second %
     *
     * @param line
     * @param target
     * @param trackParens
     * @return
     */
    static int findChar(String line, char target, boolean trackParens) {
        int idx;
        boolean insideQuotes = false;
        boolean isLiteral = false;
        int parens = 0;
        for (idx = 0; idx < line.length(); idx++) {
            // switch (  ) 
            char c = line.charAt(idx);
            if (isLiteral) {
                // skip the character and move to next char
                isLiteral = false;
            } else if (c == '\\') {
                isLiteral = true;
            } else if (trackParens && !insideQuotes && !isLiteral && c == '(') {
                parens++;
            } else if (trackParens && !insideQuotes && !isLiteral && c == ')') {
                parens--;
            } else if (c == '\'') { // || c == '"'
                insideQuotes = !insideQuotes;
            } else if (c == target && !insideQuotes && (!trackParens || parens == 0)) {
                break;
            }
        }
        return (idx < line.length() ? idx : -1);
    }

    static int findChar(String line, char target) {
        return findChar(line, target, false);
    }

    /**
     * Test if a string contains a word.
     *
     * @param text
     * @param word
     * @return
     */
    static boolean containsWord(String text, String word) {
        return text.matches(REword(word));
    }

    /**
     * Test if the first word matches a regular expression.
     *
     * @param text
     * @param word
     * @return
     */
    static boolean isFirstWord(String text, String word) {
        return text.matches(REfirstWord(word));
    }

    /**
     * Wrap a text in regex word markers
     *
     * @param text
     * @return
     */
    static String REword(String text) {
        return ".*\\b" + text + "\\b.*";
    }

    /**
     * Wrap a text in regex word markers anchored at the start of line.
     *
     * @param text
     * @return
     */
    static String REfirstWord(String text) {
        return "^\\s*" + text + "\\b.*";
    }

    /**
     * Implement grep pattern extraction. Odd that this isn't in the core
     * functionality. (and string is final, so can't be extended)
     *
     * @param text
     * @param regex
     * @return
     */
    static String REextract(String text, String regex) {
        // java doesn't have a simple pattern extractor
        Matcher matcher = Pattern.compile(regex).matcher(text);
        return (matcher.find() ? matcher.group() : "");
    }

    static ArrayList<String> REextractAll(String text, String regex) {
        // java doesn't have a simple pattern extractor
        Matcher matcher = Pattern.compile(regex).matcher(text);
        ArrayList<String> result = new ArrayList<>();

        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    /*
     * Java method to sort Map in Java by value e.g. HashMap or Hashtable
     * throw NullPointerException if Map contains null values
     * It also sort values even if they are duplicates
     */
    public static <K extends Comparable, V extends Comparable> Map<K, V> sortByValues(Map<K, V> map) {
        List<Map.Entry<K, V>> entries = new LinkedList<Map.Entry<K, V>>(map.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<K, V>>() {

            @Override
            public int compare(Entry<K, V> o1, Entry<K, V> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });

        //LinkedHashMap will keep the keys in the order they are inserted
        //which is currently sorted on natural ordering
        Map<K, V> sortedMap = new LinkedHashMap<K, V>();

        for (Map.Entry<K, V> entry : entries) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

//Read more: http://javarevisited.blogspot.com/2012/12/how-to-sort-hashmap-java-by-key-and-value.html#ixzz3ElX0qdSP
}
