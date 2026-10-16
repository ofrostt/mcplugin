# FrostDuels 2.0

**One Paper 1.21.11 / Java 21 plugin containing FrostMenu and the duel engine.** Install only `FrostDuels-2.0.0.jar`.

Players have one command: **`/duel <player>`**. Kit groups are **mctiers.io → Subtiers**. Page arrows change groups, inner arrows cycle the highlighted kit, and Confirm opens the native dialog. Choose rounds and a map, then Send Duel. The opponent clicks **ACCEPT** or **DENY** in chat. During a match, **LEAVE DUEL** in chat forfeits. There are no player slash commands for accepting, declining, or leaving.

## Download and install

Open [Build plugin in GitHub Actions](https://github.com/ofrostt/mcplugin/actions/workflows/build.yml), select a successful run, then download **FrostDuels** under **Artifacts** while signed into GitHub. Extract the ZIP to get the single JAR. Artifacts expire after 30 days; Run workflow or a new push generates another build.

Stop the server and remove old FrostMenu and FrostDuels JARs, PakMenu, and Cloud before installing the new JAR. **Keep the existing plugin data folders.** Do not run the old and combined plugins together.

The unified plugin is named FrostDuels, so existing `plugins/FrostDuels/data.yml`, `arenas/`, and `recovery/` stay in use. If `plugins/FrostDuels/menu.yml` does not exist, the old `plugins/FrostMenu/config.yml` is copied there without deleting the old file. An existing `menu.yml` is never overwritten.

Files:

| File under `plugins/FrostDuels/` | Purpose |
| --- | --- |
| `config.yml` | Gameplay rules, timers, regeneration, terrain limits, and engine messages |
| `menu.yml` | Kit pages, headings, buttons, native dialog text/icons, and optional map labels |
| `data.yml` | Saved kits, arenas, and lobby; managed by admin commands |
| `arenas/` | Native terrain templates and pending reset markers |
| `recovery/` | Pre-duel player snapshots; retained until restoration succeeds |

Old `integration.duel-command` or per-kit command templates are ignored. Menus call the integrated engine directly and do not execute player command templates. New gameplay/menu settings fall back to built-in defaults without overwriting existing config files.

## Make and edit kits (OP/admin)

1. Equip the desired inventory, armor, offhand, and starting potion effects.
2. Save the loadout with `/frostmenu kit save Sword-IO`.
3. `Sword-IO` is already the first default menu entry. For another entry, its `kit` value in `menu.yml` must exactly match the saved ID.

To edit: `/frostmenu kit load Sword-IO`, change your items/effects, then `/frostmenu kit save Sword-IO` again. **Loading replaces your current inventory and active effects**, so store personal items before loading a kit. Saving an existing ID overwrites that kit.

The default menu IDs (for example Sword-IO, UHC-IO, Diamond-SMP) are examples; save the corresponding kits or change the menu to match your kit IDs. IDs are case-sensitive, 1–48 letters/digits/underscores/hyphens.

## Create arenas and choose one

As an operator:

1. Stand at the safe return lobby: `/frostmenu setlobby`.
2. Create an entry: `/frostmenu arena create arena1`.
3. Stand at player one's spawn, facing the desired direction: `/frostmenu arena setspawn arena1 1`.
4. Stand at player two's spawn: `/frostmenu arena setspawn arena1 2`.
5. Save the original terrain: `/frostmenu arena snapshot arena1 32 128`. Replace 32 and 128 with heights enclosing the arena and both spawns, including safe margins.

After both spawns are set, the arena **automatically appears in Map Selector while free and unlocked**. No menu edit is required. Players run `/duel OtherPlayer`, choose a kit and Confirm, click Map Selector, choose `arena1` or Random, then Send Duel. Random selects a free suitable arena on acceptance. A busy/deleted arena is rechecked when the challenge is accepted.

To give an arena a friendly label, optionally add its exact ID under `maps` in `menu.yml`:

```yaml
maps:
  random: '&6Random'
  arena1: '&aDesert Arena'
```

Then `/frostmenu reload`. Labels do not create arenas, and incomplete/busy/locked arenas are not selectable. The identifier `random` is reserved.

Arena regions span horizontally between the two spawns plus `arena-padding-blocks` on every side, and vertically across the saved snapshot's inclusive min/max Y. Arenas must not overlap or include the lobby. Templates are capped at 1,000,000 blocks. Spawn changes invalidate the previous snapshot; save a fresh one afterward. A terrain-changing kit requires a valid snapshot before admission.

## Commands and permissions

| Command | Who | Purpose |
| --- | --- | --- |
| `/duel <player>` | Players | Open kit selection and the confirmation dialog |
| `/frostmenu help` | Admin | Show setup commands |
| `/frostmenu kit save <id>` | Admin | Save/overwrite a loadout |
| `/frostmenu kit load <id>` | Admin | Load a kit for editing; replaces inventory/effects |
| `/frostmenu kit list` | Admin | List kit IDs |
| `/frostmenu kit delete <id>` | Admin | Delete a kit |
| `/frostmenu setlobby` | Admin | Save your location as the return lobby |
| `/frostmenu arena create <id>` | Admin | Create an arena |
| `/frostmenu arena setspawn <id> <number>` | Admin | Set spawn 1 or 2 |
| `/frostmenu arena snapshot <id> <minY> <maxY>` | Admin | Capture original terrain and block entity data |
| `/frostmenu arena reset <id>` | Admin | Reset an idle arena, retrying a failed reset |
| `/frostmenu arena list` | Admin | List arena entries |
| `/frostmenu arena delete <id>` | Admin | Delete an idle, clean arena entry |
| `/frostmenu stop <player>` | Admin | Abort that player's match |
| `/frostmenu recover <player>` | Admin | Retry pending restoration for an online player |
| `/frostmenu reload` | Admin | Validate and reload both config files when no match is active |

`frostduels.use` defaults to true. `frostduels.admin` defaults to OP. `/frostduels` and `/fd` remain **admin-only aliases** of `/frostmenu`; normal players cannot use or tab-complete them. `/frostmenu <player>` is no longer a player menu command. Assign the new use permission instead of the old `frostmenu.use` node if managing permissions explicitly.

Accept/deny/leave buttons use Paper callbacks with owner, current-request/current-match, and expiry checks. They do not run exposed backend commands. During a match, other commands are blocked; an administrator can stop a match or use the console. Offline/dead players recover automatically on join/respawn.

## Gameplay rules

Edit `config.yml`, then `/frostmenu reload`. Global `rules` can be overridden per exact kit ID under `kit-rules`:

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

Templates restore blocks and block entity data (including containers) before rounds and after matches. They do not rewind biomes, scheduled ticks, or pre-existing entities. Use dedicated, entity-free arenas with no redstone/fluid system crossing the boundary. Capture/reset runs on the server thread, so keep volumes modest. Failed or interrupted resets keep the arena locked until `/frostmenu arena reset <id>` succeeds. Resolve dirty arenas with their original padding before changing padding and capturing new templates.

## Menu and dialog customization

Edit `menu.yml`, then `/frostmenu reload`. The page order is `menus.navigation.page-order: [mctiers-io, subtiers]`. Category `title` fields control page headings. `menus.navigation.*` controls page/kit arrows and Confirm; reserve default slots 36/38/40/42/44. Kit icon slots can use 10–16 and 19–25. Icon clicks only highlight; arrows provide selection without clicking the kits themselves.

The gray background is configured under `menus.filler`. Dialog headings/body/buttons, width, round range, optional Close button, and sprite paths are under `dialog`. Default widths are 300 pixels, with native sword, lime dye, book, and map sprites from the Minecraft 1.21.11 item atlas. Text supports `&` colors. Placeholders: `%player%`, `%target%`, `%category%`, `%gamemode%`, `%gamemode-name%` (without formatting), `%kit%`, `%rounds%`, `%map%`, `%map-name%`, `%kit-icon%`, `%rounds-icon%`, `%advanced-icon%`, and `%map-icon%`.

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

Create that loadout with `/frostmenu kit save MySavedSwordKit`. Advanced Options is editable information; gameplay rule overrides are admin configuration rather than player controls. Exact native dialog placement depends on the Minecraft client and GUI scale. No resource pack is required.

## Recovery and development checks

Inventory and player state are saved before kits are equipped. A finish, forfeit, or shutdown restores them and returns players to the lobby; offline/dead players restore on join/respawn. Failed restores retain recovery files and prevent another match. An admin can retry `/frostmenu recover <player>`. Do not delete recovery files or dirty markers to bypass errors. Requests and scores are not resumed after restart.

Build with JDK 21 and Maven 3.9+: `bash build-all.sh clean` (or `mvn clean verify`). The output is `target/FrostDuels-2.0.0.jar`. Tests cover the combined menu-to-engine handoff, permissions, admin command visibility, guarded chat buttons, map discovery, kit/arena setup, rounds, recovery, rule enforcement, and non-destructive menu migration.

MockBukkit cannot render native dialogs or capture/place native structure templates. Client rendering and actual terrain restoration still need two-player in-game validation in a copied arena. Full power-loss/filesystem durability is not simulated by these tests.
