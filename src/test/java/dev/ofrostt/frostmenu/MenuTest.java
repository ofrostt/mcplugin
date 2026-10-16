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
        Session confirmedSession;
        @Override protected void confirm(Player player, Session session) {
            confirmedKit = session.mode.kit(); confirmedSession = session; confirmations++;
        }
        void sendConfirmed(Player player) { send(player, confirmedSession); }
    }
    ServerMock server;
    RecordingFrostMenu plugin;
    PlayerMock player, target;

    @BeforeEach void setUp() {
        server = MockBukkit.mock(); plugin = MockBukkit.loadWith(RecordingFrostMenu.class, getClass().getResourceAsStream("/plugin.yml"));
        try (var input = getClass().getResourceAsStream("/populated-menu.yml")) {
            java.nio.file.Files.copy(input, new java.io.File(plugin.getDataFolder(), "menu.yml").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.prepareMenuReload().run();
        } catch (Exception ex) { throw new RuntimeException(ex); }
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
        assertTrue(server.dispatchCommand(player, "duel Opponent"));
        assertEquals("Queue: mctiers.io", title());
        assertEquals(45, player.getOpenInventory().getTopInventory().getSize());
        assertEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(0).getType());
        assertTrue(selected(10)); assertFalse(selected(11));
        assertEquals(0, plugin.confirmations);
    }

    @Test void kitArrowsCycleWithoutOpeningDialogUntilConfirm() {
        server.dispatchCommand(player, "duel Opponent");
        assertTrue(click(42).isCancelled());
        assertTrue(selected(11)); assertFalse(selected(10));
        assertEquals(0, plugin.confirmations);
        click(40);
        assertEquals(1, plugin.confirmations); assertEquals("UHC-IO", plugin.confirmedKit);
    }

    @Test void previousKitWrapsAndIconClicksOnlyHighlight() {
        server.dispatchCommand(player, "duel Opponent");
        click(38); assertTrue(selected(16));
        click(12); assertTrue(selected(12)); assertEquals(0, plugin.confirmations);
        click(40); assertEquals("Axe-IO", plugin.confirmedKit);
    }

    @Test void pageArrowsChangeGroupsAndRememberTheirHighlightedKits() {
        server.dispatchCommand(player, "duel Opponent");
        click(42); assertTrue(selected(11));
        click(44); assertEquals("Queue: Subtiers", title());
        assertEquals(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, player.getOpenInventory().getTopInventory().getItem(44).getType());
        click(44); assertEquals("Queue: Subtiers", title());
        click(36); assertEquals("Queue: mctiers.io", title()); assertTrue(selected(11));
    }

    @Test void cancelsShiftTransfersAndIgnoresRapidDuplicateNavigationClicks() {
        server.dispatchCommand(player, "duel Opponent");
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
        server.dispatchCommand(player, "duel Challenger");
        assertEquals("You cannot duel yourself.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
        server.dispatchCommand(player, "duel MissingPlayer");
        assertEquals("That player is offline or unavailable.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
        player.addAttachment(plugin, "duels.use", false);
        server.dispatchCommand(player, "duel Opponent");
        assertEquals("You do not have permission to do that.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }

    @Test void opponentDisconnectStopsPendingMenuTransition() {
        server.dispatchCommand(player, "duel Opponent");
        var click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                44, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click); target.disconnect(); server.getScheduler().performOneTick();
        assertEquals("Queue: mctiers.io", title());
        assertEquals("This menu has expired. Open /duel <player> again.", org.bukkit.ChatColor.stripColor(player.nextMessage()));
    }

    @Test void namespacedDuelAliasAlsoOpensKitPage() {
        assertTrue(server.dispatchCommand(player, "duels:duel Opponent")); assertEquals("Queue: mctiers.io", title());
    }

    @Test void reloadUsesCustomPageHeadingAndFillerWithoutOverwritingConfig() throws Exception {
        var file = new java.io.File(plugin.getDataFolder(), "menu.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        yaml.set("categories.mctiers-io.title", "&bChoose a kit for %target%");
        yaml.set("menus.filler.display-name", "&bFrost Border");
        yaml.save(file);
        PlayerMock admin = server.addPlayer("Admin"); admin.setOp(true);
        server.dispatchCommand(admin, "duels reload");
        server.dispatchCommand(player, "duel Opponent");
        assertEquals("Choose a kit for Opponent", title());
        var filler = player.getOpenInventory().getTopInventory().getItem(0).getItemMeta();
        assertEquals("Frost Border", PlainTextComponentSerializer.plainText().serialize(filler.displayName()));
        assertEquals("&bChoose a kit for %target%", org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
                .getString("categories.mctiers-io.title"));
    }

    @Test void completedArenasAutomaticallyAppearAndConfirmedKitReachesIntegratedEngine() {
        PlayerMock admin = server.addPlayer("Admin"); admin.setOp(true);
        var world = server.addSimpleWorld("arena-world");
        admin.teleport(new org.bukkit.Location(world, 0, 65, 0)); server.dispatchCommand(admin, "duels setlobby");
        admin.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD));
        server.dispatchCommand(admin, "duels kit save Sword-IO");
        server.dispatchCommand(admin, "duels arena create arena1");
        assertFalse(plugin.availableMaps().containsKey("arena1"));
        admin.teleport(new org.bukkit.Location(world, 100, 65, 100)); server.dispatchCommand(admin, "duels arena setspawn arena1 1");
        admin.teleport(new org.bukkit.Location(world, 110, 65, 100)); server.dispatchCommand(admin, "duels arena setspawn arena1 2");
        assertEquals(java.util.Set.of("random", "arena1"), plugin.availableMaps().keySet());
        server.dispatchCommand(player, "duel Opponent"); click(40);
        plugin.confirmedSession.map = "arena1";
        plugin.sendConfirmed(player);
        assertTrue(org.bukkit.ChatColor.stripColor(player.nextMessage()).contains("Challenge sent to Opponent"));
        assertTrue(org.bukkit.ChatColor.stripColor(target.nextMessage()).contains("Challenger challenged you with Sword-IO"));
    }
}
