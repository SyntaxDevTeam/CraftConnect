# AGENTS.md

## Project type

This is an Android Gradle project written in Kotlin and Jetpack Compose.

CraftConnect is a lightweight, headless Minecraft protocol client for Android. It is not a game client and must not grow a renderer, world simulation layer, audio engine, or other functionality that exists only to reproduce normal gameplay.

## Current CI state

The repository contains a checked-in GitHub Actions pipeline in `.github/workflows/android-ci.yml`.

For Codex Cloud work, treat the bootstrap and validation routine below as the required local CI-equivalent gate after every code change.

## Environment

The project requires an Android SDK. In Codex Cloud, the environment must provide:

- ANDROID_HOME
- JDK 21 for the Gradle daemon toolchain
- Android SDK command-line tools
- platform-tools
- Android platform 36
- Android build-tools 36.0.0

Do not assume Android Studio is installed.

## Codex Cloud bootstrap

Use this block on a fresh machine:

```bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  CMDLINE_TOOLS_URL="https://redirector.gvt1.com/edgedl/android/repository/commandlinetools-linux-11076708_latest.zip"
  TMP_DIR="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$TMP_DIR/cmdline-tools.zip"
  unzip -q "$TMP_DIR/cmdline-tools.zip" -d "$TMP_DIR/unzipped"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$TMP_DIR/unzipped/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$TMP_DIR"
fi

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## Verification after every fix

```bash
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
```

The project currently targets:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.1
- Kotlin 2.2.10
- Compose BOM 2025.06.01
- compileSdk / targetSdk 36
- JDK 21 daemon toolchain

## Validation commands

Use these commands when validating changes:

```bash
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:lintDebug
```

Avoid running plain `./gradlew build` unless explicitly requested.

## Local files

Do not commit `local.properties`, signing keys, keystores, or credentials.

If `local.properties` is missing in a cloud environment:

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

## Architecture policy

Keep concerns isolated:

- `ui/` — Compose screens, visual components and theme.
- `domain/` — app models and use-case contracts.
- `data/` — persistence, repositories and external data adapters.
- `protocol/` — Minecraft protocol implementation and connection state.

Compose code must not depend directly on packet implementation classes. Protocol traffic should be exposed to the app through stable domain-facing interfaces.

## Product scope

Prefer the smallest protocol implementation necessary for the product:

1. Status / handshake.
2. Authentication and login lifecycle.
3. Keep-alive and disconnect handling.
4. Chat and commands.
5. Player list.
6. Compression and protocol negotiation.
7. Reconnect and session recovery.

Rendering chunks, entities or gameplay is out of scope unless a future feature has a concrete non-rendering need for the underlying packet.

## i18n policy

All static user-facing texts must live in Android string resources and be referenced through `R.string.*`. Do not hardcode static interface text in Kotlin.

Server names, usernames, chat messages and other remote/runtime data are not translation resources.

## File creation and structure policy

Files should be segregated by responsibility. Do not create generic catch-all utility packages or oversized screens when a reusable component or domain abstraction is appropriate.

## Administration and AuthGatewayX integration

Before changing RCON, server administration, pairing, console, metrics, branding, or AuthGatewayX integration, read:

```text
docs/ADMIN_INTEGRATION.md
```

The architecture is intentionally layered:

```text
Minecraft Protocol -> baseline player capabilities
RCON               -> optional Console Lite / command-response
AuthGatewayX        -> optional paired Enhanced Mode
```

Non-negotiable rules:

- CraftConnect must remain useful without any server plugin.
- Do not make `CraftConnectBridge` mandatory for normal chat, commands, players, MOTD or status.
- Standard Minecraft actions execute with the connected player's normal server-side permissions.
- RCON is optional and must be isolated from the Minecraft session lifecycle.
- RCON passwords are secrets and must not be stored in plaintext SharedPreferences, profile JSON, logs, packet traces or telemetry.
- AuthGatewayX is the preferred provider for overlapping enhanced capabilities; RCON is only a fallback.
- Pairing does not grant authorization by itself. AuthGatewayX capabilities come from current server-side permissions.
- UI code consumes the stable domain capability model and must not depend directly on RCON or AuthGatewayX transport classes.
- Missing capabilities should normally hide/disable unavailable administrative surfaces instead of presenting misleading controls.
- `console view` and `console execute` are separate privileges.
- Failure of RCON or AuthGatewayX integration must degrade only that provider and must not disconnect a healthy Minecraft session.

The shared capability model lives under:

```text
app/src/main/java/pl/syntaxdevteam/craftconnect/domain/integration/
```

Protocol/provider implementations must map into that model instead of leaking transport-specific state into Compose.
