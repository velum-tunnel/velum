# Velum Connection Invariants

Use this checklist when reviewing connection or recovery changes.

## Entry-point convergence

Confirm that Activity, Quick Settings tile, boot/update receiver, reconnect monitor, endpoint changes, and fallback all converge on `VelumConnectionContract`. Direct calls to `VelumTunnel.up()` or `restart()` outside the contract are a finding unless the call is an intentional low-level implementation detail with a test and explanation.

## Handshake proof

`State.UP` means only that the VPN interface was established. A successful user-visible connection requires a fresh handshake for the intended endpoint within the configured timeout. Do not persist `workingEndpoint`, enable recovery, or report success before that proof.

## Intent ownership

Every asynchronous operation must capture an intent generation and check it before touching the tunnel, changing the endpoint, writing `wasUp`, enabling the monitor, or publishing success. Manual disconnect and reset must invalidate older work atomically.

## Recovery claim

Only one recovery worker may own the claim. `tryClaim` must be atomic, and cleanup must release safely even when an operation throws or release is repeated. A failed recovery must not leave the claim permanently held.

## Fallback semantics

Manual endpoint selection is authoritative and must not silently rotate. Automatic fallback may skip the failing endpoint, but each candidate must be handshake-verified before it becomes the working endpoint. Stale intent is cancellation, not a network failure.

## Persistence and process death

A durable `wasUp` decision is required before boot/update recovery can be trusted. Process recreation must refresh backend state and repeat notification, duration, listener, and recovery bookkeeping. Never infer backend state solely from a new process's default in-memory state.

## User intent

A user pressing Disconnect must win over queued reconnect, delayed endpoint probing, network callbacks, and tunnel-down callbacks. Verify this with a regression test or a deterministic state-machine test.

## Evidence to capture

Record the file and symbol, the interleaving or state transition, expected behavior, observed behavior, and the test proving the fix. Avoid including private keys or unnecessary raw addresses in reports.
