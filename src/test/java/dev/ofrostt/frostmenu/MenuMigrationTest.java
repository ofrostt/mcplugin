package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MenuMigrationTest {
    @TempDir Path plugins;
    @Test void clearsOldKitIconsOnceAndRetainsBackupAndNewlyAddedKits() throws Exception {
        Path file = plugins.resolve("menu.yml");
        Files.writeString(file, "categories:\n  custom:\n    title: My Heading\n    gamemodes:\n      old:\n        kit: OldKit\n");
        String original = Files.readString(file);
        MenuMigration.clearPresetMenu(file);
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
        assertTrue(yaml.getConfigurationSection("categories.custom.gamemodes").getKeys(false).isEmpty());
        assertEquals("My Heading", yaml.getString("categories.custom.title"));
        assertEquals(original, Files.readString(plugins.resolve("menu-before-blank-kits.yml")));
        yaml.set("categories.custom.gamemodes.new.kit", "MyKit"); yaml.save(file.toFile());
        MenuMigration.clearPresetMenu(file);
        assertEquals("MyKit", org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile()).getString("categories.custom.gamemodes.new.kit"));
    }
    @Test void copiesLegacyMenuWithoutRemovingOldFileOrOverwritingNewConfiguration() throws Exception {
        Path old = plugins.resolve("FrostMenu/config.yml"), folder = plugins.resolve("FrostDuels");
        Files.createDirectories(old.getParent()); Files.createDirectories(folder);
        Files.writeString(old, "custom: original\n"); MenuMigration.copyLegacy(folder);
        assertEquals("custom: original\n", Files.readString(folder.resolve("menu.yml")));
        assertTrue(Files.exists(old));
        Files.writeString(folder.resolve("menu.yml"), "custom: updated\n"); MenuMigration.copyLegacy(folder);
        assertEquals("custom: updated\n", Files.readString(folder.resolve("menu.yml")));
    }
}
