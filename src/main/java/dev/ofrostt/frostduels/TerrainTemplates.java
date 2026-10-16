package dev.ofrostt.frostduels;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.structure.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

/** Native structure templates include block entity data (for example container contents). */
final class TerrainTemplates {
    private final FrostDuels plugin;
    private final Path folder;
    final Set<String> locked = new HashSet<>();
    TerrainTemplates(FrostDuels plugin) throws IOException {
        this.plugin = plugin; folder = plugin.getDataFolder().toPath().resolve("arenas"); Files.createDirectories(folder);
        try (var files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().endsWith(".dirty.yml")).forEach(p -> {
                String name = p.getFileName().toString(); locked.add(name.substring(0, name.length() - ".dirty.yml".length()));
            });
        }
    }
    boolean has(DuelStore.Arena arena) {
        return plugin.store.templateOrigin(arena.id()) != null && Files.isRegularFile(file(arena))
                && plugin.store.templatePadding(arena.id()) == plugin.settings.padding;
    }
    void snapshot(DuelStore.Arena arena, int minY, int maxY) throws Exception {
        if (!arena.ready() || minY < arena.first().getWorld().getMinHeight() || maxY >= arena.first().getWorld().getMaxHeight()
                || minY > maxY || arena.first().getY() < minY || arena.second().getY() < minY
                || arena.first().getY() + 2 > maxY || arena.second().getY() + 2 > maxY) throw new IllegalArgumentException("Snapshot height invalid");
        int pad = plugin.settings.padding;
        int x = arena.maxX(pad) - arena.minX(pad), y = maxY - minY + 1, z = arena.maxZ(pad) - arena.minZ(pad);
        if ((long) x * y * z > 1_000_000) throw new IllegalArgumentException("Snapshot too large");
        Structure structure = Bukkit.getStructureManager().createStructure();
        Location origin = new Location(arena.first().getWorld(), arena.minX(pad), minY, arena.minZ(pad));
        structure.fill(origin, new BlockVector(x, y, z), false);
        Path temporary = Files.createTempFile(folder, ".template-", ".nbt");
        try {
            Bukkit.getStructureManager().saveStructure(temporary.toFile(), structure);
            String filename = arena.id() + "-" + UUID.randomUUID() + ".nbt";
            // Changing a template is only allowed while idle and clean.
            try { Files.move(temporary, folder.resolve(filename), StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, folder.resolve(filename)); }
            plugin.store.template(arena.id(), origin, minY, maxY, pad, filename);
        } finally { Files.deleteIfExists(temporary); }
    }
    void markDirty(DuelStore.Arena arena) throws IOException {
        if (!has(arena)) return;
        YamlConfiguration marker = new YamlConfiguration(); marker.set("arena", arena.id());
        DuelStore.atomic(folder.resolve(arena.id() + ".dirty.yml"), marker); locked.add(arena.id());
    }
    void reset(DuelStore.Arena arena, boolean release) throws Exception {
        if (!has(arena)) {
            if (locked.contains(arena.id())) throw new IOException("Missing or mismatched template for " + arena.id());
            return;
        }
        Structure structure = Bukkit.getStructureManager().loadStructure(file(arena).toFile());
        structure.place(plugin.store.templateOrigin(arena.id()), false, StructureRotation.NONE, Mirror.NONE, 0, 1f, new Random(0));
        // Flush restored chunks before releasing the durable dirty marker.
        arena.first().getWorld().save();
        if (release) { Files.deleteIfExists(folder.resolve(arena.id() + ".dirty.yml")); locked.remove(arena.id()); }
    }
    private Path file(DuelStore.Arena arena) {
        String name = plugin.store.templateFile(arena.id());
        if (!name.matches("[A-Za-z0-9_-]+\\.nbt")) throw new IllegalArgumentException("Invalid template filename");
        return folder.resolve(name);
    }
    void recoverAll() {
        for (String id : new HashSet<>(locked)) {
            var arena = plugin.store.arena(id);
            if (arena == null) { plugin.getLogger().severe("Cannot recover missing arena " + id); continue; }
            try { reset(arena, true); }
            catch (Exception ex) { plugin.log(ex); }
        }
    }
}
