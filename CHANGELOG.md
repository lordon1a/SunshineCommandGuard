# Changelog

All notable changes to SunshineCommandGuard are documented here.

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
[1.1.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.1.0
[1.0.0]: https://github.com/lordon1a/SunshineCommandGuard/releases/tag/v1.0.0
