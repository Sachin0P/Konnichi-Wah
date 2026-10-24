# Konnichi-Wah! 🇯🇵🇮🇳

**A Japan travel companion for Indian travellers.** Built by team OsakaPatnam for Hakuhodo India's problem statement **HAK-PS-01: "Attracting Indian Travelers to Japan."**

Indian travellers don't skip Japan because of the price. They skip it because of uncertainty:

- **Will there be food I can eat?** (Vegetarian, Jain, Halal, no beef/pork, hidden fish dashi in broth)
- **Menus and signs are in Japanese.**
- **The train labyrinths look impossible** (Shinjuku and Tokyo Station have dozens of exits and multi-level platforms).
- **What if I break an unwritten cultural rule?** (Shoes, onsen tattoos, chopstick taboos, public etiquette)

**Konnichi-Wah!** solves every single one of these friction points before the trip and on the ground.

---

## What It Does

### 1. Questionnaire & Taste Profiling
- Tailored for Indian travellers: budget in Lakhs (₹1.1L–1.8L, ₹1.9L–2.8L, ₹2.9L+), trip duration, travel month, pacing, and group dynamics (solo, couple, friends, family with kids/elders).
- **Comprehensive dietary profiles:** Vegetarian, Jain (no root vegetables, onion, garlic), Eggetarian, Halal, or Non-Vegetarian.
- **Specific avoidances:** Beef, pork, seafood, egg, onion/garlic, alcohol/mirin, with spice level calibration (Mild, Medium, Indian Spicy).
- Free-text custom notes support special requirements (e.g. "elderly parents, avoid stairs" or "die-hard One Piece & anime fan").

### 2. AI Trip Suggestions with Live Web Grounding
- Proposes multi-city routes (e.g. Tokyo 4 days, Kyoto 2, Osaka 1) with matched suggestion cards for attractions, shopping, and scenic spots.
- **Web Grounding:** Real-time search integration fetches opening hours, seasonal events, and current travel conditions.
- **Zero Hallucination on Food:** Restaurants never come from generative AI guesses. They are served from a curated, hand-checked database filtered strictly by diet.

### 3. Smart Itinerary Engine & Day Scheduling
- Groups day trips (e.g. Nara from Kyoto, Kamakura from Tokyo) so they land on dedicated days without being fragmented.
- Places morning sights at shrines/temples, afternoon activities, and evening dining/nightlife slots.
- **100% Offline-First:** Saved locally in Room on the phone. Works without internet and syncs edits when connected.

### 4. Interactive Itinerary Editing via AI Chat
- Edit your created itinerary on the fly through natural conversation in the companion chat:
  - *"Add Sensō-ji to Day 2 morning"*
  - *"Remove Tokyo Tower"*
  - *"Swap Day 1 and Day 3"*
- The companion detects structured itinerary actions (`add`, `remove`, `move`) and updates your saved itinerary in real-time.

### 5. Culture & Etiquette Tips at Every Stop
- 78 hand-curated, situation-specific etiquette rules attached to each stop type:
  - Shrines & Temples: Bowing, shoe removal, purification font wash.
  - Dining: Chopstick handling, noodle slurping, tipping refusal.
  - Transit: Priority seating, silent carriages, escalator standing rules.
  - Onsen: Pre-bath washing, tattoo regulations, modesty towel etiquette.
- Bundled directly inside the APK for instant offline guidance.

### 6. Desi Match Food Search
- Ranks restaurants 1–5 stars based specifically on *your* Indian dietary requirements:
  - ★★★★★ "Vegan-only kitchen" for vegetarians.
  - ★★★★ "Indian or Nepali dining" with "Ask for Jain preparation: no onion, garlic, or root vegetables".
  - Strictly filters out pork/beef ramen establishments for those avoiding them, even if physically closest.

### 7. Decluttered On-Device Menu Reader
- Uses CameraX and Google ML Kit Japanese on-device OCR:
  - **Clean Overlay:** High-contrast, translucent overlays drawn neatly over each Japanese menu line.
  - **Diet Safety Alerts:** Flags hidden ingredients directly from kanji analysis: `⚠ Pork (豚)`, `⚠ Alcohol/Mirin (酒/みりん)`, `⚠ Dashi/Fish Stock (だし)`, `⚠ Beef (牛)`, `⚠ Seafood (魚介)`.
  - **AI Translation:** Translates full menu context in one pass with dish explanations (e.g. 親子丼 → *"Oyakodon: chicken and egg simmered with onion over rice"*). Offline ML Kit translation model supported as fallback.

### 8. Interactive Multi-Floor Station Maps
- Detailed floor layouts for Japan's busiest railway labyrinths: **Tokyo Station, Shinjuku, Shibuya, Kyoto, and Osaka**.
- Multi-floor navigation (2F, 1F, B1F, B4F, B5F) with:
  - Line badges (JR Yamanote, Chuo Rapid, Tokaido Shinkansen, Tokyo Metro Ginza/Marunouchi).
  - Platform numbers and train directions.
  - Key station exits (Marunouchi North, Yaesu Central, Hachiko, West, East).
  - Facility markers: Coin lockers, JR ticket offices (Midori-no-Madoguchi), accessible elevators, and IC card reload machines.
  - Bundled in `RulebookAssets` for instant offline viewing in English and Hindi.

### 9. Live Station Timetable & Departure Boards
- Accessible directly from the transit menu: view upcoming departures for any Japanese station.
- Shows real-time minute countdowns (*"Due now"*, *"3 min"*, *"8 min"*), destination headsigns (*"towards ..."*), platform numbers, and official transit line colors.
- Powered by MOTIS and Transitous live transit open data.

### 10. First-Timer Visual Station Guide
- Step-by-step visual guidance designed for first-time visitors in Japanese transit stations:
  - **Step 1 ("Find this sign"):** Colored line badge (e.g. orange `G 01`), direction arrow, and floor/wall track navigation line with double chevrons (`>>`).
  - **Step 2 ("Board this train"):** Train front graphic with line stripe, headlights, and amber LED destination board (`[浅草 Asakusa]`).
  - **Step 3 ("Ride X more stops"):** Big stops-to-go countdown with active progress bar and station-to-station progress.
  - **Step 4 ("This is your stop"):** Arrival sign badge with destination Japanese/English and green checkmark.
  - **"I'm lost. Help me" Station Staff Card:** One tap opens a Japanese assistance card (`"すみません、道に迷いました。この駅に行きたいです：..."`) to show station staff or Koban police.

### 11. Bilingual English & Japanese Names
- Automated transliteration and facility suffix classification for Japanese locations:
  - Train stations display bilingual badges: `Kanda 神田` instead of raw Japanese `神田(東京都)`.
  - Hospital and clinic cards feature prominent English titles (e.g. **Sakura Watanabe Hospital**) with original Japanese subtitles (**桜橋渡辺病院**) for showing taxi drivers or local staff.

### 12. Complete Hindi & English Parity
- Instant one-tap toggle (`हिन्दी` / `English`) across the entire app.
- All 78 etiquette tips, diet card meanings, reminders, menu warnings, and Desi Match reasons have hand-written Hindi versions.
- Place and station names remain in English letters matching official physical signage across Japan.

### 13. Ask Konnichi: In-Trip Companion Chat
- Sub-second responses powered by AI tool calling rather than ungrounded guesses:
  - Live train routes from Transitous timetables.
  - Etiquette tips queried from the verified rulebook.
  - Hand-checked Japanese phrase cards to show staff full-screen.
  - Desi Match food queries.

### 14. Emergency & Help Nearby
- One-tap quick dialing for Japanese emergency services: **110 (Police)**, **119 (Ambulance/Fire)**.
- Nearby lookups for hospitals, clinics, pharmacies, ATMs, Kobans (police boxes), convenience stores, and public toilets from a database of 30,436 places with distance and directions.

---

## Architecture

```
┌────────────────── Android Client (android/) ──────────────────┐
│  • Jetpack Compose & Material 3 UI (Single-activity architecture)│
│  • Hilt Dependency Injection & Coroutines / StateFlow Flow     │
│  • Room Database (Offline Itinerary & Sync Queue)             │
│  • DataStore Preferences (User Profile & Language State)      │
│  • CameraX + ML Kit (On-Device OCR & Language Translation)     │
│  • MapLibre Native SDK + OpenFreeMap Vector Tiles              │
│  • Bundled Offline Assets: etiquette.json, phrases.json,       │
│    stations.json (English & Hindi)                             │
└───────────────────────────────┬────────────────────────────────┘
                                │ JSON REST API (HTTP/1.1)
                                ▼
┌────────────────── Go API Backend (backend/) ──────────────────┐
│  • Standard Library net/http with chi-compatible routing       │
│  • PostgreSQL 15+ (Places, Itineraries, Etiquette Rulebook)    │
│  • AI Planner & Companion Chat:                                │
│      - Local LLM: Ollama (gemma4:12b / qwen3.5:9b)            │
│      - Cloud LLM: Google Gemini & Groq APIs                    │
│      - Fallback: Curated catalogue & deterministic arrange     │
│  • Live Web Search Grounding: DuckDuckGo API                   │
│  • Transit Engine: Transitous API (MOTIS OpenTripPlanner)      │
│  • Geocoding & OpenStreetMap Enrichment: Photon API            │
└────────────────────────────────────────────────────────────────┘
```

---

## Repository Layout

```
backend/
  cmd/api/            API server entrypoint, routes, handlers, middleware
  cmd/enrich/         Database place enrichment & English transliteration
  cmd/seed/           Database seeder for rules, places, and station guides
  cmd/osmfetch/       OpenStreetMap raw snapshot fetcher
  internal/companion/ In-trip companion chat engine, tool loop, itinerary actions
  internal/data/      Data models, validation, PostgreSQL stores, romanization
  internal/desimatch/ Desi Match food scoring and diet ranking algorithm
  internal/gemini/    Google Gemini REST client
  internal/groq/      Groq OpenAI-compatible chat client
  internal/menu/      Menu translation parsing and diet alert checks
  internal/photon/    Photon OSM geocoding and search client
  internal/planner/   AI itinerary planner, catalogue fallback, day arrangement
  internal/transit/   Transitous routing, live timetable, and station romanizer
  internal/websearch/ Live web search grounding via DuckDuckGo
  rulebook/           etiquette.json, phrases.json, stations.json (EN & HI)
  migrations/         SQL migrations (golang-migrate)

android/
  app/src/main/java/com/osakapatnam/konnichiwah/
    data/             Repositories, Room database, DataStore, ML Kit MenuReader
    di/               Hilt modules (Network, Database)
    reminders/        WorkManager day-before notification worker
    ui/               Compose screens (Home, Plan, Chat, Menu, Directions, Nearby)
      components/     Shared UI components (StationMapDialog, RouteMap, PhraseCards)
      theme/          Color palette, typography, shapes
```

---

## Getting Started

### Prerequisites
- **Go 1.23+**
- **PostgreSQL 15+** & **golang-migrate** (`brew install golang-migrate`)
- **Android Studio** with Android SDK 37 (Android 17) or physical Android device
- **Ollama** (for local AI model) or Gemini/Groq API keys

---

### Step 1: Database Setup

```sh
psql -d postgres <<'SQL'
CREATE DATABASE konnichiwah;
CREATE ROLE konnichiwah WITH LOGIN PASSWORD 'choose-a-password';
ALTER DATABASE konnichiwah OWNER TO konnichiwah;
SQL
```

---

### Step 2: Backend Setup

1. Configure environment:
   ```sh
   cd backend
   cp .envrc.example .envrc
   # Edit .envrc with your database password:
   # export KONNICHIWAH_DB_DSN="postgres://konnichiwah:choose-a-password@localhost/konnichiwah?sslmode=disable"
   source .envrc
   ```

2. Run database migrations and seed content:
   ```sh
   make db/migrations/up
   make db/seed
   ```

3. **Start the Backend:**

   - **Option A: With Local AI (Ollama - Recommended, 100% Free & Private)**
     ```sh
     # Ensure Ollama is running and model is downloaded:
     ollama serve &
     ollama pull gemma4:12b

     # Build and start server:
     go build -o bin/api ./cmd/api
     ./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -llm=local -local-llm-model=gemma4:12b
     ```

   - **Option B: With Cloud AI (Gemini / Groq)**
     ```sh
     # Add GEMINI_API_KEY and GROQ_API_KEY to .envrc, then:
     ./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -llm=groq
     ```

4. Verify server health:
   ```sh
   curl http://localhost:4001/v1/healthcheck
   ```

---

### Step 3: Run the Android App

#### On a Physical Android Phone over USB
1. Enable **Developer Options** and **USB Debugging** on your phone.
2. Connect your phone to your Mac via USB and confirm `adb devices`.
3. **Set up reverse port forwarding** (so the phone can reach your local Go server):
   ```sh
   adb reverse tcp:4001 tcp:4001
   ```
4. Build and install:
   ```sh
   cd android
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.osakapatnam.konnichiwah/.MainActivity
   ```

#### On Android Studio Emulator
1. Open the `android/` directory in Android Studio.
2. Select an emulator running **API 37**.
3. Run `adb reverse tcp:4001 tcp:4001` in terminal.
4. Click **Run ▶** in Android Studio.

---

## API Reference

The full JSON schema contract is documented in [docs/api.md](docs/api.md).

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/healthcheck` | Server status and environment info |
| `POST` | `/v1/plan/suggestions` | Generates trip suggestions from questionnaire profile |
| `POST` | `/v1/itineraries` | Converts selected cards into a structured day-by-day plan |
| `GET` | `/v1/itineraries/:id` | Fetches an itinerary by ID |
| `PATCH`| `/v1/itineraries/:id` | Updates itinerary stops and dates |
| `POST` | `/v1/companion/chat` | AI companion conversation with tool-calls and itinerary actions |
| `GET` | `/v1/directions` | Transit routing with lines, exits, platforms, and geometry |
| `GET` | `/v1/transit/timetable`| Live station departures, countdowns, and platforms |
| `GET` | `/v1/places` | Nearby emergency services, hospitals, convenience stores |
| `GET` | `/v1/places/suggest` | Search-as-you-type station and place autocomplete |
| `GET` | `/v1/food` | Nearby restaurant search ranked by Desi Match |
| `POST` | `/v1/menu/translate` | AI menu line translation and dietary warning detection |
| `GET` | `/v1/etiquette` | Retrieves situational Japanese culture rules |

---

## Testing

```sh
# Run Go backend test suite:
cd backend && go test -v ./...

# Run Android unit & contract tests:
cd android && ./gradlew testDebugUnitTest
```

---

## Credits & Open Data
- **Cultural Guidance:** Public guidance and etiquette standards from Japan National Tourism Organization (JNTO / [japan.travel](https://www.japan.travel)).
- **Transit Data:** [Transitous OpenTransit](https://transitous.org) and participating Japanese railway operators under open data agreements.
- **Geocoding & Map Data:** [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [MapLibre](https://maplibre.org), [OpenFreeMap](https://openfreemap.org), and [Komoot Photon](https://photon.komoot.io).
- **Backend Architecture:** Structured after Alex Edwards' *[Let's Go Further](https://lets-go-further.alexedwards.net)*.
