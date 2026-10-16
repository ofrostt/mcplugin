package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DuelRulesTest {
    @Test void roundSliderAcceptsOnlyWholeValuesWithinConfiguredRange() {
        assertEquals(1, DuelRules.rounds(1f, 1, 20));
        assertEquals(20, DuelRules.rounds(20f, 1, 20));
        for (Float value : new Float[]{null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, 21f, 2.5f}) {
            assertThrows(IllegalArgumentException.class, () -> DuelRules.rounds(value, 1, 20));
        }
    }

    @Test void textPlaceholdersKeepExactKitIds() {
        String template = "Kit %kit% for %target%, rounds %rounds%";
        assertEquals("Kit Sword-IO for Opponent, rounds 3", DuelRules.expand(template,
                Map.of("target", "Opponent", "kit", "Sword-IO", "rounds", "3")));
        assertEquals("Elytra-IO", DuelRules.token("Elytra-IO"));
    }

    @Test void rejectIdentifiersThatCanInjectArguments() {
        for (String token : new String[]{"Sword IO", "Sword\nstop", "", "%target%", "a".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> DuelRules.token(token));
        }
    }
}
