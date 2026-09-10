# Changelog

All notable changes to SunshineCommandGuard are documented here.

## [1.4.1] - 2026-09-10

Production hardening and cache consistency. No new features, no config format
changes; existing `config.yml` files load unchanged.

### Fixed

- Async tab-completion no longer touches Bukkit permissions: permission-sync
  verdicts on the async path now come from a main-thread-captured immutable
  permission snapshot. A cold snapshot hides gated commands rather than
  leaking them; commands without a registered permission keep working.
- LuckPerms group/permission changes apply immediately: user-data
  recalculation invalidates the affected player's profile and snapshot, then
  rebuilds them on the main thread. LuckPerms stays optional.
- Player quit now cleans cached profiles, snapshots, notification throttle
  entries and wizard sessions. Temporary grants intentionally survive
  reconnects.
- A missing `enabled` key now fails safe to disabled (was: enabled).
- Temporary grants match the exact command token: a grant for `plugins` no
  longer covers `bukkit:plugins`. Namespace protection stays absolute and
  still runs before grants.
- Folia wording corrected: Paper and compatible Paper forks supported; Folia
  support is not currently guaranteed. No metadata flag was added.

### Verification

- Regression tests for every fix above; full suite green.
- Clean boot verified on Paper 26.2.
- The 1.4.0 Sentinel API (`BlockReason`, `CommandGuardBlockedEvent`) is
  unchanged and contract-tested.

## [1.4.0] - 2026-09-09

### Added

- Added the public API package `com.sunshine.commandguard.api` with the stable
  `BlockReason` enum and the non-cancellable `CommandGuardBlockedEvent`, fired exactly
  once per blocked command attempt from the central reporting path with the normalized
  root command token (namespace preserved, arguments never included).
- Added contract tests covering event payload, namespace preservation, argument
  stripping, reason mapping and dispatcher failure isolation.

### Notes

- Existing blocking semantics, permission behavior and configuration keys are unchanged;
  the API is a one-way observation hook and CommandGuard remains fully usable without
  Sunshine Sentinel.

## [1.3.0] - 2026-09-07

### Added

- Added the anti-enumeration shield for client-side plugin scanners such as Meteor.
- Added configurable namespace protection: hide namespaced command labels, block direct
  namespaced execution, and allow explicit namespace exceptions when compatibility requires it.
- Added direct completion-probe protection for `/plugins`, `/pl`, `/version`, `/ver`, `/about`,
  and related namespaced aliases through both Paper completion paths.
- Added `/cmdguard diagnose` reporting for visible namespaced commands and the server's
  `commands.send-namespaced` setting.

### Fixed

- Fixed the namespace fallback allowing namespaced command labels to remain visible when the
  unnamespaced base command was allowed.
- Added tests that model the command-tree namespace scan used by common client scanners.

### Verification

- Existing tests plus the new anti-enumeration coverage pass on the Paper 1.21.4 API.
- The release remains fail-open for unexpected plugin errors; explicit namespace protection
  only applies to players who are not OP or bypass-authorized.

## [1.2.0] - 2026-09-07

### Added

- Added a safer setup wizard for generating and applying configuration plans.
- Added YAML validation before wizard changes are written. Invalid configuration files are left untouched.
- Added timestamped `.bak` backups and temporary-file plus atomic-replace writes for wizard configuration changes.
- Added the `WizardFlow` state machine for deterministic wizard progress, including safe handling of repeated and out-of-order selections.
- Added `/cmdguard diagnose` reporting for suspended rules and validation results.
- Added exact configuration-path validation for `regex:` and `plugin:` entries inside argument allow/deny rules.

### Changed

- Setup plans now explicitly write `groups.default.inherit: []` when the default group must not inherit other groups.
- Applying a setup plan now reports whether an active filter was disabled during the change through the `wasOn` result.
- Execution checks, command visibility, permission synchronization, and diagnostics now use the same shared decision chain: grant, privacy, sync, then list behavior.
- The plain `/help` command remains intentionally available; privacy protection continues to target the documented help aliases (`?` and `bukkit:help`).
- The updater now identifies itself with the plugin's actual version and closes connections reliably in all response paths.

### Fixed

- Fixed a grant-expiry cleanup race by removing entries with compare-and-remove semantics, preventing stale cleanup from deleting a newer grant.
- Fixed wizard paths that could remain stuck after duplicate or unexpected answers.
- Fixed configuration-wizard writes that could overwrite malformed YAML or leave a partially written file.

### Verification

- 106 automated tests passed with zero failures, errors, or skipped tests.
- Clean build completed successfully.
- Boot verification completed successfully: `reloaded; groups=3 warnings=0 validation=1`.

### Scope notes

- Privacy filtering was intentionally not added to tab completion; existing tab-completion behavior remains unchanged.
- `revokeAll`/`grant` write concurrency was intentionally left unchanged because those writes are currently performed on the main thread.

## [1.1.0] - 2026-09-07

- Added command generation, grants, permission synchronization, per-world groups, monitoring, update checking, and Folia support.

## [1.0.0] - 2026-09-07

- Initial public release.

[1.2.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.2.0
[1.4.1]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.1
[1.4.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.4.0
[1.3.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.3.0
[1.1.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.1.0
[1.0.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.0.0
