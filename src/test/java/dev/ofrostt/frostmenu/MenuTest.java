package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public class MenuTest {
    /** Native dialog providers are not implemented by MockBukkit; capture the handoff. */
    public static class RecordingFrostMenu extends FrostMenu {
        String confirmedKit;
        int confirmations;
        @Override protected void confirm(Player player, Session session) {
            confirmedKit = session.mode.kit(); confirmations++;
        }
    }
    ServerMock server;
    RecordingFrostMenu plugin;
    PlayerMock player, target;

    @BeforeEach void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.loadWith(RecordingFrostMenu.class, getClass().getResourceAsStream("/plugin.yml"));
        for (var permission : plugin.getDescription().getPermissions()) server.getPluginManager().addPermission(permission);
        player = server.addPlayer("Challenger"); target = server.addPlayer("Opponent");
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }
    private String title() { return PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()); }
    private InventoryClickEvent click(int slot) {
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click); server.getScheduler().performOneTick(); return click;
    }
    private boolean selected(int slot) {
        var meta = player.getOpenInventory().getTopInventory().getItem(slot).getItemMeta();
        return meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
    }

    @Test void opensFirstKitPageDirectlyWithFrameAndSelectedFirstKit() {
        assertTrue(server.dispatchCommand(player, "frostmenu Opponent"));
        assertEquals("Queue: Famous Kits", title());
        assertEquals(45, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(0).getType());
        assertTrue(selected(10)); assertFalse(selected(11));
        assertEquals(0, plugin.confirmations);
    }

    @Test void kitArrowsCycleWithoutOpeningDialogUntilConfirm() {
        server.dispatchCommand(player, "frostmenu Opponent");
        assertTrue(click(42).isCancelled());
        assertTrue(selected(11)); assertFalse(selected(10));
        assertEquals(0, plugin.confirmations);
        click(40);
        assertEquals(1, plugin.confirmations); assertEquals("UHC-IO", plugin.confirmedKit);
    }

    @Test void previousKitWrapsAndIconClicksOnlyHighlight() {
        server.dispatchCommand(player, "frostmenu Opponent");
        click(38); assertTrue(selected(16));
        click(12); assertTrue(selected(12)); assertEquals(0, plugin.confirmations);
        click(40); assertEquals("Axe-IO", plugin.confirmedKit);
    }

    @Test void pageArrowsChangeGroupsAndRememberTheirHighlightedKits() {
        server.dispatchCommand(player, "frostmenu Opponent");
        click(42); assertTrue(selected(11));
        click(44); assertEquals("Queue: mctiers.com", title()); assertTrue(selected(10));
        click(44); assertEquals("Queue: Subtiers Kits", title());
        assertEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(44).getType());
        click(44); assertEquals("Queue: Subtiers Kits", title());
        click(36); click(36); assertEquals("Queue: Famous Kits", title()); assertTrue(selected(11));
    }

    @Test void cancelsShiftTransfersAndIgnoresRapidDuplicateNavigationClicks() {
        server.dispatchCommand(player, "frostmenu Opponent");
        var transfer = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                45, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(transfer); assertTrue(transfer.isCancelled());
        for (int index = 0; index < 2; index++) {
            var next = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    42, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(next);
        }
        server.getScheduler().performOneTick(); assertTrue(selected(11));
    }

    @Test void rejectsSelfOfflineAndMissingPermission() {
        server.dispatchCommand(player, "frostmenu Challenger");
        assertEquals("You cannot duel yourself.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
        server.dispatchCommand(player, "frostmenu MissingPlayer");
        assertEquals("That player is offline or unavailable.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
        player.addAttachment(plugin, "frostmenu.use", false);
        server.dispatchCommand(player, "frostmenu Opponent");
        assertEquals("You do not have permission to do that.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }

    @Test void opponentDisconnectStopsPendingMenuTransition() {
        server.dispatchCommand(player, "frostmenu Opponent");
        var click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                44, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click); target.disconnect(); server.getScheduler().performOneTick();
        assertEquals("Queue: Famous Kits", title());
        assertEquals("This menu has expired. Open /frostmenu <player> again.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }

    @Test void namespacedDuelAliasAlsoOpensKitPage() {
        assertTrue(server.dispatchCommand(player, "frostmenu:duel Opponent")); assertEquals("Queue: Famous Kits", title());
    }
}
