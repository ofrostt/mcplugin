package dev.ofrostt.frostmenu;

import java.io.IOException;
import java.nio.file.*;

final class MenuMigration {
    static void copyLegacy(Path dataFolder) throws IOException {
        Path destination = dataFolder.resolve("menu.yml");
        Path legacy = dataFolder.getParent().resolve("FrostMenu/config.yml");
        if (!Files.exists(destination) && Files.isRegularFile(legacy)) Files.copy(legacy, destination);
    }
}
