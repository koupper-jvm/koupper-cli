# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),  
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `koupper worker [jobsDir] [--queues=q1,q2] [--concurrency=N] [--interval=ms] [--timeout=seconds] [--max-retries=N] [--enable-scheduling]` — job worker daemon. Polls `~/.koupper/jobs/` queue directories, claims jobs atomically via POSIX `renameTo`, executes agent `.kts` scripts via `koupper run`, and streams output to `logs/<queue>/<jobId>.log`.
  - `--timeout=<seconds>` (default 300, env `KOUPPER_WORKER_TIMEOUT`) — kills the subprocess if it runs longer than N seconds and moves the job to `.failed/`.
  - `--max-retries=<N>` (default 3) — after N failures on the same job, moves it to `.dead/` instead of `.failed/` to prevent infinite retry loops.
  - `--enable-scheduling` — activates the built-in scheduler engine that reads `~/.koupper/schedules.json` and enqueues jobs when their cron/rate/once trigger fires.
  - `--status` — prints pending/processing/failed/dead counts per queue and exits immediately without starting the daemon. Indicators: `▶` active · `⚠` failed · `☠` dead.
- `koupper doctor` — diagnoses the Koupper runtime environment in one command. Checks: env vars (`KOUPPER_LLM_MODEL_PATH`, `KOUPPER_LLM_EXECUTABLE`), installed JARs (octopus, cli, monitor), ports (9998 octopus · 8081 llama-server · 18082 MCP · 18083 Web UI), job queues (pending/processing/failed/dead per queue), installed agents, and configured schedules. Output uses ✓/⚠/✗ with a summary error/warning count.
- `koupper schedule <subcommand>` — manage agent schedules stored in `~/.koupper/schedules.json`.
  - `add <agentFile> --cron="0 8 * * 1-5" [--id=<id>] [--queue=<queue>]` — schedule by cron expression (5-field: min hour day month weekday).
  - `add <agentFile> --rate=<ms> [--id=<id>]` — schedule by fixed interval in milliseconds.
  - `add <agentFile> --once="<ISO8601>" [--id=<id>]` — schedule a single one-time run.
  - `list` — display all schedules with id, trigger, agent, queue, and enabled state.
  - `remove <id>` — delete a schedule entry.
  - `enable <id>` / `disable <id>` — toggle a schedule without deleting it.
- `koupper monitor [jobsDir]` — launches the IGLY CORTEX TUI dashboard. Displays live job queues, pipeline visualization, and a CORTEX chat command bar. Reads `~/.koupper/libs/koupper-monitor.jar`.
- `koupper start [jobsDir] [--no-worker] [--no-webui] [--scheduling]` — one command to run the full CORTEX stack.
  1. Kills stale octopus/monitor/webui processes (forces env var reload).
  2. Starts `koupper worker` in the background (log: `~/.koupper/logs/worker.log`).
  3. Starts `CortexWebUiAgent.kts` in the background if installed (log: `~/.koupper/logs/webui.log`).
  4. Launches `koupper monitor` in the foreground — exits when the user closes the TUI.
  Forwards `KOUPPER_LLM_MODEL_PATH`, `KOUPPER_LLM_EXECUTABLE`, and `KOUPPER_WORKER_TIMEOUT` to all child processes.

---

- Added framework-level `infra` command group with `init`, `validate`, `plan`, `apply`, `drift`, and `output` subcommands.
- Added `reconcile run` orchestrator command with configurable stages (`infra`, `preflight`, `deploy`, `smoke`, `rollback`) and policies (`strict`, `continue_on_error`, `abort_on_failure`).
- Infra/reconcile responses now use a stable JSON contract (`ok`, `stage`, `exitCode`, `durationMs`, `warnings`, `errors`, `artifacts`, `nextAction`) for CI automation.
- Drift now supports a versioned `drift-spec` v1 evaluation mode (`required_only`, `exact_match`) via `--spec` and `--observed-file`.
- Added command-level timeout/retry flags and output redaction for common secret patterns.
- `reconcile run` now exposes AWS deploy control flags (`--aws-timeout-seconds`, `--aws-retry-count`, `--aws-retry-backoff-ms`, `--frontend-backup-mode`) and exports them to stage commands.

- `koupper new <file>.kts` now generates standalone scripts without unresolved `%PACKAGE%` placeholders.
- `new module` now validates required scaffold files (`settings.gradle`, `build.gradle`) before reporting success.
- `new module` now fails early if starter scripts are not generated in module extensions.
- Starter script module templates now generate `processManager.call(::myScript, ...)` to avoid runtime argument-type mismatch.
- `run` now supports `--json-file <path>` to load JSON payloads directly from file.
- CLI run output spacing was normalized to avoid inconsistent prompt separation across scripts.
- `new module` now infers `type` from `template` when type is omitted (`jobs` -> `job`, `pipelines` -> `pipeline`).
- `new module` type parsing now accepts aliases (`scripts`, `jobs`, `pipelines`) and validates unsupported values early.
- `help new` now documents module parameters and script import flags (`-si/-se/-swi/-swe`) explicitly.
- `new module` now injects default/imported scripts after scaffold generation to avoid losing `myScript` starter files.
- `module add-scripts` added to import scripts into existing modules without overwriting by default (`--overwrite` optional).
- Added dedicated tests for `module add-scripts` import behavior (copy, skip, overwrite, wildcard).
- Refactored shared script-import parsing/validation logic into reusable command utilities.
- `module` now resolves HTTP/controller checks relative to the selected module directory.
- Job list/worker output now avoids extra leading blank lines for more consistent terminal formatting.
- `module add-scripts` now includes a direct source-path hint when imports fail.
- Wildcard script imports now normalize Windows-style paths and support shell-expanded wildcard matches.
- `job init --force` no longer fails on projects without `shadowJar`; it now creates config safely and suggests `job build-environment`.
- Added `provider` command with:
  - `koupper provider list` (provider name + short description)
  - `koupper provider info <name>` (bindings, tags, required/optional env vars, docs link)
- Provider catalog now includes `github` metadata for GitHub issue/PR/workflow automation flows.
- Provider catalog now includes `terminal` runtime metadata for interactive prompt/print flows.

---

## [4.7.1] - 2026-03-28

### Changed
- `deploy` now requires auth token configuration (`KOUPPER_OCTOPUS_TOKEN` / `koupper.octopus.token`).
- `deploy` includes payload SHA-256 checksum for daemon-side integrity verification.

### Added
- Deploy smoke coverage improvements for auth-prefixed socket exchange and checksum propagation.

---

## [4.6.0] - 2026-03-28

### Added
- End-to-end smoke tests for `run` socket flows covering JSON responses, requestId filtering, and legacy envelope fallback.

### Changed
- `run` command now supports runtime Octopus host/port overrides via:
  - `KOUPPER_OCTOPUS_HOST` / `koupper.octopus.host`
  - `KOUPPER_OCTOPUS_PORT` / `koupper.octopus.port`
- Help and runtime docs were updated to include socket override usage.
- Updated help descriptions related to the `new` command.
- Refactored `ModuleCommand` to improve how it displays module information.
- Modified the structure of `ApiConfig` to support the new format.
- The `module` command now generates a default HTTP configuration for script types: `HANDLERS_CONTROLLERS_SCRIPTS`.
- Updated CLI arguments for `JobCommand` (new flags, defaults, and help text).

---

## [4.5.0] - 2025-08-28
### Added
- New `job` command in CLI

### Improved
- Updated command menu copies and help texts
- Minor fixes and refactors
