package dev.ofrostt.frostmenu;

import java.util.Map;
import java.util.regex.Pattern;

/** Pure validation shared by configuration loading and untrusted dialog responses. */
final class DuelRules {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.:-]{1,80}");

    static String token(String value) {
        if (value == null || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException("Kit/map identifiers must use letters, digits, _, ., :, or - (1–80 characters)");
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

    static String command(String template) {
        String result = template.startsWith("/") ? template.substring(1) : template;
        if (result.isBlank() || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0
                || !result.matches("[A-Za-z0-9_.:-]+(?: .*)?")) {
            throw new IllegalArgumentException("Command must be a single command line");
        }
        String root = result.split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (root.equals("duel") || root.equals("frostmenu") || root.endsWith(":frostmenu")
                || root.equals("frostmenu:duel")) {
            throw new IllegalArgumentException("Use Cloud's namespaced duel command to avoid menu recursion");
        }
        String stripped = result;
        for (String key : new String[]{"player", "target", "kit", "rounds", "map"}) {
            stripped = stripped.replace("%" + key + "%", "");
        }
        if (stripped.contains("%")) throw new IllegalArgumentException("Unknown command placeholder");
        return result;
    }

    static String expand(String template, Map<String, String> values) {
        String result = template;
        for (var entry : values.entrySet()) result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        return result;
    }
}
