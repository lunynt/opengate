# OpenGate

OpenGate is a small authentication gateway for modern Minecraft servers. A single Java 25 core powers Bukkit, Spigot, Paper, Velocity, and BungeeCord adapters.


## Platforms

- Velocity 4
- BungeeCord 1.21
- Bukkit and Spigot 26.2
- Paper 26.2

## Design

Authentication is modeled as a fail-closed per-connection state machine. Identity resolution, registration, passwords, TOTP, trusted sessions, and release are explicit stages rather than scattered listener flags. Platform code enforces decisions made by the shared core.

- effective: authentication decisions fail closed and security work stays off server threads
- lightweight: one local SQLite database, two bounded workers, and no required services
- easy to use: safe defaults, two editable configuration files, and platform-specific JARs
- focused: features must directly support authentication, account security, or operation
- multi-platform: adapters stay thin while behavior remains in the shared core

Reference projects under `inspo/` are used for behavior research only; OpenGate does not depend on their source.

## Build

Requires Java 25. Build and test every module with:

```bash
./gradlew clean build
```

Platform JARs are written to `bukkit/build/libs/`, `paper/build/libs/`, `velocity/build/libs/`, and `bungee/build/libs/`. Use `opengate-<platform>-<version>.jar`, not a `-sources.jar`.

## Standalone Bukkit, Spigot, or Paper install

1. Copy the JAR matching your server into `plugins/` and restart.
2. Keep `online-mode=true` for premium-only servers. Use `online-mode=false` only when offline players must register.
3. Edit `plugins/OpenGate/config.properties`, then restart to apply changes.

Install OpenGate only on the game server in this mode.

## Proxy network install

1. Copy the matching Velocity or BungeeCord JAR into the proxy `plugins/` directory. Do not install OpenGate on backend servers.
2. Register a lightweight authentication server named `limbo` and at least one destination named `lobby`.
3. Set the proxy to offline mode so OpenGate can select premium or offline authentication per connection.
4. Put backends in offline mode and allow connections only from the proxy.

For Velocity, use modern forwarding and the same forwarding secret on every Paper backend. Modern forwarding does not replace a firewall. For BungeeCord, enable IP forwarding and Paper's BungeeCord support; legacy forwarding has no cryptographic protection, so a firewall or localhost binding is mandatory. Follow PaperMC's [forwarding](https://docs.papermc.io/velocity/player-information-forwarding/) and [backend security](https://docs.papermc.io/velocity/security/) guides.

## Current authentication flow

- Offline players register with `/register <password> <password>` and return with `/login <password>`.
- Premium players and valid six-hour IP sessions authenticate automatically.
- Passwords use versioned Argon2id hashes (`64 MiB`, three iterations); hashing runs on a bounded worker pool.
- Accounts persist in `plugins/OpenGate/opengate.db` using SQLite WAL mode.
- Authentication expires after 60 seconds and closes after three incorrect passwords.
- Reconnects cannot reset brute-force protection: IP addresses are limited to ten failures per rolling ten-minute window by default.
- Paper blocks movement, chat, commands, inventory actions, interaction, damage, and block changes until authentication.
- Velocity and BungeeCord redirect unauthenticated players to `limbo`, then send them to the first configured lobby after authentication.

Two-factor authentication is available through `/2fa setup <password>`, `/2fa confirm <code>`, `/totp <code>`, and `/2fa disable <password>`. TOTP secrets are encrypted with AES-256-GCM using `plugins/OpenGate/secret.key`; back up this key with the database because losing it makes enrolled TOTP secrets unrecoverable.

Authenticated players can manage their account with:

```text
/account password <current> <new>
/account logout
/account delete <password> confirm
```

Password changes and deletion run Argon2id verification outside the server thread. Logout revokes the persisted trusted session, while deletion removes the account and immediately closes the active authentication session.

On first launch OpenGate creates `config.properties`, `messages.properties`, `opengate.db`, and `secret.key`. Authentication timing, password bounds, IP limits, premium lookup, authentication routing, lobby order, and player messages can be changed without rebuilding. Messages support standard `&` color codes on every platform. Use `proxy-auth-server` and the comma-separated `proxy-lobby-servers` list when server names differ. Older Velocity-specific property names remain compatible.

Standard SQLite JDBC does not include portable database encryption, so OpenGate does not present the database as password-protected. Passwords are one-way Argon2id hashes, TOTP secrets use AES-256-GCM, trusted addresses use keyed HMAC fingerprints, and POSIX storage is restricted to its owner. Back up `opengate.db` and `secret.key` together and keep filesystem access private.

The SQLite schema is versioned and upgraded transactionally. Security events are written to `audit_events`, including logins, failures, rate limits, registration, password changes, session revocation, account deletion, and TOTP changes. Client addresses are stored only as keyed HMAC-SHA256 fingerprints, allowing correlation without retaining raw IP addresses.

Operators with `opengate.admin` can use:

```text
/opengate lookup <player>
/opengate audit <player> [limit]
/opengate revoke <player>
```

Admin lookups, audit reads, and revocations are themselves audited. Lookup output intentionally excludes addresses and password/TOTP material.
