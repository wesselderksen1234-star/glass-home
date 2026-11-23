# Glass Home koppelen (eenmalig)

Alles wat je invult komt in `config.js` in deze map. Daarna laat je Claude het installatiebestand opnieuw maken.

## 1. Films & series (aanraders, cijfers, samenvattingen)
- **TMDB**: maak een gratis account op themoviedb.org → Instellingen → API → kopieer de *API Key* → `tmdb`.
- **OMDb** (IMDb, Rotten Tomatoes, Metacritic): omdbapi.com/apikey.aspx → gratis → sleutel uit je mail → `omdb`.
- **Claude** (samenvattingen zonder spoilers): console.anthropic.com → API Keys → `claude`. Kost een paar cent per maand.

## 2. Op basis van wat jij kijkt
- **Letterboxd**: zet je gebruikersnaam bij `letterboxd`. Je dagboek en cijfers bepalen je filmsmaak (profiel moet openbaar zijn).
- **Netflix**: ga op je pc naar netflix.com/viewingactivity → onderaan **Alles downloaden** → hernoem het bestand naar `netflix.csv` en zet het in de map `glasshome`.
- Prime Video, Videoland en NPO bieden geen kijkgeschiedenis aan; die aanraders gebruiken je Letterboxd- en Netflix-smaak.

## 2b. Trakt (echte "Ga verder" + kijkgeschiedenis, ook van Stremio)
1. trakt.tv/oauth/applications → **New application** → naam "Glass Home", Redirect URI `urn:ietf:wg:oauth:2.0:oob` → opslaan.
2. Kopieer Client ID en Client Secret → `traktClientId` en `traktClientSecret`.
3. Zet in Stremio de **Trakt-add-on** aan (Stremio → Add-ons → Trakt) zodat wat je kijkt wordt bijgehouden.
4. Op de tv: **Instellingen → Trakt-account → Koppelen** en vul de code in op je telefoon.

## 3. Spotify (op basis van wat je luistert)
1. developer.spotify.com/dashboard → **Create app**.
2. Redirect URI: `http://127.0.0.1:8888/callback` · vink **Web API** aan · opslaan.
3. Kopieer de **Client ID**.
4. Dubbelklik `spotify-koppelen.bat`, plak de Client ID en log in. Klaar.

## 4. YouTube (op basis van je abonnementen en likes)
1. console.cloud.google.com → nieuw project → **APIs & Services → Library** → zet **YouTube Data API v3** aan.
2. **Credentials → Create credentials → API key** → `youtube`.
3. **OAuth consent screen** → External → vul je naam/e-mail in → zet de status op **In production** (anders verloopt de koppeling na 7 dagen).
4. **Credentials → Create credentials → OAuth client ID** → type **TVs and Limited Input devices** → kopieer Client ID en Secret → `ytClientId` en `ytClientSecret`.
5. Na installatie op de tv: **Instellingen → YouTube-account → Koppelen** en vul de code in op je telefoon.
6. Optioneel: een eigen afspeellijst tonen → zet de ID (begint met `PL…`, staat in de link) bij `ytPlaylist`. *Later bekijken* kan niet: YouTube geeft die lijst niet meer vrij aan apps.

## 5. TiviMate-tips (alleen Google TV)
- Zet bij `epgUrl` de XMLTV-gidslink van je IPTV-aanbieder (niet de `.gz`-versie). Met `epgChannels` beperk je de zenders, zodat de tv het snel kan inlezen.

## 6. Weer in sfeermodus
- Staat standaard op Utrecht. Andere plek: pas `place`, `lat` en `lon` aan.

## LG C2: direct opstarten op je Google TV Streamer
1. **Instellingen → Algemeen → Systeem → Aanvullende instellingen → Instellingen voor startscherm** → zet **Startscherm automatisch openen** uit. De tv start dan op de laatst gebruikte ingang.
2. **Instellingen → Algemeen → Apparaten → HDMI-instellingen** → zet **SIMPLINK (HDMI-CEC)** en **Automatisch inschakelen (Auto Power Sync)** aan.
3. Kijk één keer via de HDMI-ingang van de Streamer en zet de tv uit. Daarna start de tv steeds op de Streamer, en de Streamer-afstandsbediening zet de tv aan.
4. Glass Home op de LG automatisch laten starten kan alleen met een geroote tv. Zonder root open je hem via de appbalk (zet hem vooraan).

## Slim huis (zijbalk → Slim huis)
- **Google TV Streamer**: de knop *Google Home-bediening* opent het eigen Home-paneel van Google TV. Google laat andere apps je Google Home-apparaten niet rechtstreeks bedienen.
- **Lampen en scènes direct in Glass Home** (op de Streamer én de LG): gebruik je Home Assistant, vul dan `haUrl` en `haToken` in. Je lampen, schakelaars, scènes en thermostaat verschijnen dan in het menu en schakel je met OK.
