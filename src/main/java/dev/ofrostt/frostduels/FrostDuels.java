package dev.ofrostt.frostduels;

import java.io.*;
import java.util.*;
import java.time.Duration;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FrostDuels extends JavaPlugin {
    record Request(UUID sender, UUID target, String kit, int rounds, String map, long expires) {}
    record RequestKey(UUID sender, UUID target) {}
    static final class Match {
        final MatchState state;
        final DuelStore.Arena arena;
        final DuelStore.Kit kit;
        final Rules rules;
        UUID id = UUID.randomUUID();
        final String firstName, secondName;
        final Set<UUID> entities = new HashSet<>();
        int countdown, elapsed;
        Match(Player first, Player second, DuelStore.Arena arena, DuelStore.Kit kit, int rounds, Rules rules) {
            state = new MatchState(first.getUniqueId(), second.getUniqueId(), rounds);
            firstName = first.getName(); secondName = second.getName(); this.arena = arena; this.kit = kit; this.rules = rules;
        }
    }

    DuelConfig settings;
    DuelStore store;
    TerrainTemplates terrain;
    final Map<RequestKey, Request> requests = new HashMap<>();
    final Map<UUID, Long> cooldowns = new HashMap<>();
    final Map<UUID, Match> playing = new HashMap<>();
    final Set<UUID> internalTeleports = new HashSet<>();
    private final LegacyComponentSerializer colors = LegacyComponentSerializer.legacyAmpersand();

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            settings = readSettings(); store = new DuelStore(getDataFolder()); validateArenas(settings);
            terrain = new TerrainTemplates(this); terrain.recoverAll();
        }
        catch (Exception ex) {
            getLogger().severe("Startup refused: " + ex.getMessage()); getServer().getPluginManager().disablePlugin(this); return;
        }
        DuelCommands commands = new DuelCommands(this);
        Objects.requireNonNull(getCommand("frostmenu")).setExecutor(commands);
        getCommand("frostmenu").setTabCompleter(commands);
        updateAdminPermissionMessage();
        DuelEvents events = new DuelEvents(this);
        getServer().getPluginManager().registerEvents(events, this); events.cleanupLoaded();
        getServer().getScheduler().runTaskTimer(this, this::tick, 20, 20);
        for (Player player : Bukkit.getOnlinePlayers()) recover(player, false);
    }
    protected Runnable prepareMenuReload() throws Exception { return () -> {}; }
    void updateAdminPermissionMessage() {
        Objects.requireNonNull(getCommand("frostmenu")).permissionMessage(text(settings.text("prefix") + settings.text("no-permission"), Map.of()));
    }
    protected Set<String> selectableArenas() {
        Set<String> result = new TreeSet<>();
        for (var arena : store.arenas()) if (arena.ready() && !busy(arena.id()) && !terrain.locked.contains(arena.id())) result.add(arena.id());
        return result;
    }

    @Override public void onDisable() {
        if (store == null || settings == null || terrain == null) return;
        for (Match match : new HashSet<>(playing.values())) finish(match, "stopped", Map.of());
        for (Player player : Bukkit.getOnlinePlayers()) recover(player, false);
        requests.clear(); cooldowns.clear();
    }

    DuelConfig readSettings() throws Exception {
        DuelConfig fresh = new DuelConfig(new File(getDataFolder(), "config.yml"));
        try (var input = getResource("config.yml")) {
            var defaults = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
            for (String key : defaults.getConfigurationSection("messages").getKeys(false)) fresh.text(key);
        }
        return fresh;
    }

    void validateArenas(DuelConfig config) {
        if (store == null) return;
        List<DuelStore.Arena> arenas = store.arenas();
        for (int i = 0; i < arenas.size(); i++) {
            for (int j = i + 1; j < arenas.size(); j++) {
                if (arenas.get(i).overlaps(arenas.get(j), config.padding)) throw new IllegalArgumentException("Overlapping arenas: " + arenas.get(i).id() + " / " + arenas.get(j).id());
            }
        }
    }

    protected void request(Player sender, Player target, String kit, int rounds, String map) {
        if (sender.equals(target)) { tell(sender, "self"); return; }
        if (!sender.hasPermission("frostduels.use") || !target.hasPermission("frostduels.use") || !available(sender) || !available(target)) { tell(sender, "unavailable"); return; }
        if (store.kit(kit) == null || (!map.equals("random") && store.arena(map) == null)) { tell(sender, "not-found"); return; }
        if (store.lobby() == null || store.lobby().getWorld() == null || store.arenas().stream().noneMatch(DuelStore.Arena::ready)) { tell(sender, "not-ready"); return; }
        if (rounds < 1 || rounds > settings.maxRounds) { tell(sender, "invalid-rounds", Map.of("max", "" + settings.maxRounds)); return; }
        long now = System.currentTimeMillis();
        if (cooldowns.getOrDefault(sender.getUniqueId(), 0L) > now) { tell(sender, "cooldown"); return; }
        // At most one outstanding request per sender keeps challenge traffic bounded.
        requests.entrySet().removeIf(e -> e.getKey().sender().equals(sender.getUniqueId()));
        Request request = new Request(sender.getUniqueId(), target.getUniqueId(), kit, rounds, map, now + settings.expiry * 1000L);
        requests.put(new RequestKey(request.sender, request.target), request);
        cooldowns.put(sender.getUniqueId(), now + settings.cooldown * 1000L);
        Map<String, String> values = Map.of("player", sender.getName(), "target", target.getName(), "kit", kit, "rounds", "" + rounds, "map", map);
        tell(sender, "requested", values); tell(target, "received", values);
        target.sendMessage(text(settings.text("accept-button"), values)
                .clickEvent(playerButton(target, settings.expiry, actor -> respond(actor, request, true)))
                .hoverEvent(HoverEvent.showText(text(settings.text("accept-hover"), values)))
                .append(text(settings.text("deny-button"), values)
                        .clickEvent(playerButton(target, settings.expiry, actor -> respond(actor, request, false)))
                        .hoverEvent(HoverEvent.showText(text(settings.text("deny-hover"), values)))));
    }

    ClickEvent playerButton(Player owner, long seconds, Consumer<Player> action) {
        return ClickEvent.callback(audience -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner.getUniqueId()) || !isEnabled()) return;
            Bukkit.getScheduler().runTask(this, () -> { if (actor.isOnline() && isEnabled()) action.accept(actor); });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(seconds)).build());
    }
    void respond(Player target, Request request, boolean accepted) {
        RequestKey key = new RequestKey(request.sender, request.target);
        if (!target.getUniqueId().equals(request.target)) return;
        Player sender = Bukkit.getPlayer(request.sender);
        if (requests.get(key) != request || request.expires <= System.currentTimeMillis() || sender == null || !target.canSee(sender)) {
            tell(target, "expired"); return;
        }
        if (!target.hasPermission("frostduels.use")) { tell(target, "no-permission"); return; }
        if (accepted) accept(target, sender);
        else if (requests.remove(key, request)) { tell(target, "denied"); tell(sender, "denied-sender", Map.of("player", target.getName())); }
    }

    void accept(Player target, Player sender) {
        Request request = requests.remove(new RequestKey(sender.getUniqueId(), target.getUniqueId()));
        if (request == null || request.expires <= System.currentTimeMillis()) { tell(target, "expired"); return; }
        if (!available(sender) || !available(target) || !sender.hasPermission("frostduels.use")) { tell(target, "unavailable"); return; }
        DuelStore.Kit kit = store.kit(request.kit);
        if (kit == null) { tell(target, "not-found"); return; }
        if (store.lobby() == null || store.lobby().getWorld() == null) { tell(target, "not-ready"); return; }
        List<DuelStore.Arena> free = store.arenas().stream().filter(DuelStore.Arena::ready)
                .filter(a -> request.map.equals("random") || request.map.equals(a.id()))
                .filter(a -> !busy(a.id()) && !terrain.locked.contains(a.id())).toList();
        if (free.isEmpty()) { tell(target, "no-arena"); return; }
        Rules rules = settings.rules(request.kit);
        if (rules.terrain()) free = free.stream().filter(terrain::has).toList();
        if (free.isEmpty()) { tell(target, "snapshot-required"); return; }
        DuelStore.Arena arena = free.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(free.size()));
        Match match = new Match(sender, target, arena, kit, request.rounds, rules);
        try {
            terrain.markDirty(arena);
            sender.closeInventory(); target.closeInventory();
            store.snapshot(sender); store.snapshot(target);
        } catch (Exception ex) {
            log(ex); recover(sender, false); recover(target, false);
            try { terrain.reset(arena, true); } catch (Exception resetError) { log(resetError); }
            tell(target, "io-error"); return;
        }
        playing.put(sender.getUniqueId(), match); playing.put(target.getUniqueId(), match);
        purge(sender.getUniqueId()); purge(target.getUniqueId());
        tell(sender, "starting", Map.of("opponent", target.getName(), "arena", arena.id(), "rounds", "" + request.rounds));
        tell(target, "starting", Map.of("opponent", sender.getName(), "arena", arena.id(), "rounds", "" + request.rounds));
        leaveButton(sender, match); leaveButton(target, match);
        prepareRound(match);
    }

    private void leaveButton(Player player, Match match) {
        player.sendMessage(text(settings.text("leave-button"), Map.of())
                .hoverEvent(HoverEvent.showText(text(settings.text("leave-hover"), Map.of())))
                .clickEvent(playerButton(player, (2L * settings.roundLimit + settings.countdown + 2) * (2L * match.state.required - 1) + 60,
                        actor -> { if (playing.get(actor.getUniqueId()) == match) leave(actor, true); })));
    }

    protected boolean available(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && !playing.containsKey(player.getUniqueId()) && !store.pending(player.getUniqueId());
    }
    boolean busy(String arena) { return playing.values().stream().anyMatch(match -> match.arena.id().equals(arena)); }
    void purge(UUID id) { requests.entrySet().removeIf(e -> e.getKey().sender.equals(id) || e.getKey().target.equals(id)); }

    private void prepareRound(Match match) {
        Player first = Bukkit.getPlayer(match.state.first), second = Bukkit.getPlayer(match.state.second);
        if (first == null || second == null) { finish(match, "start-failed", Map.of()); return; }
        try {
            cleanup(match);
            terrain.reset(match.arena, false);
            if (!teleport(first, match.arena.first()) || !teleport(second, match.arena.second())) {
                finish(match, "start-failed", Map.of()); return;
            }
            match.kit.equip(first); match.kit.equip(second);
            applyFood(first, match.rules); applyFood(second, match.rules);
            match.countdown = settings.countdown; match.elapsed = 0;
            announce(match, "countdown", Map.of("seconds", "" + match.countdown));
        } catch (Exception ex) { log(ex); finish(match, "start-failed", Map.of()); }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        requests.values().removeIf(request -> request.expires < now);
        cooldowns.values().removeIf(expiry -> expiry < now);
        for (Match match : new HashSet<>(playing.values())) {
            try {
                Player first = Bukkit.getPlayer(match.state.first), second = Bukkit.getPlayer(match.state.second);
                if (first == null || second == null) { finish(match, "start-failed", Map.of()); continue; }
                if (!match.rules.saturation()) { first.setSaturation(0); second.setSaturation(0); }
                if (++match.elapsed > settings.roundLimit) { finish(match, "timeout", Map.of()); continue; }
                if (match.state.phase() == MatchState.Phase.COUNTDOWN) {
                    if (--match.countdown <= 0) { match.state.fight(); match.elapsed = 0; announce(match, "fight", Map.of()); }
                    else announce(match, "countdown", Map.of("seconds", "" + match.countdown));
                } else if (match.state.phase() == MatchState.Phase.BETWEEN_ROUNDS && !first.isDead() && !second.isDead()) {
                    match.state.nextRound(); prepareRound(match);
                }
            } catch (Exception ex) { log(ex); finish(match, "start-failed", Map.of()); }
        }
    }

    void lostRound(Player loser) {
        Match match = playing.get(loser.getUniqueId());
        if (match == null || !match.state.point(match.state.opponent(loser.getUniqueId()))) return;
        String winner = loser.getUniqueId().equals(match.state.first) ? match.secondName : match.firstName;
        Map<String, String> values = scores(match, winner);
        cleanup(match);
        announce(match, "score", values);
        if (match.state.phase() == MatchState.Phase.ENDED) finish(match, "won", values);
        else match.elapsed = 0;
    }

    void leave(Player loser, boolean explicit) {
        Match match = playing.get(loser.getUniqueId());
        if (match == null) { if (explicit) tell(loser, "not-playing"); return; }
        String winner = match.state.first.equals(loser.getUniqueId()) ? match.secondName : match.firstName;
        finish(match, "forfeit", Map.of("loser", loser.getName(), "winner", winner));
        if (explicit) tell(loser, "left");
    }

    void finish(Match match, String reason, Map<String, String> values) {
        if (playing.get(match.state.first) != match && playing.get(match.state.second) != match) return;
        match.state.end(); announce(match, reason, values); cleanup(match);
        playing.remove(match.state.first, match); playing.remove(match.state.second, match);
        Player first = Bukkit.getPlayer(match.state.first), second = Bukkit.getPlayer(match.state.second);
        if (first != null && !first.isDead()) recover(first, false);
        if (second != null && !second.isDead()) recover(second, false);
        try { terrain.reset(match.arena, true); }
        catch (Exception ex) { log(ex); if (first != null) tell(first, "arena-locked"); if (second != null) tell(second, "arena-locked"); }
    }

    protected boolean recover(Player player, boolean notify) {
        if (!store.pending(player.getUniqueId())) { if (notify) tell(player, "no-recovery"); return true; }
        if (playing.containsKey(player.getUniqueId()) || player.isDead()) return false;
        try {
            if (!teleport(player, store.recoveryDestination(player))) throw new IOException("Recovery teleport was cancelled");
            store.restore(player);
            if (notify) tell(player, "recovered");
            return true;
        } catch (Exception ex) { log(ex); tell(player, "io-error"); return false; }
    }

    boolean teleport(Player player, Location location) {
        internalTeleports.add(player.getUniqueId());
        try { return player.teleport(location); }
        finally { internalTeleports.remove(player.getUniqueId()); }
    }

    boolean inArena(Location location) { return store.arenas().stream().anyMatch(a -> a.contains(location, settings.padding)); }
    Match at(Location location) { return playing.values().stream().filter(match -> match.arena.contains(location, settings.padding)).findFirst().orElse(null); }
    boolean recovering(Player player) { return !playing.containsKey(player.getUniqueId()) && store.pending(player.getUniqueId()); }
    void cleanup(Match match) {
        for (UUID id : match.entities) { var entity = Bukkit.getEntity(id); if (entity != null) entity.remove(); }
        match.entities.clear();
        match.id = UUID.randomUUID();
    }

    private Map<String, String> scores(Match match, String winner) {
        return Map.of("winner", winner, "first", match.firstName, "second", match.secondName,
                "score1", "" + match.state.firstScore(), "score2", "" + match.state.secondScore());
    }
    private void announce(Match match, String key, Map<String, String> values) {
        Player first = Bukkit.getPlayer(match.state.first), second = Bukkit.getPlayer(match.state.second);
        if (first != null) tell(first, key, values); if (second != null) tell(second, key, values);
    }
    protected void tell(CommandSender sender, String key) { tell(sender, key, Map.of()); }
    void tell(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(text(settings.text("prefix") + settings.text(key), values));
    }
    Component text(String text, Map<String, String> values) {
        for (var value : values.entrySet()) text = text.replace("%" + value.getKey() + "%", value.getValue());
        return colors.deserialize(text);
    }
    void log(Exception ex) { getLogger().severe(ex.getClass().getSimpleName() + ": " + ex.getMessage()); }
    private void applyFood(Player player, Rules rules) {
        player.setFoodLevel(rules.food()); player.setSaturation(rules.saturation() ? rules.startingSaturation() : 0);
        if (!rules.saturation()) player.removePotionEffect(org.bukkit.potion.PotionEffectType.SATURATION);
        if (!rules.regeneration() || !rules.potionRegeneration()) player.removePotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION);
    }
}
