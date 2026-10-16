package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import dev.ofrostt.frostduels.Rules;
import io.papermc.paper.dialog.DialogResponseView;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AdvancedRulesTest {
    private Rules defaults() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
        return Rules.fromValues(yaml.getConfigurationSection("rules").getValues(false));
    }
    private DialogResponseView response(Map<String, Object> values) {
        return (DialogResponseView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DialogResponseView.class},
                (proxy, method, args) -> values.get((String) args[0]));
    }
    private Map<String, Object> form(Rules rules) {
        Map<String, Object> result = new HashMap<>();
        rules.values().forEach((key, value) -> result.put(key, value instanceof Boolean ? value : value.toString()));
        return result;
    }
    @Test void editsRulesWithoutChangingDefaultsAndHonorsInclusiveLimits() {
        Rules original = defaults(); var form = form(original);
        form.put("terrain-damage", true); form.put("breaking", true); form.put("building", true);
        form.put("min-break-y", "40"); form.put("max-break-y", "90");
        form.put("min-build-y", "50"); form.put("max-build-y", "100");
        form.put("regeneration", false); form.put("starting-saturation", "4.5");
        Rules edited = FrostMenu.readAdvanced(original, response(form));
        assertFalse(original.terrain()); assertTrue(original.regeneration());
        assertTrue(edited.breaking()); assertTrue(edited.building()); assertFalse(edited.regeneration());
        assertEquals(40, edited.minBreakY()); assertEquals(100, edited.maxBuildY());
        assertEquals(4.5f, edited.startingSaturation());
    }
    @Test void rejectsMissingMalformedConflictingAndOutOfRangeResponses() {
        Rules original = defaults();
        for (var invalid : List.of(Map.entry("breaking", (Object) true), Map.entry("min-break-y", "1.5"),
                Map.entry("min-build-y", "500"), Map.entry("max-build-y", "2032"),
                Map.entry("starting-food", "21"), Map.entry("starting-saturation", "NaN"))) {
            var form = form(original); form.put(invalid.getKey(), invalid.getValue());
            assertThrows(IllegalArgumentException.class, () -> FrostMenu.readAdvanced(original, response(form)));
        }
        var form = form(original); form.remove("hunger");
        assertThrows(IllegalArgumentException.class, () -> FrostMenu.readAdvanced(original, response(form)));
    }
}
