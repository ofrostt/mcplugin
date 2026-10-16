package dev.ofrostt.frostduels;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RulesTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
    }
    @Test void defaultsPreventAllPlayerTerrainChanges() {
        Rules rules = Rules.load(config().getConfigurationSection("rules"), null);
        assertFalse(rules.canBuild(64)); assertFalse(rules.canBreak(64)); assertFalse(rules.canExplode(64));
        assertTrue(rules.saturation()); assertTrue(rules.naturalRegeneration());
    }
    @Test void kitOverridesUseInclusiveIndependentBuildAndMineLimits() {
        var config = config(); var override = config.createSection("override");
        override.set("terrain-damage", true); override.set("building", true); override.set("breaking", true);
        override.set("min-break-y", 40); override.set("max-break-y", 100);
        override.set("min-build-y", 50); override.set("max-build-y", 120);
        override.set("saturation", false); override.set("natural-regeneration", false);
        Rules rules = Rules.load(config.getConfigurationSection("rules"), override);
        assertTrue(rules.canBreak(40)); assertTrue(rules.canBreak(100)); assertFalse(rules.canBreak(39)); assertFalse(rules.canBreak(101));
        assertTrue(rules.canBuild(50)); assertTrue(rules.canBuild(120)); assertFalse(rules.canBuild(49)); assertFalse(rules.canBuild(121));
        assertFalse(rules.saturation()); assertFalse(rules.naturalRegeneration()); assertTrue(rules.potionRegeneration());
        assertFalse(rules.canExplode(64));
    }
    @Test void rejectConflictingInvalidAndMistypedRules() {
        var config = config(); var base = config.getConfigurationSection("rules");
        base.set("building", true);
        assertThrows(IllegalArgumentException.class, () -> Rules.load(base, null));
        base.set("terrain-damage", true); base.set("min-build-y", 500); base.set("max-build-y", 100);
        assertThrows(IllegalArgumentException.class, () -> Rules.load(base, null));
        base.set("min-build-y", 0); base.set("saturation", "maybe");
        assertThrows(IllegalArgumentException.class, () -> Rules.load(base, null));
    }
}
