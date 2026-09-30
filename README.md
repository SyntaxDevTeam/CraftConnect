# CraftConnect

**Headless Minecraft protocol client for Android.**

CraftConnect connects to Minecraft servers without rendering the game world. The application is intended for chat, commands, player/session information, notifications and connection-oriented tooling rather than gameplay.

## Current state

The repository contains the initial Android application skeleton and a functional Jetpack Compose UI prototype.

Implemented now:

- Kotlin + Jetpack Compose Android project,
- dark crimson / neon design system,
- saved server browser,
- mock connect flow,
- chat and command surface,
- online player list,
- settings screen,
- reusable UI components,
- protocol abstraction ready for a real implementation,
- Gradle Wrapper,
- GitHub Actions Android CI,
- repository guidance in `AGENTS.md`.

Networking is intentionally not implemented yet. The current interface uses demo data.

## Toolchain

- Android Gradle Plugin 9.4.0
- Gradle 9.6.1
- Kotlin 2.2.10
- Compose BOM 2025.06.01
- compileSdk / targetSdk 36
- minSdk 26
- JDK 21 for CI/Gradle daemon

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

The main architectural constraint is that Compose code must never depend directly on Minecraft packet implementation classes.

## Build

```bash
./gradlew :app:assembleDebug
```

Validation:

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```
