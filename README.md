# SunshineCommandGuard

Hide commands from tab-complete, block their execution, and keep your plugin list private — per LuckPerms group.

## Features

- **Tab-complete filtering** via `PlayerCommandSendEvent` — players only see what they may use.
- **Execution blocking** — a command the player may not run behaves as an unknown command.
- **Server privacy** — hides `/plugins`, `/pl`, `/ver`, `/version`, `/about` and `/help` behind custom messages.
- **Per-group profiles** — groups resolved from LuckPerms permissions (`sunshine.cmdguard.group.<name>`), with `inherit` and `priority`.
- **Flexible matching** — literal names, `regex:` patterns, negative `!entries` (deny always wins), and `plugin:<Name>` expansion.
- **Hidden aliases** — commands that stay runnable but disappear from tab-complete.
- **Argument-level rules** — per-command `args` allow/deny lists (e.g. limit `/gamemode` sub-arguments).
- **Live verification** — `/cmdguard test <player> <command>` shows exactly why a command is allowed or blocked.
- **bStats metrics** included (relocated, conflict-free).

## Requirements

- Paper (or a Paper fork) **1.21.x**, Java **21**
- LuckPerms is **optional** (`softdepend`): without it, every player falls back to the `default` group.
- Players with the bypass permission or OP status skip all filtering.

## Installation

1. Drop `SunshineCommandGuard-1.0.0.jar` into your `plugins/` folder and start the server once (this creates the default `config.yml`).
2. Edit `plugins/SunshineCommandGuard/config.yml` — define which commands each group may see.
3. Verify **before** enabling: `/cmdguard test <player> <command>`.
4. Set `enabled: true`, then `/cmdguard reload`.

## ⚠️ Warning

A misconfigured filter can leave players with **no visible commands at all**. The plugin therefore ships with `enabled: false`.
Always review your groups and run `/cmdguard test` for a default-group player **before** setting `enabled: true`.

## Configuration examples

Minimal — hide everything except a few commands:

```yaml
enabled: true
bypass-permission: "sunshine.cmdguard.bypass"
groups:
  default:
    priority: 0
    blocked-message: "<red>Unknown command."
    commands:
      - help
      - spawn
      - msg
      - "regex:^(msg|tell|w|r|reply)$"
      - "!op"
      - "!stop"
    hidden: []
    args: {}
```

Group-based access with inheritance:

```yaml
groups:
  default:
    priority: 0
    blocked-message: "<red>Unknown command."
    commands: [help, spawn, msg]
    hidden: []
    args: {}
  vip:
    priority: 10
    inherit: [default]
    commands: [hat, nick, workbench]
    hidden: [ecraft, ehat]
    args:
      gamemode:
        allow: [survival, creative]
        deny: []
  staff:
    priority: 50
    inherit: [vip]
    commands: ["plugin:WorldEdit"]
    hidden: []
    args: {}
```

Matching rules: entries are matched case-insensitively, with or without a leading `/`, and
namespaced labels (`essentials:heal`) also match their base name (`heal`).
`!entry` denies (deny always wins), `regex:<pattern>` allows full-match patterns,
`plugin:<Name>` expands to every command of that plugin. `hidden` entries are runnable
but never suggested in tab-complete.

## Commands and permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/cmdguard reload` | `sunshine.cmdguard.admin` | Reload config, rebuild index, clear caches |
| `/cmdguard refresh <player>` | `sunshine.cmdguard.admin` | Drop one player's cache, resend command tree |
| `/cmdguard test <player> <command>` | `sunshine.cmdguard.admin` | Show groups, visibility, reason for a command |
| `/cmdguard debug` | `sunshine.cmdguard.admin` | Suspend/resume filtering at runtime (no config write) |
| `/cmdguard dump` | `sunshine.cmdguard.admin` | Write all registered commands to `commands_dump.yml` |

| Permission | Default | Description |
| --- | --- | --- |
| `sunshine.cmdguard.bypass` | op | Bypasses all command filtering |
| `sunshine.cmdguard.admin` | op | Allows use of `/cmdguard` |
| `sunshine.cmdguard.group.<name>` | — | Assigns a player to group `<name>` (via LuckPerms) |

## Compatibility

Built against the **Paper 1.21.4 API (Java 21)**. Running in production on a Paper 26.2 server.
Older/newer server versions are untested — please report what works.

## Building from source

```powershell
.\gradlew.bat build   # needs JDK 21, output: build/libs/SunshineCommandGuard-1.0.0.jar
.\gradlew.bat test    # 32 unit tests (JUnit 5)
```

The uploaded jar must be the `shadowJar` output (`build/libs/...`), which bundles bStats
(relocated to `com.sunshine.cmdguard.bstats` so it never conflicts with other plugins).
`build.ps1` remains as a fast local-compile script for the original author's machine.

## Metrics

This plugin collects anonymous usage statistics via [bStats](https://bstats.org/).
Server owners can opt out in `plugins/bStats/config.yml`.

## Keywords

command hide, tab complete, plugin hide, pl hide, command blocker, permissions, luckperms

## License

GPL-3.0 — see [LICENSE](LICENSE). Derivative works must stay open source.
