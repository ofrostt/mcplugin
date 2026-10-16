package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class DialogTextTest {
    private Stream<Component> walk(Component component) {
        return Stream.concat(Stream.of(component), component.children().stream().flatMap(this::walk));
    }
    @Test void usesUntintedNativeItemSpriteInsideFormattedDialogText() {
        Component sword = DialogText.sprite("minecraft:items", "minecraft:item/diamond_sword");
        Component rendered = DialogText.render("&7Kit: %kit-icon% &a&l%gamemode-name%",
                Map.of("gamemode-name", "CREEPER PVP"), Map.of("kit", sword));
        List<ObjectComponent> icons = walk(rendered).filter(ObjectComponent.class::isInstance).map(ObjectComponent.class::cast).toList();
        assertEquals(1, icons.size());
        var contents = (SpriteObjectContents) icons.getFirst().contents();
        assertEquals("minecraft:items", contents.atlas().asString());
        assertEquals("minecraft:item/diamond_sword", contents.sprite().asString());
        assertEquals(NamedTextColor.WHITE, icons.getFirst().color());
        assertEquals(TextDecoration.State.FALSE, icons.getFirst().decoration(TextDecoration.BOLD));
        assertEquals("Kit: [item/diamond_sword@items] CREEPER PVP", PlainTextComponentSerializer.plainText().serialize(rendered));
    }
    @Test void replacesPlayerAndRoundValuesAndAllowsSeveralIcons() {
        Component rendered = DialogText.render("%player% vs %target% %rounds-icon% %rounds% %map-icon%",
                Map.of("player", "First", "target", "Second", "rounds", "3"),
                Map.of("rounds", DialogText.sprite("minecraft:items", "minecraft:item/lime_dye"),
                       "map", DialogText.sprite("minecraft:items", "minecraft:item/map")));
        assertEquals(2, walk(rendered).filter(ObjectComponent.class::isInstance).count());
        assertFalse(PlainTextComponentSerializer.plainText().serialize(rendered).contains("%"));
    }
    @Test void malformedTextureKeysAreRejected() {
        assertThrows(net.kyori.adventure.key.InvalidKeyException.class, () -> DialogText.sprite("bad atlas", "minecraft:item/book"));
    }
}
