# Handoff: where Konnichi-Wah! stands

Last updated 24 Oct 2026, 21:10 IST. Written so the next person (or AI session) can pick up without the chat history. The project brief is `CLAUDE.md`; setup is in `README.md`; the API is in `docs/api.md`.

## Done

All eight milestones in `CLAUDE.md` are built, plus all three navigation upgrades and several extras. Everything below is built and tested.

| Area | What exists |
|---|---|
| Backend skeleton | Go JSON API in the *Let's Go Further* style: config flags, JSON helpers, central errors, validator, rate limit, CORS, graceful shutdown, Postgres, migrations 1–5. |
| Etiquette | 78 hand-written rules (`backend/rulebook/etiquette.json`), `GET /v1/etiquette`, tips on every stop, bundled in the app for offline use. |
| Planner | `POST /v1/plan/suggestions` (Gemini & local Ollama Gemma 4:12b, schema-checked JSON with normalization, defensive enum handling, pace-guided suggestion counts, catalogue fallback); itinerary create, read and edit; smart curated pace selection; Room storage with offline-first sync. |
| Companion chat | `POST /v1/companion/chat` with tools for train routes, etiquette, food, shops, checked diet cards, phrase cards and help nearby. Gracefully handles planning/testing locations outside Japan. |
| Desi Match | `GET /v1/food`: restaurants ranked 1–5 for the traveller's diet, from an OpenStreetMap snapshot. |
| Reminders | Trip dates, plus a 7 pm Japan-time reminder the evening before each day (WorkManager). |
| Menu reader | Camera or gallery photo, ML Kit on-device text recognition, translations drawn over the photo. Warnings come from our Japanese word list. AI translation through `POST /v1/menu/translate`, falling back to ML Kit on the phone. |
| Hindi | In-app English/हिन्दी switch. Hindi for all screens, the 78 tips, diet card meanings, chat replies, station guides, and Desi Match reasons. |
| Local AI option | `-llm=local` runs the planner, chat and menu translation on Ollama on the Mac (`gemma4:12b`), with no keys or quotas. Tested with tool-calling. |
| Frontend redesign | By Palak Mahajan: Home screen, tab bar, new theme and shared components (`ui/components/Kit.kt`). |
| **Directions** | `GET /v1/directions`: Transitous journeys with the walk to the station, the exit to use, line colours and codes (JB12), stops and the path of each leg. App: **Get there** screen with a MapLibre map (free OpenFreeMap tiles), opened from Home's "Find my train" and from each stop. |
| **Place suggestions** | `GET /v1/places/suggest`: 300 ms debounced autocomplete under From/To on **Get there**, querying local station snapshot and Photon. |
| **Colour-coded line steps** | Step-by-step instructions for every leg with line colours, letters, platform numbers, ticket gate IC card tap reminders, and transfers. |
| **Station guides** | Built-in guides in English and Hindi for major stations (Tokyo, Shinjuku, Shibuya, Kyoto, Osaka/Umeda, Nara) with exits and transfer tips (`backend/rulebook/stations.json`, `stations.hi.json`). |
| **Help nearby** | `GET /v1/places`: hospitals, clinics, pharmacies, police boxes, ATMs, toilets and convenience stores from a snapshot of 30,436 places (`backend/osmdata/help.json`), plus live Photon search. |
| **Brand adaptive icon** | Torii gate and rising sun adaptive launcher icon matching the brand palette (`#6B1E26`, `#C8323A`, `#F7F0E4`). |

Backend tests and all 42 Android tests pass.

**How to run everything** (emulator, phone on USB, phone anywhere through a tunnel, cloud or local AI) is in [`docs/running.md`](docs/running.md).

## Left to do

1. **Content review by native speakers:**
   - all the Hindi: `res/values-hi/strings.xml`, `backend/rulebook/*.hi.json`, the Desi Match reasons
   - the Japanese diet cards (`phrases.json`)
   - the menu warning words (`MenuWords.kt`)
   - the etiquette rules that depend on dates or seasons
2. **Handwritten menus:** ML Kit can't read brush-written menus. The fix would be sending the photo itself to a vision model (e.g. local Gemma 4 or Qwen 3.5).
3. **Firebase sign-in:** so itineraries can be tied to user accounts rather than anonymous ids.
4. **Known small issues:**
   - **Bad English names in OpenStreetMap:** a police box near Fushimi Inari is called "Police Academy".
   - **Map vs scroll:** dragging on a map moves the map, not the page.
   - **Groq's free tier** answers "busy" after several quick chat requests; waiting a minute fixes it.
8. **Known small issues:**
   - **Bad English names in OpenStreetMap:** a police box near Fushimi Inari is called "Police Academy".
   - **Map vs scroll:** dragging on a map moves the map, not the page.
   - **Groq's free tier** answers "busy" after several quick chat requests; waiting a minute fixes it.

## Running it (quick reference)

- **Backend:** in `backend/`, run `source .envrc && go build -o bin/api ./cmd/api`, then `./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -groq-api-key="$GROQ_API_KEY" -gemini-api-key="$GEMINI_API_KEY"`. Port 4000 belongs to another project. When restarting, wait until the old server has released the port, or the new one fails with "address already in use".
- **Database:** after changing anything in `osmdata/` or `rulebook/`, run `go run ./cmd/seed -db-dsn="$KONNICHIWAH_DB_DSN"`.
- **Phone or emulator:** run `adb reverse tcp:4001 tcp:4001` after every reconnect; the app calls `http://localhost:4001/`.
- **Android build:** `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`. The first build after an edit sometimes fails within a second; run it again.
- **OpenStreetMap data:** `go run ./cmd/osmfetch -set=food|help|stations`. The public Overpass servers are slow and often fail on the first round; the tool retries and keeps old data for any city it can't fetch.
- **Local AI:** start `ollama serve` (Homebrew installed it; it isn't set to start at login), then add `-llm=local` to the backend command and leave out the API keys. Ollama on a Mac can't force JSON replies, so in local mode the backend asks for JSON in the prompt and checks the reply as usual (`NoJSONMode` in `internal/groq`).
