package com.imzhizi.breeze.devtools.uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BreezeJumpUriParser {
    public static final String SCHEME_PREFIX = "breeze-jump://";

    private static final Pattern URI_PATTERN = Pattern.compile(
            "^(?<class>[A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)*)(?:#(?<member>[^:]+))?(?::(?<line>\\d+))?$"
    );

    private BreezeJumpUriParser() {
    }

    public static boolean isBreezeJumpUri(String text) {
        return text != null && text.startsWith(SCHEME_PREFIX);
    }

    public static BreezeJumpTarget parse(String text) {
        if (!isBreezeJumpUri(text)) {
            return null;
        }

        String payload = text.substring(SCHEME_PREFIX.length()).trim();
        Matcher matcher = URI_PATTERN.matcher(payload);
        if (!matcher.matches()) {
            return null;
        }

        String classFqn = matcher.group("class");
        String member = matcher.group("member");
        String lineText = matcher.group("line");
        Integer lineNumber = lineText == null ? null : Integer.valueOf(lineText);
        return new BreezeJumpTarget(classFqn, member, lineNumber);
    }
}