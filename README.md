# Duels 2.1

**One Paper 1.21.11 / Java 21 plugin.** Install `Duels.jar`. Players use `/duel <player>`; administrators use `/duels`. No `/frostmenu`, `/frostduels`, or `/fd` command is registered.

Players have one command: **`/duel <player>`**. Kit groups are **mctiers.io → Subtiers**. Page arrows change groups, inner arrows cycle the highlighted kit, and Confirm opens the native dialog. Choose rounds and a map, then Send Duel. The opponent clicks **ACCEPT** or **DENY** in chat. During a match, **LEAVE DUEL** in chat forfeits. There are no player slash commands for accepting, declining, or leaving.

## Download and install

Download **Duels.jar** directly from the newest build on [Releases](https://github.com/ofrostt/mcplugin/releases). Each successful main-branch build publishes a standalone JAR. The Actions artifact is also available as **Duels**, but GitHub wraps artifacts in a ZIP.

Stop the server and remove old FrostMenu and FrostDuels JARs, PakMenu, and Cloud before installing the new JAR. **Keep the existing plugin data folders.** Do not run the old and combined plugins together.

The plugin is named Duels and uses `plugins/Duels/`. On the first upgrade, existing `plugins/FrostDuels/` data is copied without overwriting destination files; the source is retained. An older menu is backed up as `menu-before-blank-kits.yml`, then its kit icons are cleared once. Saved loadouts, arena templates, lobby settings, and recovery files are preserved. Existing custom page headings remain. New permissions are `duels.use` (everyone by default) and `duels.admin` (OP by default); update permission-manager assignments if needed.

Files:

| File under `plugins/Duels/` | Purpose |
| --- | --- |
| `config.yml` | Gameplay rules, timers, regeneration, terrain limits, and engine messages |
| `menu.yml` | Kit pages, headings, buttons, native dialog text/icons, and optional map labels |
| `data.yml` | Saved kits, arenas, and lobby; managed by admin commands |
| `arenas/` | Native terrain templates and pending reset markers |
| `recovery/` | Pre-duel player snapshots; retained until restoration succeeds |

Old `integration.duel-command` or per-kit command templates are ignored. Menus call the integrated engine directly and do not execute player command templates. New gameplay/menu settings fall back to built-in defaults without overwriting existing config files.

## Make and edit kits (OP/admin)

1. Equip the desired inventory, armor, offhand, and starting potion effects.
2. Save the loadout with `/duels add Sword`.
3. The command automatically adds your kit to the first menu page. To choose a page, use `/duels add Sword subtiers` when creating it.

To edit: `/duels load Sword`, change your items/effects, then `/duels add Sword` again. **Loading replaces your current inventory and active effects**, so store personal items before loading a kit. Saving an existing ID overwrites that kit.

No kits are preconfigured. Add your own using `/duels add <id> [page]`. IDs are case-sensitive, 1–48 letters/digits/underscores/hyphens.

## Create arenas and choose one

As an operator:

1. Stand at the safe return lobby: `/duels setlobby`.
2. Create an entry: `/duels arena create arena1`.
3. Stand at player one's spawn, facing the desired direction: `/duels arena setspawn arena1 1`.
4. Stand at player two's spawn: `/duels arena setspawn arena1 2`.
5. Save the original terrain: `/duels arena snapshot arena1 32 128`. Replace 32 and 128 with heights enclosing the arena and both spawns, including safe margins.

After both spawns are set, the arena **automatically appears in Map Selector while free and unlocked**. No menu edit is required. Players run `/duel OtherPlayer`, choose a kit and Confirm, click Map Selector, choose `arena1` or Random, then Send Duel. Random selects a free suitable arena on acceptance. A busy/deleted arena is rechecked when the challenge is accepted.

To give an arena a friendly label, optionally add its exact ID under `maps` in `menu.yml`:

```yaml
maps:
  random: '&6Random'
  arena1: '&aDesert Arena'
```

Then `/duels reload`. Labels do not create arenas, and incomplete/busy/locked arenas are not selectable. The identifier `random` is reserved.

Arena regions span horizontally between the two spawns plus `arena-padding-blocks` on every side, and vertically across the saved snapshot's inclusive min/max Y. Arenas must not overlap or include the lobby. Templates are capped at 1,000,000 blocks. Spawn changes invalidate the previous snapshot; save a fresh one afterward. A terrain-changing kit requires a valid snapshot before admission.

## Commands and permissions

| Command | Who | Purpose |
| --- | --- | --- |
| `/duel <player>` | Players | Open kit selection and the confirmation dialog |
| `/duels help` | Admin | Show setup commands |
| `/duels add <id> [page]` | Admin | Save/overwrite your loadout and add its menu icon; page defaults to the first page |
| `/duels load <id>` | Admin | Load a kit for editing; replaces inventory/effects |
| `/duels list` | Admin | List kit IDs |
| `/duels remove <id>` | Admin | Delete a kit and its menu icon |
| `/duels setlobby` | Admin | Save your location as the return lobby |
| `/duels arena create <id>` | Admin | Create an arena |
| `/duels arena setspawn <id> <number>` | Admin | Set spawn 1 or 2 |
| `/duels arena snapshot <id> <minY> <maxY>` | Admin | Capture original terrain and block entity data |
| `/duels arena reset <id>` | Admin | Reset an idle arena, retrying a failed reset |
| `/duels arena list` | Admin | List arena entries |
| `/duels arena delete <id>` | Admin | Delete an idle, clean arena entry |
| `/duels stop <player>` | Admin | Abort that player's match |
| `/duels recover <player>` | Admin | Retry pending restoration for an online player |
| `/duels reload` | Admin | Validate and reload both config files when no match is active |


Accept/deny/leave buttons use Paper callbacks with owner, current-request/current-match, and expiry checks. They do not run exposed backend commands. During a match, other commands are blocked; an administrator can stop a match or use the console. Offline/dead players recover automatically on join/respawn.

## Gameplay rules

Edit `config.yml`, then `/duels reload`. Global `rules` can be overridden per exact kit ID under `kit-rules`:

```yaml
kit-rules:
  UHC-IO:
    saturation: false
    regeneration: true
    natural-regeneration: false
    potion-regeneration: true
    hunger: true
    starting-food: 20
    starting-saturation: 0.0
    terrain-damage: true
    building: true
    breaking: true
    explosion-terrain-damage: false
    min-break-y: 40
    max-break-y: 100
    min-build-y: 40
    max-build-y: 120
```

Y limits are inclusive. Terrain edits additionally stay within the arena snapshot's editable interior, with its outermost one-block shell protected against changes escaping the reset area. For the example limits, snapshot wider heights such as 32–128. `terrain-damage` is the master switch; building, breaking, and explosion terrain damage must be false when it is false. Explosion block damage follows the mining Y range. Fluids, fire, growth, gravity, and pistons are constrained to the active editable region.

`regeneration: false` blocks health-regain events. Otherwise natural and magic/potion healing have independent toggles. Disabling saturation removes saturation effects, clears food saturation, and blocks fast saturation regeneration. Disabling hunger prevents food-level decreases. Starting food/saturation applies every round. Other plugins directly changing health or blocks can bypass Bukkit events.

Templates restore blocks and block entity data (including containers) before rounds and after matches. They do not rewind biomes, scheduled ticks, or pre-existing entities. Use dedicated, entity-free arenas with no redstone/fluid system crossing the boundary. Capture/reset runs on the server thread, so keep volumes modest. Failed or interrupted resets keep the arena locked until `/duels arena reset <id>` succeeds. Resolve dirty arenas with their original padding before changing padding and capturing new templates.

## Menu and dialog customization

Edit `menu.yml`, then `/duels reload`. The page order is `menus.navigation.page-order: [mctiers-io, subtiers]`. Category `title` fields control page headings. `menus.navigation.*` controls page/kit arrows and Confirm; reserve default slots 36/38/40/42/44. Kit icon slots can use 10–16 and 19–25. Icon clicks only highlight; arrows provide selection without clicking the kits themselves.

The gray background is configured under `menus.filler`. Dialog headings/body/buttons, width, round range, optional Close button, and sprite paths are under `dialog`. Default widths are 300 pixels, with native sword, lime dye, book, and map sprites from the Minecraft 1.21.11 item atlas. Kit icons show names without lore or equipment stats by default. `menus.show-lore` and `menus.hide-item-tooltips` control this. Text supports `&` colors. Placeholders: `%player%`, `%target%`, `%category%`, `%gamemode%`, `%gamemode-name%` (without formatting), `%kit%`, `%rounds%`, `%map%`, `%map-name%`, `%kit-icon%`, `%rounds-icon%`, `%advanced-icon%`, and `%map-icon%`.

Add a kit item inside a category's `gamemodes` section:

```yaml
CustomSword:
  material: DIAMOND_SWORD
  display-name: '&bCustom Sword'
  dialog-name: '&aCustom Sword'
  lore: ['&7Use the arrows, then Confirm']
  slot: 19
  kit: MySavedSwordKit
```

Create a loadout and its menu entry with `/duels add MySavedSwordKit`. Use `/duels add MySavedSwordKit subtiers` to add it to Subtiers. The icon uses your main-hand item (or a chest when empty). Both default pages start empty, so add your own kits before playing. After selecting a kit, click **View Advanced Options** to open the rule editor. It has toggles for saturation, regeneration (master/natural/potion), hunger, terrain damage, placing, breaking, and explosion damage; number fields set the inclusive mining/building Y limits and starting food/saturation. **Apply** saves the rules for this challenge and returns to confirmation. **Back** discards unsaved edits. New challenges start from server/per-kit defaults; no global settings are changed. Both players receive the complete selected rule summary before acceptance. Terrain-changing matches still require an arena snapshot and stay inside its protected boundaries.

Every screen heading is editable in `menu.yml`: `menus.categories.title`, `categories.<id>.title`, `dialog.title`, `dialog.advanced.title`, and `dialog.map-title`. The editor labels are under `dialog.advanced.labels`; its help text is `dialog.advanced.body`. Map help text is `dialog.map-body`. Buttons, icons, lore, filler material/name/lore, rows, and control slots are also editable. Existing config files inherit newly introduced settings without overwriting custom values. Exact native dialog placement depends on the Minecraft client and GUI scale. No resource pack is required.

## Recovery and development checks

Inventory and player state are saved before kits are equipped. A finish, forfeit, or shutdown restores them and returns players to the lobby; offline/dead players restore on join/respawn. Failed restores retain recovery files and prevent another match. An admin can retry `/duels recover <player>`. Do not delete recovery files or dirty markers to bypass errors. Requests and scores are not resumed after restart.

Build with JDK 21 and Maven 3.9+: `bash build-all.sh clean` (or `mvn clean verify`). The output is `target/Duels.jar`. Tests cover the combined menu-to-engine handoff, permissions, admin command visibility, guarded chat buttons, map discovery, kit/arena setup, rounds, recovery, rule enforcement, and non-destructive menu migration.

MockBukkit cannot render native dialogs or capture/place native structure templates. Client rendering and actual terrain restoration still need two-player in-game validation in a copied arena. Full power-loss/filesystem durability is not simulated by these tests.
