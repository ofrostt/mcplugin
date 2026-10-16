package dev.ofrostt.frostduels;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;

final class DuelConfig {
    final YamlConfiguration yaml;
    final int expiry, cooldown, countdown, roundLimit, maxRounds, padding;
    final boolean protectLobby;
    DuelConfig(File file) throws Exception {
        yaml = new YamlConfiguration(); yaml.load(file);
        try (var input = java.util.Objects.requireNonNull(DuelConfig.class.getResourceAsStream("/config.yml"))) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)));
        }
        expiry = integer("request-expiry-seconds", 5, 600);
        cooldown = integer("request-cooldown-seconds", 0, 60);
        countdown = integer("countdown-seconds", 1, 30);
        roundLimit = integer("round-time-limit-seconds", 10, 7200);
        maxRounds = integer("max-rounds", 1, 1000);
        padding = integer("arena-padding-blocks", 2, 256);
        protectLobby = yaml.getBoolean("protect-lobby", true);
        rules(null);
        var overrides = yaml.getConfigurationSection("kit-rules");
        if (overrides != null) for (String id : overrides.getKeys(false)) {
            DuelStore.id(id);
            if (overrides.getConfigurationSection(id) == null) throw new IllegalArgumentException("Invalid kit rule override");
            rules(id);
        }
        if (yaml.getConfigurationSection("messages") == null) throw new IllegalArgumentException("Missing messages");
        // Defaults are explicitly checked by the plugin before accepting a reload.
    }
    Rules rules(String kit) {
        return Rules.load(yaml.getConfigurationSection("rules"), kit == null ? null : yaml.getConfigurationSection("kit-rules." + kit));
    }
    private int integer(String path, int min, int max) {
        int value = yaml.getInt(path, Integer.MIN_VALUE);
        if (value < min || value > max) throw new IllegalArgumentException(path + " must be in " + min + ".." + max);
        return value;
    }
    String text(String key) {
        String value = yaml.getString("messages." + key);
        if (value == null) throw new IllegalArgumentException("Missing message: " + key);
        if (key.equals("command-blocked") && value.equals("&cYou can only use /frostduels leave during a duel.")) return "&cUse the Leave Duel button to forfeit.";
        if (key.equals("prefix") && value.equals("&bFrostDuels &8» &r")) return "&bFrostMenu &8» &r";
        if (key.equals("bad-arguments") && value.equals("&cInvalid arguments. Use /frostmenu help.")) return "&bⓘ Invalid arguments! Use: /frostmenu help";
        if (key.equals("help") && value.equals("&7/duel <player> — respond to challenges and leave matches using the chat buttons.")) return "&b" + value.substring(2);
        if (key.equals("admin-help") && value.equals("&7/frostmenu kit save|load|delete|list [id], /frostmenu arena create|delete|list|setspawn|snapshot|reset [id], /frostmenu setlobby, /frostmenu stop <player>, /frostmenu recover <player>, /frostmenu reload")) return "&b" + value.substring(2);
        return value;
    }
}
