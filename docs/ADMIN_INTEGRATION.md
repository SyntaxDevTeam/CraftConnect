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
- sekret jest przechowywany przez Android Keystore / szyfrowany credential store,
- `ServerProfile` przechowuje wyłącznie `credentialId`, host, port i flagę enabled,
- nie wolno wysyłać hasła RCON przez protokół Minecraft ani AuthGatewayX,
- UI musi jasno odróżniać dostęp gracza od dostępu RCON,
- awaria RCON nie może wpływać na aktywną sesję Minecraft.

### Aktualny szkielet RCON

W kodzie istnieją:

- `RconConfiguration` i `RconCredentialStore`,
- `KeystoreRconCredentialStore` — AES/GCM z kluczem w Android Keystore,
- format listy serwerów v3 przechowujący wyłącznie referencję do sekretu,
- `RconCommandService`,
- `RconPacketCodec`,
- `SourceRconClient` z limitami rozmiaru, timeoutami i obsługą dzielonych odpowiedzi,
- testy framingu i migracji profilu.

Brakuje jeszcze UI konfiguracji RCON, zarządzania cyklem życia credentialu z ekranu edycji serwera oraz testu z rzeczywistym serwerem RCON.

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

Permission nodes:

```text
authgatewayx.craftconnect.pair
authgatewayx.craftconnect.status
authgatewayx.craftconnect.stats
authgatewayx.craftconnect.console.view
authgatewayx.craftconnect.console.execute
authgatewayx.craftconnect.server-info
authgatewayx.craftconnect.branding
authgatewayx.craftconnect.diagnostics
```

Nie należy umieszczać trwałego snapshotu permissions w długowiecznym tokenie urządzenia. Uprawnienia mogą zostać zmienione w dowolnym momencie.

## Pairing

Minimalny model parowania:

```text
CraftConnect
    │
    │ zwykła, uwierzytelniona sesja Minecraft
    ▼
AuthGatewayX
    │
    ├── identyfikacja UUID sesji
    ├── jednorazowy challenge
    ├── proof-of-possession klucza urządzenia
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

### Klucz urządzenia i podpis

CraftConnect używa osobnej pary EC P-256 w Android Keystore. Klucz prywatny jest nieeksportowalny. `deviceId` jest stabilnym fingerprintem klucza publicznego.

Challenge jest podpisywany `SHA256withECDSA`. Kanoniczny payload v1 jest rozdzielony domeną:

```text
AGX-CRAFTCONNECT-PAIR-V1
```

i obejmuje co najmniej:

```text
serverId
challengeId
playerUuid
deviceId
nonce
expiresAt
```

Dzięki temu podpisu nie wolno przenosić pomiędzy serwerami, graczami ani challenge'ami. Poprawny podpis jest wyłącznie dowodem posiadania klucza urządzenia — nie oznacza zgody gracza i nie tworzy samodzielnie pairingu.

## Capability negotiation

UI nie powinno zgadywać dostępnych funkcji. Warstwa domenowa otrzymuje jawny zestaw funkcji oraz provider, który potrafi ją dostarczyć.

Aktualny provider-neutral model funkcji:

```text
CHAT
PLAYER_COMMANDS
PLAYER_LIST
SERVER_STATUS
MOTD
PAIRING
SERVER_STATS
SERVER_INFO
CONSOLE_VIEW
CONSOLE_EXECUTE
BRANDING
DIAGNOSTICS
```

Provider jest osobną wartością:

```text
MINECRAFT
RCON
AUTH_GATEWAY_X
```

Zasada preferencji:

```text
AUTH_GATEWAY_X > RCON > MINECRAFT
```

Przykład: zarówno RCON, jak i AGX mogą dostarczyć `CONSOLE_EXECUTE`, ale po przyznaniu capability przez AGX aplikacja wybiera AGX. RCON nie dostarcza `CONSOLE_VIEW`, ponieważ command-response nie jest live console.

Brak permission/capability powinien domyślnie usuwać niedostępną funkcję z UI zamiast pokazywać ekran, który dopiero po wejściu zwraca `brak uprawnień`.

## Protokół AuthGatewayX ↔ CraftConnect v1

Protokół jest wersjonowany niezależnie od wersji aplikacji i pluginu.

```text
channel = authgatewayx:craftconnect
protocolVersion = 1
maxFrameBytes = 65536
magic = AGXC (0x41475843)
```

Frame v1:

```text
magic:int32
version:uint8
type:uint8
requestId:UUID/128-bit
payload zależny od typu
```

Obsługiwane wiadomości pierwszego etapu:

```text
ClientHello
ServerHello
Capabilities
PairingBegin
PairingChallenge
PairingConfirm
PairingResult
Error
```

Pola tekstowe i binarne mają własne limity. Capability korzystają ze stabilnych `wireId`, a nie nazw enumów. Nieznane capability są ignorowane, aby umożliwić kompatybilne rozszerzanie protokołu.

W obu repozytoriach znajduje się wspólny wektor kompatybilności dla `ClientHello`, który ma wykrywać przypadkową zmianę framingu, endianowości lub typu wiadomości.

Aktualny klientowy `AuthGatewayXChannelClient` obsługuje discovery, capabilities oraz flow challenge/podpis/oczekiwanie na zgodę gracza. Hook transportowy do właściwych pakietów custom payload adaptera Minecraft pozostaje do podłączenia.

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

Checkbox `[x]` oznacza element wdrożony i zweryfikowany. Kod istniejący, ale oczekujący na pełną walidację CI/integracyjną pozostaje niezaznaczony.

### Etap 0 — szkielet

- [x] dokumentacja modelu trójwarstwowego,
- [x] domenowy model capability/provider w CraftConnect,
- [ ] bezpieczny model konfiguracji RCON — zaimplementowany, walidacja końcowa w toku,
- [ ] agregator capabilities — zaimplementowany, walidacja końcowa w toku,
- [ ] transport RCON — zaimplementowany, wymaga testu live,
- [ ] transport AuthGatewayX — codec/state machine istnieje, brak hooka packetowego.

### Etap 1 — RCON

- [ ] credential store oparty o Android Keystore — zaimplementowany, wymaga walidacji urządzeniowej,
- [ ] klient Source RCON — zaimplementowany, wymaga testu live,
- [ ] test połączenia z rzeczywistym RCON,
- [ ] Console Lite UI,
- [x] architektoniczna izolacja awarii RCON od sesji Minecraft.

### Etap 2 — AuthGatewayX pairing

- [ ] handshake v1 — codec/state machine zaimplementowane, brak podpięcia do packet adaptera,
- [ ] wykrywanie AGX — logika klienta istnieje, brak hooka transportowego,
- [ ] pairing challenge — implementacja klienta istnieje,
- [ ] device keypair — Android Keystore implementacja istnieje, wymaga walidacji urządzeniowej,
- [ ] revoke/list devices,
- [ ] capabilities z permissions — kontrakt i agregacja istnieją, wymagają pełnego end-to-end.

### Etap 3 — Enhanced Mode

- [ ] live console,
- [ ] metrics/status,
- [ ] branding,
- [ ] diagnostics,
- [ ] command execution z osobnym permission.
