# SunshineCommandGuard

[![Latest release](https://img.shields.io/github/v/release/lordon1a/SunshineCommandGuard?label=release)](https://github.com/lordon1a/SunshineCommandGuard/releases/latest)
[![License: GPL-3.0](https://img.shields.io/github/license/lordon1a/SunshineCommandGuard)](LICENSE)
[![Downloads](https://img.shields.io/github/downloads/lordon1a/SunshineCommandGuard/total)](https://github.com/lordon1a/SunshineCommandGuard/releases)
[![bStats servers](https://img.shields.io/bstats/servers/33904?label=servers)](https://bstats.org/plugin/bukkit/SunshineCommandGuard/33904)

Hide commands from tab-complete, block their execution, and keep your plugin list private —
all scoped **per LuckPerms group**, with an admin tool that tells you exactly why any given
command is allowed or blocked before you ever flip the switch.

Most servers run dozens of plugins, and every one of them registers commands, sub-commands,
and namespaced aliases that regular players were never meant to see. `/plugins` hands out your
whole stack to anyone who asks. Tab-complete leaks every admin tool you have. SunshineCommandGuard
closes both doors without touching a single permission node you already have in LuckPerms.

## Table of contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration examples](#configuration-examples)
- [Commands and permissions](#commands-and-permissions)
- [Frequently asked questions](#frequently-asked-questions)
- [Compatibility](#compatibility)
- [Building from source](#building-from-source)
- [Metrics](#metrics)
- [License](#license)

## Features

- **Tab-complete filtering** via `PlayerCommandSendEvent` — players only see what they may use.
  The command doesn't just disappear from suggestions; the client never learns it exists.
- **Execution blocking** — a command the player may not run behaves as an unknown command,
  with a fully customizable message.
- **Server privacy** — hides `/plugins`, `/pl`, `/ver`, `/version`, `/about` and the `?`
  help aliases behind a message you write (plain `/help` stays: blocked players are sent
  there).
- **Anti-enumeration shield** — removes namespaced command labels, blocks direct namespaced
  execution, and filters direct `/version`/`/plugins` completion probes used by client-side
  plugin scanners. Explicit namespace exceptions are available for compatibility.
- **Per-group profiles** — groups resolved from LuckPerms permissions
  (`sunshine.cmdguard.group.<name>`), with `inherit` and `priority` so a `staff` group can
  extend `vip`, which extends `default`, without repeating a single line.
- **Flexible matching** — literal names, `regex:` patterns, negative `!entries` (deny always
  wins), and `plugin:<Name>` expansion to grab every command a plugin registers in one line.
- **Hidden aliases** — commands that stay fully runnable but disappear from tab-complete, for
  the aliases you don't want advertised but still need to work.
- **Argument-level rules** — per-command `args` allow/deny lists, e.g. let players use
  `/gamemode survival` but not `/gamemode creative`.
- **Live verification** — `/cmdguard test <player> <command>` prints the resolved group, the
  matching rule, and the reason a command is visible or blocked, before you enable anything.
- **One-command setup** — `/cmdguard generate` scans your live server and writes a reviewed
  starter config (`config.generated.yml`): commands nobody needs a permission for go to
  `default`, permission-gated ones to `staff`. Copy what you want into `config.yml`.
- **Interactive setup wizard** — `/cmdguard setup` walks you through three clickable questions
  in chat (starter list, privacy, permission-sync) and writes `config.yml` for you.
- **Precise config validation** — every `/cmdguard reload` cross-checks your lists against the
  live server and points at exact entries: typos, unknown plugins, bad regex, unknown groups
  and unloaded worlds (`groups.default.commands[1]: 'spwan' matches no registered command`).
- **Self-diagnosis** — `/cmdguard diagnose [player]` catches the classic mistakes: filter off,
  testing with an OP account, zero visible commands, config warnings — with live counts
  ("Visible: 12 of 340 commands").
- **Permission-sync mode** — optionally also require each command's own Bukkit permission node,
  so players automatically lose commands their rank can't run, with zero list maintenance.
- **Temporary grants** — `/cmdguard grant <player> <command> <10m|2h|1d>` opens one command for
  a limited time (events, support cases), `/cmdguard ungrant` takes it back. No config edits.
  Grants match the exact command token: a grant for `plugins` does not cover `bukkit:plugins`.
- **Per-world groups** — restrict any group to specific worlds (`worlds: [arena]`), with the
  profile refreshing automatically on world change.
- **Block monitoring** — log every blocked attempt and/or ping online staff (throttled), so
  reconnaissance attempts don't go unnoticed.
- **Update notifications** — console and admin-join notice when a newer GitHub release
  exists (notify-only; see below).
- **bStats metrics** included, relocated so it never conflicts with another plugin's copy.

## Screenshots

All four below are taken on a live production server, not a demo instance — the block and
privacy messages are that server's own text, fully customizable in `config.yml`.

| | |
| --- | --- |
| ![Allowed command still tab-completes normally](docs/screenshots/tab-complete-allowed.png) **Allowed** — a whitelisted command completes normally. | ![Hidden command does not appear in tab-complete](docs/screenshots/tab-complete-hidden.png) **Hidden** — not suggested, not even recognized by the client's own command tree — but still runs if typed out in full. |
| ![Non-whitelisted command is rejected as unknown](docs/screenshots/execution-blocked.png) **Blocked** — a command outside the group's list is invisible to the client and rejected as unknown. | ![The /plugins command is replaced with a custom message](docs/screenshots/plugins-privacy.png) **Server privacy** — `/plugins` returns a custom message instead of the real plugin list. |

## Requirements

- Paper (or a Paper fork) **1.21.x**, Java **21**
- LuckPerms is **optional** (`softdepend`): without it, every player falls back to the
  `default` group.
- Players with the bypass permission or OP status skip all filtering — always test with a
  non-OP account.

## Installation

1. Drop `SunshineCommandGuard-1.4.1.jar` into your `plugins/` folder and start the server once
   (this creates the default `config.yml`).
2. Run `/cmdguard setup` in-game and answer three questions — or run `/cmdguard generate`
   and copy the useful parts of `config.generated.yml` into `config.yml`.
3. Verify **before** enabling: `/cmdguard test <player> <command>` (or `/cmdguard diagnose`),
   with a non-OP test account.
4. Set `enabled: true`, then `/cmdguard reload`.

## ⚠️ Warning

A misconfigured filter can leave players with **no visible commands at all**. The plugin
therefore ships with `enabled: false`. Always review your groups and run `/cmdguard test` for
a default-group player **before** setting `enabled: true`.

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

Server privacy — replace `/plugins`, `/ver` and help aliases (plain `/help` stays
visible on purpose: it is the fallback the blocked message points players to):

```yaml
privacy:
  plugins-command:
    enabled: true
    message: "<gold>My Server <grey>| <white>Server information is private."
    aliases: [plugins, pl, "bukkit:pl", "bukkit:plugins", ver, version, about, icanhasbukkit]
  help-command:
    enabled: true
    message: "<yellow>Type <white>/help <yellow>for a list of commands."
    aliases: ["?", "bukkit:help", "minecraft:help"]
```

Client-side plugin scanner protection — hide namespaced aliases and block direct completion
probes. This section is active only when the top-level filter is enabled:

```yaml
anti-enumeration:
  enabled: true
  hide-namespaced-commands: true
  block-namespaced-execution: true
  block-completion-probes: true
  namespace-allowlist: []
```

For defense in depth, set `commands.send-namespaced: false` in `spigot.yml` as well. The
plugin reports this server setting in `/cmdguard diagnose`.

## Copy-paste recipes

EssentialsX survival server — the usual player commands, nothing else:

```yaml
enabled: true
groups:
  default:
    priority: 0
    blocked-message: "<red>Unknown command."
    commands:
      - help
      - spawn
      - home
      - sethome
      - delhome
      - tpa
      - tpaccept
      - tpdeny
      - msg
      - mail
      - pay
      - balance
      - kit
      - warp
      - afk
      - rules
      - "regex:^(msg|tell|w|r|reply)$"
      - "!op"
      - "!stop"
    hidden: []
    args: {}
    worlds: []
```

Minigame lobby + survival worlds — lobby commands only exist in their worlds:

```yaml
enabled: true
groups:
  default:
    priority: 0
    blocked-message: "<red>Unknown command."
    commands: [help, spawn, msg]
    hidden: []
    args: {}
    worlds: []
  minigames:
    priority: 20
    inherit: [default]
    commands: [queue, leave, spectate, party]
    hidden: []
    args: {}
    worlds: [arena, lobby]
```

Strict mode — tiny lists plus permission-sync, so a command also needs its own
Bukkit permission node and new plugins stay hidden until you allow them:

```yaml
enabled: true
permission-sync: true
groups:
  default:
    priority: 0
    blocked-message: "<red>Unknown command."
    commands: [help, rules]
    hidden: []
    args: {}
    worlds: []
```

**Matching rules:** entries are matched case-insensitively, with or without a leading `/`, and
namespaced labels (`essentials:heal`) also match their base name (`heal`). `!entry` denies (deny
always wins over any allow), `regex:<pattern>` allows full-match patterns, `plugin:<Name>`
expands to every command that plugin registers. `hidden` entries are runnable but never
suggested in tab-complete — use them for aliases you don't want advertised. When
`anti-enumeration.hide-namespaced-commands` is enabled, namespaced labels are hidden from
the command tree and tab completion. When `anti-enumeration.block-namespaced-execution` is
enabled, direct execution is also blocked unless the namespace is listed in `namespace-allowlist`.

Optional top-level modes (old configs load safely; a missing anti-enumeration section receives
secure defaults):

```yaml
# Also require each command's own Bukkit permission node.
permission-sync: false

monitoring:
  log-blocked: false        # log every blocked attempt to console
  notify-staff: false       # message online staff on blocked attempts
  notify-permission: "sunshine.cmdguard.notify"
  notify-cooldown-seconds: 5  # per-player throttle

updates:
  enabled: true             # GitHub Releases check (notify-only)
  notify-console: true
  notify-admins: true
```

Per-world groups — a group with `worlds` only matches there (empty = everywhere).
If `default` itself is world-restricted you get a config warning, because players outside
those worlds would be unfiltered:

```yaml
groups:
  minigames:
    priority: 20
    inherit: [default]
    commands: [queue, leave, spectate]
    hidden: []
    args: {}
    worlds: [arena, lobby]
```

## Commands and permissions

| Command | Permission | Description |
| --- | --- | --- |
| `/cmdguard reload` | `sunshine.cmdguard.admin` | Reload config, rebuild index, clear caches |
| `/cmdguard refresh <player>` | `sunshine.cmdguard.admin` | Drop one player's cache, resend command tree |
| `/cmdguard test <player> <command>` | `sunshine.cmdguard.admin` | Show groups, visibility, reason for a command |
| `/cmdguard debug` | `sunshine.cmdguard.admin` | Suspend/resume filtering at runtime (no config write) |
| `/cmdguard dump` | `sunshine.cmdguard.admin` | Write all registered commands to `commands_dump.yml` |
| `/cmdguard generate` | `sunshine.cmdguard.admin` | Write a review-ready starter config to `config.generated.yml` |
| `/cmdguard grant <player> <command> <30s\|10m\|2h\|1d>` | `sunshine.cmdguard.admin` | Temporarily allow one command (memory-only, lost on restart) |
| `/cmdguard ungrant <player> [command]` | `sunshine.cmdguard.admin` | Revoke one or all temporary grants |
| `/cmdguard setup` | `sunshine.cmdguard.admin` | Interactive chat setup (players only): starter list, privacy, sync, anti-enumeration |
| `/cmdguard diagnose [player]` | `sunshine.cmdguard.admin` | Common-mistake check with live counts and anti-enumeration status |

| Permission | Default | Description |
| --- | --- | --- |
| `sunshine.cmdguard.bypass` | op | Bypasses all command filtering |
| `sunshine.cmdguard.admin` | op | Allows use of `/cmdguard` |
| `sunshine.cmdguard.notify` | op | Receives blocked-command staff notifications |
| `sunshine.cmdguard.group.<name>` | — | Assigns a player to group `<name>` (via LuckPerms) |

## Frequently asked questions

**Does this replace LuckPerms?** No. LuckPerms still controls what a player is *allowed* to
run. SunshineCommandGuard controls what a player *sees* and what happens when they try
something outside their group — a permission system and a visibility filter are different
problems, and this plugin only solves the second one.

**Will OPs and admins be affected?** No, by design — `sunshine.cmdguard.bypass` defaults to
`op`, so operators always see and can run everything. Test with a real non-OP account, or the
filter will look broken when it's actually just not being applied to you.

**What happens to a hidden command if a player already knows it?** It still runs. `hidden`
only removes a command from tab-complete suggestions; it's a different list from `commands`,
which controls whether the command runs at all. Use `hidden` for aliases you don't want to
advertise, and the plain block list for commands you actually want to deny.

**Can a client still enumerate plugins?** The anti-enumeration shield removes namespaced
command labels and blocks the common direct completion probes. It is not a guarantee that
plugin behavior can never be inferred; public commands, messages, resource-pack data and
other server behavior can still reveal information. Do not rely on client-brand detection,
because modified clients can spoof their brand.

**Does this affect server performance?** Filtering runs on join and on `/cmdguard reload`, not
on every keystroke — the client's command tree is rebuilt once per player, not recomputed for
every tab press.

**Can I use this without LuckPerms?** Yes. LuckPerms is a soft dependency; without it every
player resolves to the `default` group.

## Compatibility

Built against the **Paper 1.21.4 API (Java 21)**. Tested on a clean Paper 26.2 server.
Paper and compatible Paper forks supported. Folia support is not currently guaranteed:
the plugin targets the Bukkit scheduler and main-thread player APIs. Older/newer server
versions are untested — please open an issue with what works or doesn't on your setup.

## Developer API (1.4.0+)

CommandGuard fires a public monitoring event for integrations such as
[Sunshine Sentinel](https://github.com/lordon1a/SunshineSentinel):

- `com.sunshine.commandguard.api.event.CommandGuardBlockedEvent` — fired **exactly once** after a
  command attempt is definitively blocked, with `getPlayerId()`, `getPlayerName()`,
  `getCommandToken()`, `getReason()` (`com.sunshine.commandguard.api.BlockReason`),
  `getTimestamp()` and `getWorld()`.
- **Non-cancellable** — listeners observe the decision, they can never unblock a command.
- The token is the **normalized root command** with all arguments stripped and the namespace
  preserved: `/plugins` → `plugins`, `/BUKKIT:Plugins` → `bukkit:plugins`,
  `/minecraft:help foo bar` → `minecraft:help`.
- **Privacy:** no command arguments, chat content, IPs or credentials ever appear in the event.

## Building from source

```powershell
.\gradlew.bat build   # needs JDK 21, output: build/libs/SunshineCommandGuard-1.4.1.jar
.\gradlew.bat test    # unit tests (JUnit 5)
```

The uploaded jar must be the `shadowJar` output (`build/libs/...`), which bundles bStats
(relocated to `com.sunshine.cmdguard.bstats` so it never conflicts with other plugins).
`build.ps1` remains as a fast local-compile script for the original author's machine.

## Metrics

This plugin collects anonymous usage statistics via [bStats](https://bstats.org/plugin/bukkit/SunshineCommandGuard/33904).
Server owners can opt out in `plugins/bStats/config.yml`.

## Update notifications

CommandGuard can check GitHub Releases for a newer version, once per server
start. **SunshineCommandGuard never automatically downloads or installs updates.**

```yaml
updates:
  enabled: true
  notify-console: true
  notify-admins: true
```

Setting `updates.enabled: false` disables the external update request entirely —
no HTTP call is made and no message is shown. Admins holding
`sunshine.cmdguard.admin` are notified once per server session on join, with a
clickable link to the release page. The check is notify-only: no downloads, no
restarts, no telemetry.

## Keywords

command hide, tab complete, plugin hide, pl hide, command blocker, permissions, luckperms

## License

GPL-3.0 — see [LICENSE](LICENSE). Derivative works must stay open source.

Issues and pull requests are welcome.
