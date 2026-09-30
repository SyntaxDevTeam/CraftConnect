# CraftConnect — opis, założenia i plan działania

## 1. Czym jest CraftConnect

**CraftConnect** to lekka aplikacja na Androida pełniąca rolę **headless klienta protokołu Minecraft**.

Aplikacja ma łączyć się z serwerami Minecraft w sposób zbliżony do normalnego klienta gry, ale **bez renderowania świata, grafiki 3D, dźwięku i typowej warstwy rozgrywki**.

Głównym celem CraftConnect jest umożliwienie użytkownikowi pozostawania połączonym z serwerem oraz korzystania z funkcji komunikacyjnych i administracyjnych Minecrafta bez potrzeby uruchamiania pełnego klienta gry.

CraftConnect ma być przede wszystkim narzędziem do:

- łączenia się z serwerami Minecraft,
- korzystania z czatu,
- wykonywania komend,
- obserwowania listy graczy,
- odbierania komunikatów serwera,
- utrzymywania sesji,
- otrzymywania powiadomień na Androidzie,
- wykonywania prostych akcji związanych z sesją,
- obsługi wielu zapisanych serwerów i profili połączeń.

CraftConnect **nie jest zamiennikiem Minecrafta jako gry**.

---

## 2. Główna wizja produktu

CraftConnect powinien zachowywać się tak, jakby użytkownik był połączony z serwerem normalnym klientem Minecraft, ale wykonywać tylko te elementy protokołu, które są potrzebne aplikacji.

Najważniejsza zasada:

> **Nie symulujemy gry, jeśli nie jest to potrzebne do utrzymania połączenia lub realizacji konkretnej funkcji aplikacji.**

Aplikacja powinna być:

- lekka,
- szybka,
- oszczędna energetycznie,
- stabilna przy długotrwałym połączeniu,
- odporna na rozłączenia,
- bezpieczna dla danych logowania,
- łatwa w obsłudze na telefonie,
- modularna od strony kodu,
- przygotowana na obsługę wielu wersji protokołu Minecraft.

---

# 3. Główne założenia aplikacji

## 3.1. Brak renderowania świata

CraftConnect nie posiada i nie powinien posiadać:

- renderera świata,
- renderera chunków,
- modeli gracza i mobów,
- tekstur świata,
- systemu shaderów,
- renderowania bloków,
- silnika audio,
- sterowania typowego dla gry.

Pakiety świata powinny być ignorowane, minimalnie przetwarzane albo obsługiwane wyłącznie wtedy, gdy są wymagane do zachowania prawidłowej sesji.

---

## 3.2. Prawdziwe połączenie z serwerem

CraftConnect nie ma działać jako panel wykorzystujący dodatkowy plugin po stronie serwera.

Podstawowym trybem działania jest:

```
Android
   ↓
CraftConnect
   ↓
Minecraft Protocol
   ↓
Server / Proxy
```

Dla serwera CraftConnect powinien być widoczny jako normalnie połączony klient Minecraft.

Integracje serwerowe mogą powstać później jako opcjonalne rozszerzenia, ale podstawowe funkcje aplikacji nie powinny od nich zależeć.

---

## 3.3. Architektura headless

Warstwa protokołu Minecraft musi być całkowicie oddzielona od interfejsu Androida.

Docelowy podział odpowiedzialności:

```
UI / Compose
      ↓
Presentation / ViewModel
      ↓
Domain
      ↓
Repositories
      ↓
Minecraft Session API
      ↓
Protocol implementation
      ↓
TCP / Minecraft server
```

Komponenty Compose nie mogą operować bezpośrednio na pakietach Minecraft.

---

## 3.4. Lekkość

CraftConnect powinien wykonywać tylko niezbędną pracę.

Priorytety:

1. minimalne użycie CPU,
2. małe zużycie RAM,
3. ograniczenie transferu danych tam, gdzie jest to możliwe,
4. brak przetwarzania świata bez potrzeby,
5. brak ciągłego wykonywania ciężkich operacji w tle,
6. rozsądne zarządzanie WakeLockami i usługami Androida.

---

## 3.5. Długotrwałe sesje

Jednym z głównych zastosowań CraftConnect może być pozostawienie konta połączonego z serwerem przez dłuższy czas.

Aplikacja musi więc dobrze obsługiwać:

- keep-alive,
- utratę Wi-Fi,
- przejście Wi-Fi → LTE/5G,
- chwilową utratę internetu,
- timeout,
- restart serwera,
- kick,
- zmianę stanu aplikacji,
- działanie w tle,
- automatyczne ponowne połączenie.

---

# 4. Zakres funkcjonalny

## 4.1. Serwery

Użytkownik powinien móc:

- dodać serwer,
- zapisać adres i port,
- nadać własną nazwę,
- oznaczyć serwer jako ulubiony,
- edytować serwer,
- usunąć serwer,
- sprawdzić status,
- zobaczyć ping,
- zobaczyć liczbę graczy,
- zobaczyć MOTD,
- szybko się połączyć.

---

## 4.2. Sesja

Aktywna sesja powinna przechowywać między innymi:

- stan połączenia,
- nazwę serwera,
- adres,
- wersję protokołu,
- ping,
- nazwę konta,
- UUID,
- czas trwania połączenia,
- liczbę reconnectów,
- ostatni powód rozłączenia.

Podstawowe stany:

```
DISCONNECTED
CONNECTING
AUTHENTICATING
JOINING
CONNECTED
RECONNECTING
FAILED
```

---

## 4.3. Czat

Czat jest jedną z najważniejszych funkcji aplikacji.

Powinien wspierać:

- odbieranie wiadomości,
- wysyłanie wiadomości,
- wykonywanie komend,
- przewijanie historii,
- oznaczanie nicków,
- kopiowanie wiadomości,
- podstawowe formatowanie komponentów tekstowych,
- linki,
- timestamp lokalny,
- filtrowanie wiadomości,
- powiadomienia o wzmiankach.

Docelowo warto rozważyć:

- wyszukiwanie historii,
- własne filtry,
- wyciszanie wybranych typów wiadomości,
- osobne zakładki chat / commands / system.

---

## 4.4. Lista graczy

CraftConnect powinien prezentować aktualną listę graczy, jeśli serwer udostępnia te dane przez protokół.

Możliwe informacje:

- nick,
- UUID,
- ping,
- display name,
- informacje wysyłane przez Player Info,
- stan online.

Lista graczy nie powinna wymagać pluginu serwerowego.

---

## 4.5. Komendy

Aplikacja powinna ułatwiać wykonywanie komend.

Planowane elementy:

- historia komend,
- ulubione komendy,
- szybkie akcje,
- własne skróty,
- ponowne wykonanie ostatniej komendy.

W przyszłości możliwe jest lokalne tworzenie makr, np.:

```
/home
/msg Player test
/server survival
```

Makra muszą być funkcją użytkownika i nie mogą automatycznie wykonywać niebezpiecznych operacji bez jego wiedzy.

---

# 5. Konta i autoryzacja

## 5.1. Konto Microsoft / Minecraft

Dla serwerów online-mode CraftConnect będzie potrzebował prawidłowej sesji Minecraft.

Docelowo należy obsłużyć oficjalny przepływ logowania Microsoft/Minecraft.

Dane uwierzytelniające:

- nie mogą być przechowywane jako plain text,
- powinny korzystać z mechanizmów Android Keystore,
- nie powinny trafiać do logów,
- nie powinny być eksportowane razem z konfiguracją aplikacji.

---

## 5.2. Serwery offline-mode

Jeżeli aplikacja będzie obsługiwać serwery offline-mode, użytkownik powinien móc utworzyć lokalny profil z wybraną nazwą gracza.

Tryb offline i konto Microsoft muszą być wyraźnie rozdzielone w interfejsie.

---

# 6. Obsługa wersji Minecraft

CraftConnect nie powinien zostać trwale związany z jednym numerem protokołu.

Warstwa protokołu powinna być zaprojektowana jako wersjonowana.

Przykładowo:

```
protocol/
├── api/
├── common/
├── codec/
├── versions/
│   ├── v1_xx/
│   └── v1_yy/
└── session/
```

Docelowo połączenie powinno:

1. sprawdzić status serwera,
2. ustalić wersję protokołu,
3. dobrać odpowiedni adapter,
4. rozpocząć właściwą sesję.

Priorytetem jest najpierw aktualna wersja Minecraft, a dopiero później rozszerzanie kompatybilności wstecznej.

---

# 7. Pakiety świata

Pakiety niezwiązane z funkcjami CraftConnect powinny być traktowane możliwie tanio.

Przykładowo CraftConnect nie potrzebuje normalnie:

- danych chunków do renderowania,
- pozycji wszystkich bloków,
- oświetlenia,
- modeli,
- particle effects,
- danych renderowania encji.

Nie oznacza to jednak automatycznie, że każdy taki pakiet można bezwarunkowo odrzucić.

Warstwa protokołu musi rozróżniać:

- pakiety całkowicie zbędne,
- pakiety wymagające ACK,
- pakiety wpływające na stan połączenia,
- pakiety potrzebne tylko do wybranych funkcji.

---

# 8. Android — działanie w tle

Długotrwałe połączenie z serwerem wymaga poprawnej integracji z mechanizmami Androida.

Należy założyć wykorzystanie:

- foreground service dla aktywnej sesji,
- persistent notification informującego o aktywnym połączeniu,
- poprawnej obsługi lifecycle,
- kontrolowanego reconnectu,
- obsługi zmian sieci przez ConnectivityManager.

Aplikacja nie może próbować obchodzić ograniczeń Androida w agresywny sposób.

---

# 9. Powiadomienia

Powiadomienia powinny być jednym z najważniejszych elementów mobilnego charakteru aplikacji.

Przykładowe zdarzenia:

- wzmianka na czacie,
- prywatna wiadomość,
- rozłączenie,
- kick,
- ponowne połączenie,
- wejście wybranego gracza,
- wiadomość zawierająca określone słowo.

Każdy typ powiadomienia powinien być konfigurowalny.

---

# 10. Dane lokalne

Docelowo aplikacja powinna przechowywać lokalnie:

- listę serwerów,
- ulubione serwery,
- ustawienia,
- profile,
- historię połączeń,
- opcjonalną historię czatu,
- szybkie komendy,
- reguły powiadomień.

Preferowany kierunek:

- Room / SQLite dla danych strukturalnych,
- DataStore dla ustawień,
- Android Keystore dla sekretów.

---

# 11. Bezpieczeństwo

CraftConnect operuje na danych pozwalających zalogować się na konto Minecraft, dlatego bezpieczeństwo jest elementem architektury, a nie dodatkiem.

Podstawowe zasady:

- brak tokenów w logach,
- brak haseł/tokenów w zwykłym SharedPreferences,
- brak sekretów w crash reportach,
- przechowywanie sekretów z użyciem Android Keystore,
- możliwość wylogowania i usunięcia sesji,
- czytelne informacje o aktywnym koncie,
- brak automatycznego wysyłania danych do zewnętrznych usług.

Telemetry, jeśli kiedyś zostanie dodane, musi być jawne i ograniczone do danych technicznych.

---

# 12. Interfejs

## 12.1. Kierunek wizualny

Aktualny kierunek:

- bardzo ciemne tło,
- ciemne odcienie czerwieni,
- crimson jako kolor akcentu,
- kontrolowane neonowe poświaty,
- wysoki kontrast,
- futurystyczny charakter,
- czytelność ważniejsza od efektów.

UI nie powinno wyglądać jak gra.

Powinno bardziej przypominać nowoczesne narzędzie komunikacyjne / terminal połączenia.

---

## 12.2. Główne ekrany

Pierwszy zestaw ekranów:

### Servers

Lista zapisanych serwerów i szybkie łączenie.

### Chat

Główne centrum aktywnej sesji.

### Players

Lista graczy i informacje o obecnych użytkownikach.

### Settings

Konfiguracja aplikacji, sesji, reconnectu, powiadomień i kont.

---

# 13. Architektura projektu

Docelowy kierunek:

```
app/
├── ui/
│   ├── screens/
│   ├── components/
│   └── theme/
│
├── presentation/
│   ├── viewmodel/
│   └── state/
│
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
│
├── data/
│   ├── local/
│   ├── repository/
│   └── preferences/
│
└── protocol/
    ├── api/
    ├── codec/
    ├── session/
    ├── auth/
    └── versions/
```

W miarę wzrostu projektu warstwa protokołu może zostać wydzielona do osobnego modułu Gradle.

---

# 14. Najważniejsze ograniczenia zakresu

## CraftConnect 1.x NIE powinien próbować implementować

- renderowania świata,
- normalnego movement gameplay,
- walki,
- kopania,
- stawiania bloków,
- pełnej symulacji fizyki,
- shaderów,
- resource pack renderingu,
- audio,
- pełnego GUI Minecrafta.

Wyjątek może powstać tylko wtedy, gdy określony fragment stanu gry jest konieczny do konkretnej funkcji CraftConnect.

---

# 15. Plan działania

## Etap 0 — szkielet projektu

Status: **wykonany**

Zakres:

- projekt Android,
- Kotlin,
- Jetpack Compose,
- Gradle Wrapper,
- GitHub Actions,
- AGENTS.md,
- UI prototype,
- ekrany Servers / Chat / Players / Settings,
- wstępny kontrakt MinecraftConnection,
- dokumentacja architektury.

---

## Etap 1 — model sesji

Cel: stworzenie stabilnego fundamentu, zanim pojawi się prawdziwe połączenie.

Do wykonania:

- SessionState,
- ConnectionState,
- SessionManager,
- model błędów,
- event stream,
- lifecycle sesji,
- logowanie diagnostyczne bez danych wrażliwych.

Efekt:

UI może reagować na prawdziwy stan sesji bez znajomości implementacji protokołu.

---

## Etap 2 — Server Status

Pierwsza prawdziwa komunikacja z Minecraft.

Do wykonania:

- TCP connection,
- handshake,
- status request,
- status response,
- ping/pong,
- parser MOTD,
- wersja serwera,
- liczba graczy.

Po tym etapie ekran Servers powinien korzystać z prawdziwych danych.

---

## Etap 3 — logowanie i sesja Minecraft

Do wykonania:

- login state,
- encryption,
- compression,
- disconnect handling,
- konfiguracja protokołu,
- wejście do play state,
- keep-alive.

Cel:

CraftConnect potrafi pozostać faktycznie zalogowany na serwerze.

---

## Etap 4 — konta

Do wykonania:

- konto Microsoft/Minecraft,
- bezpieczne przechowywanie sesji,
- wybór profilu,
- odświeżanie sesji,
- logout,
- opcjonalny profil offline-mode.

---

## Etap 5 — chat i komendy

Do wykonania:

- odbieranie wiadomości,
- komponenty tekstowe Minecraft,
- wysyłanie czatu,
- wysyłanie komend,
- systemowe komunikaty,
- historia lokalna,
- obsługa signed chat zgodna z obsługiwaną wersją protokołu.

Po tym etapie CraftConnect zaczyna realizować swój główny cel użytkowy.

---

## Etap 6 — lista graczy

Do wykonania:

- Player Info,
- join/leave,
- display name,
- UUID,
- ping,
- aktualizacja listy w czasie rzeczywistym.

---

## Etap 7 — Android background session

Do wykonania:

- foreground service,
- persistent notification,
- reconnect,
- zmiana sieci,
- obsługa Doze,
- kontrola zużycia baterii.

---

## Etap 8 — zapis serwerów i ustawień

Do wykonania:

- Room,
- DataStore,
- edycja serwerów,
- ulubione,
- profile,
- ustawienia połączenia,
- ustawienia reconnectu.

---

## Etap 9 — powiadomienia

Do wykonania:

- mention alerts,
- private message alerts,
- disconnect alerts,
- reconnect alerts,
- reguły użytkownika,
- kanały Android Notification.

---

## Etap 10 — stabilizacja

Zakres:

- odporność na malformed packets,
- timeouty,
- reconnect loop protection,
- diagnostyka,
- raport błędów lokalnych,
- pomiary CPU/RAM,
- testy wielogodzinnych sesji,
- testy zmiany Wi-Fi/LTE,
- testy różnych proxy i serwerów.

---

## Etap 11 — kompatybilność protokołów

Po ustabilizowaniu podstawowej wersji:

- wydzielenie adapterów wersji,
- test matrix,
- obsługa kolejnych wersji Minecraft,
- graceful unsupported-version message.

---

# 16. CraftConnect 1.0 — minimalny zakres

Wersję 1.0 można uznać za gotową, jeśli aplikacja:

- potrafi zapisać serwer,
- sprawdza jego status,
- potrafi się z nim połączyć,
- obsługuje konto Microsoft,
- obsługuje opcjonalny profil offline-mode,
- utrzymuje sesję,
- odbiera chat,
- wysyła chat,
- wykonuje komendy,
- pokazuje listę graczy,
- działa poprawnie w tle,
- automatycznie odzyskuje połączenie,
- wysyła podstawowe powiadomienia,
- nie wymaga pluginu po stronie serwera,
- nie renderuje świata,
- nie przechowuje sekretów w sposób jawny.

---

# 17. Kryteria jakości

Każda większa funkcja powinna spełniać następujące warunki:

- brak blokowania głównego wątku Androida,
- brak bezpośredniego dostępu UI do warstwy packetów,
- czytelny stan błędu,
- możliwość anulowania połączenia,
- brak danych wrażliwych w logach,
- stabilność po utracie internetu,
- przewidywalne zachowanie po zmianie lifecycle aplikacji.

---

# 18. Priorytety projektu

Kolejność ważności:

1. **stabilne połączenie,**
2. **poprawność protokołu,**
3. **bezpieczeństwo konta,**
4. **niskie zużycie zasobów,**
5. **chat i komendy,**
6. **stabilne działanie w tle,**
7. **czytelny interfejs,**
8. **dodatkowe funkcje.**

Efekty wizualne i funkcje poboczne nie powinny opóźniać stabilności warstwy połączenia.

---

# 19. Zasada rozwoju projektu

Przy każdej propozycji nowej funkcji należy zadać trzy pytania:

1. Czy funkcja pasuje do headless charakteru CraftConnect?
2. Czy wymaga symulowania fragmentu gry, który nie jest potrzebny aplikacji?
3. Czy koszt CPU/RAM/baterii jest uzasadniony realną wartością dla użytkownika?

Jeżeli odpowiedź na drugie pytanie brzmi „tak”, funkcja powinna być implementowana tylko wtedy, gdy nie istnieje prostsze rozwiązanie.

---

# 20. Dalsze możliwe kierunki po 1.0

Po ustabilizowaniu rdzenia można rozważyć:

- wiele równoległych sesji,
- rozbudowane filtry czatu,
- makra,
- zaawansowane powiadomienia,
- historia sesji,
- statystyki połączenia,
- eksport/import konfiguracji,
- opcjonalne integracje z pluginami serwerowymi,
- obsługę prostych ekranów/formularzy Minecraft,
- plugin messages,
- dodatkowe narzędzia administracyjne.

Te elementy nie są częścią podstawowego celu CraftConnect 1.0.

---

## Podsumowanie

CraftConnect ma być **mobilnym, lekkim interfejsem do połączenia z serwerem Minecraft**, a nie mobilną implementacją samej gry.

Najważniejszy produktowy podział brzmi:

> **Minecraft odpowiada za grę. CraftConnect odpowiada za połączenie.**
