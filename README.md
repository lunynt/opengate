<div align="center">
  <img src="assets/banner.png" alt="OpenGate" width="750">

  <p>Authentication for modern Minecraft servers and proxy networks, with support for premium, offline, and Floodgate players.</p>

  <a href="https://github.com/lunynt/opengate/actions/workflows/build.yml"><img src="https://github.com/lunynt/opengate/actions/workflows/build.yml/badge.svg?branch=main" alt="Build"></a>
  <a href="https://github.com/lunynt/opengate/stargazers"><img src="https://img.shields.io/github/stars/lunynt/opengate?style=flat" alt="GitHub stars"></a>
  <a href="https://github.com/lunynt/opengate"><img src="https://img.shields.io/github/repo-size/lunynt/opengate?style=flat" alt="Repository size"></a>
  <a href="https://hits.sh/github.com/lunynt/opengate/"><img src="https://hits.sh/github.com/lunynt/opengate.svg?label=views" alt="Repository views"></a>
</div>

## Features

- Premium auto-login and offline account authentication
- Floodgate and Geyser support
- Configurable `/login` and `/register` commands
- TOTP two-factor authentication
- Optional login sessions using secure, revocable cookies
- SQLite by default, with PostgreSQL, MySQL, MariaDB, and H2 support
- Velocity and BungeeCord proxy support
- Redis coordination for multi-proxy networks
- Rate limiting and brute-force protection
- Per-player translations and configurable messages
- Permission-required login and 2FA
- Protected staff accounts
- Offline player allow-list
- UUIDv4 to UUIDv7 translation
- Login and registration dialogs on supported clients
- Automatic password hash upgrades
- ajQueue integration
- API for other plugins

## Supported platforms

- Paper 1.21 through the latest release
- Bukkit and Spigot 1.21 through the latest release
- Velocity 4
- Latest BungeeCord

Paper, Bukkit, Spigot, and BungeeCord require Java 21 or newer. Velocity requires Java 25.

## Installation

### Paper / Bukkit / Spigot

1. Download the JAR for your platform and place it in `plugins/`.
2. Start the server once to generate the configuration files.
3. Edit `plugins/OpenGate/config.yml` and restart.

Keep `online-mode=true` on premium-only servers. Only use offline mode if you want offline players to register and log in.

### Velocity / BungeeCord

1. Install OpenGate on the proxy, not on every backend.
2. Add a lightweight authentication server named `limbo`.
3. Add at least one destination server such as `lobby`.
4. Put the backends in offline mode and block direct connections to them.

More proxy details are covered below.

## Commands

Player commands:

```text
/register <password> <password>
/login <password>
/totp <code>
/2fa setup <password>
/2fa confirm <code>
/2fa disable <password>
/account password <current> <new>
/account logout
/account delete <password> confirm
/premium <password>
/cracked <password>
```

Admin commands require `opengate.admin`:

```text
/opengate lookup <player>
/opengate audit <player> [limit]
/opengate revoke <player>
/opengate recover <player> <new-password>
```

Password recovery only works for offline accounts and requires `opengate.admin.recover`. It replaces the password, logs the action, and revokes the player's active sessions and cookies. Recovering a protected player who is currently online also requires `opengate.admin.recover.protected`.

## Configuration

OpenGate creates `config.yml`, `messages.yml`, `opengate.db`, and `secret.key` on first launch. For most servers, the default SQLite setup is enough.

To use another database:

```yaml
database:
  type: postgresql
  url: jdbc:postgresql://database.internal:5432/opengate
  username: opengate
  password: change-me
  pool-size: 10
```

Valid database types are `sqlite`, `postgresql`, `mysql`, `mariadb`, and `h2`.

Command aliases are YAML lists:

```yaml
commands:
  login:
    aliases: [l, signin]
  register:
    aliases: [reg, signup]
  totp:
    aliases: [otp]
```

You can require stronger authentication for staff and protect sensitive accounts from credential changes:

```yaml
authentication:
  require-login-permissions: [group.admin]
  require-2fa-permissions: [group.owner]
  protected-account-permissions: [group.owner]
```

Names belonging to Microsoft profiles are reserved by default, so a new offline account can't claim them. Existing offline accounts are left alone. After logging in, a Java player can run `/premium <password>` to enable Microsoft authentication for that account. OpenGate checks the Mojang profile and requires the exact profile name casing, then verifies ownership during the next connection. `/cracked <password>` switches back to password login. Set `authentication.premium-lookup.reserve-names: false` if you accept the username-squatting risk, or enable `auto-detect` to select premium mode automatically.

Add translation files such as `messages_lt.yml` or `messages_pt_BR.yml`. OpenGate uses the player's exact locale first, then the base language, then `messages.yml`.

Database, Redis, and secret-key settings can also come from environment variables for containerized deployments.

## Proxy setup

Configure the authentication server and post-login destinations in `config.yml`:

```yaml
proxy:
  auth-server: limbo
  lobby-servers:
    - lobby
    - survival
```

Add servers with those exact names to your Velocity or BungeeCord configuration. `limbo` should be an isolated authentication server with no gameplay permissions or sensitive plugins. OpenGate keeps unauthenticated players there, then sends them to the first available lobby after login.

Velocity networks should use modern forwarding with the same forwarding secret on every backend. BungeeCord networks need IP forwarding enabled. In both cases, use a firewall or private network so players can't connect to backend servers directly.

See PaperMC's [player forwarding](https://docs.papermc.io/velocity/player-information-forwarding/) and [backend security](https://docs.papermc.io/velocity/security/) guides if you're setting up a new proxy network.

## Security

Passwords are hashed with Argon2id, and old supported hashes are upgraded after a successful login. Password hashing runs asynchronously so it doesn't block the server thread.

TOTP secrets are encrypted with AES-256-GCM. Back up `secret.key` together with your database. Losing that key means enrolled TOTP secrets can't be recovered.

OpenGate rate-limits failed logins and registrations. IP addresses are never accepted as proof of identity, and audit logs store keyed fingerprints instead of raw addresses. If authentication can't be verified safely, the connection is rejected.

SQLite is a normal database file, so protect the whole `plugins/OpenGate/` directory and keep backups of both `opengate.db` and `secret.key`. Proxy backends should never be exposed directly to the internet.

Modern clients can optionally resume authenticated sessions using cookies. Logout, password changes, account deletion, and session revocation invalidate them.

## Integrations

### Floodgate / Geyser

Install Floodgate alongside OpenGate on the server or proxy. Verified Bedrock players can log in automatically without relying on username prefixes.

### Redis

If you're running multiple proxies, Redis keeps authentication sessions in sync:

```yaml
redis:
  enabled: true
  uri: rediss://user:password@redis.internal:6379/0
  channel: opengate:production
```

Every proxy should use the same Redis channel, database, `secret.key`, and UUID settings. If Redis becomes unavailable, OpenGate rejects authentication rather than risking inconsistent sessions.

### ajQueue

OpenGate can send authenticated players into an ajQueue queue instead of connecting them directly:

```yaml
integrations:
  ajqueue:
    enabled: true
    target: survival
```

Install ajQueue on the same proxy before enabling the integration.

## API

Other plugins can access OpenGate through `dev.lunynt.opengate.api.OpenGateApi` to check authentication state, look up accounts, inspect sessions, and revoke sessions.

Paper registers the API with Bukkit's services manager. The Paper, Velocity, and BungeeCord plugin entry points also expose `api()`.

## Building

Use the checked-in Gradle wrapper:

```bash
./gradlew clean build
```

Platform JARs are generated in `paper/build/libs/`, `bukkit/build/libs/`, `velocity/build/libs/`, and `bungee/build/libs/`. Use the platform JAR, not the sources JAR.

## License

OpenGate is licensed under the GNU Affero General Public License v3.0. See [LICENSE](LICENSE) for the full terms.
