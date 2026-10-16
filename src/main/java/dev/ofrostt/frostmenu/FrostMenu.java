package dev.ofrostt.frostmenu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public class FrostMenu extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final LegacyComponentSerializer colors = LegacyComponentSerializer.legacyAmpersand();
    private Settings settings;

    static final class Session {
        final UUID owner, target;
        final String targetName;
        final long expires;
        Settings.Mode mode;
        int rounds;
        String map;
        long view;
        String categoryName = "";
        final Map<String, Integer> selections = new HashMap<>();
        Session(Player player, Player target, Settings settings) {
            owner = player.getUniqueId(); this.target = target.getUniqueId(); targetName = target.getName();
            expires = System.currentTimeMillis() + settings.timeout * 1000L;
            rounds = settings.defaultRounds; map = settings.arenas.getFirst().id();
        }
    }

    private static final class Menu implements InventoryHolder {
        final Session session;
        final Settings.Category category;
        final long view;
        Inventory inventory;
        Menu(Session session, Settings.Category category) { this.session = session; this.category = category; view = ++session.view; }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        try { settings = new Settings(new File(getDataFolder(), "config.yml")); }
        catch (Exception ex) {
            getLogger().severe("Invalid config.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this); return;
        }
        Objects.requireNonNull(getCommand("frostmenu")).setExecutor(this);
        getCommand("frostmenu").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> sessions.entrySet().removeIf(e -> e.getValue().expires < System.currentTimeMillis()), 200, 200);
    }

    @Override public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof Menu) player.closeInventory();
            if (sessions.containsKey(player.getUniqueId())) player.closeDialog();
        }
        sessions.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("frostmenu.reload")) { message(sender, "no-permission", Map.of()); return true; }
            try {
                Settings fresh = new Settings(new File(getDataFolder(), "config.yml"));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Inventory top = player.getOpenInventory().getTopInventory();
                    if (top != null && top.getHolder() instanceof Menu) player.closeInventory();
                    if (sessions.containsKey(player.getUniqueId())) player.closeDialog();
                }
                sessions.clear(); settings = fresh;
                message(sender, "reloaded", Map.of());
            } catch (Exception ex) {
                getLogger().warning("Configuration reload rejected: " + ex.getMessage());
                message(sender, "reload-failed", Map.of());
            }
            return true;
        }
        if (!(sender instanceof Player player)) { message(sender, "players-only", Map.of()); return true; }
        if (!player.hasPermission("frostmenu.use")) { message(player, "no-permission", Map.of()); return true; }
        if (args.length != 1) { message(player, "usage", Map.of()); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) { message(player, "player-not-found", Map.of()); return true; }
        if (target.equals(player)) { message(player, "self-duel", Map.of()); return true; }
        player.closeDialog();
        Session session = new Session(player, target, settings);
        sessions.put(player.getUniqueId(), session);
        openMenu(player, session, settings.paged ? settings.categories.getFirst() : null);
        return true;
    }

    private void openMenu(Player player, Session session, Settings.Category category) {
        if (category != null) {
            session.categoryName = plain(category.icon().name());
            if (settings.paged) session.mode = category.modes().get(session.selections.getOrDefault(category.id(), 0));
        }
        Menu holder = new Menu(session, category);
        String title = category == null ? settings.text("menus.categories.title") : category.title();
        if (settings.paged && title.equals("&aDuel &f%target%")) title = "&8Queue: %category%";
        holder.inventory = Bukkit.createInventory(holder, (category == null ? settings.categoryRows : category.rows()) * 9,
                render(title, player, session));
        if (settings.fillerEnabled) {
            ItemStack filler = new ItemStack(settings.fillerMaterial);
            filler.editMeta(meta -> meta.displayName(Component.text(" ")));
            for (int slot = 0; slot < holder.inventory.getSize(); slot++) holder.inventory.setItem(slot, filler);
        }
        if (category == null) {
            for (Settings.Category cat : settings.categories) holder.inventory.setItem(cat.icon().slot(), item(cat.icon(), player, session));
        } else {
            for (Settings.Mode mode : category.modes()) {
                ItemStack stack = item(mode.icon(), player, session);
                if (settings.paged) stack.editMeta(meta -> meta.setEnchantmentGlintOverride(mode == session.mode));
                if (settings.paged && mode == session.mode) stack.editMeta(meta -> {
                    meta.setEnchantmentGlintOverride(true);
                    List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                    lore.add(render(settings.text("menus.navigation.selected-lore"), player, session));
                    meta.lore(lore);
                });
                holder.inventory.setItem(mode.icon().slot(), stack);
            }
            if (settings.paged) {
                int page = settings.categories.indexOf(category);
                if (page > 0) holder.inventory.setItem(settings.previousPage.slot(), item(settings.previousPage, player, session));
                if (page + 1 < settings.categories.size()) holder.inventory.setItem(settings.nextPage.slot(), item(settings.nextPage, player, session));
                for (Settings.Icon icon : List.of(settings.previousKit, settings.confirm, settings.nextKit)) {
                    holder.inventory.setItem(icon.slot(), item(icon, player, session));
                }
            } else holder.inventory.setItem(settings.back.slot(), item(settings.back, player, session));
        }
        player.openInventory(holder.inventory);
    }

    private ItemStack item(Settings.Icon icon, Player player, Session session) {
        ItemStack item = new ItemStack(icon.material());
        item.editMeta(meta -> {
            meta.displayName(render(icon.name(), player, session));
            meta.lore(icon.lore().stream().map(line -> render(line, player, session)).toList());
        });
        return item;
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true); // Includes shift-clicks, number keys, drops, and bottom-inventory transfers.
        if (!(event.getWhoClicked() instanceof Player player) || !event.isLeftClick()
                || event.getRawSlot() < 0 || event.getRawSlot() >= menu.inventory.getSize()) return;
        Session session = menu.session;
        if (!valid(player, session) || menu.view != session.view) return;
        int slot = event.getRawSlot();
        if (settings.paged && menu.category != null) { pagedClick(player, menu, slot); return; }
        Settings.Category next = null;
        Settings.Mode mode = null;
        if (menu.category == null) {
            next = settings.categories.stream().filter(cat -> cat.icon().slot() == slot).findFirst().orElse(null);
            if (next == null) return;
        } else if (slot != settings.back.slot()) {
            mode = menu.category.modes().stream().filter(value -> value.icon().slot() == slot).findFirst().orElse(null);
            if (mode == null) return;
        }
        long ticket = ++session.view; // Immediately invalidate this inventory against duplicate clicks.
        Settings.Category selectedCategory = next;
        Settings.Mode selectedMode = mode;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!valid(player, session) || session.view != ticket) return;
            if (selectedMode != null) {
                session.mode = selectedMode;
                player.closeInventory();
                confirm(player, session);
            } else openMenu(player, session, selectedCategory);
        });
    }

    private void pagedClick(Player player, Menu menu, int slot) {
        Session session = menu.session;
        Settings.Category category = menu.category;
        int page = settings.categories.indexOf(category);
        int selection = session.selections.getOrDefault(category.id(), 0);
        boolean openDialog = slot == settings.confirm.slot();
        Settings.Category destination = category;
        if (slot == settings.previousPage.slot()) {
            if (page == 0) return;
            destination = settings.categories.get(page - 1);
        } else if (slot == settings.nextPage.slot()) {
            if (page + 1 >= settings.categories.size()) return;
            destination = settings.categories.get(page + 1);
        } else if (slot == settings.previousKit.slot()) selection = Math.floorMod(selection - 1, category.modes().size());
        else if (slot == settings.nextKit.slot()) selection = (selection + 1) % category.modes().size();
        else if (!openDialog) {
            selection = -1;
            for (int index = 0; index < category.modes().size(); index++) {
                if (category.modes().get(index).icon().slot() == slot) { selection = index; break; }
            }
            if (selection < 0) return;
        }
        int selected = selection;
        Settings.Category next = destination;
        long ticket = ++session.view;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!valid(player, session) || session.view != ticket) return;
            session.selections.put(category.id(), selected);
            if (openDialog) {
                session.mode = category.modes().get(selected);
                player.closeInventory(); confirm(player, session);
            } else openMenu(player, session, next);
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.entrySet().removeIf(entry -> entry.getKey().equals(id) || entry.getValue().target.equals(id));
    }

    protected void confirm(Player player, Session session) {
        long view = ++session.view;
        var rounds = DialogInput.numberRange("rounds", render(settings.text("dialog.rounds.label"), player, session),
                settings.minRounds, settings.maxRounds).initial((float) session.rounds).step(1f).width(settings.sliderWidth).build();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("advanced", player, session, view, response -> {
            if (readRounds(player, session, response)) advanced(player, session);
        }));
        buttons.add(ActionButton.create(render(settings.text("dialog.buttons.map"), player, session), null, settings.buttonWidth,
                callback(player, session, view, response -> {
                    if (readRounds(player, session, response)) maps(player, session);
                })));
        buttons.add(button("send", player, session, view, response -> {
            if (readRounds(player, session, response)) send(player, session);
        }));
        show(player, settings.text("dialog.title"), session, settings.lines("dialog.body"), List.of(rounds), buttons,
                settings.showClose ? button("close", player, session, view, response -> sessions.remove(player.getUniqueId(), session)) : null);
    }

    private void advanced(Player player, Session session) {
        long view = ++session.view;
        show(player, settings.text("dialog.advanced.title"), session, settings.lines("dialog.advanced.body"), List.of(),
                List.of(button("back", player, session, view, response -> confirm(player, session))),
                button("close", player, session, view, response -> sessions.remove(player.getUniqueId(), session)));
    }

    private void maps(Player player, Session session) {
        long view = ++session.view;
        List<ActionButton> buttons = new ArrayList<>();
        for (Settings.Arena arena : settings.arenas) {
            buttons.add(ActionButton.create(render(arena.label(), player, session), null, settings.buttonWidth,
                    callback(player, session, view, response -> { session.map = arena.id(); confirm(player, session); })));
        }
        show(player, settings.text("dialog.map-title"), session, List.of(), List.of(), buttons,
                button("back", player, session, view, response -> confirm(player, session)));
    }

    private ActionButton button(String key, Player player, Session session, long view, Consumer<DialogResponseView> action) {
        return ActionButton.create(render(settings.text("dialog.buttons." + key), player, session), null, settings.buttonWidth,
                callback(player, session, view, action));
    }

    private DialogAction callback(Player owner, Session session, long view, Consumer<DialogResponseView> action) {
        return DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player actor) || !actor.getUniqueId().equals(owner.getUniqueId())) return;
            Bukkit.getScheduler().runTask(this, () -> {
                if (!valid(actor, session) || session.view != view) return;
                ++session.view; // Only one button from a given dialog may take effect.
                action.accept(response);
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofSeconds(settings.timeout)).build());
    }

    private void show(Player player, String title, Session session, List<String> body,
                      List<? extends DialogInput> inputs, List<ActionButton> actions, ActionButton exit) {
        DialogBase base = DialogBase.builder(render(title, player, session)).canCloseWithEscape(true)
                .afterAction(DialogBase.DialogAfterAction.CLOSE)
                .body(body.stream().map(line -> DialogBody.plainMessage(render(line, player, session), settings.bodyWidth)).toList())
                .inputs(inputs).build();
        player.showDialog(Dialog.create(builder -> builder.empty().base(base).type(DialogType.multiAction(actions, exit, 1))));
    }

    private boolean readRounds(Player player, Session session, DialogResponseView response) {
        try { session.rounds = DuelRules.rounds(response.getFloat("rounds"), settings.minRounds, settings.maxRounds); return true; }
        catch (IllegalArgumentException ex) {
            message(player, "invalid-response", values(player, session));
            confirm(player, session); return false;
        }
    }

    private void send(Player player, Session session) {
        if (!valid(player, session)) return;
        String command = DuelRules.expand(session.mode.command(), values(player, session));
        String root = command.split(" ", 2)[0];
        Command registered = Bukkit.getCommandMap().getCommand(root);
        if (registered == null || registered == getCommand("frostmenu")) {
            sessions.remove(player.getUniqueId(), session);
            message(player, "command-unavailable", values(player, session)); return;
        }
        sessions.remove(player.getUniqueId(), session); // Consume before dispatch, including if Cloud rejects the request.
        if (Bukkit.dispatchCommand(player, command)) message(player, "dispatched", values(player, session));
        else message(player, "command-failed", values(player, session));
    }

    private boolean valid(Player player, Session session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session || session.expires < System.currentTimeMillis()) {
            sessions.remove(player.getUniqueId(), session);
            if (player.isOnline()) message(player, "expired", Map.of());
            return false;
        }
        if (!player.hasPermission("frostmenu.use")) {
            sessions.remove(player.getUniqueId(), session); message(player, "no-permission", Map.of()); return false;
        }
        Player target = Bukkit.getPlayer(session.target);
        if (target == null || !player.canSee(target) || !target.getName().equals(session.targetName)) {
            sessions.remove(player.getUniqueId(), session); message(player, "player-not-found", Map.of()); return false;
        }
        return true;
    }

    private Map<String, String> values(Player player, Session session) {
        String mapLabel = settings.arenas.stream().filter(arena -> arena.id().equals(session.map)).findFirst().orElseThrow().label();
        return Map.of("player", player.getName(), "target", session.targetName,
                "kit", session.mode == null ? "" : session.mode.kit(),
                "gamemode", session.mode == null ? "" : session.mode.dialogName(),
                "gamemode-name", session.mode == null ? "" : plain(session.mode.dialogName()),
                "category", session.categoryName,
                "rounds", String.valueOf(session.rounds), "map", session.map, "map-name", mapLabel);
    }

    private Component render(String text, Player player, Session session) {
        return DialogText.render(modernize(text), values(player, session), settings.dialogIcons);
    }

    private String plain(String text) { return PlainTextComponentSerializer.plainText().serialize(colors.deserialize(text)); }

    // Upgrade only the previous shipped labels in memory; retain custom text and server config files.
    private String modernize(String text) {
        return switch (text) {
            case "&a♟ &fRounds" -> "%rounds-icon% &fRounds";
            case "&f📖 View Advanced Options" -> "%advanced-icon% &fView Advanced Options";
            case "&f✦ Map Selector &6(%map-name%&6)" -> "%map-icon% &fMap Selector &6(%map-name%&6)";
            case "&a♟ &fSend Duel" -> "%rounds-icon% &fSend Duel";
            case "&7Kit: &a%gamemode%" -> "&7Kit: %kit-icon% &a&l%gamemode-name%";
            case "&6Confirm Duel! ⚠" -> "&6Confirm Duel!";
            default -> text;
        };
    }

    private void message(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(colors.deserialize(DuelRules.expand(settings.text("messages." + key), values)));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        List<String> names = new ArrayList<>();
        if (sender.hasPermission("frostmenu.use") && sender instanceof Player viewer) {
            for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(viewer) && viewer.canSee(target)) names.add(target.getName());
        }
        if (sender.hasPermission("frostmenu.reload")) names.add("reload");
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
    }
}
