# Audit Findings Ledger

This ledger tracks findings across audit and remediation commits. A finding is not marked verified merely because a code change exists; the relevant validation evidence must also be recorded.

| ID | Finding | Found in commit | Remediation | Verification | Status |
|---|---|---|---|---|---|
| `BUG-CI-001` | `.github/workflows/build.yml` used `continue-on-error: true` for lint, allowing lint errors to leave CI green. | `45e72840eec5019b5fe0e0a20ed2946a8628929e` | Local working-tree change removes `continue-on-error` and keeps `warningsAsErrors=false` in `app/build.gradle.kts`. | Static check and `git diff --check` passed. GitHub Actions run [`35549790828`](https://github.com/velum-tunnel/velum/actions/runs/35549790828) on remediation commit `f72669a719a6b3af62a3d9643dd1120933f45233` completed successfully; job `verifikasi (build, tes, lint)` and step `Lint debug` both passed. | **Fixed; CI verification passed** |
| `POT-STATE-001` | Activity recreation may leave an old controller operation able to finish after the Activity is destroyed; runtime ownership behavior was not device-tested. | `45e72840eec5019b5fe0e0a20ed2946a8628929e` | No fix applied. Requires lifecycle instrumentation/device testing before deciding whether a product change is needed. | Static review only; no emulator/device available during audit. | **Potential; not verified** |
| `POT-NET-001` | Three-second per-candidate fallback handshake limit may be aggressive on slow or newly-awakened networks. | `45e72840eec5019b5fe0e0a20ed2946a8628929e` | No fix applied. Requires network-shaping and device testing. | Static reasoning only; no runtime reproduction. | **Potential; not verified** |
| `RISK-PIN-001` | Certificate pin set requires operational rotation before its expiration date. | `45e72840eec5019b5fe0e0a20ed2946a8628929e` | Follow the documented rotation procedure in `SECURITY.md` before expiration. | Static configuration review; device TLS validation remains pending. | **Residual operational risk** |

## Status rules

- **Confirmed:** reproducible defect supported by current evidence.
- **Fixed locally:** remediation exists in the working tree but is not yet verified in the target integration environment.
- **Fixed; CI verification pending:** source/configuration remediation exists, but the relevant GitHub Actions run has not yet passed on the remediation commit.
- **Potential; not verified:** credible indication without runtime proof.
- **Residual operational risk:** accepted or managed risk requiring future operational action.

When a finding changes, preserve the previous status in the commit history and update this ledger with the new evidence and commit hash. Do not delete historical findings because they were fixed.
