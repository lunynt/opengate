# OpenGate

OpenGate is an all-in-one Minecraft authentication plugin focused on security, stability, and a clean architecture. A single Java core powers thin Paper and Velocity adapters.


## Platforms

- Velocity 4
- Paper 26.2

## Design

Authentication is modeled as a fail-closed per-connection state machine. Identity resolution, registration, passwords, TOTP, trusted sessions, and release are explicit stages rather than scattered listener flags. Platform code enforces decisions made by the shared core.

Reference projects under `inspo/` are used for behavior research only; OpenGate does not depend on their source.

## Build

Requires Java 25. Build and test every module with:

```bash
./gradlew clean build
```

## Current authentication flow

- Offline players register with `/register <password> <password>` and return with `/login <password>`.
- Premium players and valid six-hour IP sessions authenticate automatically.
- Passwords use versioned Argon2id hashes (`64 MiB`, three iterations); hashing runs on a bounded worker pool.
- Accounts persist in `plugins/OpenGate/opengate.db` using SQLite WAL mode.
- Authentication expires after 60 seconds and closes after three incorrect passwords.
- Reconnects cannot reset brute-force protection: IP addresses are limited to ten failures per rolling ten-minute window by default.
- Paper blocks movement, chat, commands, inventory actions, interaction, damage, and block changes until authentication.
- Velocity redirects unauthenticated players to a registered server named `limbo`, then sends them to the first non-limbo server after authentication.

Two-factor authentication is available through `/2fa setup <password>`, `/2fa confirm <code>`, `/totp <code>`, and `/2fa disable <password>`. TOTP secrets are encrypted with AES-256-GCM using `plugins/OpenGate/secret.key`; back up this key with the database because losing it makes enrolled TOTP secrets unrecoverable.

Authenticated players can manage their account with:

```text
/account password <current> <new>
/account logout
/account delete <password> confirm
```

Password changes and deletion run Argon2id verification outside the server thread. Logout revokes the persisted trusted session, while deletion removes the account and immediately closes the active authentication session.

On first launch OpenGate creates `config.properties` and `messages.properties`. Authentication timing, password bounds, IP limits, premium lookup, limbo routing, lobby order, and player messages can be changed without rebuilding the plugin.

The SQLite schema is versioned and upgraded transactionally. Security events are written to `audit_events`, including logins, failures, rate limits, registration, password changes, session revocation, account deletion, and TOTP changes. Client addresses are stored only as keyed HMAC-SHA256 fingerprints, allowing correlation without retaining raw IP addresses.
