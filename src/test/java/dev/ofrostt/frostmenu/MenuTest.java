package dev.ofrostt.frostmenu;

import static org.junit.jupiter.api.Assertions.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MenuTest {
    ServerMock server;
    FrostMenu plugin;
    PlayerMock player, target;

    @BeforeEach void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(FrostMenu.class);
        player = server.addPlayer("Challenger");
        target = server.addPlayer("Opponent");
    }

    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void opensCategoryThenGamemodeGuiAndCancelsItemTransfers() {
        assertTrue(server.dispatchCommand(player, "frostmenu Opponent"));
        assertEquals("Select Kit Category", PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()));
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                11, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertTrue(click.isCancelled());
        server.getScheduler().performOneTick();
        assertEquals("Duel Opponent", PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()));
        assertEquals(45, player.getOpenInventory().getTopInventory().getSize());
        ItemStack sword = player.getOpenInventory().getTopInventory().getItem(10);
        assertNotNull(sword);
        assertEquals("Sword", PlainTextComponentSerializer.plainText().serialize(sword.getItemMeta().displayName()));
        InventoryClickEvent transfer = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                45, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(transfer);
        assertTrue(transfer.isCancelled());
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
        InventoryClickEvent click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                11, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        target.disconnect();
        server.getScheduler().performOneTick();
        assertEquals("Select Kit Category", PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()));
        assertEquals("This menu has expired. Open /frostmenu <player> again.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }

    @Test void namespacedDuelAliasAlsoOpensMenu() {
        assertTrue(server.dispatchCommand(player, "frostmenu:duel Opponent"));
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());
    }
}
