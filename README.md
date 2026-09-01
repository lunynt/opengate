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
- Paper blocks movement, chat, commands, inventory actions, interaction, damage, and block changes until authentication.
- Velocity redirects unauthenticated players to a registered server named `limbo`, then sends them to the first non-limbo server after authentication.

Two-factor authentication is available through `/2fa setup <password>`, `/2fa confirm <code>`, `/totp <code>`, and `/2fa disable <password>`. TOTP secrets are encrypted with AES-256-GCM using `plugins/OpenGate/secret.key`; back up this key with the database because losing it makes enrolled TOTP secrets unrecoverable.
