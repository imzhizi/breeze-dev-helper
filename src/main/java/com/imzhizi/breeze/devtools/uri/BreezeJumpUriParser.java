package com.imzhizi.breeze.devtools.uri;

import com.imzhizi.breeze.devtools.settings.BreezeSettings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BreezeJumpUriParser {
    /** Default scheme, used as fallback when settings are unavailable. */
    public static final String DEFAULT_SCHEME_PREFIX = "breeze-jump://";

    private static final Pattern URI_PATTERN = Pattern.compile(
            "^(?<class>[A-Za-z_][\\w$]*(?:\\.[A-Za-z_][\\w$]*)*)(?:#(?<member>[^:]+))?(?::(?<line>\\d+))?$"
    );

    private BreezeJumpUriParser() {
    }

    /** Returns the currently configured scheme prefix. */
    public static String getSchemePrefix() {
        try {
            String scheme = BreezeSettings.getInstance().breezeJumpScheme;
            if (scheme != null && !scheme.isBlank()) {
                return scheme;
            }
        } catch (Exception ignored) {
            // Service may not be available during early init
        }
        return DEFAULT_SCHEME_PREFIX;
    }

    public static boolean isBreezeJumpUri(String text) {
        return text != null && text.startsWith(getSchemePrefix());
    }

    public static BreezeJumpTarget parse(String text) {
        if (!isBreezeJumpUri(text)) {
            return null;
        }

        String payload = text.substring(getSchemePrefix().length()).trim();
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