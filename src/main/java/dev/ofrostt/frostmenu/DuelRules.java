package dev.ofrostt.frostmenu;

import java.util.Map;
import java.util.regex.Pattern;

/** Pure validation shared by configuration loading and untrusted dialog responses. */
final class DuelRules {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,48}");

    static String token(String value) {
        if (value == null || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Kit/map identifiers must use letters, digits, _, or - (1–48 characters)");
        }
        return value;
    }

    static int rounds(Float value, int min, int max) {
        if (value == null || !Float.isFinite(value) || value != Math.rint(value)
                || value < min || value > max) {
            throw new IllegalArgumentException("Invalid rounds response");
        }
        return value.intValue();
    }

    static String expand(String template, Map<String, String> values) {
        String result = template;
        for (var entry : values.entrySet()) result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        return result;
    }
}
