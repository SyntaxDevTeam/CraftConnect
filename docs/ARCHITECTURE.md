# CraftConnect architecture

CraftConnect is an Android application that behaves as a lightweight Minecraft protocol client without rendering the game world.

## Layers

- **ui/** — Jetpack Compose screens, visual components and theme.
- **domain/model/** — app-level models independent from packet implementations.
- **data/** — repositories, persistence and external data adapters.
- **protocol/** — Minecraft connection and packet handling boundary.

The UI must not depend directly on packet classes.

## Initial protocol scope

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
