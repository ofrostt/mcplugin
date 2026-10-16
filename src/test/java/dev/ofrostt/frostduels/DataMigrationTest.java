package dev.ofrostt.frostduels;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataMigrationTest {
    @TempDir Path plugins;
    @Test void preservesLegacyRecoveryAndDoesNotOverwriteCurrentData() throws Exception {
        Path source = plugins.resolve("FrostDuels"), target = plugins.resolve("Duels");
        Files.createDirectories(source.resolve("recovery"));
        Files.writeString(source.resolve("config.yml"), "old config");
        Files.writeString(source.resolve("recovery/player.yml"), "inventory");
        DataMigration.copyLegacy(target);
        assertEquals("inventory", Files.readString(target.resolve("recovery/player.yml")));
        assertEquals("inventory", Files.readString(source.resolve("recovery/player.yml")));
        Files.writeString(target.resolve("config.yml"), "new config");
        DataMigration.copyLegacy(target);
        assertEquals("new config", Files.readString(target.resolve("config.yml")));
    }
}
