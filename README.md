# OpenGate

OpenGate is an authentication plugin for modern Minecraft servers. A shared Java 25 core powers separate Bukkit, Spigot, Paper, Velocity, and BungeeCord adapters.


## Platforms

- Velocity 4
- BungeeCord 1.21
- Bukkit and Spigot 26.2
- Paper 26.2

## Main principles

Authentication is modeled as a fail-closed per-connection state machine. Identity resolution, registration, passwords, TOTP, and release are explicit stages rather than scattered listener flags. Platform code enforces decisions made by the shared core.

- Fail closed when identity or session state cannot be verified
- Keep password and database work off server threads
- Work without external services by default
- Keep platform adapters small and authentication rules in the shared core
- Limit features to authentication, account security, and server operation

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
- Premium players authenticate automatically. Floodgate players do too when the optional integration is installed and confirms their identity.
- Offline accounts always require their password; an IP address is never treated as proof of identity.
- Passwords use versioned Argon2id hashes (`64 MiB`, three iterations); hashing runs on a bounded worker pool.
- Accounts persist through a bounded HikariCP connection pool. SQLite WAL mode is the zero-configuration default;
  PostgreSQL, MySQL, MariaDB, and H2 are also supported.
- Authentication expires after 60 seconds and closes after three incorrect passwords.
- Reconnects cannot reset brute-force protection: both accounts and IP addresses are limited to ten failures per rolling ten-minute window by default. An address may register five accounts per hour.
- Paper blocks movement, chat, commands, inventory actions, interaction, damage, and block changes until authentication.
- Velocity and BungeeCord redirect unauthenticated players to `limbo`, then send them to the first configured lobby after authentication.

Two-factor authentication is available through `/2fa setup <password>`, `/2fa confirm <code>`, `/totp <code>`, and `/2fa disable <password>`. New enrollments use TOTP-HMAC-SHA-256. Existing untagged TOTP credentials remain verifiable with their original HMAC-SHA-1 setting so upgrades do not lock users out. TOTP secrets are encrypted with AES-256-GCM using `plugins/OpenGate/secret.key`; back up this key with the database because losing it makes enrolled TOTP secrets unrecoverable.

Authenticated players can manage their account with:

```text
/account password <current> <new>
/account logout
/account delete <password> confirm
```

Password changes and deletion run Argon2id verification outside the server thread. Logout closes the active authentication session, while deletion removes the account and immediately disconnects the player.

On first launch OpenGate creates `config.properties`, `messages.properties`, `opengate.db`, and `secret.key`. Authentication timing, password bounds, IP limits, premium lookup, authentication routing, lobby order, and player messages can be changed without rebuilding. Messages support standard `&` color codes on every platform. Use `proxy-auth-server` and the comma-separated `proxy-lobby-servers` list when server names differ. Older Velocity-specific property names remain compatible.

Command aliases are independent comma-separated lists. Labels are normalized, and invalid aliases, duplicates, or
aliases shadowing another primary command prevent startup:

```properties
command.login.aliases=l,signin
command.register.aliases=reg,signup
command.totp.aliases=otp
```

Paper/Bukkit remap configured aliases before player or server-command dispatch; Velocity and Bungee register the
same aliases with their native command managers.

Multiple translations can be active simultaneously. Add files such as `messages_lt.properties` or
`messages_pt_BR.properties`; each file only needs translated overrides. Resolution uses the player's exact locale,
then language, then `messages.properties`. Unknown translation keys fail startup to expose mistakes.

Set `translate-uuid4-to-uuid7=true` to persistently map UUIDv4 identities to RFC 9562 UUIDv7. All proxies in a
network must share the mapping database and use the same setting.

Permission-based requirements and protected accounts use permission-node lists:

```properties
require-login-permissions=group.admin,opengate.require.login
require-2fa-permissions=group.owner,opengate.require.2fa
protected-account-permissions=group.owner
```

Requiring 2FA also requires a password. Players without the required credentials remain inside the authentication
gate and receive an enrollment flow. Protected players can complete mandatory first-time enrollment, but cannot
subsequently change passwords, disable 2FA, or delete their accounts.

Modern clients can resume fully authenticated offline sessions through opaque cookies. Only SHA-256 token digests
are stored in the database; cookies are account-bound, expire, and are revoked by logout, password changes, deletion,
admin revocation, and API revocation. IP addresses are never accepted as authentication evidence.
Cookies do not bypass permission-required passwords, enrolled 2FA, or newly required 2FA enrollment.
Each cookie is also bound to a one-way fingerprint of the account's current password hash and encrypted TOTP value,
so any credential change invalidates older cookies even if explicit cleanup encounters a transient failure.

The `dev.lunynt.opengate.api.OpenGateApi` interface exposes sanitized user lookup, session snapshots,
authentication status, and revocation. `revokeSession(connectionId)` returns `CompletionStage<Boolean>`:
`false` means no local session existed; `true` is returned only after local invalidation, cookie deletion,
and Redis publication complete. Any failed step completes the result exceptionally. Handle that result
asynchronously; do not block a platform event thread waiting for database or Redis operations.
Paper also registers this interface with Bukkit's services manager. Paper, Velocity, and Bungee entry-point classes
all expose `api()` for integrations that obtain the OpenGate plugin instance from their platform plugin manager.

```properties
cookie-sessions-enabled=true
cookie-session-hours=12
```

Native login/register dialog prompts can be toggled with `minecraft-dialogs-enabled`. Dialogs intentionally prefill
the normal command instead of collecting passwords in visible, unmasked Minecraft dialog fields.
Dialog titles and buttons use each player's active message locale. Paper and Bungee expose constructible native
dialog types. Velocity 4.1 only exposes the `DialogLike` marker and a no-op API default, so its secure text prompt
remains the supported fallback until Velocity publishes a constructible dialog API.

An optional Redis coordinator resolves simultaneous authentications across proxies using a Redis-ordered sequence;
the latest successful authentication wins and older connections are disconnected. Redis carries only UUIDs and
invalidation metadata. The configured SQL database remains durable truth.

```properties
redis-enabled=true
redis-uri=rediss://user:password@redis.internal:6379/0
redis-channel=opengate:production
redis-timeout-millis=3000
```

Every proxy must use the same Redis channel, database, UUID translation setting, and secret key. Use `rediss://`
outside a trusted private network. If Redis initialization or authentication publication fails, OpenGate fails closed.
Loss of the Redis subscription invalidates local sessions because pub/sub cannot replay missed revocations.
Authentication publication is rejected until the subscription is acknowledged again. In-flight publications
also fail if the subscription disconnects during them. Players must reconnect after an interruption.

ajQueue 2.9.1 integration is optional and uses its official API:

```properties
ajqueue-enabled=true
ajqueue-target=survival
```

When enabled, ajQueue must load on the proxy. Authenticated players are submitted to the configured queue instead of
bypassing it through a direct lobby connection. A missing integration fails plugin startup and releases OpenGate's
resources. A rejected or failed enqueue disconnects the player with the localized `queue-unavailable` message.

Enable the offline allow-list with `offline-whitelist-enabled=true` and configure comma-separated, case-insensitive
Minecraft names in `offline-whitelist`. Premium identities remain unaffected.

### Database configuration

Set `database-type` to `sqlite`, `postgresql`, `mysql`, `mariadb`, or `h2`. SQLite needs no other setting.
For a remote database, configure the JDBC URL and credentials:

```properties
database-type=postgresql
database-url=jdbc:postgresql://database.internal:5432/opengate
database-username=opengate
database-password=change-me
database-pool-size=10
database-connection-timeout-millis=5000
```

Create the database itself and a least-privilege database user before starting OpenGate. Never commit a populated
configuration file or reuse the database password for another service.

For immutable or orchestrated deployments, infrastructure settings can be injected without modifying the generated
file. `OPENGATE_DATABASE_TYPE`, `OPENGATE_DATABASE_URL`, `OPENGATE_DATABASE_USERNAME`,
`OPENGATE_DATABASE_PASSWORD`, `OPENGATE_DATABASE_POOL_SIZE`, and
`OPENGATE_DATABASE_CONNECTION_TIMEOUT_MILLIS` override their corresponding database properties. Redis supports
`OPENGATE_REDIS_ENABLED`, `OPENGATE_REDIS_URI`, `OPENGATE_REDIS_CHANNEL`, and
`OPENGATE_REDIS_TIMEOUT_MILLIS`. Set `OPENGATE_SECRET_KEY` to Base64 encoding of exactly 32 random bytes to share
one encryption root across replicas without writing `secret.key`; environment values take precedence and are never
copied into `config.properties`.

CI can enable live remote-database coverage with `OPENGATE_TEST_POSTGRESQL_URL`, `OPENGATE_TEST_MYSQL_URL`, and
`OPENGATE_TEST_MARIADB_URL`, plus their matching `_USERNAME` and `_PASSWORD` variables. Use a dedicated test
database: the test applies OpenGate migrations and inserts uniquely named test accounts without altering existing rows.

Standard SQLite JDBC does not include portable database encryption, so OpenGate does not present the database as password-protected. Passwords are one-way Argon2id hashes, TOTP secrets use AES-256-GCM, audit addresses use keyed HMAC fingerprints, and POSIX storage is restricted to its owner. Back up `opengate.db` and `secret.key` together and keep filesystem access private.

## Floodgate and Geyser

Install Geyser and Floodgate on the same proxy or server as OpenGate; no additional OpenGate setting is required. OpenGate uses Floodgate's live API and never trusts username prefixes alone. If Floodgate is absent or its API cannot confirm a Bedrock player, authentication fails closed and the normal premium/offline rules apply. On proxy networks, keep Geyser and Floodgate at the proxy layer alongside the OpenGate proxy JAR.

## Security notes

Offline-mode passwords are Minecraft command arguments and are not end-to-end encrypted by OpenGate. Use encrypted transport where your platform supports it, lock backend ports to the proxy, restrict access to `plugins/OpenGate/`, and never share `secret.key`. Rotate exposed credentials immediately. Audit records are retained for 90 days and store keyed address fingerprints rather than raw addresses.

The SQLite schema is versioned and upgraded transactionally. Security events are written to `audit_events`, including logins, failures, rate limits, registration, password changes, session revocation, account deletion, and TOTP changes. Client addresses are stored only as keyed HMAC-SHA256 fingerprints, allowing correlation without retaining raw IP addresses.

Operators with `opengate.admin` can use:

```text
/opengate lookup <player>
/opengate audit <player> [limit]
/opengate revoke <player>
```

Admin lookups, audit reads, and revocations are themselves audited. Lookup output intentionally excludes addresses and password/TOTP material.

## License

OpenGate is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE) for the full terms.
