# Velum Lifecycle Checklist

Use for boot, tile, Activity, process death, and long-running connection audits.

| Scenario | Expected behavior |
|---|---|
| Cold start while registered and `wasUp=true` | Boot/recovery may reconnect only when VPN permission is valid and handshake succeeds. |
| Cold start while `wasUp=false` | Do not auto-connect. |
| Activity recreated | Backend state is refreshed; UI reflects actual tunnel state. |
| Process recreated while tunnel backend is active | State, notification, duration, listener, and recovery guard are synchronized. |
| Quick Settings tile starts listening | Tile refreshes backend state before rendering. |
| Tile connect | It uses the unified contract and requires handshake proof. |
| Tile disconnect | It invalidates intent, stops monitor, and brings the tunnel down. |
| Network changes | Recovery is debounced, claimed atomically, bounded by backoff, and handshake-verified. |
| Tunnel goes down unexpectedly | Recovery is attempted only when user intent still says UP. |
| Manual disconnect during recovery | Queued work becomes stale and cannot bring the tunnel back. |
| App update | Package replacement recovery follows the same permission and handshake rules as boot. |
| No network | Recovery does not spin indefinitely; it stops after the bounded attempt policy. |
| Notification permission denied | Connection logic still works; notification failure does not corrupt state. |

When a scenario cannot be covered by unit tests, document a manual device or emulator procedure and expected observation.
