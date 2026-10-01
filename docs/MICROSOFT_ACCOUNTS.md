# Microsoft / premium accounts

CraftConnect now supports adding personal Microsoft accounts through OAuth device
code authorization. The app shows a short code and opens the Microsoft verification
page in the system browser. Passwords are never collected by CraftConnect. The
process-scoped sign-in survives Activity recreation; Cancel stops local polling.

The public client ID is `931c77bd-a884-457d-9dbd-da85c2fca433`. This is an application
identifier, not a secret. Do not add a client secret to an Android application.

## Publisher prerequisites

Before testing with a real account:

1. Configure this registration in Microsoft Entra to allow **personal Microsoft
   accounts** (the previously observed single-organization setting does not).
2. Enable **Allow public client flows** for device code authorization. This flow
   does not need a redirect URI. Requested scopes: `XboxLive.signin offline_access`.
3. Wait for Mojang to approve this exact client ID through AppID Review.

This code change does not modify the Entra registration or grant consent on a
user's behalf. The UI distinguishes configuration failures, refused Minecraft app
access, missing Java entitlement/profile, Xbox restrictions, expired codes, and
sessions requiring sign-in again. A refusal from Minecraft is not proof that the
review is still pending; the message deliberately says it *may* be pending.

## Account and session lifecycle

Microsoft tokens are exchanged for Xbox Live, XSTS, and Minecraft access tokens.
The account is added only after checking Java Edition entitlements and retrieving
its player profile. Its name and UUID come from Minecraft; they cannot be edited.
Profiles are deduplicated by UUID, so reauthorization updates a renamed player.
Offline and Microsoft profiles may have the same name and remain distinct.

Only the refresh token is persisted, using AES-GCM and a device-bound Android
Keystore key. Encrypted files are in `noBackupFilesDir`, with the account ID bound
as additional authenticated data. Access tokens stay in memory. The existing
profile list contains display names, IDs and types only, preserving its v1 format.
Restoring that list to another device requires Microsoft sign-in again.

Before connecting, expired/missing in-memory sessions are refreshed. A rotated
refresh token is saved before contacting the downstream Xbox/Minecraft services.
Removing an account removes its token and cached session; deleting the currently
connected premium account also disconnects that session. This is a local sign-out,
not revocation of Microsoft's application consent.

Premium connections use the verified UUID in Login Start, Mojang session join,
RSA challenge response, and AES/CFB8 encryption. The session token is sent only to
Mojang over HTTPS, never to the Minecraft server. Premium profiles reject a server
that skips authenticated login or returns a mismatching identity; there is no
silent fallback to an offline identity. Existing offline profiles remain available
for explicitly offline-mode servers.

## Validation and limits

Unit tests cover the authorization polling interval/slow-down, cancellation and
expiry, denied sign-in, app approval and entitlement errors, refresh rotation,
UUID identity persistence and deletion. A local TCP test server exercises RSA,
Mojang join hash, encrypted/compressed login and keep-alive on protocols 775–777.
It does not contact Microsoft or Mojang or require real credentials.

Live Microsoft consent, Mojang approval and a real online-mode server have not
been tested here. Android Keystore and browser return behavior also need an actual
device/emulator check. Signed chat/profile certificates are not implemented by
this change: servers that require a secure chat profile may reject the connection
or chat. Do not disable a server's security settings to work around this limitation.

Validation:

```sh
./gradlew --no-daemon :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Protocol/authentication references:

- https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code
- https://github.com/PrismLauncher/PrismLauncher/tree/develop/launcher/minecraft/auth/steps
- https://github.com/GeyserMC/MCProtocolLib/blob/master/protocol/src/main/java/org/geysermc/mcprotocollib/protocol/packet/login/clientbound/ClientboundHelloPacket.java
