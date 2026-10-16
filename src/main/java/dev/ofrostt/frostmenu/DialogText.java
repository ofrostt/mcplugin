package dev.ofrostt.frostmenu;

import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Native 1.21.11 sprite components render Minecraft textures without an extra resource pack. */
final class DialogText {
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.legacyAmpersand();
    static Component sprite(String atlas, String texture) {
        return Component.object(ObjectContents.sprite(Key.key(atlas), Key.key(texture)))
                .color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false);
    }
    static Component render(String template, Map<String, String> values, Map<String, Component> icons) {
        Component result = COLORS.deserialize(DuelRules.expand(template, values));
        for (var icon : icons.entrySet()) {
            result = result.replaceText(builder -> builder.matchLiteral("%" + icon.getKey() + "-icon%").replacement(icon.getValue()));
        }
        return result;
    }
}
