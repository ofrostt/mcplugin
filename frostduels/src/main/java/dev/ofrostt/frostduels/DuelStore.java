package dev.ofrostt.frostduels;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/** All writes use replacement files; player recovery is saved BEFORE any kit is equipped. */
final class DuelStore {
    record Kit(String id, ItemStack[] contents, List<PotionEffect> effects) {
        void equip(Player player) {
            player.getInventory().setContents(copy(contents));
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            player.setGameMode(GameMode.SURVIVAL);
            player.setAllowFlight(false); player.setFlying(false);
            player.setInvulnerable(false);
            player.setAbsorptionAmount(0); player.addPotionEffects(effects);
            player.setHealth(player.getMaxHealth()); player.setFoodLevel(20); player.setSaturation(20);
            player.setExhaustion(0); player.setFireTicks(0); player.setFreezeTicks(0);
            player.setFallDistance(0); player.setRemainingAir(player.getMaximumAir());
            player.setLevel(0); player.setExp(0); player.setTotalExperience(0);
            player.getInventory().setHeldItemSlot(0);
            for (ItemStack item : contents) if (item != null && !item.getType().isAir()) player.setCooldown(item.getType(), 0);
        }
    }

    record Arena(String id, Location first, Location second, int minY, int maxY) {
        boolean ready() {
            return first != null && second != null && first.getWorld() != null && second.getWorld() != null
                    && first.getWorld().equals(second.getWorld()) && first.distanceSquared(second) >= 4;
        }
        boolean contains(Location location, int padding) {
            return ready() && first.getWorld().equals(location.getWorld())
                    && location.getX() >= minX(padding) && location.getX() < maxX(padding)
                    && location.getZ() >= minZ(padding) && location.getZ() < maxZ(padding)
                    && location.getY() >= minY && location.getY() < maxY + 1;
        }
        boolean overlaps(Arena other, int padding) {
            return ready() && other.ready() && first.getWorld().equals(other.first.getWorld())
                    && minX(padding) <= other.maxX(padding) && maxX(padding) >= other.minX(padding)
                    && minZ(padding) <= other.maxZ(padding) && maxZ(padding) >= other.minZ(padding);
        }
        boolean editable(Location location, int pad) {
            return contains(location, pad) && location.getBlockX() > minX(pad) && location.getBlockX() < maxX(pad) - 1
                    && location.getBlockZ() > minZ(pad) && location.getBlockZ() < maxZ(pad) - 1
                    && location.getBlockY() > minY && location.getBlockY() < maxY;
        }
        int minX(int pad) { return Math.min(first.getBlockX(), second.getBlockX()) - pad; }
        int maxX(int pad) { return Math.max(first.getBlockX(), second.getBlockX()) + pad + 1; }
        int minZ(int pad) { return Math.min(first.getBlockZ(), second.getBlockZ()) - pad; }
        int maxZ(int pad) { return Math.max(first.getBlockZ(), second.getBlockZ()) + pad + 1; }
    }

    final Path folder, recovery;
    private YamlConfiguration data;
    DuelStore(File folder) throws Exception {
        this.folder = folder.toPath(); recovery = this.folder.resolve("recovery");
        Files.createDirectories(recovery);
        data = new YamlConfiguration();
        if (Files.exists(this.folder.resolve("data.yml"))) data.load(this.folder.resolve("data.yml").toFile());
    }

    static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,48}")) throw new IllegalArgumentException("Invalid ID");
        return value;
    }
    Set<String> ids(String section) {
        var node = data.getConfigurationSection(section);
        return node == null ? Set.of() : new TreeSet<>(node.getKeys(false));
    }
    Kit kit(String id) {
        if (!ids("kits").contains(id)) return null;
        return new Kit(id, items(data.getList("kits." + id + ".contents")), effects(data.getList("kits." + id + ".effects")));
    }
    void saveKit(String id, Player player) throws Exception {
        YamlConfiguration fresh = cloneData();
        fresh.set("kits." + id + ".contents", Arrays.asList(copy(player.getInventory().getContents())));
        fresh.set("kits." + id + ".effects", new ArrayList<>(player.getActivePotionEffects()));
        commit(fresh);
    }
    Arena arena(String id) {
        if (!ids("arenas").contains(id)) return null;
        Location first = data.getLocation("arenas." + id + ".first");
        int bottom = first == null || first.getWorld() == null ? -64 : first.getWorld().getMinHeight();
        int top = first == null || first.getWorld() == null ? 319 : first.getWorld().getMaxHeight() - 1;
        return new Arena(id, first, data.getLocation("arenas." + id + ".second"),
                data.getInt("arenas." + id + ".template.min-y", bottom), data.getInt("arenas." + id + ".template.max-y", top));
    }
    List<Arena> arenas() { return ids("arenas").stream().map(this::arena).toList(); }
    void createArena(String id) throws Exception {
        YamlConfiguration fresh = cloneData(); fresh.createSection("arenas." + id); commit(fresh);
    }
    void spawn(String id, int number, Location location) throws Exception {
        YamlConfiguration fresh = cloneData();
        fresh.set("arenas." + id + (number == 1 ? ".first" : ".second"), location);
        fresh.set("arenas." + id + ".template", null); commit(fresh);
    }
    Location templateOrigin(String arena) { return data.getLocation("arenas." + arena + ".template.origin"); }
    void template(String arena, Location origin, int minY, int maxY, int padding, String filename) throws Exception {
        YamlConfiguration fresh = cloneData(); String path = "arenas." + arena + ".template.";
        fresh.set(path + "origin", origin); fresh.set(path + "min-y", minY); fresh.set(path + "max-y", maxY);
        fresh.set(path + "padding", padding); fresh.set(path + "file", filename); commit(fresh);
    }
    int templatePadding(String arena) { return data.getInt("arenas." + arena + ".template.padding", -1); }
    String templateFile(String arena) { return data.getString("arenas." + arena + ".template.file", "missing.nbt"); }
    Location lobby() { return data.getLocation("lobby"); }
    void lobby(Location location) throws Exception {
        YamlConfiguration fresh = cloneData(); fresh.set("lobby", location); commit(fresh);
    }
    void delete(String section, String id) throws Exception {
        YamlConfiguration fresh = cloneData(); fresh.set(section + "." + id, null); commit(fresh);
    }

    private YamlConfiguration cloneData() throws Exception {
        YamlConfiguration fresh = new YamlConfiguration(); fresh.loadFromString(data.saveToString()); return fresh;
    }
    private void commit(YamlConfiguration fresh) throws IOException { atomic(folder.resolve("data.yml"), fresh); data = fresh; }

    boolean pending(UUID id) { return Files.exists(recovery.resolve(id + ".yml")); }

    void snapshot(Player player) throws IOException {
        Path path = recovery.resolve(player.getUniqueId() + ".yml");
        if (Files.exists(path)) throw new IOException("Existing recovery must be restored before a new match");
        YamlConfiguration state = new YamlConfiguration();
        state.set("inventory", Arrays.asList(copy(player.getInventory().getContents())));
        state.set("effects", new ArrayList<>(player.getActivePotionEffects()));
        state.set("location", player.getLocation()); state.set("mode", player.getGameMode().name());
        state.set("health", player.getHealth()); state.set("absorption", player.getAbsorptionAmount());
        state.set("food", player.getFoodLevel()); state.set("saturation", player.getSaturation());
        state.set("exhaustion", player.getExhaustion()); state.set("level", player.getLevel());
        state.set("exp", player.getExp()); state.set("total-exp", player.getTotalExperience());
        state.set("slot", player.getInventory().getHeldItemSlot());
        state.set("flight", player.getAllowFlight()); state.set("flying", player.isFlying());
        state.set("invulnerable", player.isInvulnerable());
        state.set("fire", player.getFireTicks()); state.set("air", player.getRemainingAir());
        atomic(path, state);
    }

    // The engine owns teleport permission checks and deletes the file only after restore succeeds.
    Location recoveryDestination(Player player) throws Exception {
        Location lobby = lobby();
        if (lobby != null && lobby.getWorld() != null) return lobby;
        YamlConfiguration state = readRecovery(player.getUniqueId());
        Location original = state.getLocation("location");
        if (original == null || original.getWorld() == null) throw new IOException("Recovery world unavailable");
        return original;
    }
    void restore(Player player) throws Exception {
        YamlConfiguration state = readRecovery(player.getUniqueId());
        // Validate before touching inventory. The recovery file is retained on failure.
        ItemStack[] contents = items(state.getList("inventory"));
        GameMode mode = GameMode.valueOf(Objects.requireNonNull(state.getString("mode")));
        List<PotionEffect> effects = effects(state.getList("effects"));
        player.getInventory().setContents(contents);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setGameMode(mode); player.addPotionEffects(effects);
        player.setHealth(Math.max(0.5, Math.min(player.getMaxHealth(), state.getDouble("health", 20))));
        player.setAbsorptionAmount(state.getDouble("absorption"));
        player.setFoodLevel(state.getInt("food", 20)); player.setSaturation((float) state.getDouble("saturation"));
        player.setExhaustion((float) state.getDouble("exhaustion"));
        player.setLevel(state.getInt("level")); player.setExp((float) state.getDouble("exp"));
        player.setTotalExperience(state.getInt("total-exp"));
        player.getInventory().setHeldItemSlot(state.getInt("slot"));
        player.setAllowFlight(state.getBoolean("flight"));
        player.setFlying(state.getBoolean("flying") && state.getBoolean("flight"));
        player.setInvulnerable(state.getBoolean("invulnerable"));
        player.setFireTicks(state.getInt("fire")); player.setRemainingAir(state.getInt("air", 300));
        player.setFreezeTicks(0); player.setFallDistance(0);
        Files.delete(recovery.resolve(player.getUniqueId() + ".yml"));
    }
    private YamlConfiguration readRecovery(UUID id) throws Exception {
        YamlConfiguration state = new YamlConfiguration(); state.load(recovery.resolve(id + ".yml").toFile()); return state;
    }
    private static ItemStack[] items(List<?> values) {
        if (values == null || (values.size() != 41 && values.size() != 43)) throw new IllegalArgumentException("Inventory must contain 41 or 43 slots");
        ItemStack[] result = new ItemStack[values.size()];
        for (int i = 0; i < result.length; i++) {
            Object value = values.get(i);
            if (value != null && !(value instanceof ItemStack)) throw new IllegalArgumentException("Invalid inventory item");
            result[i] = value == null ? null : ((ItemStack) value).clone();
        }
        return result;
    }
    private static List<PotionEffect> effects(List<?> values) {
        if (values == null) return List.of();
        return values.stream().map(value -> (PotionEffect) value).toList();
    }
    private static ItemStack[] copy(ItemStack[] original) {
        return Arrays.stream(original).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }
    static void atomic(Path path, YamlConfiguration yaml) throws IOException {
        Path temp = Files.createTempFile(path.getParent(), ".frostduels-", ".tmp");
        try {
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
