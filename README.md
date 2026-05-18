# Minecraft Hardcore Shared-Life

A cooperative **hardcore** Minecraft server where the whole team **shares one
health bar and one hunger bar**. When anyone dies, the **entire world is
wiped and regenerated from scratch** with a new seed, and everyone is sent
back to a lobby showing run statistics before the next attempt.

Built as a server-side **PaperMC plugin** — players join with a vanilla
Minecraft client, no mods required.

---

## Table of Contents

- [Concept & Rules](#concept--rules)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Admin Commands](#admin-commands)
- [Building from Source](#building-from-source)
- [How It Works](#how-it-works)
- [Gameplay Notes](#gameplay-notes)
- [Troubleshooting](#troubleshooting)
- [Validation](#validation)
- [License](#license)

---

## Concept & Rules

- **Shared life & hunger.** All players draw from a single common health
  pool and a single hunger pool. If one player takes damage or burns hunger,
  the whole team feels it. If one player eats, everyone is fed.
- **Hardcore.** If any player dies — or the shared health hits zero — the
  game is over for everyone.
- **Full world regeneration.** On game over, the Overworld, Nether and End
  are unloaded, deleted, and recreated with a brand-new random seed. The next
  run starts from absolute zero.
- **Lobby with stats.** Between runs, players wait in a void hub showing:
  - number of **games played**
  - **last survival time** and the **best record**
  - a **countdown bar** while the world regenerates
- **Auto-restart.** When the countdown ends, everyone is teleported into the
  fresh world and a new run begins automatically.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Game | Minecraft Java Edition | `26.1.2` |
| Server software | [PaperMC](https://papermc.io/) | `26.1.2` build `64` |
| Plugin API | Bukkit / Paper API | `26.1.2.build.64-stable` |
| Runtime | JDK (Eclipse Temurin) — **required by MC 26.1+** | `Java 25` |
| Plugin language | Java (no external deps) | — |
| Build | `javac` + `jar` (no Maven/Gradle) | JDK 25 toolchain |
| Scripts | Windows `.bat` + PowerShell | — |

The plugin only uses the standard Paper/Bukkit API: the event system,
the Bukkit scheduler, a custom `ChunkGenerator` (void lobby), `WorldCreator`
for live world (re)creation, `BossBar` + `Scoreboard` for the lobby UI, and
`YamlConfiguration` for config and stat persistence.

> **A portable JDK 25 is bundled** in the `jdk/` folder, so nothing needs to
> be installed on Windows. Minecraft 26.1+ refuses to run on Java 21 or below.

---

## Quick Start

### Windows (recommended — everything is bundled)

1. Double-click **`start.bat`**.
2. Once you see `Done (X.XXXs)! For help, type "help"`, the server is up.
3. Connect from Minecraft **26.1.2** to `localhost` (port `25565`).

That's it — Java and the server are included in the repo.

### Linux / macOS

The `.bat`/`.ps1` scripts are Windows-specific, but the server itself is
cross-platform. Install **Java 25**, then:

```bash
cd server
java -Xms2G -Xmx4G -jar paper.jar --nogui
```

To play with friends over the internet, port-forward `25565/TCP` (or use a
tunnel) and set `online-mode`/`white-list` appropriately in
`server/server.properties`.

---

## Project Structure

```
.
├── start.bat            # Launches the server (uses the bundled JDK)
├── build.ps1            # Compiles & packages the plugin (javac/jar)
├── rcon.ps1             # Minimal RCON client (testing only)
├── README.md
├── jdk/                 # Portable JDK 25 (Temurin) — runtime & compiler
├── server/              # Paper server: worlds, config, plugins
│   ├── paper.jar
│   ├── server.properties
│   ├── bukkit.yml       # Maps the 'hub' world to the void generator
│   └── plugins/
│       ├── HardcoreShared.jar
│       └── HardcoreShared/
│           ├── config.yml
│           └── stats.yml        # Generated at runtime
└── plugin/
    ├── paper-api.jar    # Compile-time API
    └── src/main/
        ├── java/fr/hardcore/*.java
        └── resources/   # plugin.yml, config.yml
```

---

## Configuration

`server/plugins/HardcoreShared/config.yml` (created on first launch):

| Key | Description | Default |
|-----|-------------|---------|
| `min-players-to-start` | Players required to start a run | `1` |
| `auto-start` | Start a run automatically when ready | `true` |
| `regen-countdown-seconds` | Lobby wait before the next run | `12` |
| `difficulty` | Game world difficulty | `HARD` |
| `nether-enabled` / `end-enabled` | Enable Nether / End | `true` |
| `start-health` / `start-food` | Starting shared health / hunger | `20` |
| `lobby-world` / `game-world` | World names | `hub` / `game` |
| `lobby-spawn.y` | Lobby platform height | `101` |

Apply changes with `/hc reload` in-game (or restart the server).

Relevant `server/server.properties` values (managed): `difficulty=hard`,
`pvp=true`, `hardcore=false` (custom hardcore is handled by the plugin —
vanilla hardcore would ban/spectate on death and break the lobby flow),
`level-name=hub`, `spawn-protection=0`.

---

## Admin Commands

Permission: `hardcoreshared.admin` (OP by default).

| Command | Effect |
|---|---|
| `/hc start` | Force a new run (from the lobby) |
| `/hc stop` | End the current run (triggers regeneration) |
| `/hc stats` | Show statistics and current state |
| `/hc reload` | Reload `config.yml` |

---

## Building from Source

No Maven/Gradle. The build uses the bundled JDK 25 toolchain.

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

This compiles `plugin/src/main/java` against `plugin/paper-api.jar` plus
every jar in `server/libraries/` (Adventure, Bungee-chat and other
transitive API dependencies), then packages everything — classes +
`plugin.yml` + `config.yml` — into `server/plugins/HardcoreShared.jar`.

> Stop the server before rebuilding: a running server locks
> `HardcoreShared.jar` and the build will fail to overwrite it.

### Upgrading Paper / Minecraft

Minecraft now uses `year.major.minor` versioning (e.g. `26.1.2`). Only the
**Paper v3 API** (`fill.papermc.io/v3/projects/paper`) lists these versions —
the old v2 API caps at `1.21.11`. The matching `paper-api` artifact is a
**release** named `<version>.build.<n>-stable` (not a `-SNAPSHOT`) on
`repo.papermc.io`. Newer Minecraft drops may also require a newer JDK.

---

## How It Works

**State machine.** The game cycles through `LOBBY → REGENERATING → RUNNING`.
A per-tick scheduler task drives shared-life sync while `RUNNING` and the
lobby watch/countdown otherwise.

**Shared life/hunger (aggregation model).** Every tick, the plugin measures
each player's delta versus the last synced value (damage taken, hunger
consumed, food eaten, regeneration…), sums all deltas into a common pool,
then re-applies the common value to every player. When the pool reaches zero,
or any `PlayerDeathEvent` fires in the game world, it's game over.

**Void lobby.** The main world `hub` is generated empty by a custom
`ChunkGenerator`. The plugin loads in `STARTUP` so the generator is available
when Paper prepares the default world; world *creation* is deferred to the
first tick (creating worlds during `STARTUP` is forbidden). A small quartz
platform with barrier walls is built around spawn.

**World regeneration.** On game over, players are sent to the lobby, then the
game worlds (`game`, `game_nether`, `game_the_end`) are unloaded, their
folders deleted (with retry to survive transient Windows file locks), and
recreated with a fresh random seed. Nether/End portal linking between these
non-default worlds is handled by a `PlayerPortalEvent` listener.

**Player locator bar.** `GameRule.LOCATOR_BAR` is explicitly forced on in the
game worlds so teammates can always find each other in the shared world.

---

## Gameplay Notes

- **Friendly fire hurts the team.** Because health is shared, hitting another
  player drains the *common* pool — PvP between teammates is self-defeating
  (intentional, given the concept).
- **The lobby is protected.** It runs in `PEACEFUL`; no damage, no hunger,
  no fall damage while waiting.
- **The locator bar** only shows in the game world (nobody to locate in the
  hub).
- **Difficulty is enforced** as `HARD` on every regenerated world, regardless
  of `server.properties`.

---

## Troubleshooting

### Crash: `WorldFolderMigration` / `Failed to migrate world storage`

Paper 26.1+ changed the world storage format (all dimensions live under
`hub/dimensions/...` instead of separate `hub_nether` folders). Leftover
world folders from a different Minecraft version trigger a migration that can
fail with *"Refusing to overwrite existing migrated file"*.

Fix — delete the world folders (they are recreated automatically; the lobby
is rebuilt by the plugin and game worlds regenerate every run):

```powershell
Remove-Item server\hub*,server\game*,server\world* -Recurse -Force
Remove-Item server\crash-reports -Recurse -Force
```

**Never mix worlds generated by different Minecraft versions.**

### Server won't start: `requires Java 25 or above`

Use the bundled `jdk/` (via `start.bat`) or install Java 25. Minecraft 26.1+
does not run on older Java.

### Build fails to overwrite `HardcoreShared.jar`

The server is running and locking the jar. Stop it first, then run
`build.ps1`.

### Harmless warnings

`sun.misc.Unsafe` (from the `joml` library) and `GameRule` deprecation
warnings are expected on Paper 26 and do not affect functionality.

---

## Validation

The full server-side cycle has been validated on **Paper 26.1.2 / Java 25**:
void lobby generation, run start, world creation, game over (`/hc stop`),
world deletion + recreation with a new seed, countdown, automatic restart and
stat persistence — across multiple back-to-back cycles, with **no exceptions
and no file-lock failures**. The shared life/hunger mechanic is best
experienced in-game with multiple connected players.

---

## License

No license file is included. Add the license of your choice (e.g. MIT)
before publishing publicly.
