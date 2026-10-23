# Japan Travel Companion for Indian Travellers — Project Brief

> Working name: **Konnichi-Wah!** (may change). Team: OsakaPatnam.
> Built for Hakuhodo India problem **HAK-PS-01: "Attracting Indian Travelers to Japan."**
> This is a hackathon prototype: favour a working end-to-end demo over completeness.

## 1. What we are building

An Android app that helps Indian travellers plan a trip to Japan and then guides them **during** the trip.
The core idea: Indians don't avoid Japan because of price; they avoid it because of **uncertainty**
(food they may not like, Japanese-only menus/signs, confusing trains, fear of breaking etiquette).
The app removes that uncertainty. It does **not** compete on discounts.

The app has three stages:

1. **Plan:** a short questionnaire plus a free-text box.
2. **Itinerary:** an AI planner suggests places, activities and food. The user picks what they like and the app turns it into a day-by-day itinerary, with culture tips attached to every stop.
3. **Companion:** during the trip, an AI chat assistant answers questions about trains, food and etiquette, using the user's own itinerary and taste profile as context.

## 2. Target users

- Indian travellers (families, students, couples, business travellers), mostly first-time visitors to Japan.
- Diet ranges from strict vegetarian / Jain to non-vegetarian. Many non-vegetarians still avoid beef and/or pork.
- English-speaking. Hindi support comes later.

## 3. Features (prototype scope)

### 3.1 Questionnaire (onboarding)
Multiple-choice screens, then a free-text box at the end:
- **Interests** (multi-select): temples & shrines, anime/pop culture, food, nature, shopping, snow/winter, nightlife, history.
- **Trip length** (days) and **travel month**.
- **Budget** (in INR) as bands: ₹1–2L, ₹2–3L, ₹3L+ per person.
- **Pace:** relaxed / balanced / packed.
- **Travel group:** solo, couple, friends, family with kids, family with elders.
- **Taste profile** (see 3.4): diet, foods to avoid, spice level.
- **Comfort with Japanese:** none / basic.
- **Free text:** "Anything else? (wheelchair access, elderly parents, must-see places, etc.)"

All answers plus the free text are sent to the planner agent.

### 3.2 AI planner → suggestions
- The backend sends the answers to the LLM and gets back **structured JSON** (not prose): suggested cities, places, activities and restaurants, each with a short reason.
- The app shows the suggestions as selectable cards.

### 3.3 Itinerary builder
- The user selects or removes suggestions. The app (or the planner, in a second call) arranges them into **days → stops** (time slot, place, food option, travel hint).
- **Culture tips are attached to every stop** based on the stop's `place_type` (temple, shrine, onsen, train, restaurant, ryokan, shop…), taken from our etiquette rulebook (3.6), not invented by the LLM.
- The user can reorder, delete and add stops.
- The itinerary is **saved locally (Room) and works offline.**

### 3.4 Desi Taste Match (key differentiator)
The food feature targets **Indian-palatable** food, not only vegetarian food.
- Taste profile: diet (veg / Jain / eggetarian / halal / non-veg), avoid list (beef, pork, seafood, egg, onion-garlic for Jain), spice level (mild / medium / Indian-spicy).
- Restaurants near a stop come from Google Places and get a simple **"Desi Match" score (1–5)** based on cuisine type, keywords and the user's profile.
- **Hidden-ingredient alerts:** warn that dashi (fish stock) and pork-based broths are common in miso soup, ramen and udon, so even "vegetarian-looking" dishes may not be.
- **Comfort food mode:** finds nearby Indian/Nepali curry restaurants, or familiar Japanese dishes such as Japanese curry rice.
- Offline **phrase cards** in Japanese, e.g. "I am vegetarian. No meat, no fish, no dashi." and "No beef, please."

### 3.5 AI Companion (in-trip chat)
- A chat screen. Each request includes context: today's itinerary day, the current/next stop, the taste profile, and (optionally) the user's location.
- The agent can call **tools** (server-side function calling):
  - `get_train_route(from, to, depart_time)` → Transitous (transit mode; see 4. External APIs)
  - `find_food(near_lat, near_lng, taste_profile)` → OpenStreetMap (Overpass) + Desi Match scoring
  - `find_places(query, near)` → OpenStreetMap (Overpass): shops and sights, e.g. "figure shops near me"
  - `get_etiquette(place_type)` → our rulebook
  - `translate_phrase(text)` → returns Japanese plus romaji
- Answers must be short and practical, e.g. *"Take the JR Chuo Line to Kanda, then the Ginza Line to Asakusa (~30 min). Tip: stand on the left of escalators in Tokyo."*

### 3.6 Etiquette rulebook (our own data)
- A hand-written JSON file (about 50–100 rules) based on JNTO / japan.travel guidance, stored in the backend and bundled in the app for offline use.
- Shape:
  ```json
  { "id": "temple-shoes", "place_types": ["temple", "ryokan"], "title": "Remove your shoes",
    "tip": "Take off shoes where you see a step up or shoe racks.", "severity": "important" }
  ```

### 3.7 Menu / sign camera (stretch goal)
- CameraX + ML Kit Text Recognition (Japanese) + ML Kit on-device Translation (ja → en). Fully on-device.

## 4. Tech stack

### Android app (Kotlin)
- **UI:** Jetpack Compose + Material 3, Navigation Compose.
- **Architecture:** MVVM (ViewModel + StateFlow), single-activity, Hilt for DI.
- **Networking:** Retrofit + OkHttp + kotlinx.serialization.
- **Local storage:** Room (itinerary, stops, tips, phrase cards, cached suggestions); DataStore for user profile/settings.
- **Maps:** Maps Compose (Google Maps SDK).
- **Camera/ML (stretch):** CameraX, ML Kit Text Recognition v2 (Japanese), ML Kit Translation.
- **Background:** WorkManager (sync, day-before reminders).
- Min SDK 26. Offline-first: the app must open and show the itinerary with no network.

### Backend (Go, JSON API)
- **Follow the structure and style of Alex Edwards' *Let's Go Further*:**
  - `cmd/api/` (main, routes, handlers, helpers, errors, middleware), `internal/data/` (models), `internal/validator/`, `migrations/`.
  - `httprouter` routing, a JSON envelope, `readJSON`/`writeJSON` helpers, centralized error responses, the validator pattern, config via command-line flags, structured logging, graceful shutdown, panic recovery, rate limiting and CORS middleware.
- **Database:** PostgreSQL (users, saved itineraries, etiquette rules, cached place results); migrations with `golang-migrate`.
- **Auth:** Firebase Auth on the app; the backend verifies Firebase ID tokens in middleware.
- **LLM:** called **only from the backend**. API keys must **never** be in the APK. Use structured JSON output with a JSON schema for the planner and function calling for the companion.
- **External APIs:** Transitous for transit routes and OpenStreetMap (Overpass API) for places and food. NAVITIME is a future upgrade; don't build it now.
  - *Changed from Google Routes API + Places API (New), 2026-10-23.* Google Maps Platform needs a billing account with a card, and its transit directions appear not to cover Japan. Transitous (free, no key) returned real routes for JR, Tokyo Metro, the Shinkansen and Kintetsu in testing, but Kyoto city buses have gaps and some names are Japanese-only.
  - Transitous is a volunteer service for open-source and non-profit use, run best-effort: cache results, keep request volume low, and send a descriptive User-Agent (no personal data). For a public launch, switch to NAVITIME or a self-hosted instance.
  - Public Overpass servers are community-run and often slow: use short timeouts and fall back to our catalogue when they fail.

## 5. API endpoints (v1)

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/healthcheck` | Status/version |
| POST | `/v1/plan/suggestions` | Questionnaire answers → suggestions JSON |
| POST | `/v1/itineraries` | Create an itinerary from selected suggestions |
| GET | `/v1/itineraries/:id` | Fetch an itinerary |
| PATCH | `/v1/itineraries/:id` | Reorder/edit stops |
| GET | `/v1/etiquette?place_type=temple` | Rules for a place type |
| GET | `/v1/food?lat=&lng=` | Desi-Match-scored restaurants (uses the profile from the auth token) |
| POST | `/v1/companion/chat` | Chat turn with context; the backend runs the tool loop |

## 6. Core data model (sketch)

- **UserProfile:** id, interests[], budget_band, pace, group_type, diet, avoid[], spice_level, japanese_level, notes
- **Itinerary:** id, user_id, title, start_date, days[]
- **Day:** index, city, stops[]
- **Stop:** id, order, time_slot, place_name, place_type, lat, lng, google_place_id, food_option?, travel_hint?, tip_ids[]
- **EtiquetteRule:** id, place_types[], title, tip, severity
- **Restaurant (cached):** place_id, name, cuisine_tags[], lat, lng, desi_match_score

## 7. Build order (milestones)

1. Go backend skeleton following *Let's Go Further* (healthcheck, config, JSON helpers, errors, middleware) plus Postgres and migrations.
2. Etiquette rulebook JSON with the `/v1/etiquette` endpoint.
3. Planner endpoint with the LLM structured output (start with a mocked LLM response so the app can be built in parallel).
4. Android: questionnaire screens, then the suggestions screen, then the itinerary builder, with Room persistence.
5. Culture tips attached to stops, plus offline viewing.
6. Companion chat with the tool loop (routes, food, etiquette).
7. Desi Taste Match scoring and phrase cards.
8. Stretch: menu camera, reminders, Hindi.

## 8. Rules for the coding assistant

- Keep the prototype simple and demo-able; avoid over-engineering.
- Never put secrets (LLM, Google API keys) in the Android code or commit them; use env vars/flags on the backend.
- The LLM must not invent train routes or etiquette rules. Routes come from the Routes API and etiquette from our rulebook.
- Validate all LLM JSON against the schema on the backend before returning it to the app; fall back gracefully on bad output.
- Write small, readable code with comments in the *Let's Go* style; explain non-obvious decisions.
- Ask before adding new major dependencies.
