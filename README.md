# FrostMenu + FrostDuels

Two plugins for a dedicated **Paper 1.21.11 / Java 21** duel server. **Cloud is no longer required.**

## Download from GitHub

Open [Build plugins in GitHub Actions](https://github.com/ofrostt/mcplugin/actions/workflows/build.yml), select a successful run, then click **FrostPlugins** under **Artifacts**. Sign in to GitHub if prompted. Extract the downloaded ZIP to get both plugin JARs. Artifacts expire after 30 days; use **Run workflow** on the repository's main branch to generate a fresh download when needed. Each push also builds and tests both plugins automatically.

`/frostmenu <player>` → kit pages → choose with Previous/Next Kit → Confirm → native confirmation dialog → FrostDuels challenge → opponent accepts → countdown → first-to-N match → return to lobby.

FrostMenu handles configurable inventory menus and native dialog text. FrostDuels handles kits, arena reservation, challenge acceptance, rounds, gameplay rules, terrain snapshots, and player recovery. Install both JARs. No web service or database is needed.

## Install and first duel

1. Build with JDK 21 and Maven 3.9+: `bash build-all.sh`. Outputs:
   - `target/FrostMenu-1.2.0.jar`
   - `frostduels/target/FrostDuels-1.0.0.jar`
2. Stop the server, remove PakMenu and Cloud, and put both new JARs in `plugins/`. Start Paper 1.21.11.
3. Stand at a safe lobby **outside all arena regions**, then run `/fd setlobby` as an operator.
4. Put the desired kit in your inventory, including armor and offhand. Apply any starting potion effects you want saved. Run `/fd kit save Sword-IO`.
5. Run `/fd arena create arena1`. Stand at the first spawn and run `/fd arena setspawn arena1 1`; stand at the opponent's spawn and run `/fd arena setspawn arena1 2`. Look in the directions players should face before saving the spawns.
6. For terrain-changing kits, save a template with `/fd arena snapshot arena1 32 128` (use heights appropriate to your arena). The region extends horizontally from the two spawns plus `arena-padding-blocks` on each side, and vertically from Y 32 through 128 in this example. Templates are capped at 1,000,000 blocks. Include the complete arena and safe margins; both spawns must fit inside.
7. With two players online, run `/frostmenu OtherPlayer`. Use the page arrows to change kit groups, use Previous/Next Kit to move the glowing highlight, then click Confirm. Set rounds in the dialog and click Send Duel. The other player clicks **ACCEPT** or runs `/fd accept YourName`.

The default menu lists PakMenu's example kit IDs. Create those kits or change the IDs in `plugins/FrostMenu/config.yml`. Menu entries do not create kits automatically.

When upgrading from the previous FrostMenu build, existing config files are preserved. Change `integration.duel-command` in the existing **FrostMenu** config to:

```yaml
integration:
  duel-command: frostduels:frostduels duel %target% %kit% %rounds% %map%
```

Then run `/frostmenu reload`. Update any per-gamemode `command` overrides too. New installs already use this template. `/duel <player>` remains a FrostMenu alias; `/frostmenu <player>` is unambiguous if another plugin owns `/duel`.

FrostMenu 1.2 opens kit pages directly. Bottom-left/right page arrows change groups, the two inner arrows cycle the highlighted kit, and the center Confirm button opens the dialog. Clicking a kit only highlights it, so a mouse click on a kit is optional. Selection is remembered separately on each page. Mouse hovering or keyboard arrow navigation is not used; these are server inventory buttons, so no client mod is needed.

Existing configuration files remain unchanged: missing new navigation and sprite settings are supplied from built-in defaults. Existing custom headings, kit IDs, and item slots are preserved. Previous shipped emoji labels are upgraded to native item icons in memory; custom text is retained. Existing five-row default layouts have room for the controls; if your custom kit slots use 36/38/40/42/44, move either those kits or the controls. To adopt the compact new layout, put kit icons at slots 10–16, followed by 19–25. Copy new settings from `src/main/resources/config.yml` into your server config when you want to edit them.

## All commands

`/fd` aliases `/frostduels`. All commands also have namespaced forms, such as `/frostduels:frostduels accept Name`.

| Command | Purpose | Permission |
| --- | --- | --- |
| `/frostmenu <player>` | Open kit pages with arrow selection and a Confirm button | `frostmenu.use` |
| `/duel <player>` | Alias for the menu, subject to command conflicts | `frostmenu.use` |
| `/frostmenu reload` | Reload menu headings, items, messages and integration | `frostmenu.reload` |
| `/fd help` | Show player commands and, for admins, admin commands | None |
| `/fd duel <player> <kit> [rounds] [map]` | Send a challenge directly; defaults to 1 round and random arena | `frostduels.use` |
| `/fd accept <player>` | Accept that player's current challenge | `frostduels.use` |
| `/fd deny <player>` | Reject that challenge | `frostduels.use` |
| `/fd leave` | Forfeit and return to the lobby | `frostduels.use` |
| `/fd recover` | Retry pending pre-duel state recovery outside a match | `frostduels.use` |
| `/fd kit save <id>` | Create or overwrite a kit from your inventory, armor, offhand, and active effects | `frostduels.admin` |
| `/fd kit load <id>` | Replace your current inventory and active effects with a kit for editing | `frostduels.admin` |
| `/fd kit delete <id>` | Delete a saved kit | `frostduels.admin` |
| `/fd kit list` | List saved kit IDs | `frostduels.admin` |
| `/fd arena create <id>` | Create an arena entry | `frostduels.admin` |
| `/fd arena setspawn <id> <spawn-number>` | Set one spawn to your position and facing; invalidates its old snapshot | `frostduels.admin` |
| `/fd arena snapshot <id> <minY> <maxY>` | Save original blocks and block entity data, including containers | `frostduels.admin` |
| `/fd arena reset <id>` | Reset an idle arena; moves players inside it to the lobby and retries a failed reset | `frostduels.admin` |
| `/fd arena delete <id>` | Delete an idle, clean arena entry | `frostduels.admin` |
| `/fd arena list` | List arena IDs, including incomplete entries | `frostduels.admin` |
| `/fd setlobby` | Set the return lobby to your position | `frostduels.admin` |
| `/fd stop <player>` | Stop that player's match without declaring a winner | `frostduels.admin` |
| `/fd reload` | Reload gameplay rules and messages; requires no active matches | `frostduels.admin` |

Player permissions default to true; admin/reload permissions default to operators. A kit ID is case-sensitive, 1–48 letters/digits/underscores/hyphens. `random` is reserved as a map choice. Kit loading replaces an administrator's current inventory/effects, so store any personal items before using it. To edit a saved kit: load it, change your equipment/effects, then save the same ID again.

## Configure gameplay rules

Edit `plugins/FrostDuels/config.yml`. `rules` supplies global defaults. `kit-rules.<exact-kit-id>` overrides any subset. For example:

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

Run `/fd reload` while no matches are active. `regeneration: false` blocks all normal health-regain events. With it true, `natural-regeneration` controls hunger/peaceful regeneration and `potion-regeneration` controls instant and over-time magic healing. `saturation: false` removes saturation effects, starts at zero saturation, clears saturation gained from food, and blocks the fast saturation-regeneration event. `hunger: false` prevents food-level decreases. Kit food/saturation values apply at every round start. Other plugins directly changing health or blocks can bypass Bukkit events; FrostDuels does not override their code.

Y limits are **inclusive block coordinates**. Break and place limits are independent. Terrain edits also require the selected arena's saved snapshot. All edits must remain inside that snapshot, with its outermost one-block shell protected to stop changes spreading beyond the reset area. For the example limits above, snapshot something wider vertically, such as Y 32–128. Player movement is contained to the arena region; ender pearls and chorus fruit cannot escape it.

`terrain-damage` is the master terrain switch. When false, building, breaking and explosion terrain damage must also be false. When true, building, breaking and explosion damage have independent toggles. Fluids, fire, growth, falling blocks and pistons are constrained to the active arena's editable interior. Explosion block damage also respects the mining Y range. The snapshot includes blocks and their block entity data; it does **not** rewind biomes, scheduled ticks, or pre-existing entities. Use dedicated, entity-free arenas without redstone/fluids crossing the protected boundary. Arena regions must not overlap or include the lobby.

A saved template is restored before each round and after the match. A durable dirty marker is written before using it. After restart, dirty arenas are reset before reuse; failed resets keep the arena locked. `/fd arena reset <id>` retries recovery. Changing arena padding invalidates templates for new matches; resolve dirty arenas with the original padding first, then change it and save fresh templates. Capture/reset runs on the server thread, so keep snapshot volumes modest.

## Menu customization and maps

Edit `plugins/FrostMenu/config.yml` and run `/frostmenu reload`:

| Setting | Controls |
| --- | --- |
| `messages.*` | Usage, permissions, errors, reload and dispatch text |
| `menus.categories.title` | Category menu heading |
| `categories.<id>.title` | Kit page heading |
| `menus.navigation.*` | Enable page navigation; set arrow/Confirm items, labels, lore and slots |
| `menus.filler.*` | Enable and choose the glass-pane background |
| Item `display-name`, `lore`, `material`, `slot` | Category/gamemode/back button appearance |
| `dialog.title`, `dialog.body` | Confirmation dialog heading and text |
| `dialog.buttons.*` | Dialog button labels |
| `dialog.rounds.*` | Rounds slider label, range and starting value |
| `dialog.button-width`, `dialog.slider-width`, `dialog.body-width` | Independent native widths (default 300 pixels) |
| `dialog.show-close-button` | Show/hide the confirmation Close button; Escape still closes it |
| `dialog.icons.*` | Native atlas and sprite keys for sword, dye, book, and map icons |
| `dialog.advanced.title`, `dialog.advanced.body` | Advanced information page |
| `dialog.map-title`, `maps` | Map selector heading and selectable arena IDs |
| `integration.duel-command` | Integration command template |

All text supports `&` color codes. Menu placeholders: `%player%`, `%target%`, `%category%`, `%gamemode%`, `%gamemode-name%` (without formatting codes), `%kit%`, `%rounds%`, `%map%`, `%map-name%`. Each gamemode can optionally set `dialog-name` separately from its inventory `display-name`. `%kit-icon%`, `%rounds-icon%`, `%advanced-icon%`, and `%map-icon%` insert native item sprites; the defaults use the Minecraft 1.21.11 `minecraft:items` atlas. Command templates support `%player%`, `%target%`, `%kit%`, `%rounds%`, `%map%`. FrostDuels messages are separately editable under its own `messages` section, including accept/deny buttons. Keep the menu's maximum rounds within FrostDuels' `max-rounds`.

To add a specific arena to the dialog's Map Selector:

```yaml
maps:
  random: '&6Random'
  arena1: '&aArena One'
```

To add a kit item, merge an entry into an existing category's `gamemodes` section:

```yaml
CustomSword:
  material: DIAMOND_SWORD
  display-name: '&bCustom Sword'
  lore: ['&7Choose this kit']
  slot: 28
  kit: MySavedSwordKit
```

Use `/fd kit save MySavedSwordKit` to actually create that kit. Slots start at zero and must be unique; reserve 36/38/40/42/44 for the default navigation controls. Set `menus.navigation.enabled: false` to restore the legacy category picker and immediate kit-click dialog; that mode uses the old back button at slot 44. The Advanced Options button is an editable information page; gameplay settings are administrator configuration, not player-selectable rule overrides. The dialog follows the reference screenshot's single-column structure, with real item sprites and matching button/slider widths; exact placement and screen scaling are controlled by Minecraft. No resource pack is required.

## Persistence and match behavior

Challenges expire and have a configurable sender cooldown. Requests are revalidated on acceptance, and only one match can reserve an arena. Leaving or disconnecting forfeits; a timeout, administrative stop, or clean shutdown aborts the match. Between rounds, dead players use the normal Minecraft respawn screen; the next countdown begins once both players have respawned. A player who does not respawn before the configured round time limit causes an abort.

Before equipping kits, inventory and player state are atomically saved under `plugins/FrostDuels/recovery/`. Match completion restores them and returns players to the lobby. Offline/dead players recover on join/respawn. If teleport or restore fails, the recovery file remains and `/fd recover` retries; pending players cannot start another match. Commands are restricted to leave/recover during a match, containers cannot be opened, drops are blocked, and tagged match drops/XP cannot be picked up by outsiders. Do not delete recovery files to resolve errors; fix the underlying destination or file error and retry recovery.

Kit and arena definitions are stored in `plugins/FrostDuels/data.yml`. Use commands for inventory saves and arena edits. Back up this data along with the world's normal backups. Online match scores and requests are not resumed across restarts.

## Validation

`bash build-all.sh` compiles both plugins against Paper 1.21.11 and runs the automated suites. Tests exercise menus, request acceptance/expiry, first-to-N scoring, duplicate event protection, arena reservation, forfeit and shutdown recovery, persistent kit/player data, per-kit saturation/regeneration, build/mine height event limits, explosion containment, and missing-template locks.

MockBukkit cannot render native dialogs or capture/place native structure templates. **In-game client rendering and native terrain restoration have not been validated by these tests.** Before regular use, test two players in a copied arena: verify confirmation and acceptance; multiple rounds and respawns; actual block/container restoration after building/mining/explosions; per-kit healing rules; quit/rejoin and server restart recovery. Full power-loss/filesystem durability is not simulated by the automated suite.
