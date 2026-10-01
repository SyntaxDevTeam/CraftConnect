# CraftConnect

## Automatyczna próba ukrycia postaci

Po pierwszym potwierdzeniu pozycji w świecie aplikacja wysyła `/gamemode spectator`.
Jeżeli serwer nie potwierdzi trybu spectator pakietem zmiany trybu w ciągu 3 sekund,
aplikacja jednokrotnie wysyła `/vanish` jako fallback. Jeżeli gracz już dołącza jako
spectator, komendy są pomijane. Teleporty i respawn nie ponawiają `/vanish`, ponieważ
na wielu serwerach ta komenda przełącza niewidzialność. Rozłączenie anuluje próbę.

Obie komendy wymagają wsparcia serwera i odpowiednich uprawnień. Wysłanie komendy
nie gwarantuje niewidzialności, odporności na obrażenia ani ochrony ekwipunku.
Na serwerach wymagających dodatkowego `/login` po wejściu do świata pierwsza próba
może zostać odrzucona przez plugin logowania; po uwierzytelnieniu komendę należy
wtedy wysłać ręcznie. Aplikacja nie przywraca automatycznie wcześniejszego trybu gry
i nie wysyła ponownie `/vanish` przy rozłączeniu.

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
