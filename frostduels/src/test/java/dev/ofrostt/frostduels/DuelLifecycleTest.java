package dev.ofrostt.frostduels;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class DuelLifecycleTest {
    ServerMock server;
    FrostDuels plugin;
    PlayerMock admin, first, second;
    World world;

    @BeforeEach void setUp() throws Exception {
        server = MockBukkit.mock(); world = server.addSimpleWorld("duelworld");
        plugin = MockBukkit.load(FrostDuels.class);
        admin = server.addPlayer("Admin"); admin.setOp(true);
        first = server.addPlayer("First"); second = server.addPlayer("Second");
        admin.teleport(new Location(world, 0, 65, 0));
        server.dispatchCommand(admin, "fd setlobby");
        admin.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        server.dispatchCommand(admin, "fd kit save Sword-IO");
        server.dispatchCommand(admin, "fd arena create arena1");
        admin.teleport(new Location(world, 100, 65, 100)); server.dispatchCommand(admin, "fd arena setspawn arena1 1");
        admin.teleport(new Location(world, 110, 65, 100)); server.dispatchCommand(admin, "fd arena setspawn arena1 2");
        admin.teleport(plugin.store.lobby());
        first.teleport(plugin.store.lobby()); second.teleport(plugin.store.lobby());
        first.getInventory().setItem(3, new ItemStack(Material.EMERALD, 7));
        second.getInventory().setItem(2, new ItemStack(Material.GOLD_INGOT, 5));
    }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    void challenge() { server.dispatchCommand(first, "fd duel Second Sword-IO 2 arena1"); }
    FrostDuels.Match start() {
        challenge(); server.dispatchCommand(second, "fd accept First");
        assertEquals(2, plugin.playing.size()); return plugin.playing.get(first.getUniqueId());
    }

    @Test void requiresAcceptanceEquipsKitAndForfeitRestoresBothPlayers() {
        challenge(); assertTrue(plugin.playing.isEmpty()); assertEquals(1, plugin.requests.size());
        assertEquals(Material.EMERALD, first.getInventory().getItem(3).getType());
        server.dispatchCommand(second, "fd accept First");
        assertEquals(2, plugin.playing.size()); assertTrue(plugin.store.pending(first.getUniqueId()));
        assertEquals(Material.DIAMOND_SWORD, first.getInventory().getItem(0).getType());
        server.dispatchCommand(first, "fd leave");
        assertTrue(plugin.playing.isEmpty()); assertFalse(plugin.store.pending(first.getUniqueId()));
        assertFalse(plugin.store.pending(second.getUniqueId()));
        assertEquals(7, first.getInventory().getItem(3).getAmount());
        assertNull(first.getInventory().getItem(0));
        assertEquals(5, second.getInventory().getItem(2).getAmount());
        assertEquals(plugin.store.lobby(), first.getLocation());
    }

    @Test void reservesArenaAndFinishesFirstToTwoWithoutDuplicateScoring() {
        var match = start();
        server.getScheduler().performTicks(60);
        assertEquals(MatchState.Phase.FIGHTING, match.state.phase());
        plugin.lostRound(second); plugin.lostRound(first);
        assertEquals(1, match.state.firstScore()); assertEquals(0, match.state.secondScore());
        server.getScheduler().performTicks(80);
        assertEquals(MatchState.Phase.FIGHTING, match.state.phase());
        plugin.lostRound(second);
        assertTrue(plugin.playing.isEmpty()); assertEquals(MatchState.Phase.ENDED, match.state.phase());
        assertFalse(plugin.busy("arena1"));
    }

    @Test void deniesExpiredRequestsAndRequiresSnapshotForBuildingKit() throws Exception {
        challenge();
        var key = new FrostDuels.RequestKey(first.getUniqueId(), second.getUniqueId());
        var old = plugin.requests.get(key);
        plugin.requests.put(key, new FrostDuels.Request(old.sender(), old.target(), old.kit(), old.rounds(), old.map(), 0));
        server.dispatchCommand(second, "fd accept First"); assertTrue(plugin.playing.isEmpty());
        plugin.cooldowns.clear();
        plugin.settings.yaml.set("kit-rules.Sword-IO.terrain-damage", true);
        plugin.settings.yaml.set("kit-rules.Sword-IO.building", true);
        challenge(); server.dispatchCommand(second, "fd accept First");
        assertTrue(plugin.playing.isEmpty()); assertFalse(plugin.store.pending(first.getUniqueId()));
    }

    @Test void rulesCancelMiningAndNaturalHealingAndLockOnlyRequestedArena() {
        var match = start(); server.getScheduler().performTicks(60);
        BlockBreakEvent breaking = new BlockBreakEvent(world.getBlockAt(102, 64, 100), first);
        server.getPluginManager().callEvent(breaking); assertTrue(breaking.isCancelled());
        // Defaults allow natural regeneration; changing global config cannot alter a running match.
        plugin.settings.yaml.set("rules.natural-regeneration", false);
        var heal = new EntityRegainHealthEvent(first, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
        server.getPluginManager().callEvent(heal); assertFalse(heal.isCancelled());
        assertTrue(plugin.busy("arena1")); assertFalse(plugin.busy("other"));
        plugin.finish(match, "stopped", Map.of());
    }

    @Test void recoverySurvivesNewStoreAndCannotBeOverwritten() throws Exception {
        var store = plugin.store;
        store.snapshot(first);
        assertThrows(java.io.IOException.class, () -> store.snapshot(first));
        first.getInventory().clear();
        DuelStore reopened = new DuelStore(plugin.getDataFolder());
        assertTrue(reopened.pending(first.getUniqueId()));
        reopened.restore(first);
        assertEquals(7, first.getInventory().getItem(3).getAmount());
        assertFalse(reopened.pending(first.getUniqueId()));
        assertNotNull(reopened.kit("Sword-IO")); assertTrue(reopened.arena("arena1").ready());
    }

    @Test void rejectsNonAdminKitChangesAndOverlappingArenas() {
        server.dispatchCommand(first, "fd kit save StolenKit"); assertNull(plugin.store.kit("StolenKit"));
        server.dispatchCommand(admin, "fd arena create overlap");
        admin.teleport(new Location(world, 101, 65, 100)); server.dispatchCommand(admin, "fd arena setspawn overlap 1");
        admin.teleport(new Location(world, 111, 65, 100)); server.dispatchCommand(admin, "fd arena setspawn overlap 2");
        assertFalse(plugin.store.arena("overlap").ready());
    }

    @Test void disablesSaturationAndNaturalRegenerationPerKitButAllowsPotionHealing() {
        plugin.settings.yaml.set("kit-rules.Sword-IO.saturation", false);
        plugin.settings.yaml.set("kit-rules.Sword-IO.natural-regeneration", false);
        start(); server.getScheduler().performTicks(60);
        assertEquals(0, first.getSaturation());
        var natural = new EntityRegainHealthEvent(first, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
        var potion = new EntityRegainHealthEvent(first, 1, EntityRegainHealthEvent.RegainReason.MAGIC);
        server.getPluginManager().callEvent(natural); server.getPluginManager().callEvent(potion);
        assertTrue(natural.isCancelled()); assertFalse(potion.isCancelled());
    }

    @Test void listenersEnforceInclusiveBuildAndMineHeightsAndExplosionBoundary() {
        var initial = start();
        var overrides = plugin.settings.yaml.createSection("test-rules");
        overrides.set("terrain-damage", true); overrides.set("building", true); overrides.set("breaking", true);
        overrides.set("explosion-terrain-damage", true);
        overrides.set("min-break-y", 40); overrides.set("max-break-y", 100);
        overrides.set("min-build-y", 50); overrides.set("max-build-y", 120);
        Rules rules = Rules.load(plugin.settings.yaml.getConfigurationSection("rules"), overrides);
        // Isolate event policy from the native structure implementation, which MockBukkit cannot run.
        var match = new FrostDuels.Match(first, second, initial.arena, initial.kit, 2, rules);
        plugin.playing.put(first.getUniqueId(), match); plugin.playing.put(second.getUniqueId(), match); match.state.fight();
        for (int y : new int[]{39, 40, 100, 101}) {
            var breaking = new BlockBreakEvent(world.getBlockAt(102, y, 100), first);
            server.getPluginManager().callEvent(breaking);
            assertEquals(y < 40 || y > 100, breaking.isCancelled());
        }
        for (int y : new int[]{49, 50, 120, 121}) {
            var block = world.getBlockAt(102, y, 100);
            var place = new BlockPlaceEvent(block, block.getState(), block.getRelative(0, -1, 0), new ItemStack(Material.STONE), first, true, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(place);
            assertEquals(y < 50 || y > 120, place.isCancelled());
        }
        var inside = world.getBlockAt(102, 64, 100); var outside = world.getBlockAt(200, 64, 100);
        var explosion = new EntityExplodeEvent(first, first.getLocation(), new ArrayList<>(List.of(inside, outside)), 1, ExplosionResult.DESTROY);
        server.getPluginManager().callEvent(explosion);
        assertEquals(List.of(inside), explosion.blockList());
    }

    @Test void pendingDirtyMarkerSurvivesRestartAndMissingTemplateNeverUnlocksArena() throws Exception {
        var path = plugin.getDataFolder().toPath().resolve("arenas/arena1.dirty.yml");
        java.nio.file.Files.writeString(path, "arena: arena1\n");
        var templates = new TerrainTemplates(plugin);
        assertTrue(templates.locked.contains("arena1"));
        assertThrows(java.io.IOException.class, () -> templates.reset(plugin.store.arena("arena1"), true));
        assertTrue(java.nio.file.Files.exists(path)); assertTrue(templates.locked.contains("arena1"));
    }

    @Test void serverDisableRestoresInventoryAndReleasesReservations() {
        start(); server.getPluginManager().disablePlugin(plugin);
        assertTrue(plugin.playing.isEmpty()); assertFalse(plugin.store.pending(first.getUniqueId()));
        assertEquals(7, first.getInventory().getItem(3).getAmount()); assertEquals(plugin.store.lobby(), second.getLocation());
    }

    @Test void adminCanLoadEditAndResaveKitIncludingOffhandAndNewPlayerSlots() {
        admin.getInventory().clear();
        server.dispatchCommand(admin, "fd kit load Sword-IO");
        assertEquals(Material.DIAMOND_SWORD, admin.getInventory().getItem(0).getType());
        admin.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        admin.getInventory().setItem(42, new ItemStack(Material.SADDLE));
        server.dispatchCommand(admin, "fd kit save Sword-IO");
        admin.getInventory().clear(); server.dispatchCommand(admin, "fd kit load Sword-IO");
        assertEquals(Material.SHIELD, admin.getInventory().getItemInOffHand().getType());
        assertEquals(Material.SADDLE, admin.getInventory().getItem(42).getType());
    }
}
