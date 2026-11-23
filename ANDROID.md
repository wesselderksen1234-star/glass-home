# Glass Home voor Android TV / Google TV

## Bouwen
**Via GitHub (makkelijkst):** zet deze repo op GitHub (met de webapp in de map `web/`), push, open het tabblad
*Actions*, kies de laatste run "Build APK" en download het artifact `glasshome-apk`.

**Via Android Studio:** open deze map, kopieer de web-bestanden naar `app/src/main/assets/web/`,
kies *Build > Build APK(s)*. (Gradle 8.7, JDK 17.)

## Installeren
1. Zet ontwikkelaarsopties aan: Instellingen > Systeem > Info > tik 7x op "Android TV OS-build".
2. Zet USB-/netwerkfoutopsporing aan (op de Google TV Streamer: "Draadloze foutopsporing").
3. Verbinden en installeren:
   ```
   adb connect <ip>:5555
   adb install -r glasshome.apk
   ```
   Draadloze foutopsporing met koppelcode: `adb pair ip:poort code`, daarna `adb connect ip:poort`
   (gebruik de poort die bij "Verbinden" wordt getoond).

## Instellen als startscherm (veilige methode)
Ga naar Instellingen > Systeem > Toegankelijkheid en zet **"Glass Home startscherm"** aan.
Telkens als het standaard Google TV-startscherm opent, springt Glass Home ervoor. Het originele
startscherm blijft geinstalleerd, dus Cast, Bluetooth en het snelle instellingenpaneel blijven werken.
Uitzetten kan in dezelfde instelling (of via de schakelaar in de app).

## Optioneel: harde methode (niet aanbevolen)
```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```
Let op: dit schakelt de stock launcher uit en daarmee ook het Google TV-dashboard en het
snelle-instellingenpaneel. Ongedaan maken:
```
adb shell pm enable com.google.android.apps.tv.launcherx
```

## Sleutels veilig in GitHub
Zet `web/config.js` niet in je repo (staat in .gitignore). Ga in je repo naar **Settings → Secrets and variables → Actions → New repository secret**, naam `GH_CONFIG_JS`, en plak de volledige inhoud van je ingevulde `config.js`. De build zet hem dan automatisch in de app.

## Updaten
Elke GitHub-build krijgt een nieuwe testhandtekening. Lukt `adb install -r` niet ("signatures do not match"), verwijder dan eerst de oude versie: `adb uninstall com.custom.glasshome`.
