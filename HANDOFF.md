# Handoff: where Konnichi-Wah! stands

Last updated 24 Oct 2026, 18:30 IST. Written so the next person (or AI session) can pick up without the chat history. The project brief is `CLAUDE.md`; setup is in `README.md`; the API is in `docs/api.md`.

## Done

All eight milestones in `CLAUDE.md` are built, plus several extras. Everything below is on `origin/main` unless marked otherwise.

| Area | What exists |
|---|---|
| Backend skeleton | Go JSON API in the *Let's Go Further* style: config flags, JSON helpers, central errors, validator, rate limit, CORS, graceful shutdown, Postgres, migrations 1–5. |
| Etiquette | 78 hand-written rules (`backend/rulebook/etiquette.json`), `GET /v1/etiquette`, tips on every stop, bundled in the app for offline use. |
| Planner | `POST /v1/plan/suggestions` (Gemini, schema-checked JSON, catalogue fallback); itinerary create, read and edit; Room storage with offline-first sync. |
| Companion chat | `POST /v1/companion/chat` with tools for train routes, etiquette, food, shops, checked diet cards, phrase cards and help nearby. The server refuses AI-written diet statements, so only checked cards are shown. |
| Desi Match | `GET /v1/food`: restaurants ranked 1–5 for the traveller's diet, from an OpenStreetMap snapshot. |
| Reminders | Trip dates, plus a 7 pm Japan-time reminder the evening before each day (WorkManager). |
| Menu reader | Camera or gallery photo, ML Kit on-device text recognition, translations drawn over the photo. Warnings come from our Japanese word list. AI translation through `POST /v1/menu/translate`, falling back to ML Kit on the phone. |
| Hindi | In-app English/हिन्दी switch. Hindi for all screens, the 78 tips, diet card meanings, chat replies and Desi Match reasons. |
| Local AI option | `-llm=local` runs the planner, chat and menu translation on Ollama on the Mac, with no keys or quotas. Default model `gemma4:12b`; `qwen3.5:9b` is also downloaded. Tested: Gemma used the tools correctly and planned a 5-day trip in 24 s; Qwen skipped the tools. |
| Frontend redesign | By Palak Mahajan: Home screen, tab bar, new theme and shared components (`ui/components/Kit.kt`). |
| **Directions** | `GET /v1/directions`: Transitous journeys with the walk to the station, the exit to use (e.g. "Exit A1"), line colours and codes (JB12), stops and the path of each leg. App: **Get there** screen with a MapLibre map (free OpenFreeMap tiles), opened from Home's "Find my train" and from each stop. Supports "From my location". |
| **Help nearby** | `GET /v1/places`: hospitals, clinics, pharmacies, police boxes, ATMs, toilets and convenience stores from a snapshot of 30,436 places (`backend/osmdata/help.json`), plus live Photon search for anything else (supermarket, laundromat, a shop by name). App: **Help nearby** screen with the 119/110 emergency numbers, a map, Call and Directions buttons. The chat has a matching `find_nearby` tool and receives the traveller's location when permission is granted. |
| English station names | `backend/osmdata/stations.json` (1,237 stations from OpenStreetMap) gives English names to stations the timetables only name in Japanese (Toei and Kansai lines). |

Directions and Help nearby were checked end to end on the emulator:
- Shinjuku Gyoen → Senso-ji, and the route to Keio University Hospital.
- Hospitals near Shinjuku Gyoen.
- Chat answers for "nearest hospital", "pharmacy near me", "ATM" (in Hindi) and "how do I get to Senso-ji from here".

Backend tests and all 42 Android tests passed at the last run.

## Not committed yet (on this Mac only)

- `android/.../data/LocationProvider.kt`: uses a location fix up to 2 minutes old straight away, and waits at most 10 seconds for a new one.
- `android/.../ui/components/RouteMap.kt`: waits for the map to be laid out before zooming to the route. Without this the map sometimes showed the wrong area.
- `backend/osmdata/stations.json`: now complete for all five cities. The committed copy only had Tokyo and Kyoto.

Commit and push these first.

## Left to do

1. **Docs for the new work.**
   - `docs/api.md` needs sections for `GET /v1/directions` and `GET /v1/places`, the chat's optional `context.location`, and the `find_nearby` tool.
   - `README.md` needs Directions and Help nearby added to the features list.
2. **Home subtitle.** The "Find my train" card still says "Live routes, just ask" (`home_train_sub` in both `strings.xml` files). It now opens the directions screen, so change it to something like "Routes, exits and platforms".
3. **Test "My location" on a real phone.** Then test the Call button on a hospital. On the emulator, location needs a mock provider:
   ```sh
   adb shell appops set com.android.shell android:mock_location allow
   for p in gps fused network; do
     adb shell cmd location providers add-test-provider $p
     adb shell cmd location providers set-test-provider-enabled $p true
     adb shell cmd location providers set-test-provider-location $p --location 35.6852,139.7100
   done
   ```
4. **Local models.** Compared on menus, diet cards, Hindi etiquette and planning; Gemma 4 12B won and is the default. Still untested on local: train routes, food and help searches in the chat. Close Android Studio while using it; memory is tight on 16 GB.
5. **Handwritten menus.** ML Kit can't read brush-written menus. The fix would be sending the photo itself to a vision model, for example a local Gemma 4 or Qwen 3.5, which both read images. Not built.
6. **Content review by native speakers:**
   - all the Hindi: `res/values-hi/strings.xml`, `backend/rulebook/*.hi.json`, the Desi Match reasons
   - the Japanese diet cards (`phrases.json`)
   - the menu warning words (`MenuWords.kt`)
   - the etiquette rules that depend on dates or seasons
7. **Still missing from the brief:**
   - Firebase sign-in, so itineraries aren't tied to users and the diet profile comes from query parameters rather than a login
   - an app launcher icon
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
