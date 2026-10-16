package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MenuMigrationTest {
    @TempDir Path plugins;
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
