# CraftConnect — model integracji administracyjnej

## Cel

CraftConnect pozostaje uniwersalnym, headless klientem Minecraft. Podstawowe funkcje nie mogą wymagać żadnego pluginu serwerowego.

Rozszerzenia administracyjne są dostarczane warstwowo:

```text
Minecraft Protocol
      ↓
podstawowe funkcje gracza

RCON
      ↓
lekka administracja / Console Lite

AuthGatewayX
      ↓
pełna, sparowana integracja administracyjna
```

AuthGatewayX jest preferowanym providerem dla funkcji, które udostępnia. RCON jest opcjonalnym fallbackiem i nie zastępuje AGX.

## 1. Tryb podstawowy — bez pluginu

CraftConnect łączy się jak zwykły klient Minecraft i ma dokładnie takie możliwości, jakie serwer udostępnia zalogowanemu graczowi.

Zakres:

- status serwera i MOTD,
- logowanie i utrzymanie sesji,
- czat,
- komendy wykonywane jako gracz,
- lista graczy,
- informacje dostępne przez standardowy protokół Minecraft.

Autoryzacja odbywa się po stronie serwera. CraftConnect nie omija permissions i nie emuluje uprawnień administratora.

## 2. RCON — Console Lite

RCON jest opcjonalną funkcją per profil serwera. Użytkownik ręcznie podaje host/port oraz hasło RCON.

RCON może zapewnić:

- wykonywanie komend jako konsola,
- wyświetlanie odpowiedzi zwracanych przez RCON,
- prostą historię poleceń,
- gotowe skróty diagnostyczne, jeżeli dana komenda istnieje na serwerze.

### Ograniczenie

RCON nie jest pełnym strumieniem konsoli. CraftConnect nie może traktować odpowiedzi RCON jako kompletnego live logu serwera. UI powinno używać nazwy `Console Lite` lub równoważnej, dopóki źródłem jest wyłącznie RCON.

### Bezpieczeństwo RCON

- hasło RCON jest sekretem administracyjnym,
- nie wolno zapisywać go w plaintext `SharedPreferences`, JSON profilu ani logach,
- sekret ma być przechowywany przez Android Keystore / szyfrowany credential store,
- zwykły `ServerProfile` może przechowywać jedynie referencję do credentialu,
- nie wolno wysyłać hasła RCON przez protokół Minecraft ani AuthGatewayX,
- UI musi jasno odróżniać dostęp gracza od dostępu RCON.

## 3. AuthGatewayX — Enhanced Mode

AuthGatewayX jest zaufanym providerem rozszerzonych funkcji CraftConnect. Integracja wymaga sparowania urządzenia z konkretną tożsamością gracza.

Docelowe możliwości:

- live console,
- wykonywanie komend konsolowych,
- status i health serwera,
- TPS / MSPT,
- CPU / RAM i inne metryki udostępnione przez serwer,
- uptime,
- wersja platformy i Minecraft,
- diagnostyka,
- branding serwera: nazwa, ikona/logo i metadane,
- zdarzenia serwerowe i przyszłe powiadomienia.

### Autoryzacja

Samo sparowanie urządzenia NIE daje pełnych uprawnień.

Każda capability jest przyznawana na podstawie bieżących permissions gracza/rangi na serwerze. AGX jest źródłem decyzji autoryzacyjnej.

Przykładowe permission nodes:

```text
authgatewayx.craftconnect.pair
authgatewayx.craftconnect.status
authgatewayx.craftconnect.stats
authgatewayx.craftconnect.console.view
authgatewayx.craftconnect.console.execute
authgatewayx.craftconnect.server-info
authgatewayx.craftconnect.branding
```

Nie należy umieszczać trwałego snapshotu permissions w długowiecznym tokenie urządzenia. Uprawnienia mogą zostać zmienione w dowolnym momencie.

## Pairing

Minimalny model parowania:

```text
CraftConnect
    │
    │ zwykła sesja Minecraft
    ▼
AuthGatewayX
    │
    ├── identyfikacja UUID sesji
    ├── jednorazowy challenge
    ├── jawne zatwierdzenie przez gracza
    └── rejestracja klucza urządzenia
```

Rekord sparowanego urządzenia powinien identyfikować co najmniej:

```text
serverId
playerUuid
deviceId
devicePublicKey
createdAt
lastUsedAt
revokedAt (opcjonalnie)
```

Sekret prywatny urządzenia pozostaje wyłącznie po stronie CraftConnect.

## Capability negotiation

UI nie powinno zgadywać dostępnych funkcji. Warstwa domenowa ma otrzymywać jawny zestaw capabilities.

Minimalny model:

```text
MINECRAFT_CHAT
MINECRAFT_COMMANDS
PLAYER_LIST
SERVER_STATUS
RCON_COMMANDS
AGX_PAIRING
AGX_STATUS
AGX_STATS
AGX_CONSOLE_VIEW
AGX_CONSOLE_EXECUTE
AGX_BRANDING
AGX_DIAGNOSTICS
```

Zasada preferencji:

```text
jeżeli AGX zapewnia daną funkcję
    -> użyj AGX
else if RCON zapewnia ograniczony odpowiednik
    -> użyj RCON
else
    -> użyj możliwości standardowego klienta lub ukryj funkcję
```

Brak permission/capability powinien domyślnie usuwać niedostępną funkcję z UI zamiast pokazywać ekran, który dopiero po wejściu zwraca `brak uprawnień`.

## Protokół AuthGatewayX ↔ CraftConnect

Integracja musi być wersjonowana niezależnie od wersji aplikacji i pluginu.

Początkowa stała:

```text
protocolVersion = 1
channel = authgatewayx:craftconnect
```

Przykładowy handshake logiczny:

```text
C -> S  HELLO(protocolVersion, appVersion, deviceId)
S -> C  HELLO(protocolVersion, serverId, supportedFeatures)
S -> C  CAPABILITIES(grantedCapabilities)
```

Dokładny format binarny/serializacja zostaną ustalone przed implementacją transportu. Nie należy zamrażać przypadkowego formatu protokołu w UI ani klasach platformowych.

## Granice odpowiedzialności

### CraftConnect

- standardowy klient Minecraft,
- bezpieczne przechowywanie credentiali,
- klient RCON,
- klient protokołu AGX,
- capability aggregation,
- dynamiczne UI.

### AuthGatewayX

- identyfikacja zalogowanego gracza,
- pairing/revocation urządzeń,
- sprawdzanie permissions,
- generowanie capabilities,
- bezpieczne udostępnianie danych administracyjnych,
- platformowe providery metryk, konsoli i brandingu.

## Wymagania implementacyjne

- podstawowy CraftConnect musi działać bez AGX i bez RCON,
- awaria RCON nie może zerwać sesji Minecraft,
- awaria integracji AGX nie może zerwać podstawowej sesji Minecraft,
- AGX ma pierwszeństwo przed RCON dla pokrywających się capabilities,
- credentiale nigdy nie trafiają do telemetryki, packet trace ani diagnostyki,
- integracja ma być rozszerzalna o nowe capabilities bez breaking change całego klienta,
- kod UI nie może zależeć bezpośrednio od pakietów RCON ani implementacji custom payload.

## Etapy

### Etap 0 — szkielet

- [x] dokumentacja modelu trójwarstwowego,
- [x] domenowy model capability/provider w CraftConnect,
- [ ] bezpieczny model konfiguracji RCON,
- [ ] agregator capabilities,
- [ ] transport RCON,
- [ ] transport AuthGatewayX.

### Etap 1 — RCON

- [ ] credential store oparty o Android Keystore,
- [ ] klient Source RCON,
- [ ] test połączenia,
- [ ] Console Lite,
- [ ] izolacja awarii od sesji Minecraft.

### Etap 2 — AuthGatewayX pairing

- [ ] handshake v1,
- [ ] wykrywanie AGX,
- [ ] pairing challenge,
- [ ] device keypair,
- [ ] revoke/list devices,
- [ ] capabilities z permissions.

### Etap 3 — Enhanced Mode

- [ ] live console,
- [ ] metrics/status,
- [ ] branding,
- [ ] diagnostics,
- [ ] command execution z osobnym permission.
