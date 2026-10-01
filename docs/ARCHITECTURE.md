# CraftConnect architecture

CraftConnect is an Android application that behaves as a lightweight Minecraft protocol client without rendering the game world.

## Layers

- **ui/** — Jetpack Compose screens, visual components and theme.
- **domain/model/** — app-level content models independent from packet implementations.
- **domain/session/** — observable session state, lifecycle events and the UI-facing session contract.
- **data/** — repositories, persistence and external data adapters.
- **protocol/** — Minecraft connection and packet handling boundary.

The UI must not depend directly on packet classes.

`DefaultSessionManager` is the adapter between the domain-facing `SessionManager` and
the packet-independent `MinecraftConnection`. It serializes lifecycle operations,
publishes state through `StateFlow`, emits one-off lifecycle events through
`SharedFlow`, and maps protocol failures to diagnostic domain errors without exposing
exception messages that could contain server or account data.

## Initial protocol scope

The default adapter, `ModernOfflineMinecraftConnection`, implements offline-mode login
for Java protocol 775 (Minecraft 26.1), including the configuration state, compression,
keep-alive, position acknowledgement, chat and commands. The protocol 47 adapter remains
separate rather than mixing version-specific packet identifiers in one implementation.

The first real networking milestone should implement only what the product needs:

1. Server status and handshake.
2. Login/session lifecycle.
3. Keep-alive and disconnect handling.
4. Chat and commands.
5. Player list.
6. Compression and protocol negotiation.
7. Automatic reconnect.

World rendering, audio and gameplay simulation remain outside the product scope.

## UI prototype

The first prototype intentionally uses in-memory demo data so interface work can progress before protocol decisions are frozen.

Current screens:

- saved server browser,
- connected-session chat,
- player list and quick actions,
- connection/application settings.

The visual system follows the initial CraftConnect concept: near-black surfaces, deep crimson panels, sharp red accents and restrained neon glow.


## Authentication integrations

`bridge-protocol` is a small Java library shared by Android and the server plugin.
`server-bridge` adapts AuthMe/nLogin public APIs and AuthGatewayX ServicesManager.
The client subscribes on `craftconnect:auth` with a fresh nonce per TCP session.
Server authentication status, not chat text, gates post-login visibility commands.
See [authentication bridge](AUTHENTICATION_BRIDGE.md).
