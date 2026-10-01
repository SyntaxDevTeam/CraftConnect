# AuthMe, nLogin i AuthGatewayX

Automatyczne komendy ukrycia wymagają `CraftConnectBridge` na serwerze. Aplikacja
nie rozpoznaje sukcesu na podstawie czatu, języka, wysłania `/login`, zamknięcia
dialogu ani upływu czasu. Bridge odczytuje rzeczywisty stan uwierzytelnienia.

| Plugin | Publiczna integracja |
| --- | --- |
| AuthMe Reloaded | `AuthMeApi.getInstance().isAuthenticated(Player)` — API v3 |
| nLogin | `nLoginAPI.getApi().isAuthenticated(String)` |
| AuthGatewayX Paper | `AuthenticationStatusProvider.isAuthenticated(UUID)` przez Bukkit ServicesManager |

AuthGatewayX wymaga nowego builda z zarejestrowanym providerem. Wcześniejszy build
nie potwierdzi logowania. API nLogin musi być dostępne na backendzie; instalacja
wyłącznie na Velocity/Bungee bez backendowego API nie jest obsługiwana.

## Instalacja

1. Zbuduj `./gradlew :server-bridge:jar` lub pobierz artefakt `CraftConnectBridge`
   z GitHub Actions. JAR powstaje w `server-bridge/build/libs/`.
2. Umieść JAR w `plugins/` serwera z AuthMe, nLogin lub nowym AuthGatewayX.
3. Zrestartuj serwer. Konsola bridge'a wypisze wykryte pluginy logowania.
4. Połącz się aplikacją i zaloguj przez dotychczasową komendę lub dialog.

Plugin jest przeznaczony dla Spigot/Paper/Purpur 1.21+; na Paper/Folia korzysta z
EntityScheduler, a na Spigot z BukkitScheduler. Native protocol 47 / Minecraft 1.8
nie obsłuży tego JAR-a; stary adapter może współpracować z nowoczesnym backendem
przez translator protokołu, co wymaga testu ViaVersion/proxy.

`craftconnect.bridge.use` ma domyślnie `true`. Ta permisja pozwala jedynie otrzymać
status własnego logowania. Nie przyznaje permisji spectator/vanish i nie ukrywa
postaci bezpośrednio. Aplikacja wysyła komendy jako gracz, nigdy jako konsola.

## Przepływ

Klient po potwierdzeniu pozycji rejestruje `craftconnect:auth` i wysyła subskrypcję
z losowym nonce przypisanym do bieżącego połączenia. Co 2 sekundy ponawia tylko
subskrypcję, aby poradzić sobie z odrzuceniem plugin message przed logowaniem.
Bridge przechowuje najwyżej jedną subskrypcję na połączonego gracza, sprawdza stan
raz na sekundę na odpowiednim schedulerze i wysyła potwierdzenie dopiero po auth.
Nie wykonuje HTTP, JDBC ani operacji na haśle.

Jeżeli wykryto kilka obsługiwanych pluginów, wszystkie muszą potwierdzić auth.
Brak providerów, niedostępne API, shutdown pluginu lub błąd odczytu nie oznaczają
logowania. Wiadomość zawiera wersję protokołu, nonce i nazwy providerów; nie zawiera
IP, haseł ani danych konta. Nonce chroni przed użyciem odpowiedzi starego połączenia,
nie jest kryptograficznym dowodem uczciwości serwera — klient ufa połączonemu serwerowi.

Po potwierdzeniu aplikacja wysyła `/gamemode spectator`; jeśli serwer nie potwierdzi
spectator przez 3 sekundy, wysyła jedno `/vanish`. Powtórzone potwierdzenia i teleporty
nie ponawiają komend. Disconnect czyści nonce i anuluje oczekujące zadania.
Nie przywracamy trybu gry przy wyjściu ani nie przełączamy ponownie vanish.

## Walidacja i ograniczenia

CI buduje APK i JAR bridge'a, uruchamia lint oraz testy wspólnego protokołu, bramki
auth, starych odpowiedzi, kolejności komend i obu adapterów sieciowych.
Pełne testy na uruchomionym AuthMe, nLogin, AuthGatewayX oraz Paper/Folia/proxy
pozostają potrzebne przed wdrożeniem produkcyjnym. Most nie zastępuje logowania,
vanisha, ochrony ekwipunku ani uprawnień. `/vanish` może być przełącznikiem i już
niewidzialnego gracza ujawnić; bridge nie potwierdza stanu konkretnego pluginu vanish.

Źródła kontraktów:
- [AuthMe API v3](https://github.com/AuthMe/AuthMeReloaded/blob/master/authme-core/src/main/java/fr/xephi/authme/api/v3/AuthMeApi.java)
- [nLogin API](https://jd.nickuc.com/nlogin/com/nickuc/login/api/nLoginAPI.html)
- [Paper plugin messaging](https://docs.papermc.io/paper/dev/plugin-messaging/)
