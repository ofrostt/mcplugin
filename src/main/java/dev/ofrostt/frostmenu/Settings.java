package dev.ofrostt.frostmenu;

import java.io.File;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;

final class Settings {
    record Icon(Material material, String name, List<String> lore, int slot) {}
    record Mode(String id, Icon icon, String kit, String dialogName) {}
    record Category(String id, Icon icon, String title, int rows, List<Mode> modes) {}
    record Arena(String id, String label) {}

    final YamlConfiguration yaml;
    final int categoryRows, minRounds, maxRounds, defaultRounds, width, timeout;
    final int buttonWidth, sliderWidth, bodyWidth;
    final boolean paged, fillerEnabled, showClose;
    final Material fillerMaterial;
    final Icon previousPage, nextPage, previousKit, nextKit, confirm;
    final Map<String, Component> dialogIcons;
    final Icon back;
    final List<Category> categories;
    final List<Arena> arenas;

    Settings(File file) throws Exception {
        yaml = new YamlConfiguration();
        yaml.load(file); // Unlike loadConfiguration(), parse failures must abort reload.
        try (var input = Objects.requireNonNull(Settings.class.getResourceAsStream("/menu.yml"))) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8)));
        }
        categoryRows = integer("menus.categories.rows", 1, 6);
        minRounds = integer("dialog.rounds.min", 1, 1000);
        maxRounds = integer("dialog.rounds.max", minRounds + 1, 1000);
        defaultRounds = integer("dialog.rounds.default", minRounds, maxRounds);
        width = integer("dialog.width", 150, 1024);
        buttonWidth = integer("dialog.button-width", 150, 1024);
        sliderWidth = integer("dialog.slider-width", 150, 1024);
        bodyWidth = integer("dialog.body-width", 150, 1024);
        showClose = yaml.getBoolean("dialog.show-close-button", false);
        paged = yaml.getBoolean("menus.navigation.enabled", true);
        previousPage = icon(section("menus.navigation.previous-page"), 54);
        nextPage = icon(section("menus.navigation.next-page"), 54);
        previousKit = icon(section("menus.navigation.previous-kit"), 54);
        nextKit = icon(section("menus.navigation.next-kit"), 54);
        confirm = icon(section("menus.navigation.confirm"), 54);
        fillerEnabled = yaml.getBoolean("menus.filler.enabled", true);
        fillerMaterial = Material.matchMaterial(text("menus.filler.material"));
        if (fillerMaterial == null || fillerMaterial.isAir() || !fillerMaterial.isItem()) fail("Invalid filler material");
        Map<String, Component> icons = new LinkedHashMap<>();
        for (String key : List.of("kit", "rounds", "advanced", "map")) {
            icons.put(key, DialogText.sprite(text("dialog.icons." + key + ".atlas"), text("dialog.icons." + key + ".sprite")));
        }
        dialogIcons = Map.copyOf(icons);
        timeout = integer("session-timeout-seconds", 10, 3600);
        back = icon(section("menus.back"), 54);
        for (String path : List.of("menus.categories.title", "menus.navigation.selected-lore", "dialog.title", "dialog.rounds.label",
                "dialog.buttons.advanced", "dialog.buttons.map", "dialog.buttons.send", "dialog.buttons.close",
                "dialog.buttons.back", "dialog.buttons.apply", "dialog.advanced.title", "dialog.map-title",
                "messages.usage", "messages.players-only", "messages.no-permission", "messages.player-not-found",
                "messages.self-duel", "messages.expired",
                "messages.invalid-response", "messages.request-failed")) text(path);
        if (yaml.getStringList("dialog.body").isEmpty()) fail("dialog.body must contain text");
        if (yaml.getStringList("dialog.advanced.body").isEmpty()) fail("dialog.advanced.body must contain text");
        Set<Integer> categorySlots = new HashSet<>();
        List<Category> parsed = new ArrayList<>();
        ConfigurationSection cats = section("categories");
        for (String id : cats.getKeys(false)) {
            ConfigurationSection cat = Objects.requireNonNull(cats.getConfigurationSection(id));
            Icon icon = icon(cat, categoryRows * 9);
            if (!categorySlots.add(icon.slot())) fail("Duplicate category slot " + icon.slot());
            int rows = cat.getInt("rows");
            if (rows < 1 || rows > 6 || (!paged && back.slot() >= rows * 9)) fail("Invalid rows/back slot for " + id);
            String title = required(cat, "title");
            ConfigurationSection modes = cat.getConfigurationSection("gamemodes");
            if (modes == null || modes.getKeys(false).isEmpty()) fail("No gamemodes in " + id);
            Set<Integer> slots = new HashSet<>();
            if (paged) {
                for (Icon control : List.of(previousPage, nextPage, previousKit, nextKit, confirm)) {
                    if (control.slot() >= rows * 9 || !slots.add(control.slot())) fail("Navigation controls must fit and have unique slots in " + id);
                }
            } else slots.add(back.slot());
            List<Mode> parsedModes = new ArrayList<>();
            for (String modeId : modes.getKeys(false)) {
                ConfigurationSection mode = Objects.requireNonNull(modes.getConfigurationSection(modeId));
                Icon item = icon(mode, rows * 9);
                if (!slots.add(item.slot())) fail("Duplicate gamemode/back slot in " + id);
                parsedModes.add(new Mode(modeId, item, DuelRules.token(required(mode, "kit")),
                        mode.getString("dialog-name", item.name())));
            }
            parsed.add(new Category(id, icon, title, rows, List.copyOf(parsedModes)));
        }
        if (parsed.isEmpty()) fail("At least one category is required");
        String orderPath = "menus.navigation.page-order";
        List<String> order = yaml.getStringList(orderPath);
        Map<String, Category> byId = new LinkedHashMap<>();
        for (Category category : parsed) byId.put(category.id(), category);
        if (new HashSet<>(order).size() != order.size()) fail("Duplicate category in " + orderPath);
        boolean known = order.stream().allMatch(byId::containsKey);
        if (!known && yaml.contains(orderPath, true)) fail("Unknown category in " + orderPath);
        categories = order.isEmpty() || !known ? List.copyOf(parsed) : order.stream().map(byId::get).toList();
        List<Arena> maps = new ArrayList<>();
        ConfigurationSection mapSection = section("maps");
        for (String id : mapSection.getKeys(false)) maps.add(new Arena(DuelRules.token(id), required(mapSection, id)));
        if (maps.isEmpty()) fail("At least one map is required");
        arenas = List.copyOf(maps);

    }

    String text(String path) { return required(yaml, path); }
    List<String> lines(String path) { return yaml.getStringList(path); }
    private ConfigurationSection section(String path) {
        ConfigurationSection value = yaml.getConfigurationSection(path);
        if (value == null) throw new IllegalArgumentException("Missing section " + path);
        return value;
    }
    private int integer(String path, int min, int max) {
        if (!yaml.isInt(path)) throw new IllegalArgumentException(path + " must be an integer");
        int value = yaml.getInt(path);
        if (value < min || value > max) throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        return value;
    }
    private static String required(ConfigurationSection section, String key) {
        String value = section.getString(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing text " + section.getCurrentPath() + "." + key);
        return value;
    }
    private static Icon icon(ConfigurationSection section, int size) {
        Material material = Material.matchMaterial(required(section, "material"));
        if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("Invalid item material at " + section.getCurrentPath());
        int slot = section.isInt("slot") ? section.getInt("slot") : -1;
        if (slot < 0 || slot >= size) throw new IllegalArgumentException("Invalid slot at " + section.getCurrentPath());
        return new Icon(material, required(section, "display-name"), List.copyOf(section.getStringList("lore")), slot);
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
