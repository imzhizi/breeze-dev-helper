package com.imzhizi.breeze.devtools.spec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for @spec annotation in code comments
 * Format: @spec docs/specs/xxx-spec.md
 */
public final class SpecJumpParser {
    // Matches @spec followed by a path to a markdown file
    // Supports paths with letters, numbers, hyphens, underscores, slashes, and unicode characters (including Chinese)
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "@spec\\s+([\\w\\-/.\\u4E00-\\u9FFF]+\\.md)",
            Pattern.MULTILINE
    );

    private SpecJumpParser() {
    }

    /**
     * Check if the text contains a @spec annotation
     */
    public static boolean containsSpecAnnotation(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return SPEC_PATTERN.matcher(text).find();
    }

    /**
     * Extract the first @spec annotation path from text
     */
    public static SpecJumpTarget parse(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = SPEC_PATTERN.matcher(text);
        if (matcher.find()) {
            String path = matcher.group(1);
            return new SpecJumpTarget(path);
        }
        return null;
    }

    /**
     * Find all @spec annotation paths in text
     */
    public static SpecJumpTarget[] parseAll(String text) {
        if (text == null || text.isEmpty()) {
            return new SpecJumpTarget[0];
        }
        Matcher matcher = SPEC_PATTERN.matcher(text);
        java.util.ArrayList<SpecJumpTarget> targets = new java.util.ArrayList<>();
        while (matcher.find()) {
            targets.add(new SpecJumpTarget(matcher.group(1)));
        }
        return targets.toArray(new SpecJumpTarget[0]);
    }

    /**
     * Get the text range of the first @spec path within the given text
     */
    public static int[] findRange(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        Matcher matcher = SPEC_PATTERN.matcher(text);
        if (matcher.find()) {
            return new int[]{matcher.start(1), matcher.end(1)};
        }
        return null;
    }
}
