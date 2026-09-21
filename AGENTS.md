# Agent Instructions

## Repository

- Repository: `velum-tunnel/velum`
- Primary branch: `main`
- Application: Android client for a Cloudflare WARP/WireGuard tunnel.

## Required first steps

1. Read this file.
2. Read `README.md`.
3. Inspect `git status`, branch, and HEAD.
4. Read `docs/audits/README.md`.
5. Read the latest applicable report under `docs/audits/`.
6. Review the diff since the latest audited commit before repeating broad checks.
7. Treat historical audit claims as evidence to re-check, not as proof that the current HEAD is safe.

## Current audit baseline

- Latest comprehensive audit: `docs/audits/2026-09-21-comprehensive-audit.md`
- Audited branch: `main`
- Audited commit: `45e72840eec5019b5fe0e0a20ed2946a8628929e`
- Audit scope covered static code, Android configuration, tests, builds, R8, lint, CI, networking, security, lifecycle, concurrency, and resource risks.
- Runtime/device testing was not verified because the audit environment had no Android device, emulator, or `adb`.
- Do not claim runtime, OEM, VPN traffic, signed-release, or device verification without new evidence.

## Known findings and risks

- `BUG-CI-001`: lint errors were previously suppressed by `continue-on-error: true` in `.github/workflows/build.yml`. The local remediation removes that suppression; verify the remediation in CI before marking it fully verified.
- Lifecycle, network-transition, OEM, Doze, Always-on VPN, Lockdown VPN, and device-specific behavior remain test gaps unless a later report changes their status.
- Certificate pin rotation is an operational requirement; consult the latest audit and `SECURITY.md` before release work.
- Use `docs/audits/findings.md` as the status ledger. Update status only with evidence and record the relevant commit.

## Change policy

- Inspect the relevant configuration and current diff before editing.
- Prefer minimal, focused changes.
- Do not modify production source code unless explicitly requested.
- Do not fix unrelated findings discovered incidentally.
- Run only validations relevant to the requested change; avoid full-suite runs when they are not necessary.
- Do not commit or push unless explicitly requested by the user.
- Before stopping, show the diff, validation results, and changed-file list.

## Reporting requirements

Every maintenance task should state:

- root cause;
- files changed;
- exact change;
- validations run;
- validation results;
- remaining limitations;
- confirmation that unrelated files were not changed.

## Audit handoff prompt

For a follow-up audit, use a delta-audit approach:

> Read `AGENTS.md`, `docs/audits/README.md`, the latest report, and `docs/audits/findings.md`. Inspect changes since the latest audited commit. Re-verify open or recently fixed findings and test only affected areas. Do not repeat a full audit of unchanged areas unless the new diff affects them. Do not modify production source code unless explicitly requested.
