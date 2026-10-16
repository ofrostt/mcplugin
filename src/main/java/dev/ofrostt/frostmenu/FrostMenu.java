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
import dev.ofrostt.frostduels.FrostDuels;
import dev.ofrostt.frostduels.Rules;

public class FrostMenu extends FrostDuels implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final LegacyComponentSerializer colors = LegacyComponentSerializer.legacyAmpersand();
    private Settings settings;

    static final class Session {
        final UUID owner, target;
        final String targetName;
        final long expires;
        Settings.Mode mode;
        int rounds;
        Rules rules;
        String rulesKit;
        String map;
        long view;
        String categoryName = "";
        final Map<String, Integer> selections = new HashMap<>();
        Session(Player player, Player target, Settings settings) {
            owner = player.getUniqueId(); this.target = target.getUniqueId(); targetName = target.getName();
            expires = System.currentTimeMillis() + settings.timeout * 1000L;
            rounds = settings.defaultRounds; map = "random";
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
        super.onEnable();
        if (!isEnabled()) return;
        try {
            File menu = new File(getDataFolder(), "menu.yml");
            MenuMigration.copyLegacy(getDataFolder().toPath());
            if (!menu.exists()) {
                saveResource("menu.yml", false);
            }
            settings = new Settings(menu);
        } catch (Exception ex) {
            getLogger().severe("Invalid menu.yml: " + ex.getMessage());
            getServer().getPluginManager().disablePlugin(this); return;
        }
        Objects.requireNonNull(getCommand("duel")).setExecutor(this);
        getCommand("duel").setTabCompleter(this);
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
        super.onDisable();
    }

    @Override protected Runnable prepareMenuReload() throws Exception {
        Settings fresh = new Settings(new File(getDataFolder(), "menu.yml"));
        return () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof Menu) player.closeInventory();
                if (sessions.containsKey(player.getUniqueId())) player.closeDialog();
            }
            sessions.clear(); settings = fresh;
        };
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { message(sender, "players-only", Map.of()); return true; }
        if (!player.hasPermission("frostduels.use")) { message(player, "no-permission", Map.of()); return true; }
        if (args.length != 1) { message(player, "usage", Map.of()); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !player.canSee(target)) { message(player, "player-not-found", Map.of()); return true; }
        if (target.equals(player)) { message(player, "self-duel", Map.of()); return true; }
        if (!available(player)) { tell(player, "unavailable"); return true; }
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
        holder.inventory = Bukkit.createInventory(holder, (category == null ? settings.categoryRows : category.rows()) * 9,
                render(title, player, session));
        if (settings.fillerEnabled) {
            ItemStack filler = new ItemStack(settings.fillerMaterial);
            filler.editMeta(meta -> {
                meta.displayName(render(settings.yaml.getString("menus.filler.display-name", " "), player, session));
                meta.lore(settings.lines("menus.filler.lore").stream().map(line -> render(line, player, session)).toList());
            });
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
        if (!session.mode.kit().equals(session.rulesKit)) {
            session.rules = defaultRules(session.mode.kit()); session.rulesKit = session.mode.kit();
        }
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
        List<DialogInput> inputs = new ArrayList<>();
        for (var entry : session.rules.values().entrySet()) {
            String key = entry.getKey();
            Component label = render(settings.text("dialog.advanced.labels." + key), player, session);
            if (entry.getValue() instanceof Boolean flag) {
                inputs.add(DialogInput.bool(key, label).initial(flag).build());
            } else {
                inputs.add(DialogInput.text(key, label).initial(entry.getValue().toString())
                        .width(settings.sliderWidth).maxLength(16).build());
            }
        }
        show(player, settings.text("dialog.advanced.title"), session, settings.lines("dialog.advanced.body"), inputs,
                List.of(button("apply", player, session, view, response -> {
                    try {
                        session.rules = readAdvanced(session.rules, response);
                        confirm(player, session);
                    } catch (IllegalArgumentException ex) {
                        message(player, "invalid-rules", values(player, session)); advanced(player, session);
                    }
                })), button("back", player, session, view, response -> confirm(player, session)));
    }

    static Rules readAdvanced(Rules previous, DialogResponseView response) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (var entry : previous.values().entrySet()) {
            String key = entry.getKey();
            if (entry.getValue() instanceof Boolean) {
                Boolean value = response.getBoolean(key);
                if (value == null) throw new IllegalArgumentException("Missing toggle");
                values.put(key, value);
            } else {
                String value = response.getText(key);
                if (value == null) throw new IllegalArgumentException("Missing number");
                if (entry.getValue() instanceof Integer) values.put(key, Integer.parseInt(value.trim()));
                else values.put(key, Float.parseFloat(value.trim()));
            }
        }
        return Rules.fromValues(values);
    }

    private void maps(Player player, Session session) {
        long view = ++session.view;
        List<ActionButton> buttons = new ArrayList<>();
        for (var arena : availableMaps().entrySet()) {
            buttons.add(ActionButton.create(render(arena.getValue(), player, session), null, settings.buttonWidth,
                    callback(player, session, view, response -> { session.map = arena.getKey(); confirm(player, session); })));
        }
        show(player, settings.text("dialog.map-title"), session, settings.lines("dialog.map-body"), List.of(), buttons,
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

    protected void send(Player player, Session session) {
        if (!valid(player, session)) return;
        Player target = Bukkit.getPlayer(session.target);
        sessions.remove(player.getUniqueId(), session);
        if (target == null) { message(player, "player-not-found", Map.of()); return; }
        try { request(player, target, session.mode.kit(), session.rounds, session.map,
                session.rules == null ? defaultRules(session.mode.kit()) : session.rules); }
        catch (Exception ex) { getLogger().severe("Duel request failed: " + ex.getMessage()); message(player, "request-failed", Map.of()); }
    }

    protected Map<String, String> availableMaps() {
        Map<String, String> configured = new LinkedHashMap<>();
        for (Settings.Arena arena : settings.arenas) configured.put(arena.id(), arena.label());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("random", configured.getOrDefault("random", "&6Random"));
        for (String id : selectableArenas()) result.put(id, configured.getOrDefault(id, id));
        return result;
    }

    private boolean valid(Player player, Session session) {
        if (!player.isOnline() || sessions.get(player.getUniqueId()) != session || session.expires < System.currentTimeMillis()) {
            sessions.remove(player.getUniqueId(), session);
            if (player.isOnline()) message(player, "expired", Map.of());
            return false;
        }
        if (!player.hasPermission("frostduels.use")) {
            sessions.remove(player.getUniqueId(), session); message(player, "no-permission", Map.of()); return false;
        }
        Player target = Bukkit.getPlayer(session.target);
        if (target == null || !player.canSee(target) || !target.getName().equals(session.targetName)) {
            sessions.remove(player.getUniqueId(), session); message(player, "player-not-found", Map.of()); return false;
        }
        return true;
    }

    private Map<String, String> values(Player player, Session session) {
        String mapLabel = settings.arenas.stream().filter(arena -> arena.id().equals(session.map)).findFirst().map(Settings.Arena::label).orElse(session.map);
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
            case "&7Arenas, kits, and match rules are managed by FrostDuels." -> "&7Change the rules below, then click Apply. Back discards unsaved edits.";
            case "&6Confirm Duel! ⚠" -> "&6Confirm Duel!";
            case "&bFrostMenu &8» &7/frostmenu <player> or /frostmenu reload",
                 "&bFrostDuels &8» &7/duel <player>" -> "&bFrostMenu &8» &bⓘ Invalid arguments! Use: /duel <player>";
            case "&cThis menu has expired. Open /frostmenu <player> again." -> "&cThis menu has expired. Open /duel <player> again.";
            default -> text;
        };
    }

    private void message(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(colors.deserialize(DuelRules.expand(modernize(settings.text("messages." + key)), values)));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return List.of();
        List<String> names = new ArrayList<>();
        if (sender.hasPermission("frostduels.use") && sender instanceof Player viewer) {
            for (Player target : Bukkit.getOnlinePlayers()) if (!target.equals(viewer) && viewer.canSee(target)) names.add(target.getName());
        }
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
    }
}
