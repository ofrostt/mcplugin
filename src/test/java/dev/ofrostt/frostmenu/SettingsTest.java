package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class SettingsTest {
    @TempDir Path temp;
    Path file;

    @BeforeEach void setUp() throws Exception {
        MockBukkit.mock();
        file = temp.resolve("config.yml");
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            Files.copy(input, file);
        }
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void defaultConfigLoadsAllSampleCategoriesAndExactKitIds() throws Exception {
        Settings settings = new Settings(file.toFile());
        assertEquals(2, settings.categories.size());
        assertEquals(12, settings.categories.stream().mapToInt(c -> c.modes().size()).sum());
        var elytra = settings.categories.getFirst().modes().stream().filter(m -> m.id().equals("Elytra-IO")).findFirst().orElseThrow();
        assertEquals("Elytra-IO", elytra.kit()); // No accidental -IO-IO suffix.
        assertEquals("random", settings.arenas.getFirst().id());
    }

    @Test void duplicateAndOutOfRangeSlotsAreRejected() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("categories.mctiers-io.gamemodes.Sword.slot", 44);
        config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
        config.set("categories.mctiers-io.gamemodes.Sword.slot", 55);
        config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
    }

    @Test void extraMapsRequireAnExplicitCloudMapIntegration() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("maps.arena1", "&aArena One");
        config.set("integration.duel-command", "frostduels:frostduels duel %target% %kit% %rounds%");
        config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
        config.set("integration.duel-command", "cloud:duel %target% %kit% %rounds% %map%");
        config.save(file.toFile());
        assertEquals(2, new Settings(file.toFile()).arenas.size());
    }

    @Test void arbitraryHeadingsAndMessagesCanBeChangedWithoutCode() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("dialog.title", "&bFight %target%!");
        config.set("menus.categories.title", "&dCustom Categories");
        config.set("messages.self-duel", "&ePick somebody else.");
        config.save(file.toFile());
        Settings settings = new Settings(file.toFile());
        assertEquals("&bFight %target%!", settings.text("dialog.title"));
        assertEquals("&dCustom Categories", settings.text("menus.categories.title"));
        assertEquals("&ePick somebody else.", settings.text("messages.self-duel"));
    }

    @Test void invalidYamlAndRecursiveCommandsAreRejected() throws Exception {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("integration.duel-command", "duel %target% %kit% %rounds%");
        config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
        Files.writeString(file, "dialog: [broken");
        assertThrows(Exception.class, () -> new Settings(file.toFile()));
    }

    @Test void oldConfigsGainNavigationAndSpriteDefaultsWithoutFileChanges() throws Exception {
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        for (String path : java.util.List.of("menus.navigation", "menus.filler", "dialog.icons", "dialog.button-width",
                "dialog.slider-width", "dialog.body-width", "dialog.show-close-button")) config.set(path, null);
        config.save(file.toFile()); String before = Files.readString(file);
        Settings settings = new Settings(file.toFile());
        assertTrue(settings.paged); assertEquals(300, settings.buttonWidth); assertEquals(300, settings.sliderWidth);
        assertFalse(settings.showClose); assertEquals(4, settings.dialogIcons.size());
        assertEquals(before, Files.readString(file));
    }

    @Test void navigationSlotsCannotOverlapKitsOrEachOther() throws Exception {
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("menus.navigation.next-kit.slot", 10); config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
        config.set("menus.navigation.next-kit.slot", 40); config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
    }

    @Test void configuredPageOrderControlsVisibilityAndRejectsUnknownOrDuplicateIds() throws Exception {
        var config = YamlConfiguration.loadConfiguration(file.toFile());
        config.set("menus.navigation.page-order", java.util.List.of("subtiers", "mctiers-io")); config.save(file.toFile());
        assertEquals(java.util.List.of("subtiers", "mctiers-io"), new Settings(file.toFile()).categories.stream().map(Settings.Category::id).toList());
        config.set("menus.navigation.page-order", java.util.List.of("missing")); config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
        config.set("menus.navigation.page-order", java.util.List.of("subtiers", "subtiers")); config.save(file.toFile());
        assertThrows(IllegalArgumentException.class, () -> new Settings(file.toFile()));
    }
}
