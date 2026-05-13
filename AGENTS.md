# AGENTS.md

## Project Shape
- Single-module Gradle Kotlin DSL project for a Paper `1.21.8` Minecraft plugin.
- Java toolchain and bytecode target are Java 21; do not downgrade language/API assumptions.
- Plugin entrypoint is `com.example.sxt.SimpleXpTeleportPlugin`, declared in `src/main/resources/plugin.yml`.
- Command registration is manual: every command needs both a `plugin.yml` entry and a `registerCommand(...)` call in `SimpleXpTeleportPlugin`.

## Commands
- Build the shaded plugin jar with `./gradlew build` on Unix or `./gradlew.bat build` on Windows.
- The distributable jar is `build/libs/simple-xp-teleport-1.0.0.jar`; `assemble` depends on `shadowJar` and removes the classifier.
- Run the Paper dev server with `./gradlew runServer` or `./gradlew.bat runServer`; it creates/uses the ignored `run/` directory.
- Run tests with `./gradlew test` or `./gradlew.bat test`; JUnit 5 is configured, but this repo currently has no `src/test` tests.

## Build Quirks
- `plugin.yml` uses `${version}` and is expanded by `processResources`; do not hardcode the plugin version there unless changing the build.
- `sqlite-jdbc` is bundled and relocated from `org.sqlite` to `com.example.sxt.libs.org.sqlite` in the shadow jar.
- PlaceholderAPI and WorldGuard are `compileOnly` soft dependencies; code must tolerate them being absent at runtime.
- There is no formatter, lint plugin, or CI workflow in this repo; `build` is the main verification source of truth.

## Runtime Config
- Default config lives in `src/main/resources/config.yml`; default language is `ja_JP`.
- Language files in `src/main/resources/lang/*.yml` use MiniMessage strings and shared placeholders documented at the top of each file.
- Persistent plugin data is configured as SQLite `data.db` under the plugin data folder; do not commit runtime data from `run/`.
