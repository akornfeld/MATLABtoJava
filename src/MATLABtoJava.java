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
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Simple program to convert MATLAB syntax to 
 *
 * @author Ari
 */
public class MATLABtoJava /* extends Application*/ {

    static class ParseData {

        public Deque<String> blockStack = null;
        public String objName = "";
        public Deque<String> fnName;
        public Deque<String> returnVar;

        public ParseData(Deque<String> blockStack) {
            this.blockStack = blockStack;
            this.fnName = new ArrayDeque<>();
            this.returnVar = new ArrayDeque<>();

        }
    }

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        // Start with args[0] being filename
        if (args.length == 0) {
            System.out.println("Need one arg!");
            System.exit(1);
        }
        String destDir = System.getProperty("user.dir");

        /*
        System.out.println("\\bisa\\b\\s*\\(([^,]*),\\s*\"([^\"]*)\"\\s*\\)");
        System.exit(0);
         String test = " end";
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
        for (String sourceDir : args) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(sourceDir), "*.m")) {
                for (Path sourceFile : stream) {
                    System.out.println("Processing: " + sourceFile.getFileName());
                    translateFile(sourceFile, destDir);
                }
            } catch (IOException x) {
                // IOException can never be thrown by the iteration.
                // In this snippet, it can // only be thrown by newDirectoryStream.
                //System.err.println(x);
                throw (x);
            }
        }

    }

    static File requestFileName(Window parentWin) {
        File selectedFile;
        String currentPath = System.getProperty("user.dir");

        // JavaFX file chooser is prettier but has annoying side behaviors:
        //   showOpenDialog requires an existing file
        //   showSaveDialog asks "Do you want to overwrite yes/no?"
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select Log File");
        fileChooser.setInitialDirectory(new File(currentPath));
        fileChooser.getExtensionFilters().addAll(new javafx.stage.FileChooser.ExtensionFilter("xSpect log files (*.h5)", "*.h5"), new javafx.stage.FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"), new FileChooser.ExtensionFilter("All Files", "*.*"));
        selectedFile = fileChooser.showSaveDialog(parentWin);
        return selectedFile;
    }

    public static void translateFile(Path mfilePath, String destDir) throws IOException {
        //Path mfilePath = Paths.get(filename);
        String javaFilename = mfilePath.getFileName().toString().replaceFirst("\\.m$", ".java");
        Path jfilePath = Paths.get(destDir, javaFilename);

        Charset charset = Charset.forName("US-ASCII");
        int lineNum = 0;
        try (BufferedReader reader = Files.newBufferedReader(mfilePath, charset);
                BufferedWriter writer = Files.newBufferedWriter(jfilePath, charset)) {
            String line;
            ParseData parseInfo = new ParseData(new ArrayDeque<>());
            while ((line = reader.readLine()) != null) {
                lineNum++;
                // translate the line into Java syntax
                String result = translateLine(line, parseInfo);

                writer.write(result);
                writer.newLine();
            }
        } catch (IOException e) {
            //System.err.format("IOException: %s%n", x);
            throw e;
        }
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
     * @return
     */
    static String translateLine(String line, ParseData parseInfo) {
        if (line.trim().isEmpty()) {
            return line;
        } // else:

        String newReturnVar = "";

        Deque<String> blockStack = parseInfo.blockStack;
        String context = blockStack.peek();
        if (context == null) { // so function tests don't break when reading function files (first line is "function"
            context = "";
        }

        // **** GLOBAL REPLACEMENTS (DO EVEN IN COMMENTS CODE)
        // note: this also converts "missing" args:  function x = mycallback(use_this, ~, ~)
        line = line.replace('~', '!');

        // indexing: replace xx{n} => xx[n]  (cell indexing)
        line = line.replaceAll("([^\\s])\\{([^}]*)\\}", "$1[$2]");

        // [] => null
        line = line.replace("[]", "null");

        //  numel(x) --> x.length  (also length(x) --> x.length) (fails on nested functions: length(a())
        line = line.replaceAll("(numel|length)\\s*\\(\\s*([^)]*)\\s*\\)", "$2.length");

        //  isempty(x) --> x.isEmpty() 
        line = line.replaceAll("isempty\\s*\\(\\s*([^)]*)\\s*\\)", "$1.isEmpty()");

        // sprintf -> String.format
        line = line.replace("sprintf", "String.format");

        // replace many array constant decl [a,b,c] with {a, b, c} 
        //   note: doesn't handle multiline
        line = line.replaceAll("=\\s*\\[(.*)\\]", "={$1}");

        // *********** PARSE CODE : COMMENT
        // 1. separte code from comments  (tolerating % inside quotes)
        String code = line;
        int cIdx = findChar(line, '%');
        if (cIdx >= 0) {
            code = code.substring(0, cIdx);
        }
        String indent = REextract(code, "^\\s*");
        String whitespace = REextract(code.substring(indent.length()), "\\s*$");
        
        // Translate various punctuation marks (needs to be before single-quote translation)
        //   note, this still fails for any even number of transposes on the same line!
        int nakedQuoteIdx = findChar(code, '\'');
        if (nakedQuoteIdx > 0) { // not sure what it means to be the first char on the line!
            code = code.substring(0, nakedQuoteIdx) + ".transpose()" + code.substring(nakedQuoteIdx+1);
        }
        code = code.replace("\"", "\\\"");
        code = code.replace('\'', '"');
        code = code.replace("\"\"", "\\'"); //(need to be after single-quote translation) '' was converted to "" (but if we do this first, '' -> \' -> \"
        code = code.replace("...", "");
        code = code.trim();

        // ********** CODE MODS
        // replace MATLAB's explicit object args in methods: (objName is only set when appropriate
        if (!parseInfo.objName.isEmpty() && !code.startsWith("function")) {
            code = code.replaceAll("\\b" + parseInfo.objName + "\\b", "this");
        }

        // convert "asserts", warnings & errors to Java idioms.
        if (code.matches("^(assert|warning|error).*")) {
            String fname = REextract(code, "^(assert|warning|error)");
            //  strip args out of "assert( args );"
            String args = code.replaceFirst("^" + fname + "\\s*\\((.*)\\)\\s*;?", "$1");
            int separator = findChar(args, ',', true); // is there a comma between args
            switch (fname) {
                case "assert":
                    if (separator >= 0) {
                        // assert has at least two args: assert( a, b ... )
                        String secondArg = args.substring(separator + 1);
                        int sep = findChar(secondArg, ',', true);
                        if (sep >= 0) {
                            // at least three args: assert( a, fmt, b1, b2, ...)
                            secondArg = "String.format(" + secondArg + ")";
                        }
                        args = args.substring(0, separator) + " : " + secondArg;
                    }
                    code = "assert " + args + ";";
                    break;
                case "warning":
                    if (separator > 0) {
                        args = String.format("String.format(%s)", args);
                    }
                    code = "new RuntimeException(\"Warning: \" + " + args + ").printStackTrace();";
                    //new RuntimeException("your message here").printStackTrace();
                    break;
                case "error":
                    if (separator > 0) {
                        args = "String.format(" + args + ")";
                    }
                    code = "throw new Exception(" + args + ");";
                    break;
                default:
                    throw new IllegalArgumentException("Impossible!!!");
            }
        }

        // change size(mat, 1) --> mat.dim(1)  [problem is that size is often already taken in Java collections)
        if (code.matches(".*\\bsize\\s*\\(.*")) {
            String[] parts = code.split("size\\s*\\(");
            // we can have more than one match per line, so iterate of the parts (and hope they're not nested):
            code = parts[0];
            for (int idx = 1; idx < parts.length; idx++) {
                String args = parts[idx];
                String obj;
                int separator = findChar(args, ',', true); // find the comma between args
                if (separator >= 0) {
                    obj = args.substring(0, separator).trim();
                    args = args.substring(separator + 1).trim();
                } else {
                    int closingParen = findChar(args, ')', true);
                    obj = args.substring(0, closingParen).trim();
                    args = args.substring(closingParen).trim();  // leave the ')' in!
                }
                code = code + obj + ".dims(" + args; // args includes the trailing ';'
            }
        }
        
        //  isa(a, "class") --> a instanceof "class"
        code = code.replaceAll("\\bisa\\b\\s*\\(([^,]*),\\s*\"([^\"]*)\"\\s*\\)", "$1 instanceof $2");

        String comment = (cIdx < 0 ? "" : "//" + line.substring(cIdx + 1));

        // process comments
        //  the only thing is to convert %% -> //------- \n // comment
        if (code.isEmpty() && !comment.isEmpty() && comment.startsWith("//%")) {
            // replace double-comment with ------
            String sectionBreak = "//-------------------------------------------------------------------------------";
            comment = String.format("%s%n%s//%s", sectionBreak, indent, comment.substring(3)); // note: code is just the indent, since it's all whitespace
        }

        // Code blocks:
        String[] blockStarts = {"classdef", "function", "try", "catch", "elseif", "else", "if", "while", "switch", "for"};
        // handle all block/flow keywords
        for (String token : blockStarts) {
            if (isFirstWord(code, token)) {
                // parse the line into indent + token + subcode + (trailing) whitespace
                String subcode = code.replaceFirst(token, "").trim();

                // add parentheses to the if/while etc statement
                if (token.equals("catch") && subcode.isEmpty()) {
                    // MATLAB allows an isolated catch statement, lets add the exception clause
                    subcode = "(Exception e)";
                } else if (!token.matches("classdef|function|else|try")
                        && !subcode.isEmpty() && subcode.charAt(0) != '(') {
                    // note MATLAB allows semicolons at the end of these sub-statements; Java doesn't.
                    subcode = "(" + subcode.replaceFirst(";$", "") + " )";
                }

                // special cases?
                if (token.equals("for")) {
                    //  for (index = values) ==> for (double index : values)
                    String indexValues[] = subcode.split("=", 2);
                    String element = indexValues[0].substring(1).trim(); // substring(1) skips the initial paren (which is now guaranteed)
                    String iterable = indexValues[1].replaceFirst("\\)$", "");
                    if (iterable.contains(":")) {
                        String[] startStop = iterable.split(":");
                        String startVal = startStop[0];
                        String stopVal = (startStop.length == 2 ? startStop[1] : startStop[2]);
                        String iter = (startStop.length == 2 ? "++" : " += " + startStop[1]);
                        String comparator = (iter.contains("-") ? " >= " : " <= "); // are we stepping down (-) or up?
                        subcode = "(int " + element + " = " + startVal + "; " + element + comparator + stopVal + "; " + element + iter + " )";
                    } else {
                        subcode = "(double " + element + ":" + iterable + ")";
                    }
                }

                // add "{" to all open blocks
                switch (token) {
                    case "else":
                    case "catch":
                        code = "} " + token + " " + subcode + " {";
                        break;
                    case "elseif":
                        code = "} else if " + subcode + " {";
                        break;
                    case "classdef":
                        code = "public class " + subcode.replaceAll("\\s*<\\s*", " extends ") + " {";
                        break;
                    case "function":
                        // function result = name(args) => public static Type name(args)
                        // also see further special handling, just below this switch
                        String level = (context.equals("static") ? "static " : "");
                        String scope = (context.equals("protected") ? "protected " : "public ");
                        if (context.matches("methods|protected")) {
                            // remove the first arg of instance methods and record its name
                            //String methodArgs = subcode.substring(subcode.indexOf('(')+1);
                            parseInfo.objName = REextract(subcode, "\\([^,)]*").substring(1); // store the first arg name
                            subcode = subcode.replaceFirst("\\(([^,)]*\\,?)", "("); // delete the first arg
                        } else {
                            parseInfo.objName = "";
                        }

                        // block out the return value? (Needs to be a type instead)
                        final String returnType = "Matrix";
                        if (subcode.contains("=")) {
                            newReturnVar = REextract(subcode, "^\\s*[^\\s=]*");
                            // replace the return value with the default return type: Matrix
                            subcode = subcode.replaceAll("(^[^=]*)=", level + returnType + " ");
                            //subcode = subcode.replaceFirst("\\s*=\\s*", level + " ");
                        } else {
                            subcode = level + "void " + subcode;
                            newReturnVar = "";
                        }
                        code = scope + subcode + " {";
                        // add a declaration for the return value on a new line: Matrix retval;
                        if (!newReturnVar.isEmpty()) {
                            code += String.format("%n%s   %s %s=null;", indent, returnType, newReturnVar);
                        }
                        break;
                    default:
                        code = token + " " + subcode + " {";
                        break;
                }

                // add the block to the stack (else & catch are special since they both close and open a block; context doesn't change
                if (token.equals("function") && context.equals("function")) {
                    // MATLAB doesn't require "end" between functions
                    //  so add the } here and don't add a new "function" to the context stack
                    String returnCode = getReturnCode(parseInfo, indent); //  "return xxx; \n   "
                    code = returnCode + String.format("%s}%n", indent) + code;

                } else if (!token.matches("else.*|catch")) {
                    blockStack.push(token);
                    context = token;
                }

                // we need to set it after (possibly) get the old return code above.
                if (token.equals("function")) {
                    parseInfo.returnVar.push(newReturnVar);
                }
                break;  // a line can have only one token (?)
            }

        }

        // "case" (inside switch statement)
        if (isFirstWord(code, "case")) {
            String subcode = code.replaceFirst("case", code).trim();
            String newCode = "";
            if (context.equals("case")) {
                // add a break
                newCode = String.format("break;%n") + indent;
            } else {
                // first "case":
                blockStack.push("case");
            }
            code = newCode + "case " + subcode + ":";
        }

        // "otherwise" (  switch () case case otherwise end  )
        if (isFirstWord(code, "otherwise")) {
            String newCode = "";
            if (context.equals("case")) {
                // add a break
                newCode = String.format("break;%n") + indent;
                blockStack.pop();
            }
            code = newCode + "default:";
        }

        // handle extraneous code (parts of classdef blocks) note: we could do something with Constant too?
        if (isFirstWord(code, "(properties|methods)")) {
            String token = REextract(code, "properties|methods");
            code = "//" + code; // comment out non-Java compatible code
            if (containsWord(code, "Static")) {
                blockStack.push("static");
            } else if (containsWord(code, "protected")) {
                blockStack.push("protected");
            } else {
                blockStack.push(token);
            }
        }

        // add semicolons, poss. a bit too aggressive.
        if ((isFirstWord(code, "(break|continue|return)") || code.matches(".*\\)$"))
                && !code.contains(";")) {
            code = code + ";";
        }

        // "end" - convert to "}" and pop the current context off of the stack
        // this should probably be the last clause, since we change the context
        if (isFirstWord(code, "end")) {
            // end is a block end, except for "properties" and "methods" which don't belong in Java
            if (context.matches("properties|methods|static|protected")) {
                code = "//" + code;  // just remove "end"
            } else {
                // for all others: replace "end" with "}"
                code = code.replaceAll(REword("end"), "}");

            }

            // if terminating a function:  prepend return code, if necessary
            //   note: this never coincides with setting the return value, which happens on a "function" line
            if (context.equals("function")) {
                //  "return xxx; \n   "  (& do a little sleight of hand to indent the return statement
                String returnCode = getReturnCode(parseInfo, indent);
                code = (returnCode.isEmpty() ? "" : "   " + returnCode) + code;
            }

            // now remove the current context from the stack. 
            //    switch + case blocks may need two elements removed
            if (blockStack.size() > 0) {
                String oldContext = blockStack.pop();
                if (oldContext.equals("case")) {
                    blockStack.pop(); // remove the switch as well
                }
                // context = blockStack.peek();
            } else {
                // top of the parse tree (function file with additional functions)
                // context = "";
            }
        }

        //ParsedLine result = new ParsedLine(code + comment, blockStack);
        return (indent + code + whitespace + comment);
    }

    static String getReturnCode(ParseData parseInfo, String indent) {
        String returnCode = "";
        String returnVar = parseInfo.returnVar.pop();
        if (!returnVar.isEmpty()) {
            returnCode = "return " + returnVar + String.format(";%n%s", indent);
        }
        return returnCode;
    }

    /**
     * Find an "exposed" character in a line, honoring MATLAB syntax: look only
     * <BR> (a) outside <I>single</I> quotes (the only valid MATLAB quotes)
     * <BR> (b) unescaped (backslash)
     * <BR> and (c) if trackParens = true, outside parenthesized expressions.
     * <BR>
     * <BR>
     * Thus findChar("f(a,b), c", ',', true) will return the second comma
     * <BR> and findChar("f(a,b), '%' % comment", "%") returns the second %
     * <BR> Two special cases are allowed:
     * <BR> 1. find a closing paren in "arg1, arg2)"
     * <BR> 2. find a singleton quote (transpose)
     *
     * @param line
     * @param target
     * @param trackParens
     * @return
     */
    static int findChar(String line, char target, boolean trackParens) {
        int pos, lastQuotePos = -1;
        boolean insideQuotes = false;
        boolean isLiteral = false;
        int parens = 0;
        for (pos = 0; pos < line.length(); pos++) {
            // switch (  ) 
            char c = line.charAt(pos);
            if (isLiteral) {
                // skip the character and move to next char
                isLiteral = false;
            } else if (c == '\\') {
                isLiteral = true;
            } else if (c == '\'') { // || c == '"'
                insideQuotes = !insideQuotes;
                lastQuotePos = pos;
            } else if (trackParens && !insideQuotes && !isLiteral && c == '(') {
                parens++;
            } else if (trackParens && !insideQuotes && !isLiteral && c == ')') {
                parens--;
                // special case to find a closing ')' for "arg1, arg2)"
                if (c == target && parens < 0 & !insideQuotes && !isLiteral) {
                    break;
                }
            } else if (c == target && !isLiteral && !insideQuotes && (!trackParens || parens <= 0)) {
                break;
            }
        }
        if (pos < line.length()) {
            return pos;
        } else if (target == '\'' && insideQuotes) {
            // a singleton quote (most likely a transpose char)
            return lastQuotePos;
        } else {
            return -1;
        }
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

}
