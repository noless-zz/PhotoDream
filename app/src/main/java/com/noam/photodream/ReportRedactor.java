package com.noam.photodream;

import java.util.regex.Pattern;

/**
 * Removes anything private from the text of a problem report before it is shared:
 * sign-in tokens, e-mail addresses, photo file names and content/file URIs.
 * Pure Java (no Android), unit-tested. When in doubt it removes too much rather than too little.
 */
public final class ReportRedactor {

    private ReportRedactor() { }

    private static final Pattern URI = Pattern.compile("(?i)\\b(?:content|file)://\\S*");
    private static final Pattern PHOTO_NAME =
            Pattern.compile("(?i)[^\\s\"'<>|]*\\.(?:jpe?g|png|webp|heic|heif|gif|bmp|dng)\\b");
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)\\b(access_token|refresh_token|id_token|token|code|code_verifier|client_secret|authorization|password|api[_-]?key)"
                    + "(\"?\\s*[:=]\\s*\"?)[^\\s&\",}]+");
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern LONG_OPAQUE = Pattern.compile("\\b[A-Za-z0-9_-]{40,}\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    public static String redact(String text) {
        if (text == null) return "";
        String s = URI.matcher(text).replaceAll("[uri]");
        s = PHOTO_NAME.matcher(s).replaceAll("[photo]");
        s = BEARER.matcher(s).replaceAll("$1[token]");
        s = KEY_VALUE.matcher(s).replaceAll("$1$2[token]");
        s = JWT.matcher(s).replaceAll("[token]");
        s = LONG_OPAQUE.matcher(s).replaceAll("[token]");
        s = EMAIL.matcher(s).replaceAll("[email]");
        return s;
    }
}
