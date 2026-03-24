# Hodiny – Sledování pracovní docházky

Nativní Android aplikace pro automatické sledování příchodů a odchodů z pracoviště. Detekce probíhá přes GPS geofencing a WiFi SSID bez nutnosti jakékoli manuální interakce.

## Funkce

- **Automatická detekce** příchodu a odchodu přes GPS geofencing nebo WiFi SSID
- **Běh na pozadí** – Foreground Service s neviditelnou notifikací (žádná ikona v liště)
- **Přehled docházky** – dnešní stav s živým timerem, přehled po měsících s přepínáním gesty
- **Ruční záznamy** – přidání nebo úprava záznamu přímo v historii
- **Export** – PDF report, CSV nebo přímo do Google Sheets / Google Drive
- **Denní notifikace** – připomenutí odchodu v nastavenou hodinu, pokud nebyl odchod zaznamenán
- **Restart po restartu telefonu** – monitorování se automaticky obnoví

## Screenshoty

> _Doplň screenshoty po prvním nasazení_

## Architektura

```
app/src/main/java/cz/hodiny/
├── data/
│   ├── db/             # Room databáze (AttendanceRecord, ZoneEvent, DAO)
│   ├── preferences/    # DataStore – nastavení aplikace
│   └── repository/     # AttendanceRepository
├── export/             # PDF a CSV export
├── google/             # Google Sign-In, Sheets export, Drive záloha
├── service/            # Foreground Service, Geofencing, WiFi, Boot receiver
├── ui/
│   ├── components/     # Sdílené Compose komponenty (dialogy, TimePicker)
│   ├── navigation/     # Bottom navigation, NavHost
│   ├── screens/        # Home, History, Settings, Export, Onboarding
│   └── theme/
└── worker/             # WorkManager – denní notifikace
```

## Technologie

| Vrstva | Technologie |
|---|---|
| Jazyk | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| Databáze | Room 2.6.1 (SQLite) |
| Nastavení | DataStore Preferences |
| Geofencing | Google Play Services Location 21.3.0 |
| WiFi detekce | WifiManager (Foreground Service) |
| Pozadí | Foreground Service + WorkManager |
| Google integrace | Google Sign-In, Sheets API v4, Drive API v3 |
| Build | Gradle 8.13, AGP 8.5.2, KSP |

## Požadavky

- Android 8.0 (API 26) nebo novější
- Google Play Services (pro GPS geofencing a Google přihlášení)
- Oprávnění: Přesná poloha, Poloha na pozadí, WiFi stav, Notifikace

## Sestavení

```bash
git clone https://github.com/qwerin/hodiny.git
cd hodiny
./gradlew assembleDebug
```

APK najdeš v `app/build/outputs/apk/debug/app-debug.apk`.

### Release build (podepsaný)

1. Vytvoř keystore:
   ```bash
   keytool -genkeypair -v -keystore hodiny.keystore -alias hodiny -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Přidej signing config do `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../hodiny.keystore")
           storePassword = "HESLO"
           keyAlias = "hodiny"
           keyPassword = "HESLO"
       }
   }
   ```

3. Sestav:
   ```bash
   ./gradlew assembleRelease
   ```

## Nastavení aplikace

Při prvním spuštění projdeš onboarding:

- **GPS** – zadáš souřadnice pracoviště a poloměr geofence (doporučeno 100–200 m)
- **WiFi** – zadáš SSID pracovní sítě (tlačítko „Aktuální" doplní automaticky)
- **Detekční režim** – GPS, WiFi nebo obojí
- **Hodinová sazba** – pro výpočet odměny v exportu
- **Čas notifikace** – kdy tě aplikace upozorní na nezaznamenaný odchod
- **Google účet** – volitelné přihlášení pro export do Sheets a zálohu na Drive

## Google integrace (volitelné)

Pro export do Google Sheets a Google Drive je potřeba nastavit Google Cloud projekt:

1. Vytvoř projekt na [console.cloud.google.com](https://console.cloud.google.com)
2. Povol **Google Sheets API** a **Google Drive API**
3. Vytvoř OAuth 2.0 credentials → typ **Android**
   - Package name: `cz.hodiny.dochazka`
   - SHA-1 fingerprint: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
4. V aplikaci se přihlas přes **Nastavení → Google účet**

## Logika docházky

- **Příchod** se zaznamená jen jednou za den (první vstup do zóny)
- **Odchod** se průběžně přepisuje (poslední odchod dne), dokud není potvrzen
- Po potvrzení odchodu (z notifikace nebo ručně) je záznam **uzamčen** a nelze ho přepsat automaticky

### Priorita zdrojů detekce (režim GPS + WiFi)

- **Příchod** – preferován GPS: pokud WiFi detekuje vstup a GPS dorazí do 2 minut, GPS přepíše zdroj záznamu
- **Odchod** – preferována WiFi/SSID: pokud GPS detekuje odchod a WiFi dorazí do 2 minut, WiFi přepíše zdroj záznamu

### Večerní notifikace

Připomenutí se zobrazí v nastavenou hodinu, pokud dnešní záznam existuje a **není uzamčen**.
Uzamčení nastane po ručním potvrzení odchodu (z notifikace nebo z přehledu). Pokud uživatel odchod potvrdí nebo uzamkne ručně, notifikace se ten den již nezobrazí.

## Licence

MIT
