# CraftConnect

## Logowanie i automatyczna próba ukrycia

Obsługa AuthMe Reloaded, nLogin i AuthGatewayX korzysta z pluginu serwerowego
**CraftConnectBridge**. Stan logowania pochodzi z publicznego API pluginu; treść,
język i format komunikatów nie mają znaczenia. Wymagane jest zainstalowanie bridge'a
na serwerze. [Instalacja, API i ograniczenia](docs/AUTHENTICATION_BRIDGE.md).

Dopiero po potwierdzeniu uwierzytelnienia i pozycji w świecie aplikacja wysyła
`/gamemode spectator`. Po 3 sekundach bez potwierdzenia spectator wysyła jednorazowo
`/vanish`. Brak bridge'a, brak obsługiwanego API lub błędne hasło nie uruchamiają
komend. Obie komendy nadal wymagają uprawnień gracza; nie gwarantują ochrony ekwipunku.

**Headless Minecraft protocol client for Android.**

CraftConnect connects to Minecraft servers without rendering the game world. The application is intended for chat, commands, player/session information, notifications and connection-oriented tooling rather than gameplay.

## Current state

The repository contains the initial Android application skeleton and a functional Jetpack Compose UI prototype.

Implemented now:

- Kotlin + Jetpack Compose Android project,
- dark crimson / neon design system,
- branded launcher, adaptive and in-app logo assets,
- saved server browser,
- direct offline-mode connection using Minecraft Java 26.1 protocol 775,
- chat and command surface,
- online player list,
- settings screen,
- reusable UI components,
- observable session lifecycle and typed diagnostic errors,
- protocol abstraction ready for a real implementation,
- Gradle Wrapper,
- GitHub Actions Android CI,
- repository guidance in `AGENTS.md`.

The default networking adapter supports direct TCP login to an offline-mode server using
Minecraft Java 26.1 protocol 775, including the login, configuration and play states,
compression negotiation, keep-alive replies and position acknowledgement. The older
protocol 47 adapter remains isolated for later explicit version selection.

The client sends only the selected nickname. In offline mode the server or proxy owns
identity assignment and returns the UUID during login; CraftConnect does not allow an
arbitrary UUID to be injected. Permission data tied to an offline UUID is only as secure
as the server's nickname protection, so this mode must not be exposed as authentication.

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


## Per-server Minecraft version

Server creation and editing offer Java 26.1 (775), 26.2 (776), and 26.3 (777).
New servers default to 26.3; existing lists retain 26.1 until edited.
The selection controls the handshake, login session ID, configuration and play packet
IDs, game-mode decoding, and teleport confirmation layout. It is not automatic
version detection. Offline authentication limitations still apply.
Connection failures display a diagnostic code containing the stage and protocol.

Packet references: ViaVersion `Protocol26_1To26_2`, `Protocol26_2To26_3`,
`ClientboundConfigurationPackets26_3`, `ClientboundPackets26_3`, and
`EntityPacketRewriter26_3` in https://github.com/ViaVersion/ViaVersion.
