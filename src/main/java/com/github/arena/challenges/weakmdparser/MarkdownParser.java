package com.github.arena.challenges.weakmdparser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.nio.CharBuffer;

/**
 * A simple parser for custom in-house markdown syntax.
 *
 * Class with only default constructor:
 * <code>MarkdownParser markdownParser = new MarkdownParser();</code>
 *
 * Class exposes only one function:
 * <code>String parse(String markdown)</code>
 * This function never throws.
 *
 * @see MarkdownParser.parse
 */
public class MarkdownParser {

    /**
     * Function that parses custom markdown into equivalent html.
     *
     * @param markdown document
     * @return equivalent html
     */
    public String parse(String markdown) {
        CharBuffer movingView = CharBuffer.wrap(markdown);

        return parseNormalMode(movingView);
    }

    //
    // Notes for extending the markdown syntax parsing rules:
    //
    // This form of parsing is based on functions,
    // to provide a natural stack and structure.
    //
    // Every function is based on regex expressions
    // @see java.util.regex.Pattern
    // @see java.util.regex.Matcher
    //
    // and a movingView
    // @see java.nio.CharBuffer
    //
    // Using Pattern regexes allows us to compile them.
    // By using CharBuffer we can move around and "slice" strings
    // without memory copying overhead.
    //
    // This also allows us to define both character and line based parsers.
    //
    //
    // ! Note that movingView is "shared" by all functions (unless copied) !
    //
    // You can save movingView position if You may need to restore it:
    // ```
    // int savedPosition = movingView.position();
    // ...
    // movingView.position(savedPosition);
    // ```
    //
    // ! Don't modify movingView before You are certain that
    // this parser will not fail OR be sure to restore it !
    //
    //
    // All regex rule names should fallow the convention: regex[ExpresionName]
    //
    //
    // There are 3 types of functions:
    // * mode-parsers;
    // Naming convention: parse[ModeName]Mode
    // * expression-parsers;
    // Naming convention: tryParsing[ExpresionName]
    // * workers // for the lack of a better name;
    // Naming convention: use meaningful name without word "parser"
    //
    //
    // Workers are functions that:
    // - parse the current movingView;
    // - decide if they need to do anything;
    // - potentially compute some value;
    // - move the movingView;
    // - return the computed value (can be any type);
    //
    // ! Keep them simple !
    //
    // Consider them for operations like:
    // * skipping empty lines;
    // * skipping white space;
    // * calculating indentation levels;
    //
    //
    // Expression-parsers are functions that:
    // - parse the current movingView;
    // - decide if they need to do anything;
    // - compute necessary values;
    // - may call other expression-parsers;
    // - move movingView;
    // - return the parsing result (String);
    //
    // ! Return null if failed !
    //
    // Consider them for operations like:
    // * parsing a line;
    // * parsing a paragraph;
    // * parsing a list item;
    //
    //
    // Mode-parsers are functions that:
    // - combine alternative parsers withe a context;
    // - control precedence of individual parsers;
    // - call workers and parsers;
    // - return the parsing result (String);
    //
    // ! To make sure that You should enter a new mode-parser
    // put a testing parser at the beginning. !
    // @see parseUnorderedListMode for an example.
    //
    // Consider them for operations like:
    // * parsing whole markdown;
    // * parsing a list;
    // * using context dependent parsers
    // (like indentation based lists or graphs)
    //
    //
    // Potential extensions:
    //
    // This style was chosen as it allows great extensibility
    // and easy maintenance while being concise (base written in about 1:40h).
    //
    // However it may happen that it will not be enough or additional features
    // like warnings or errors will be needed.
    //
    // In such a case it may be worth considering for parsers to return
    // more complex classes instead of String's, or be extended with more logic.
    // If possible contexts and stacks should be contained in mode-parsers.
    // Note that this alone will not complicate the structure very much
    // and should be enough for most grammars.
    //
    // For errors, warnings, etc., we can record the absolute position from
    // movingView.position() and calculate line and column numbers if needed.
    //
    // ! Note that this implementation is not prepared for stream parsing
    // and expects a whole text to be present. !
    //

    private String parseNormalMode(CharBuffer movingView) {

        String result = "";
        String out = "";

        while (movingView.length() > 0) {
            while (skipEmptyLine(movingView))
                ;

            if ((out = tryParsingHeading(movingView)) != null) {
                result += out;
                continue;
            }
            if ((out = parseUnorderedListMode(movingView)) != null) {
                result += out;
                continue;
            }
            if ((out = tryParsingParagraph(movingView)) != null) {
                // place to put collapsing paragraphs
                // (note that we need to know if we've skipped an empty line)
                result += out;
                continue;
            }
            System.err.println("Unable to parse the rest: " + movingView.toString());
            break;
        }

        return result;
    }

    private boolean skipEmptyLine(CharBuffer movingView) {
        if (movingView.charAt(0) != '\n')
            return false;
        movingView.position(movingView.position() + 1);
        return true;
    }

    private final static Pattern regexFullLine = Pattern.compile("^.*(\n|$)");
    private final static Pattern regexStrongItalic = Pattern.compile("___(.+)___");
    private final static Pattern regexStrong = Pattern.compile("__(.+)__");
    private final static Pattern regexItalic = Pattern.compile("_(.+)_");

    private String tryParsingLine(CharBuffer movingView) {
        Matcher line = regexFullLine.matcher(movingView);
        if (!line.find())
            return null;

        String output = line.group().trim();

        output = regexStrongItalic.matcher(output)
                .replaceAll(match -> "<strong><em>" + match.group(1) + "</em></strong>");
        output = regexStrong.matcher(output)
                .replaceAll(match -> "<strong>" + match.group(1) + "</strong>");
        output = regexItalic.matcher(output)
                .replaceAll(match -> "<em>" + match.group(1) + "</em>");

        movingView.position(movingView.position() + line.end());

        return output;
    }

    private String tryParsingParagraph(CharBuffer movingView) {
        String out = tryParsingLine(movingView);
        if (out == null)
            return null;
        return "<p>" + out + "</p>";
    }

    private final static Pattern regexHeadings = Pattern.compile("^#+ ");

    private String tryParsingHeading(CharBuffer movingView) {
        Matcher prefix = regexHeadings.matcher(movingView);
        if (!prefix.find() || prefix.hitEnd())
            return null;

        int savedPosition = movingView.position();
        movingView.position(movingView.position() + prefix.end());

        int level = prefix.end() - 1;
        String out = tryParsingLine(movingView);
        if (out == null) {
            movingView.position(savedPosition);
            return null;
        }
        return "<h" + level + ">" + out + "</h" + level + ">";
    }

    private String parseUnorderedListMode(CharBuffer movingView) {
        String out = tryParsingUnorderedListItem(movingView);
        if (out == null)
            return null;

        String result = "<ul>" + out;

        while (movingView.length() > 0) {
            while (skipEmptyLine(movingView))
                ;

            if ((out = tryParsingUnorderedListItem(movingView)) != null) {
                result += out;
                continue;
            }

            break;
        }

        return result + "</ul>";
    }

    private final static Pattern regexListItem = Pattern.compile("^\\* ");

    private String tryParsingUnorderedListItem(CharBuffer movingView) {
        Matcher prefix = regexListItem.matcher(movingView);
        if (!prefix.find())
            return null;

        int savedPosition = movingView.position();
        movingView.position(movingView.position() + prefix.end());

        String out = tryParsingLine(movingView);
        if (out == null) {
            movingView.position(savedPosition);
            return null;
        }
        return "<li>" + out + "</li>";
    }

}
