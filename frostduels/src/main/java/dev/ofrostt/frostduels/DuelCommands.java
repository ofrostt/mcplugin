package dev.ofrostt.frostduels;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

final class DuelCommands implements CommandExecutor, TabCompleter {
    private final FrostDuels plugin;
    DuelCommands(FrostDuels plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            plugin.tell(sender, "help"); if (sender.hasPermission("frostduels.admin")) plugin.tell(sender, "admin-help"); return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        boolean admin = Set.of("kit", "arena", "setlobby", "reload", "stop").contains(action);
        if (!sender.hasPermission(admin ? "frostduels.admin" : "frostduels.use")) { plugin.tell(sender, "no-permission"); return true; }
        if (sender instanceof Player player && (plugin.playing.containsKey(player.getUniqueId()) || plugin.recovering(player))
                && !action.equals("leave") && !action.equals("recover") && !action.equals("stop")) {
            plugin.tell(player, "command-blocked"); return true;
        }
        try {
            if (admin) admin(sender, action, args);
            else if (sender instanceof Player player) player(player, action, args);
            else plugin.tell(sender, "players-only");
        } catch (NumberFormatException ex) { plugin.tell(sender, "invalid-rounds", Map.of("max", "" + plugin.settings.maxRounds)); }
        catch (IllegalArgumentException ex) { plugin.tell(sender, "bad-arguments"); }
        catch (Exception ex) { plugin.log(ex); plugin.tell(sender, "io-error"); }
        return true;
    }

    private void player(Player player, String action, String[] args) {
        switch (action) {
            case "duel" -> {
                if (args.length < 3 || args.length > 5) { plugin.tell(player, "bad-arguments"); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !player.canSee(target)) { plugin.tell(player, "not-found"); return; }
                int rounds = args.length >= 4 ? Integer.parseInt(args[3]) : 1;
                String map = args.length == 5 ? args[4] : "random";
                plugin.request(player, target, args[2], rounds, map);
            }
            case "accept", "deny" -> {
                if (args.length != 2) { plugin.tell(player, "bad-arguments"); return; }
                Player challenger = Bukkit.getPlayerExact(args[1]);
                if (challenger == null || !player.canSee(challenger)) { plugin.tell(player, "expired"); return; }
                if (action.equals("accept")) plugin.accept(player, challenger);
                else {
                    var request = plugin.requests.remove(new FrostDuels.RequestKey(challenger.getUniqueId(), player.getUniqueId()));
                    if (request == null || request.expires() <= System.currentTimeMillis()) { plugin.tell(player, "expired"); return; }
                    plugin.tell(player, "denied"); plugin.tell(challenger, "denied-sender", Map.of("player", player.getName()));
                }
            }
            case "leave" -> {
                if (args.length != 1) { plugin.tell(player, "bad-arguments"); return; }
                if (plugin.recovering(player)) plugin.recover(player, true); else plugin.leave(player, true);
            }
            case "recover" -> {
                if (args.length != 1) { plugin.tell(player, "bad-arguments"); return; }
                if (plugin.playing.containsKey(player.getUniqueId())) { plugin.tell(player, "unavailable"); return; }
                plugin.recover(player, true);
            }
            default -> plugin.tell(player, "bad-arguments");
        }
    }

    private void admin(CommandSender sender, String action, String[] args) throws Exception {
        switch (action) {
            case "reload" -> {
                if (args.length != 1) { plugin.tell(sender, "bad-arguments"); return; }
                if (!plugin.playing.isEmpty()) { plugin.tell(sender, "reload-busy"); return; }
                try {
                    DuelConfig fresh = plugin.readSettings(); plugin.validateArenas(fresh);
                    plugin.settings = fresh; plugin.requests.clear(); plugin.tell(sender, "reloaded");
                } catch (Exception ex) { plugin.log(ex); plugin.tell(sender, "config-error"); }
            }
            case "stop" -> {
                if (args.length != 2) { plugin.tell(sender, "bad-arguments"); return; }
                Player player = Bukkit.getPlayerExact(args[1]);
                var match = player == null ? null : plugin.playing.get(player.getUniqueId());
                if (match == null) plugin.tell(sender, "not-playing");
                else { plugin.finish(match, "stopped", Map.of()); plugin.tell(sender, "stopped"); }
            }
            case "setlobby" -> {
                if (args.length != 1) { plugin.tell(sender, "bad-arguments"); return; }
                if (!(sender instanceof Player player)) { plugin.tell(sender, "players-only"); return; }
                if (plugin.inArena(player.getLocation())) { plugin.tell(sender, "arena-invalid"); return; }
                plugin.store.lobby(player.getLocation()); plugin.tell(sender, "saved", Map.of("id", "lobby"));
            }
            case "kit", "arena" -> data(sender, action, args);
            default -> plugin.tell(sender, "bad-arguments");
        }
    }

    private void data(CommandSender sender, String type, String[] args) throws Exception {
        if (args.length < 2) { plugin.tell(sender, "bad-arguments"); return; }
        String section = type.equals("kit") ? "kits" : "arenas";
        String operation = args[1].toLowerCase(Locale.ROOT);
        if (operation.equals("list") && args.length == 2) {
            plugin.tell(sender, "list", Map.of("kind", section, "entries", String.join(", ", plugin.store.ids(section)))); return;
        }
        if (args.length < 3 || args.length > 5) { plugin.tell(sender, "bad-arguments"); return; }
        String id;
        try { id = DuelStore.id(args[2]); }
        catch (IllegalArgumentException ex) { plugin.tell(sender, "invalid-id"); return; }
        if (type.equals("arena") && id.equalsIgnoreCase("random")) { plugin.tell(sender, "invalid-id"); return; }
        boolean exists = plugin.store.ids(section).contains(id);
        if (type.equals("arena") && plugin.busy(id)) { plugin.tell(sender, "arena-busy"); return; }
        if (type.equals("arena") && operation.equals("reset") && args.length == 3) {
            if (!exists) { plugin.tell(sender, "not-found"); return; }
            var arena = plugin.store.arena(id);
            for (Player player : Bukkit.getOnlinePlayers()) if (arena.contains(player.getLocation(), plugin.settings.padding)) {
                if (plugin.store.lobby() == null || !plugin.teleport(player, plugin.store.lobby())) { plugin.tell(sender, "start-failed"); return; }
            }
            plugin.terrain.reset(plugin.store.arena(id), true); plugin.tell(sender, "saved", Map.of("id", id)); return;
        }
        if (type.equals("arena") && plugin.terrain.locked.contains(id)) { plugin.tell(sender, "arena-locked"); return; }
        if (operation.equals("delete") && args.length == 3) {
            if (!exists) { plugin.tell(sender, "not-found"); return; }
            plugin.store.delete(section, id); plugin.tell(sender, "deleted", Map.of("id", id)); return;
        }
        if (type.equals("arena") && operation.equals("create") && args.length == 3) {
            if (exists) { plugin.tell(sender, "exists"); return; }
            plugin.store.createArena(id); plugin.tell(sender, "saved", Map.of("id", id)); return;
        }
        if (type.equals("arena") && operation.equals("snapshot") && args.length == 5) {
            if (!exists) { plugin.tell(sender, "not-found"); return; }
            try { plugin.terrain.snapshot(plugin.store.arena(id), Integer.parseInt(args[3]), Integer.parseInt(args[4])); }
            catch (IllegalArgumentException ex) { plugin.tell(sender, "snapshot-limit"); return; }
            plugin.tell(sender, "saved", Map.of("id", id)); return;
        }
        if (!(sender instanceof Player player)) { plugin.tell(sender, "players-only"); return; }
        if (type.equals("kit") && operation.equals("save") && args.length == 3) {
            player.closeInventory(); plugin.store.saveKit(id, player); plugin.tell(player, "saved", Map.of("id", id)); return;
        }
        if (type.equals("kit") && operation.equals("load") && args.length == 3) {
            var kit = plugin.store.kit(id);
            if (kit == null) { plugin.tell(sender, "not-found"); return; }
            player.closeInventory(); player.getInventory().setContents(kit.contents());
            for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            player.addPotionEffects(kit.effects()); plugin.tell(sender, "loaded", Map.of("id", id)); return;
        }
        if (type.equals("arena") && operation.equals("setspawn") && args.length == 4 && Set.of("1", "2").contains(args[3])) {
            if (!exists) { plugin.tell(sender, "not-found"); return; }
            int number = Integer.parseInt(args[3]);
            var old = plugin.store.arena(id);
            Location location = player.getLocation();
            Location first = number == 1 ? location : old.first(), second = number == 2 ? location : old.second();
            var updated = new DuelStore.Arena(id, first, second, old.minY(), old.maxY());
            if (first != null && second != null && (!updated.ready()
                    || plugin.store.arenas().stream().anyMatch(arena -> !arena.id().equals(id) && updated.overlaps(arena, plugin.settings.padding))
                    || (plugin.store.lobby() != null && updated.contains(plugin.store.lobby(), plugin.settings.padding)))) {
                plugin.tell(sender, "arena-invalid"); return;
            }
            plugin.store.spawn(id, number, location); plugin.tell(sender, "saved", Map.of("id", id)); return;
        }
        plugin.tell(sender, "bad-arguments");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("frostduels.use") && !sender.hasPermission("frostduels.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("frostduels.use")) options.addAll(List.of("help", "duel", "accept", "deny", "leave", "recover"));
            if (sender.hasPermission("frostduels.admin")) options.addAll(List.of("kit", "arena", "setlobby", "stop", "reload"));
        } else if (args.length == 2 && Set.of("duel", "accept", "deny", "stop").contains(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) if (!(sender instanceof Player viewer) || viewer.canSee(player)) options.add(player.getName());
        } else if (args.length == 2 && sender.hasPermission("frostduels.admin")) {
            if (args[0].equals("kit")) options.addAll(List.of("save", "load", "delete", "list"));
            if (args[0].equals("arena")) options.addAll(List.of("create", "delete", "list", "setspawn", "snapshot", "reset"));
        } else if (args.length == 3) {
            if (args[0].equals("duel")) options.addAll(plugin.store.ids("kits"));
            if (args[0].equals("kit") && sender.hasPermission("frostduels.admin")) options.addAll(plugin.store.ids("kits"));
            if (args[0].equals("arena") && sender.hasPermission("frostduels.admin")) options.addAll(plugin.store.ids("arenas"));
        } else if (args.length == 4 && args[0].equals("arena") && args[1].equals("setspawn") && sender.hasPermission("frostduels.admin")) options.addAll(List.of("1", "2"));
        else if (args.length == 4 && args[0].equals("duel")) options.addAll(List.of("1", "3", "5"));
        else if (args.length == 5 && args[0].equals("duel")) { options.add("random"); options.addAll(plugin.store.ids("arenas")); }
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).sorted().toList();
    }
}
