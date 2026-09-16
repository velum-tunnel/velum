---
name: velum-forensic-audit-release-guard
description: Forensic audit and release guard for the Velum Android VPN repository. Use when auditing connection paths, handshake enforcement, reconnect/fallback behavior, race conditions, process recreation, lifecycle persistence, UI-related release regressions, or preparing a commit, PR, APK, or release build for Velum.
---

# Velum Forensic Audit & Release Guard

Use this skill from the repository root. Prefer evidence from source, tests, Gradle output, CI, and generated artifacts over assumptions. Do not alter `main` directly: use a focused branch and a small commit or PR.

## Operating rules

- Read repository-local instructions before editing.
- Never bypass `VelumConnectionContract` for connect, reconnect, fallback, boot, tile, or endpoint-change paths.
- Treat a WireGuard `UP` state as provisional until the required handshake is observed.
- Preserve user intent: stale work must not write `wasUp`, restart the tunnel, enable recovery, or overwrite a newer endpoint.
- Preserve atomic recovery ownership through the existing claim/generation mechanism.
- Do not log or report private keys, full sensitive configuration, or unnecessary IP data.
- Keep UI-only fixes separate from connection/security fixes when practical.
- Do not merge or push to `main` unless the user explicitly requests it.

## Audit workflow

1. Establish scope, current branch, working-tree status, and recent commits.
2. Inspect `VelumConnectionContract`, `VelumTunnel`, `VelumController`, `ReconnectMonitor`, `VelumRecoveryClaim`, `BootReceiver`, `VelumTileService`, preferences, manifest, tests, and CI.
3. Run `scripts/scan_connection_paths.sh` and investigate every direct tunnel mutation or direct memo write it reports.
4. Check the invariants in `references/connection-invariants.md` and lifecycle cases in `references/lifecycle-checklist.md`.
5. For each finding, classify impact as blocker, high, medium, low, or informational. Fix concrete defects precisely; do not refactor speculatively.
6. Add or update a focused contract test for every behavioral fix. Tests should cover tile, boot, reconnect, fallback, process recreation, claim ownership, stale intent, and handshake acceptance where relevant.
7. Run the verification workflow below. Stop and report the first failure with its evidence; do not claim success from a partial build.
8. Re-run static scans and `git diff --check`. Confirm artifacts exist and the working tree contains only intentional files.
9. Summarize findings, changes, residual risks, commands, artifacts, and whether a PR/merge was performed.

## Connection invariants

- All connection entry points converge on the contract.
- `State.UP` without a fresh, expected handshake is not a successful connection.
- Manual endpoint choice is never silently replaced by automatic fallback.
- Fallback candidates are recorded as working only after handshake proof.
- A newer intent invalidates older work before every destructive or enabling operation.
- `wasUp` is changed only through generation-aware/atomic lifecycle operations and is durable when it controls boot recovery.
- Only one recovery worker owns the claim at a time; release is safe if cleanup repeats.
- Process recreation refreshes backend state and repeats lifecycle bookkeeping, notification, duration, and recovery guards.
- Manual disconnect prevents subsequent automatic recovery.

## Release guard workflow

Run from repository root:

```bash
scripts/verify_release.sh
```

The script runs the repository's unit tests, debug/preview/release build tasks when available, lint, diff validation, and artifact checks. If a task is unavailable in a particular checkout, report it explicitly rather than silently skipping it.

For a PR, also verify:

```bash
git status --short
git diff --check
gh pr checks <number>
```

Use the repository CI and CodeQL result as the final merge gate. A local build does not replace CI.

## Findings report

Use this compact structure:

```markdown
# Velum Forensic Audit

## Executive summary
[Overall risk and release recommendation]

## Findings
| ID | Severity | Area | Evidence | Action |
|---|---|---|---|---|

## Changes made
[Files and behavioral effects]

## Verification
[Exact commands and pass/fail results]

## Residual risks
[Known limitations and follow-up work]

## Release decision
[Do not release / ready for review / ready to merge]
```

## Decision points

- **Finding in a connection invariant:** fix before merge and add a regression test.
- **Only a cosmetic UI issue:** keep it isolated; build and visually verify the affected APK.
- **CI failure:** do not merge; identify whether the failure is code, environment, or flaky infrastructure.
- **No concrete finding after a complete pass:** stop repeating the full audit and recommend soak/instrumented testing instead.
- **External action such as merge, release, or publication:** require explicit user authorization unless already granted for that exact action.

## Bundled resources

- Read `references/connection-invariants.md` for the forensic invariant checklist.
- Read `references/lifecycle-checklist.md` for Android lifecycle and process-death cases.
- Use `scripts/scan_connection_paths.sh` for deterministic static checks.
- Use `scripts/verify_release.sh` for the standard build/test/lint/artifact gate.
