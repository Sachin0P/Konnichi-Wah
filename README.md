# Konnichi-Wah!

**A Japan travel companion for Indian travellers.** Built by team OsakaPatnam for Hakuhodo India's problem statement **HAK-PS-01: "Attracting Indian Travelers to Japan."**

Indian travellers don't skip Japan because of the price. They skip it because of uncertainty:

- Will there be food I can eat?
- Menus and signs are only in Japanese.
- The trains look impossible.
- What if I break some rule I don't know about?

Konnichi-Wah! removes that uncertainty, before the trip and during it.

> Hackathon prototype: it favours a working end-to-end demo over completeness. See [What's not built yet](#whats-not-built-yet).

## What it does

1. **Plan.** A short questionnaire covers interests, trip length and month, budget (in ₹), pace, who's travelling, and a taste profile (vegetarian, Jain, eggetarian, halal or non-vegetarian, plus anything to avoid). A free-text box takes the rest, e.g. "elderly parents, avoid long walks" or "I collect anime figurines".
2. **Suggestions.** An AI planner proposes a route (e.g. Tokyo 4 days, Kyoto 2, Osaka 1) and suggestion cards for places and activities to pick from. **Food never comes from the AI.** It comes from a hand-checked list filtered by diet, so a Jain traveller is never sent to a ramen shop with pork broth.
3. **Itinerary.** The chosen cards become a day-by-day plan:
   - Sights are spread evenly, and day trips (Nara from Kyoto) get their own day.
   - Temples are scheduled in daylight, and lantern-lit streets in the evening.
   - Reorder, remove and add stops.
   - **Saved on the phone and works fully offline.** Edits sync when you're back online.
4. **Culture tips on every stop.** Each stop shows etiquette tips from our own hand-written rulebook of 78 rules, such as "Remove your shoes", "Bathe without swimwear" or "Don't pass food chopstick to chopstick". The rulebook is bundled in the app, so the tips work with no signal.
5. **Desi Match food search.** "Find food nearby" on any stop lists restaurants ranked 1–5 for *your* diet, with the reasons and what to ask:
   - ★★★★★ "Vegan-only kitchen" for a vegetarian
   - ★★★★ "Indian or Nepali food", with "Ask for Jain food: no onion, garlic or root vegetables"
   - A ramen shop is left out for someone avoiding pork, even if it's the closest place.
6. **Day-before reminders.** Set the trip's dates and you get a notification at 7 pm Japan time the evening before each day, with tomorrow's stops and a culture tip ("Tomorrow: Day 2 in Kyoto · Morning · Fushimi Inari Taisha · Tip: …"). They're scheduled on the phone with WorkManager and need no network.
7. **Menu reader.** Photograph a Japanese menu (or pick a photo) and the app reads it on the phone:
   - **The English is drawn over the photo,** on top of each Japanese line, like Google Lens. Pinch to zoom, tap a line to see it in full, or switch to a list. Vertical Japanese gets its translation running down the line. The translation is machine-made, so only a rough guide.
   - **Warnings for your diet**, found in the Japanese itself rather than the translation: "⚠ Pork (豚)", "⚠ Dashi (fish stock) (味噌汁)". It knows what dishes usually hide (ramen broth, miso soup) and that 牛乳 is milk, not beef.
   - Text recognition works offline from the start. Translation needs a one-time 30 MB download.
8. **Diet cards, offline.** The "Cards" screen shows *your* hand-checked Japanese diet cards first (vegetarian, Jain, eggetarian, halal, no beef, no pork, "does this contain dashi?"). They're bundled in the app and work with no signal.
9. **In Hindi or English.** One tap (हिन्दी / English) switches the whole app, instantly:
   - Every screen, the 78 culture tips, each diet card's meaning, the reminders, the menu warnings and the Desi Match reasons have hand-written Hindi versions. The Japanese on the cards stays exactly the same.
   - The chat answers in Hindi and still uses our checked content, not machine translation.
   - Planner suggestions, place names and station names stay in English, as they appear on signs in Japan.
10. **Ask Konnichi, the in-trip chat.** Answers take 1–3 seconds, using tools rather than guesses:
   - **Train routes** from live timetable data: *"Take the Miyakoji Rapid from Kyoto 09:04 → Nara 09:51."* The AI never states a route from memory.
   - **Etiquette** only from our rulebook. City-specific rules apply only where they belong, so the tip about feeding Nara's deer doesn't show up for a park in Tokyo.
   - **Diet cards:** hand-checked Japanese cards to show restaurant staff, e.g. 「私はジャイナ教徒です。肉、魚、魚介類、卵、だしは食べられません。…」 They're never machine-translated on the fly.
   - **Phrase cards** for anything else ("Where is the toilet?"), shown full screen to hold up to staff.
   - **Food and shops nearby:** "Where can we eat near Senso-ji?" uses the same Desi Match ranking. "Figure shops near Akihabara?" lists real anime, collector, toy, hobby and 100-yen shops.

## How it fits together

```
┌───────── Android app (android/) ─────────┐         ┌──────────── Go API (backend/) ────────────┐
│ Jetpack Compose screens                  │         │ /v1/plan/suggestions ─► Gemini            │
│   ↓ ViewModels (StateFlow)               │  JSON   │        └─ fallback ─► curated catalogue   │
│   ↓ Repositories ── Room + DataStore     │ ──────► │ /v1/itineraries ─────► PostgreSQL         │
│        (offline copy of the trip)        │         │ /v1/etiquette ───────► rulebook (Postgres)│
│   ↓ Retrofit + OkHttp                    │         │ /v1/companion/chat ──► Groq + tools:      │
│ Bundled: etiquette.json, phrases.json    │         │      train routes (Transitous), rules,    │
└──────────────────────────────────────────┘         │      checked diet cards, phrase cards     │
                                                     └───────────────────────────────────────────┘
```

- **The app never talks to an AI or holds an API key.** All AI calls happen on the backend.
- **Every AI answer is checked before it reaches a traveller.** The planner's JSON is validated against a schema, and place types must exist in the rulebook. If anything fails, the backend falls back to the catalogue, so the app always gets a plan.
- **One source of truth for the content.** `backend/rulebook/etiquette.json` and `phrases.json` are loaded into the backend and copied into the APK at build time.

### Free services we use

| Service | Used for | Why this one |
|---|---|---|
| [Gemini API](https://ai.google.dev) (free tier) | Trip planner (structured JSON output) | Schema-constrained output. The free tier allows only **20 requests per day per model**, so the backend falls back through several models and then to the catalogue. |
| [Groq](https://console.groq.com) (free tier) | Companion chat (tool calling) | About 1-second answers and around 1,000 free requests a day |
| [Transitous](https://transitous.org) | Train, subway, Shinkansen and bus routes | Free, no key, with real timetables for JR, Tokyo Metro, Shinkansen and Kintetsu. Google's transit directions appear not to cover Japan. |
| [ML Kit](https://developers.google.com/ml-kit) | Menu reader: Japanese text recognition and Japanese-English translation | Free and on the phone, so photos never leave it and it works offline |
| [OpenStreetMap](https://www.openstreetmap.org) | Restaurants (Desi Match) and shops | Free, and has diet tags (`diet:vegetarian`, `diet:halal`) and cuisines (`indian`, `nepalese`). We keep a snapshot (`osmdata/places.json`) because the public Overpass servers were often too slow or down to use live. |

Transitous is run by volunteers for open-source and non-profit projects. We cache its results and keep request volume low. A public launch would move to NAVITIME or a self-hosted instance. Route data © Transitous and the transit operators whose feeds it uses. Map data © OpenStreetMap contributors (ODbL).

## Repository layout

```
backend/            Go JSON API, structured after Alex Edwards' "Let's Go Further"
  cmd/api/            main, routes, handlers, helpers, errors, middleware
  cmd/seed/           loads the etiquette rulebook and the places snapshot into PostgreSQL
  cmd/osmfetch/       refreshes osmdata/places.json from OpenStreetMap (make osm/fetch)
  internal/desimatch/ Desi Match scoring and ranking
  internal/data/      models and validation (itineraries, rules, profiles, phrase cards)
  internal/planner/   Gemini planner, catalogue fallback, day-by-day arranging
  internal/companion/ chat tool loop and tools (provider-neutral: Groq or Gemini)
  internal/gemini/    internal/groq/    internal/transit/   small net/http API clients
  rulebook/           etiquette.json and phrases.json (hand-written content), and their Hindi versions
  osmdata/            places.json: the OpenStreetMap snapshot (Tokyo, Kyoto, Osaka, Nara, Sapporo)
  migrations/         golang-migrate SQL migrations
android/            Kotlin app: Compose, Material 3, Hilt, Retrofit, Room, DataStore, WorkManager, CameraX, ML Kit
docs/api.md         the API contract between app and backend
CLAUDE.md           the project brief
```

## Running it locally

### Prerequisites

- **Go 1.27+**, **PostgreSQL 15+**, and [golang-migrate](https://github.com/golang-migrate/migrate) (`brew install golang-migrate`).
- **Android Studio** with an emulator on **API 37** (Android 17), or a phone connected by USB.
- Optional free API keys:
  - [Google AI Studio](https://aistudio.google.com) for Gemini: the AI planner.
  - [Groq](https://console.groq.com): the chat.

  Without them, the planner uses the built-in catalogue and the chat is switched off.

### 1. Database

```sh
psql -d postgres <<'SQL'
CREATE DATABASE konnichiwah;
CREATE ROLE konnichiwah WITH LOGIN PASSWORD 'choose-a-password';
ALTER DATABASE konnichiwah OWNER TO konnichiwah;
SQL
```

### 2. Backend

```sh
cd backend
cp .envrc.example .envrc       # then fill in the DSN password and any API keys
make db/migrations/up          # create the tables
make db/seed                   # load the etiquette rulebook and the places snapshot
make run/api port=4001         # start the API (default port is 4000)
```

`.envrc` is gitignored. Never commit keys. Check it's up with `curl localhost:4001/v1/healthcheck`.

Other useful targets: `make audit` (vet and tests), `make db/psql`, and `make help` for the full list. The live Transitous test is skipped by default; run it with `TRANSIT_LIVE=1 go test ./internal/transit -run Live -v`.

### 3. Android app

1. In Android Studio, **File → Open → `android/`**. Open that folder, not the repo root, or the Run button stays greyed out.
2. Start the emulator, then forward the port. Do this again each time the emulator restarts or the phone reconnects:
   ```sh
   adb reverse tcp:4001 tcp:4001
   ```
   Debug builds call `http://localhost:4001/`. We don't use the emulator's usual `10.0.2.2` alias because Android 17 blocks apps from reaching local-network addresses without a runtime permission.
3. Press **Run ▶**.

If you build from a terminal and see `jlink ... does not exist`, a Gradle daemon was started with VS Code's bundled Java runtime. Run `./gradlew --stop`, then build with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## API

The full contract, with request and response examples for every endpoint, is in **[docs/api.md](docs/api.md)**.

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/healthcheck` | Status and version |
| GET | `/v1/etiquette?place_type=temple` | Etiquette rules for a kind of place |
| POST | `/v1/plan/suggestions` | Questionnaire answers → route and suggestion cards |
| POST | `/v1/itineraries` | Chosen cards → a day-by-day itinerary |
| GET / PATCH | `/v1/itineraries/:id` | Fetch, or save edits (with optimistic locking) |
| POST | `/v1/companion/chat` | One chat turn; the backend runs the tool loop |
| GET | `/v1/food?near=Senso-ji&city=Tokyo&diet=veg` | Restaurants nearby, ranked by Desi Match |

## Testing

- **Backend:** `cd backend && go test ./...` covers validation, the day-by-day arranging, the planner's fallback, the chat tool loop (with fake LLMs), the API clients and the content files. Every diet must have a checked card, every place type must have tips, the Jain card must say ジャイナ教徒, and every rule, card and Desi Match reason must have Hindi.
- **Android:** `cd android && ./gradlew testDebugUnitTest` covers contract tests that the app's JSON matches `docs/api.md`, plus itinerary editing, the questionnaire rules, the offline tip lookup, reminders, the menu warnings, and that every Hindi string exists with the same placeholders as the English.

## What's not built yet

- **Restaurant coverage beyond the Desi-relevant places.** The snapshot holds only places with diet tags or South Asian and curry cuisines, in five cities. OpenStreetMap's diet tags are also incomplete, so a good vegetarian place may simply not be tagged.
- **"Near me" with the phone's location.** Searches are by place name for now.
- **Hindi planner suggestions.** The planner's reasons and the catalogue are English only.
- **Sign-in.** Itineraries aren't tied to users yet, so anyone with an itinerary id can read it. Firebase Auth is planned.
- **Exact locations for stops.** They have no coordinates yet; routes are found by place name.
- **Content review.** The Japanese phrase cards and the time-sensitive etiquette rules (tax-free shopping, Shinkansen luggage, Gion photo rules) should be checked by a native speaker before any real use, and so should all the Hindi (`res/values-hi/strings.xml`, `rulebook/*.hi.json`, the Desi Match reasons) and the menu reader's word list.

## Credits

Etiquette content is based on public guidance from JNTO / [japan.travel](https://www.japan.travel). Route data comes from [Transitous](https://transitous.org) and the operators whose open data it uses. Restaurant and shop data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, available under the Open Database Licence. The backend follows the structure of Alex Edwards' [*Let's Go Further*](https://lets-go-further.alexedwards.net).
