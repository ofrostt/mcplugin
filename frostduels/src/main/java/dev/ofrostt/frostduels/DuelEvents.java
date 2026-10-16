package dev.ofrostt.frostduels;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

final class DuelEvents implements Listener {
    private final FrostDuels plugin;
    private final NamespacedKey entityKey;
    DuelEvents(FrostDuels plugin) { this.plugin = plugin; entityKey = new NamespacedKey(plugin, "match"); }
    private FrostDuels.Match match(Player player) { return plugin.playing.get(player.getUniqueId()); }
    private boolean fighting(FrostDuels.Match match) { return match != null && match.state.phase() == MatchState.Phase.FIGHTING; }
    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof Tameable tame && tame.getOwner() instanceof Player player) return player;
        return null;
    }
    private FrostDuels.Match entityMatch(Entity entity) {
        Player source = attacker(entity);
        if (source != null) return match(source);
        String token = entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        return token == null ? null : plugin.playing.values().stream().filter(match -> match.id.toString().equals(token)).findFirst().orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            if (event instanceof EntityDamageByEntityEvent byEntity) {
                var source = entityMatch(byEntity.getDamager());
                if (source != null && !source.arena.contains(event.getEntity().getLocation(), plugin.settings.padding)) event.setCancelled(true);
            }
            return;
        }
        var match = match(player);
        if ((match == null && (plugin.settings.protectLobby || plugin.recovering(player))) || (match != null && !fighting(match))) {
            event.setCancelled(true); return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            var sourceMatch = entityMatch(byEntity.getDamager());
            if (sourceMatch != null && sourceMatch != match) { event.setCancelled(true); return; }
            Player source = attacker(byEntity.getDamager());
            if (source != null && (match(source) != match || (match != null && !match.state.contains(source.getUniqueId())))) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (match(player) == null && !plugin.store.pending(player.getUniqueId())) return;
        event.setKeepInventory(true); event.setKeepLevel(true); event.getDrops().clear(); event.setDroppedExp(0);
        event.deathMessage(null);
        plugin.lostRound(player);
    }

    @EventHandler public void respawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer(); var match = match(player);
        if (match != null) event.setRespawnLocation(match.state.first.equals(player.getUniqueId()) ? match.arena.first() : match.arena.second());
        else if (plugin.store.pending(player.getUniqueId())) {
            try { event.setRespawnLocation(plugin.store.recoveryDestination(player)); }
            catch (Exception ex) { plugin.log(ex); }
        }
        Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline() && match(player) == null) plugin.recover(player, true); });
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (event.getPlayer().isOnline()) plugin.recover(event.getPlayer(), false); });
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        plugin.purge(event.getPlayer().getUniqueId()); plugin.cooldowns.remove(event.getPlayer().getUniqueId());
        plugin.leave(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !event.hasChangedPosition()) return;
        var match = match(event.getPlayer());
        if (match != null && (!fighting(match) || !match.arena.contains(event.getTo(), plugin.settings.padding))) {
            var back = event.getFrom().clone(); back.setYaw(event.getTo().getYaw()); back.setPitch(event.getTo().getPitch()); event.setTo(back);
        } else if (match == null && plugin.at(event.getTo()) != null) event.setTo(event.getFrom());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (plugin.internalTeleports.contains(player.getUniqueId())) return;
        var match = match(player);
        if (match != null) {
            boolean pearl = event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                    || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT;
            if (!fighting(match) || !pearl || !match.arena.contains(event.getTo(), plugin.settings.padding)) event.setCancelled(true);
        } else if (plugin.at(event.getTo()) != null || plugin.recovering(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (match(player) == null && !plugin.recovering(player)) return;
        String[] words = event.getMessage().substring(1).trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean ours = Set.of("fd", "frostduels", "frostduels:fd", "frostduels:frostduels").contains(words[0]);
        boolean allowed = ours && words.length == 2 && (words[1].equals("leave") || words[1].equals("recover"));
        if (!allowed) { event.setCancelled(true); plugin.tell(player, "command-blocked"); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void food(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var match = match(player);
        if (match == null && plugin.settings.protectLobby) { event.setCancelled(true); return; }
        if (match != null) {
            if (!fighting(match) || (!match.rules.hunger() && event.getFoodLevel() < player.getFoodLevel())) event.setCancelled(true);
            if (!match.rules.saturation()) Bukkit.getScheduler().runTask(plugin, () -> { if (match(player) == match) player.setSaturation(0); });
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void heal(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var match = match(player); if (match == null) return;
        var reason = event.getRegainReason();
        boolean natural = reason == EntityRegainHealthEvent.RegainReason.SATIATED || reason == EntityRegainHealthEvent.RegainReason.REGEN;
        boolean potion = reason == EntityRegainHealthEvent.RegainReason.MAGIC || reason == EntityRegainHealthEvent.RegainReason.MAGIC_REGEN;
        if (!fighting(match) || !match.rules.regeneration() || (natural && !match.rules.naturalRegeneration())
                || (potion && !match.rules.potionRegeneration()) || (!match.rules.saturation() && event.isFastRegen())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void effect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) return;
        var match = match(player); if (match == null) return;
        if ((!match.rules.saturation() && event.getNewEffect().getType().equals(PotionEffectType.SATURATION))
                || ((!match.rules.regeneration() || !match.rules.potionRegeneration()) && event.getNewEffect().getType().equals(PotionEffectType.REGENERATION))) event.setCancelled(true);
    }

    private boolean edit(Player player, Block block, boolean place) {
        var match = match(player);
        if (match != null) return fighting(match) && match.arena.editable(block.getLocation(), plugin.settings.padding)
                && (place ? match.rules.canBuild(block.getY()) : match.rules.canBreak(block.getY()));
        return !plugin.recovering(player) && (!plugin.inArena(block.getLocation()) || (player.hasPermission("frostduels.admin") && plugin.at(block.getLocation()) == null));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        boolean permitted = edit(event.getPlayer(), event.getBlockPlaced(), true);
        if (event instanceof BlockMultiPlaceEvent multiple) for (var state : multiple.getReplacedBlockStates()) permitted &= edit(event.getPlayer(), state.getBlock(), true);
        if (!permitted) { event.setCancelled(true); plugin.tell(event.getPlayer(), "rules-blocked"); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent event) {
        if (!edit(event.getPlayer(), event.getBlock(), false)) { event.setCancelled(true); plugin.tell(event.getPlayer(), "rules-blocked"); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fill(PlayerBucketFillEvent event) {
        if (!edit(event.getPlayer(), event.getBlock(), false)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void empty(PlayerBucketEmptyEvent event) {
        if (!edit(event.getPlayer(), event.getBlock(), true)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        Player player = event.getPlayer(); var match = match(player);
        Block block = event.getClickedBlock();
        if (plugin.recovering(player) || (match != null && !fighting(match))) { event.setCancelled(true); return; }
        if (block != null && plugin.inArena(block.getLocation())) {
            // Prevent inventory extraction, switches, trampling, ignition, etc. while protected.
            boolean terrain = match != null && fighting(match) && match.rules.terrain() && match.arena.contains(block.getLocation(), plugin.settings.padding);
            boolean admin = match == null && player.hasPermission("frostduels.admin") && plugin.at(block.getLocation()) == null;
            if (!terrain && !admin) event.setUseInteractedBlock(Event.Result.DENY);
            if (event.getItem() != null) {
                String type = event.getItem().getType().name();
                boolean tool = type.endsWith("_AXE") || type.endsWith("_HOE") || type.endsWith("_SHOVEL")
                        || type.equals("SHEARS") || type.equals("HONEYCOMB") || type.equals("FLINT_AND_STEEL") || type.equals("FIRE_CHARGE");
                if (tool && !edit(player, block, false)) event.setUseItemInHand(Event.Result.DENY);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void harvest(PlayerHarvestBlockEvent event) {
        if (!edit(event.getPlayer(), event.getHarvestedBlock(), false)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void shear(io.papermc.paper.event.block.PlayerShearBlockEvent event) {
        if (!edit(event.getPlayer(), event.getBlock(), false)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityInteract(PlayerInteractEntityEvent event) {
        var target = plugin.at(event.getRightClicked().getLocation());
        var owner = match(event.getPlayer());
        if (plugin.recovering(event.getPlayer()) || (owner != null && (!fighting(owner) || owner != target))
                || (target != null && owner != target)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        var source = match(event.getPlayer());
        if (plugin.recovering(event.getPlayer()) || (source != null && !fighting(source))) { event.setCancelled(true); return; }
        if (event.getCaught() instanceof Player player && match(player) != source) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (plugin.recovering(player) || match(player) != null) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void inventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && plugin.recovering(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void inventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && plugin.recovering(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drop(PlayerDropItemEvent event) {
        if (match(event.getPlayer()) != null || plugin.recovering(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        String token = event.getItem().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (token != null && (!(event.getEntity() instanceof Player participant) || match(participant) == null || !match(participant).id.toString().equals(token))) {
            event.setCancelled(true); return;
        }
        if (event.getEntity() instanceof Player player) {
            var match = match(player);
            if (plugin.recovering(player) || (match != null && (!fighting(match) || !match.id.toString().equals(event.getItem().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING))))) event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void experience(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent event) {
        String token = event.getExperienceOrb().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        var match = match(event.getPlayer());
        if (plugin.recovering(event.getPlayer()) || (token != null && (match == null || !match.id.toString().equals(token)))
                || (match != null && !fighting(match))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mergeExperience(com.destroystokyo.paper.event.entity.ExperienceOrbMergeEvent event) {
        String from = event.getMergeSource().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        String to = event.getMergeTarget().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (!Objects.equals(from, to)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        Location from = event.getSource().getLocation(), to = event.getDestination().getLocation();
        if (from != null && to != null && !environment(from.getBlock(), to.getBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopperPickup(InventoryPickupItemEvent event) {
        if (event.getItem().getPersistentDataContainer().has(entityKey)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer(); if (player == null) return;
        var match = match(player);
        if (plugin.recovering(player) || (match != null && (!fighting(match) || !match.arena.editable(event.getEntity().getLocation(), plugin.settings.padding)))
                || (match == null && plugin.at(event.getEntity().getLocation()) != null)) event.setCancelled(true);
        else if (match != null) tag(event.getEntity(), match);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectile(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            var match = match(player);
            if (plugin.recovering(player) || (match != null && !fighting(match))) event.setCancelled(true);
            else if (match != null) tag(event.getEntity(), match);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        Player source = event.getPotion().getShooter() instanceof Player player ? player : null;
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (source != null && match(source) != null && !match(source).arena.contains(entity.getLocation(), plugin.settings.padding)) { event.setIntensity(entity, 0); continue; }
            if (entity instanceof Player player && match(player) != null && (source == null || match(source) != match(player) || !fighting(match(player)))) event.setIntensity(entity, 0);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        Player source = event.getEntity().getSource() instanceof Player player ? player : null;
        event.getAffectedEntities().removeIf(entity -> (source != null && match(source) != null && !match(source).arena.contains(entity.getLocation(), plugin.settings.padding))
                || (entity instanceof Player player && match(player) != null && (source == null || match(source) != match(player) || !fighting(match(player)))));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(EntitySpawnEvent event) {
        var match = plugin.at(event.getLocation());
        if (match != null && !(event.getEntity() instanceof Player)) tag(event.getEntity(), match);
    }
    private void tag(Entity entity, FrostDuels.Match match) {
        match.entities.add(entity.getUniqueId()); entity.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, match.id.toString());
    }
    @EventHandler public void load(EntitiesLoadEvent event) {
        clean(event.getEntities());
    }
    void cleanupLoaded() { for (World world : Bukkit.getWorlds()) clean(world.getEntities()); }
    private void clean(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            String token = entity.getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
            if (token != null && plugin.playing.values().stream().noneMatch(match -> match.id.toString().equals(token))) entity.remove();
        }
    }

    private boolean environment(Block source, Block destination) {
        var match = plugin.at(source.getLocation());
        if (match == null) return !plugin.inArena(source.getLocation()) && !plugin.inArena(destination.getLocation());
        return fighting(match) && match.rules.terrain() && match.arena.editable(destination.getLocation(), plugin.settings.padding)
                && destination.getY() >= Math.min(match.rules.minBreakY(), match.rules.minBuildY())
                && destination.getY() <= Math.max(match.rules.maxBreakY(), match.rules.maxBuildY());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void flow(BlockFromToEvent event) { if (!environment(event.getBlock(), event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent event) { if (!environment(event.getSource(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void grow(BlockGrowEvent event) { if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void form(BlockFormEvent event) { if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void decay(LeavesDecayEvent event) { if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent event) {
        for (var state : event.getBlocks()) if (!environment(event.getBlock(), state.getBlock())) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void sponge(SpongeAbsorbEvent event) {
        event.getBlocks().removeIf(state -> !environment(event.getBlock(), state.getBlock()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void tree(org.bukkit.event.world.StructureGrowEvent event) {
        for (var state : event.getBlocks()) if (!environment(event.getLocation().getBlock(), state.getBlock())) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(org.bukkit.event.world.PortalCreateEvent event) {
        for (var state : event.getBlocks()) if (plugin.inArena(state.getLocation())) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fade(BlockFadeEvent event) { if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) { if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) { if (!edit(event.getPlayer(), event.getBlock(), true)) event.setCancelled(true); }
        else if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityBlock(EntityChangeBlockEvent event) {
        if (!environment(event.getBlock(), event.getBlock())) event.setCancelled(true);
        String token = event.getEntity().getPersistentDataContainer().get(entityKey, PersistentDataType.STRING);
        if (token != null) {
            var match = plugin.at(event.getBlock().getLocation());
            if (match == null || !match.id.toString().equals(token)) { event.setCancelled(true); event.getEntity().remove(); }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        if (!environment(event.getBlock(), event.getBlock().getRelative(event.getDirection()))) { event.setCancelled(true); return; }
        for (Block block : event.getBlocks()) if (!environment(block, block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent event) {
        if (!environment(event.getBlock(), event.getBlock().getRelative(event.getDirection()))) { event.setCancelled(true); return; }
        for (Block block : event.getBlocks()) if (!environment(block, block.getRelative(event.getDirection()))) { event.setCancelled(true); return; }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explode(EntityExplodeEvent event) { explosion(event.getLocation(), event.blockList(), entityMatch(event.getEntity())); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void explodeBlock(BlockExplodeEvent event) { explosion(event.getBlock().getLocation(), event.blockList(), null); }
    private void explosion(Location origin, List<Block> blocks, FrostDuels.Match source) {
        var match = source == null ? plugin.at(origin) : source;
        blocks.removeIf(block -> {
            if (match != null) return !fighting(match) || !match.arena.editable(block.getLocation(), plugin.settings.padding) || !match.rules.canExplode(block.getY());
            return plugin.inArena(block.getLocation());
        });
    }
}
