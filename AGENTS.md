# Repository Guidelines

## Project Structure & Module Organization

This is a Java 25 Gradle multi-module project. Put platform-neutral logic in `common/`, Velocity integration in `velocity/`, BungeeCord integration in `bungee/`, and Bukkit-compatible server integration in `paper/`. The `bukkit/` module packages that server adapter for Bukkit and Spigot. Each module uses the standard `src/main/java`, `src/main/resources`, and `src/test/java` layout. Organize packages by feature or responsibility rather than generic buckets.

The `inspo/NavAuth-main/`, `inspo/LibreLogin-master/`, and `inspo/LibreLoginProd-master/` trees are reference implementations. Consult them for authentication flows, platform integration, configuration, and migration behavior, but do not edit or depend directly on their source unless a task explicitly requires it. Reimplement only the concepts needed by this project and respect upstream licenses.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper so every environment uses the same Gradle version:

```bash
./gradlew build          # compile, test, and package the project
./gradlew test           # run all automated tests
./gradlew clean build    # verify a build from a clean workspace
./gradlew :paper:build   # create the Paper plugin JAR
./gradlew :bukkit:build  # create the Bukkit and Spigot plugin JAR
./gradlew :velocity:build # create the Velocity plugin JAR
./gradlew :bungee:build  # create the BungeeCord plugin JAR
```

Target one test with `./gradlew test --tests 'package.ClassName'`. Do not commit generated files from `build/` or `.gradle/`.

## Coding Style & Naming Conventions

Use modern Java and four-space indentation. Prefer immutable state and constructor injection. Name classes and interfaces with `UpperCamelCase`, methods and variables with `lowerCamelCase`, constants with `UPPER_SNAKE_CASE`, and packages in lowercase. Choose domain names such as `SessionService` and `PasswordHasher`; avoid vague names such as `Manager` or `Utils` unless their scope is genuinely general.

Follow the formatter and static-analysis configuration in the Gradle build. Run formatting before submitting changes. Never copy secrets, production addresses, or credentials from reference configurations.

## Testing Guidelines

Use JUnit 5. Mirror production package paths under `src/test/java` and name test classes `*Test`; use `*IntegrationTest` for tests crossing database, network, or platform boundaries. Test success, failure, and security-sensitive edge cases, especially session expiry, password verification, account migration, and authorization. Each bug fix should include a regression test. Run `./gradlew build` before opening a pull request.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects, optionally scoped, such as `auth: reject expired sessions`. Keep commits focused and avoid mixing reference-file changes with implementation work. Pull requests should explain the motivation, summarize behavior changes, link related issues, and list verification commands. Include logs or screenshots for user-visible command, configuration, or login-flow changes, and call out compatibility or migration concerns explicitly.
