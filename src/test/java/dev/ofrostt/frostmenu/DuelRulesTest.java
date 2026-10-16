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

    @Test void commandTemplatesKeepCloudNamespaceAndExpandExactKitIds() {
        String template = DuelRules.command("/cloud:duel %target% %kit% %rounds%");
        assertEquals("cloud:duel Opponent Sword-IO 3", DuelRules.expand(template,
                Map.of("target", "Opponent", "kit", "Sword-IO", "rounds", "3")));
        assertEquals("Elytra-IO", DuelRules.token("Elytra-IO"));
    }

    @Test void rejectRecursiveMultilineAndUnknownCommandTemplates() {
        for (String template : new String[]{"duel %target%", "frostmenu %target%", "frostmenu:frostmenu %target%",
                "frostmenu:duel %target%", "cloud:duel %target%\nop me", "cloud:duel %unknown%", ""}) {
            assertThrows(IllegalArgumentException.class, () -> DuelRules.command(template), template);
        }
    }

    @Test void rejectIdentifiersThatCanInjectArguments() {
        for (String token : new String[]{"Sword IO", "Sword\nstop", "", "%target%", "a".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> DuelRules.token(token));
        }
    }
}
