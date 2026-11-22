# FluxysGUI — handover notes

Notes for picking this project up on another PC (and for Claude Code there, which won't have the
old chat). The code and its comments are the full record; this is the map.

## What it is

A personal Fabric client mod (Minecraft 26.2, Java 25, Mojang mappings) for Hypixel SkyBlock:
clean menus for Hypixel's SkyBlock pages, plus mining "dopamine" animations.

## Build, preview, install

- Build: `./gradlew build` (runs the JUnit tests too; needs JDK 25). Jar: `build/libs/FluxysGUI-<version>.jar`, version in `gradle.properties`.
- Screenshots without Hypixel: `./gradlew runPreview -Ponly=<shot>,<shot>` — shots are listed in
  `src/main/java/cleanui/dev/Preview.java` and land in `run/screenshots/preview-<shot>.png`.
- Install: copy the jar into the Modrinth profile's `mods` folder (on the old PC:
  `%APPDATA%\ModrinthApp\profiles\Ny pack taunahi V5 og ny gamble\mods`).
- Release: `release/FluxysGUI.jar` is the latest build. The GitHub release (tag `HypixelSkyblockMod`)
  holds it as `FluxysGUI-<version>.jar`. **After every change** (owner's standing request): bump the
  version, push, replace the release jar, replace the jar in the Modrinth profile "Fluxy's modpack 0.1.2"
  (folder `%APPDATA%\ModrinthApp\profiles\Fabulously Optimized\mods`) and put a copy on the Desktop.
- That profile also has TaunahiAutoPatch, which crashes any window bigger than 1280×720 (its blur
  buffer is fixed at that size). Not a FluxysGUI bug; the owner plays windowed at 1280×720.

## Mining features (all in `src/main/java/cleanui/feature/`)

| Feature | Files | Test it |
|---|---|---|
| Scatha slots / roulette / "Then I walk in" (buff Scatha) | `ScathaSlots`, `ScathaRoulette`, `BuffScatha`, `ScathaMusic`, `BlockModel` | `/scathaslots [win\|rare\|epic\|legendary]` |
| Scatha Stats panel (pets, Stoneworm/Scatha kills, bestiary sync, pet editor, Tunnel Vision + 30 s spawn cooldown bars) | `ScathaStats`, `ScathaSettingsScreen`, `skyblock/AbilityParser` | shows in the Crystal Hollows |
| Fancy sidebar scoreboard (Scatha Stats frame; title, spacers, date line, footer options) | `ScoreboardHud` | any server with a sidebar |
| Customize HUD: move / resize / options for every panel (grid snap, corner drag, scroll, arrow nudge) | `HudEditor` | Settings → "Customize HUD", or Scatha settings → "Move / resize Scatha Stats" |
| Crystal Nucleus runs: tracker, 6 finales, best-drop popup | `NucleusRun`, `NucleusFinale`, `FinaleModels` (3D), `FinaleScenes` (2D), `NucleusSettingsScreen` | `/nucleustest [gauntlet\|altar\|reactor\|constellation\|crown\|golem]` |
| Vanguard corpse: ouroboros snake or lockpick | `VanguardSnake`, `VanguardLock`, `VanguardSettingsScreen` | `/testvanguard`, `/testlock` `[dye\|locket\|wisp\|key]` |

Settings pages for Scatha, Nucleus and Vanguard open from buttons on the Mining skill page
(`TrackScreen`). All settings live in `data/Config.java` (saved as JSON).

## Things worth knowing

- Hypixel calls the worms **Stoneworm** now. Bestiary page "Crystal Hollows ➜ Stoneworm" lists
  `[Lv119] Stoneworm` and `[Lv181] Scatha` with "Kills: N"; the family item `Stoneworm XV` is both added up.
- Soft glows must use `NucleusFinale.glow` (one textured blit). Stacked `ScathaRoulette.ellipse`
  glows cost thousands of fills a frame and tanked FPS.
- Big HUD takeovers (nucleus finale, Vanguard) swap the whole HUD in `mixin/HudMixin` so you can still walk.
- Prices come from `data/Networth.price(id)`; item ids from `data/SkyblockItems.id(name)`.

## Not verified in game yet

- Scatha Stats: Stoneworm nametag format and the bestiary sync (unreadable worm/Scatha nametags are
  logged as `[Scatha Stats] nametag not read…` in `latest.log`).
- Nucleus best-drop popup on a real run; all sounds.
- Tunnel Vision chat lines ("You used your Tunnel Vision Pickaxe Ability!", "...is now available!",
  "...on cooldown for Ns") are Hypixel's usual pickaxe-ability wording, not yet seen in game.

## How the owner likes to work

- Show it (preview screenshots) before calling a visual done; "make it look better" means more detail and depth.
- Commit only when asked; don't push or publish without asking.
