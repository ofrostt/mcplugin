package dev.ofrostt.frostmenu;

import java.io.IOException;
import java.nio.file.*;

final class MenuMigration {
    static void clearPresetMenu(Path file) throws IOException {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try { yaml.load(file.toFile()); }
        catch (Exception ex) { throw new IOException("Cannot read menu", ex); }
        if (yaml.getInt("menu-version", 0) >= 3) return;
        Path backup = file.resolveSibling("menu-before-blank-kits.yml");
        if (!Files.exists(backup)) Files.copy(file, backup);
        var categories = yaml.getConfigurationSection("categories");
        if (categories != null) for (String id : categories.getKeys(false)) {
            yaml.set("categories." + id + ".gamemodes", null);
            yaml.createSection("categories." + id + ".gamemodes");
        }
        yaml.set("menu-version", 3);
        yaml.set("menus.show-lore", false);
        yaml.set("menus.hide-item-tooltips", true);
        yaml.set("menus.navigation.selected-lore", "");
        yaml.save(file.toFile());
    }

    static void copyLegacy(Path dataFolder) throws IOException {
        Path destination = dataFolder.resolve("menu.yml");
        Path legacy = dataFolder.getParent().resolve("FrostMenu/config.yml");
        if (!Files.exists(destination) && Files.isRegularFile(legacy)) Files.copy(legacy, destination);
    }
}
