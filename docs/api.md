# Konnichi-Wah! API (v1)

This is the contract between the Android app and the Go backend in `backend/`. If you change a request or response shape, update this file in the same commit.

- [Basics](#basics)
- [Errors](#errors)
- [GET /v1/healthcheck](#get-v1healthcheck)
- [GET /v1/etiquette](#get-v1etiquette)
- [POST /v1/plan/suggestions](#post-v1plansuggestions)
- [POST /v1/itineraries](#post-v1itineraries)
- [GET /v1/itineraries/:id](#get-v1itinerariesid)
- [PATCH /v1/itineraries/:id](#patch-v1itinerariesid)
- [POST /v1/companion/chat](#post-v1companionchat)
- [GET /v1/food](#get-v1food)
- [POST /v1/menu/translate](#post-v1menutranslate)
- [GET /v1/directions](#get-v1directions)
- [GET /v1/places](#get-v1places)
- [GET /v1/places/suggest](#get-v1placessuggest)
- [Permitted values](#permitted-values)
- [Running the backend for app development](#running-the-backend-for-app-development)
- [Not built yet](#not-built-yet)

## Basics

- All requests and responses are JSON (`Content-Type: application/json`).
- Every response is wrapped in one top-level key, such as `{"plan": {...}}` or `{"etiquette_rules": [...]}`.
- Keys are `snake_case`.
- Request bodies are strict. An unknown key (for example a typo such as `"profle"`) is rejected with a 400, and bodies over 1 MB are rejected too.
- The app should ignore unknown keys in **responses** (`Json { ignoreUnknownKeys = true }`), so that the backend can add fields without breaking older app builds.
- Rate limit: about 2 requests per second per IP address, with bursts of up to 4. Going over returns a 429.
- Auth: none yet. Firebase ID tokens (`Authorization: Bearer <token>`) will be added later.

## Errors

Every error uses the same envelope, with an `error` key. Its value is either a string or an object:

| Status | When | `error` value |
|---|---|---|
| 400 | The request body is badly formed, or contains an unknown key | string |
| 404 | No such route | string: `"the requested resource could not be found"` |
| 405 | Wrong HTTP method for the route | string |
| 409 | Edit conflict: another update saved first. Fetch the itinerary again and retry. | string |
| 422 | Validation failed | object of field → message |
| 429 | Rate limited | string: `"rate limit exceeded"` |
| 500 | A bug on our side | string (generic; the details are only in the server log) |
| 503 | The AI service is busy, out of today's free quota, or not configured (companion chat only) | string, safe to show the user |

```json
{ "error": "body contains unknown key \"profle\"" }
```

```json
{
  "error": {
    "diet": "must be one of: veg, jain, eggetarian, halal, non_veg",
    "trip_days": "must be at least 1"
  }
}
```

Validation messages list the permitted values, so they can be shown to a developer as they are. For users, map the field names to friendly text.

## GET /v1/healthcheck

Returns the server's status and version.

```json
{
  "status": "available",
  "system_info": { "environment": "development", "version": "1.0.0" }
}
```

## GET /v1/etiquette

Returns etiquette rules from our hand-written rulebook, with `important` rules first.

| Query parameter | Required | Notes |
|---|---|---|
| `place_type` | no | One of the [place types](#permitted-values). Leave it out to get the whole rulebook, which is useful for refreshing the offline copy. |

```
GET /v1/etiquette?place_type=temple
```

```json
{
  "etiquette_rules": [
    {
      "id": "temple-shoes",
      "place_types": ["temple", "ryokan"],
      "title": "Remove your shoes",
      "tip": "Take off your shoes where you see a step up, a shoe rack or slippers laid out...",
      "severity": "important"
    }
  ]
}
```

- `severity` is `important` (breaking the rule could cause real offence or get you refused entry) or `helpful`.
- `cities` is only present on rules that apply in specific places (for example `"cities": ["Nara"]` for feeding the deer). Those rules are only attached to stops whose `city` matches, ignoring case. Rules without it apply everywhere.
- Rule `id`s are stable slugs. Itinerary stops will refer to rules by these ids.
- The same rules ship inside the app as `assets/etiquette.json`. The build copies them from `backend/rulebook/etiquette.json`.
- An unknown `place_type` returns a 422.

## POST /v1/plan/suggestions

Takes the questionnaire answers and returns suggested cities, plus suggestion cards the user can select. Nothing is saved.

**This call can take 10–20 seconds**, because it waits for the LLM. Set the HTTP client's read timeout to at least 35 seconds and show a loading state. If the LLM is slow, overloaded or returns bad output, the backend falls back to a built-in catalogue, so a successful call always returns a plan.

### Request

```json
{
  "profile": {
    "interests": ["temples_shrines", "anime", "food"],
    "budget_band": "2-3L",
    "pace": "balanced",
    "group_type": "family_elders",
    "diet": "veg",
    "avoid": [],
    "spice_level": "medium",
    "japanese_level": "none",
    "notes": "Parents are 65+, avoid long walks"
  },
  "trip_days": 7,
  "travel_month": 4
}
```

| Field | Rules |
|---|---|
| `profile.interests` | At least 1, no duplicates, from [interests](#permitted-values) |
| `profile.budget_band`, `pace`, `group_type`, `diet`, `spice_level`, `japanese_level` | Required, one of the [permitted values](#permitted-values) |
| `profile.avoid` | May be empty; no duplicates |
| `profile.notes` | Optional free text, up to 1000 bytes |
| `trip_days` | 1–30 |
| `travel_month` | 1–12 |

### Response: 200 OK

```json
{
  "plan": {
    "cities": [
      {
        "name": "Tokyo",
        "days": 4,
        "reason": "...",
        "hotel_name": "APA Hotel Shinjuku Kabukicho",
        "hotel_price": "~¥7,500/night (~₹4,200)",
        "hotel_reason": "Central station access within budget"
      },
      {
        "name": "Kyoto",
        "days": 3,
        "reason": "...",
        "hotel_name": "Sotetsu Fresa Inn Kyoto-Shijo",
        "hotel_price": "~¥6,800/night (~₹3,800)",
        "hotel_reason": "Near subway station within budget"
      }
    ],
    "suggestions": [
      {
        "id": "s1",
        "kind": "place",
        "name": "Senso-ji",
        "city": "Tokyo",
        "place_type": "temple",
        "reason": "Tokyo's oldest temple, with flat paved grounds that suit older travellers."
      }
    ],
    "source": "llm"
  }
}
```

- `cities[].days` always add up to `trip_days`.
- `suggestions[].kind` is `place`, `activity` or `food`. Food suggestions always have `place_type: "restaurant"`.
- Food suggestions never come from the LLM. They come from our hand-checked catalogue, filtered by the traveller's `diet` and `avoid` list, for the chosen cities. A city with nothing suitable (for example Jain in Osaka) gets no food cards; Google Places search will fill that gap later.
- `suggestions[].id` is unique **within this response only**. It isn't stable across calls, so don't store it as a long-term key. LLM places are `s1`, `s2`…, and catalogue items use slugs like `tokyo-ts-tantan`.
- `suggestions[].city` can be a day-trip town (for example `Nara` or `Hakone`) that isn't in `cities`.
- `place_type` is always one of the [place types](#permitted-values), so every suggestion can be matched to etiquette rules.
- `source` is `llm` (Gemini) or `catalogue` (the built-in fallback). The app doesn't need to show it, but it helps when debugging.
- Suggestions have no coordinates or Google place ids on purpose. Those will be looked up when the itinerary is created.

## POST /v1/itineraries

Turns the plan's cities and the suggestions the user kept into a day-by-day itinerary, then saves it. The arranging is deterministic (no LLM call), so the response is instant:

- Each city gets its planned number of days, in route order.
- Places and activities are spread evenly over the city's days. A day trip (e.g. Nara from Kyoto) starts a new day when the city has one free.
- Streets, onsen and ryokan go in the `evening` slot; other sights fill the `morning` and `afternoon`. Food goes in `lunch`, then `dinner`.
- Every stop gets `tip_ids` from the etiquette rulebook (up to 5, most important first) based on its `place_type`, leaving out city-specific rules for other cities.

### Request

Send the `cities` and the selected `suggestions` exactly as they came from `POST /v1/plan/suggestions`.

```json
{
  "title": "Amma & Appa in Japan",
  "start_date": "2027-04-02",
  "cities": [ { "name": "Tokyo", "days": 3, "reason": "..." } ],
  "suggestions": [ { "id": "s1", "kind": "place", "name": "Senso-ji", "city": "Tokyo", "place_type": "temple", "reason": "..." } ]
}
```

- `title` is optional. It defaults to `"My <n>-day Japan trip"`.
- `start_date` is optional (`YYYY-MM-DD` or `null`).
- `cities[].days` must add up to 1–30. `suggestions` needs 1–40 items.

### Response: 201 Created

Includes a `Location: /v1/itineraries/<id>` header.

```json
{
  "itinerary": {
    "id": 4,
    "created_at": "2026-10-23T12:30:53+05:30",
    "title": "My 5-day Japan trip",
    "start_date": null,
    "days": [
      {
        "index": 1,
        "city": "Tokyo",
        "stops": [
          {
            "id": "st_m4bmebcjzb6anhyf5yksqymh22",
            "time_slot": "morning",
            "place_name": "Senso-ji Temple, Asakusa",
            "place_type": "temple",
            "city": "Tokyo",
            "note": "Tokyo's oldest temple...",
            "lat": null,
            "lng": null,
            "google_place_id": null,
            "tip_ids": ["temple-photos", "temple-sacred-objects", "temple-shoes", "ryokan-tatami", "street-rubbish"]
          }
        ]
      }
    ],
    "version": 1
  }
}
```

- Stops are in visiting order. There is no separate `order` field: the position in the list is the order.
- `stops[].city` can differ from `days[].city` on a day trip.
- A day can have no stops (a free day).
- `lat`, `lng` and `google_place_id` are `null` until we add Google Places lookups.
- `tip_ids` refer to rule ids in `etiquette.json`. Look them up in the bundled rulebook; there's no need to call `/v1/etiquette`.

## GET /v1/itineraries/:id

Returns `{"itinerary": {...}}` in the same shape as above. An unknown or malformed id returns a 404.

## PATCH /v1/itineraries/:id

A partial update: send only the fields you're changing. After the user reorders, deletes or adds stops, send the whole `days` list.

```json
{ "days": [ { "index": 1, "city": "Tokyo", "stops": [ ... ] } ] }
```

```json
{ "title": "Amma & Appa in Japan" }
```

```json
{ "start_date": "" }
```

(An empty `start_date` clears the date.)

- Days are renumbered by their position; any `index` you send is ignored.
- **New stops need an `id` from the app** (a UUID is fine). Ids must be unique across the whole itinerary.
- `time_slot` must be one of the [time slots](#permitted-values). `place_type` must be one of the place types.
- **`tip_ids` are always recalculated by the server** from each stop's `place_type`. Whatever you send is replaced, so a stop the user adds gets real tips too.
- Returns 200 with the updated itinerary (with `version` increased), 404 if the id doesn't exist, or 409 if another update saved at the same moment.

## POST /v1/companion/chat

Answers one message in the in-trip companion chat. The backend keeps no chat state: send the conversation so far, ending with the traveller's new message, plus context about their day.

Answers usually take 1–6 seconds. The backend calls the LLM (Groq, or Gemini as a fallback) with tools:

- `get_train_route`: live timetable routes from Transitous. The model never states routes from memory.
- `get_etiquette`: our rulebook.
- `find_food`: Desi Match restaurant search (the same ranking as `GET /v1/food`).
- `find_places`: anime and figure, toy and game, hobby and model, and 100-yen shops near a place.
- `find_nearby`: hospitals, clinics, pharmacies, police boxes, ATMs, toilets and convenience stores from our snapshot, or live Photon search for anything else.
- `show_diet_card`: the hand-checked cards from `backend/rulebook/phrases.json`, picked from the traveller's saved diet.
- `show_phrase_card`: other phrases ("Where is the toilet?"), translated by the model and checked to be in Japanese script. A phrase that states a diet ("I am Jain…", "no onion, please") is refused, and the model is told to use `show_diet_card` instead.

With `"language": "hi"` the companion answers in Hindi, and the tools hand it our own Hindi: the rulebook's Hindi tips (`rulebook/etiquette.hi.json`), each diet card's Hindi meaning (`rulebook/phrases.hi.json`, in the card's `english` field) and Hindi Desi Match reasons. Station, line, place and restaurant names stay in English letters, as they appear on signs.

### Request

```json
{
  "messages": [
    { "role": "user", "text": "How do we get from Kyoto Station to Nara Park tomorrow at 9am?" }
  ],
  "context": {
    "city": "Kyoto",
    "day_index": 5,
    "stops": [ { "place_name": "Fushimi Inari Taisha", "place_type": "shrine", "time_slot": "morning" } ],
    "profile": { "...": "the same object as in /v1/plan/suggestions" },
    "location": { "lat": 34.985, "lng": 135.758 }
  },
  "language": "en"
}
```

- `messages`: 1–20 messages, each with `role` set to `user` or `assistant` and `text` up to 2000 bytes. The last message must be from the user. Send the reply texts back as `assistant` messages; phrase cards aren't part of the history.
- `context` and all its fields are optional, but the answers are much better with them. `profile` is what makes `show_diet_card` work. `location` is the traveller's current GPS fix (if granted); points outside Japan are ignored.
- `language`: optional, `en` (the default) or `hi`.

### Response: 200 OK

```json
{
  "reply": {
    "text": "Show this card to the staff. Tap it to make the Japanese bigger.",
    "phrases": [
      {
        "english": "I am Jain. I can't eat meat, fish, seafood, eggs or dashi (fish stock), or onion, garlic or root vegetables such as potato and carrot.",
        "japanese": "私はジャイナ教徒です。肉、魚、魚介類、卵、だしは食べられません。玉ねぎ、にんにく、じゃがいもやにんじんなどの根菜も食べられません。",
        "romaji": "Watashi wa Jaina-kyōto desu. ..."
      }
    ],
    "tools_used": ["show_diet_card"]
  }
}
```

- `text` is plain text: no markdown.
- `phrases` holds 0–3 cards to show large to staff.
- `tools_used` tells the app where the answer came from, so it can show "Live train times" or "From our etiquette rulebook".
- A 503 means the chat is busy (try again in a minute), out of today's free quota, or not configured. Show its `error` message to the user.

## GET /v1/food

Finds restaurants near a named place, or a point, and ranks them with **Desi Match**: how well each one suits the traveller's diet, from 1 to 5, with the reasons.

The data is a snapshot of OpenStreetMap (`backend/osmdata/places.json`), loaded by `make db/seed`. It covers Tokyo, Kyoto, Osaka, Nara and Sapporo, and only includes places with diet tags or South Asian and curry cuisines. Searches are fast and don't depend on any external service at request time. The one exception is turning a `near` name into a location, which uses Transitous.

```
GET /v1/food?near=Senso-ji&city=Tokyo&diet=veg&avoid=onion_garlic&spice=indian_spicy
GET /v1/food?lat=35.7148&lng=139.7967&diet=halal
```

| Query parameter | Required | Notes |
|---|---|---|
| `near` | `near`, or `lat` and `lng` | A station or landmark name |
| `city` | no | Helps `near` find the right place, e.g. `Tokyo` |
| `lat`, `lng` | see `near` | A point in Japan |
| `diet` | no (default `non_veg`) | One of the [diets](#permitted-values) |
| `avoid` | no | Comma separated, e.g. `pork,beef` |
| `spice` | no (default `medium`) | One of the spice levels |
| `language` | no (default `en`) | `en` or `hi`: the language of each result's `reasons` and `caution` |
| `radius` | no (default 1000) | Metres, 100–3000 |

The brief has the profile coming from the auth token. There's no auth yet, so the app sends `diet`, `avoid` and `spice` instead.

### Response: 200 OK

```json
{
  "food": {
    "near": { "name": "Sensō-ji", "lat": 35.7148, "lng": 139.7967 },
    "results": [
      {
        "id": "node/123456",
        "city": "Tokyo",
        "name": "サウスパーク",
        "name_en": "South Park - Taste of Kerala",
        "lat": 35.71,
        "lng": 139.79,
        "tags": { "amenity": "restaurant", "cuisine": "indian", "opening_hours": "11:00-22:00" },
        "distance_m": 420,
        "desi_match": {
          "score": 4,
          "reasons": ["Indian or Nepali food"],
          "caution": "Ask for Jain food: no onion, garlic or root vegetables. Show your diet card."
        }
      }
    ],
    "attribution": "Map data © OpenStreetMap contributors"
  }
}
```

- `results` has up to 10 places, best `score` first, then nearest. Places scoring 1 (a poor fit, e.g. a ramen shop for a vegetarian) are left out.
- Show `name_en` first if present, with `name` (the Japanese sign) next to it.
- `caution` warns about things to ask, such as dashi or onion and garlic. Always show it.
- `opening_hours` comes from OpenStreetMap and may be out of date.
- **You must show `attribution` wherever these results appear.** OpenStreetMap's licence requires it.
- A 422 means a bad parameter, or that `near` couldn't be found. A 503 means place search is temporarily down.

## POST /v1/menu/translate

Translates the text the app read from a menu photo. The app reads the menu on the phone (ML Kit text recognition) and sends only the lines of text, never the photo. The backend asks the LLM (Groq) to translate the whole menu in one go, so it has context: 親子丼 becomes "Oyakodon" rather than "parent and child", and it can tell when text recognition misread a word (響油 for 醤油).

The model only translates and explains. The app's diet warnings come from its own word list, matched against both the recognised Japanese and the model's corrected reading, so they can be added this way but never removed.

### Request

```json
{
  "lines": ["お品書き", "響油ラーメン ¥850", "親子井 ¥950"],
  "language": "en"
}
```

- `lines`: 1–60 lines, in reading order, each up to 200 bytes.
- `language`: optional, `en` (the default) or `hi`. In Hindi, `text` and `note` are in Hindi, with dish names in English letters.

### Response: 200 OK

```json
{
  "menu": {
    "lines": [
      { "japanese": "お品書き", "text": "Menu", "note": "" },
      { "japanese": "醤油ラーメン ¥850", "text": "Shoyu ramen ¥850", "note": "ramen in a soy sauce broth" },
      { "japanese": "親子丼 ¥950", "text": "Oyakodon ¥950", "note": "chicken and egg simmered with onion, on rice" }
    ]
  }
}
```

There is exactly one entry per line sent, in the same order. `japanese` is the model's best reading of the line, `text` the translation, and `note` what a dish is (empty for anything else).

### Errors

- `422` for invalid input.
- `503` if no Groq key is configured, the model is busy, or its reply isn't a valid translation (wrong number of lines, for example). The app then translates on the phone with ML Kit instead.

## GET /v1/directions

Finds up to three ways to get from one place to another by train, subway, bus and on foot, with walking directions to the station, exit numbers, line colours, and coordinates of each leg for the map.

```
GET /v1/directions?from=Shinjuku&to=Senso-ji&city=Tokyo&depart=09:00
GET /v1/directions?from_lat=35.68&from_lng=139.71&to=Senso-ji&city=Tokyo
```

| Query parameter | Required | Notes |
|---|---|---|
| `from`, `to` | `from` or `from_lat`+`from_lng`, and `to` or `to_lat`+`to_lng` | Station, sight or landmark name |
| `from_lat`, `from_lng` | see `from` | Starting point coordinates (must be in Japan) |
| `to_lat`, `to_lng` | see `to` | Destination coordinates (must be in Japan) |
| `city` | no | City context for geocoding (e.g. `Tokyo`) |
| `depart` | no | Departure time in 24h format (`HH:MM`, Japan time) |

### Response: 200 OK

```json
{
  "directions": {
    "from": { "name": "Shinjuku", "lat": 35.6899, "lng": 139.6997 },
    "journeys": [
      {
        "minutes": 32,
        "transfers": 1,
        "walk_minutes": 8,
        "departs": "09:04",
        "arrives": "09:36",
        "legs": [
          {
            "mode": "transit",
            "line": "Marunouchi Line",
            "operator": "Tokyo Metro",
            "color": "#F62E36",
            "line_code": "M",
            "towards": "Ikebukuro",
            "from": { "name": "Shinjuku", "name_ja": "新宿", "code": "M08", "lat": 35.69, "lng": 139.70 },
            "to": { "name": "Ginza", "name_ja": "銀座", "code": "M16", "lat": 35.67, "lng": 139.76 },
            "distance_m": 6200,
            "minutes": 16,
            "exit": "A1",
            "platform": "2",
            "via": [],
            "path": [[35.69, 139.70], [35.67, 139.76]]
          }
        ]
      }
    ],
    "attribution": "Routes: Transitous and the transit operators. Map data © OpenStreetMap contributors"
  }
}
```

## GET /v1/places

Finds essential places nearby (hospitals, clinics, pharmacies, police boxes, ATMs, toilets, convenience stores) from our curated snapshot, or searches for other venues via Photon.

```
GET /v1/places?kind=hospital&near=Shinjuku&city=Tokyo
GET /v1/places?kind=pharmacy&lat=35.6895&lng=139.6917
GET /v1/places?q=supermarket&near=Kyoto+Station&city=Kyoto
```

| Query parameter | Required | Notes |
|---|---|---|
| `kind` | `kind` or `q` | One of: `hospital`, `clinic`, `pharmacy`, `police`, `atm`, `toilet`, `convenience_store` |
| `q` | `kind` or `q` | Free-text search term for anything else (via Photon) |
| `near` | `near`, or `lat`+`lng` | Landmark or station name |
| `lat`, `lng` | `near`, or `lat`+`lng` | Point in Japan |
| `city` | no | City context for geocoding |
| `radius` | no (default 1000) | Metres, 100–3000 |

### Response: 200 OK

```json
{
  "places": {
    "near": { "name": "Shinjuku", "lat": 35.6895, "lng": 139.6917 },
    "results": [
      {
        "name": "Keio University Hospital",
        "name_ja": "慶應義塾大学病院",
        "kind": "hospital",
        "detail": "Emergency services available",
        "lat": 35.6835,
        "lng": 139.7171,
        "distance_m": 850,
        "phone": "+81-3-3353-1211"
      }
    ],
    "attribution": "Map data © OpenStreetMap contributors"
  }
}
```

## GET /v1/places/suggest

Provides search-as-you-type completions for place and station names on the directions and map screens. Returns stations instantly from our snapshot, followed by Photon matching landmarks.

```
GET /v1/places/suggest?q=shinj&city=Tokyo
```

| Query parameter | Required | Notes |
|---|---|---|
| `q` | yes | Partial place name (at least 2 characters) |
| `city` | no | City context |
| `lat`, `lng` | no | Optional user coordinates to prioritize closest matches |

### Response: 200 OK

```json
{
  "suggestions": [
    {
      "name": "Shinjuku",
      "name_ja": "新宿",
      "kind": "station",
      "detail": "Tokyo",
      "lat": 35.69026,
      "lng": 139.70051
    }
  ]
}
```

## Permitted values

These exact strings are the contract. They are defined in `backend/internal/data/profiles.go`, `etiquette.go` and `plans.go`.

| Field | Values |
|---|---|
| `interests` | `temples_shrines`, `anime`, `food`, `nature`, `shopping`, `snow`, `nightlife`, `history` |
| `budget_band` | `1-2L`, `2-3L`, `3L+` (₹ per person in-Japan ground spend, excluding international flights) |
| `pace` | `relaxed`, `balanced`, `packed` |
| `group_type` | `solo`, `couple`, `friends`, `family_kids`, `family_elders` |
| `diet` | `veg`, `jain`, `eggetarian`, `halal`, `non_veg` |
| `avoid` | `beef`, `pork`, `seafood`, `egg`, `onion_garlic` |
| `spice_level` | `mild`, `medium`, `indian_spicy` |
| `japanese_level` | `none`, `basic` |
| `place_type` | `temple`, `shrine`, `onsen`, `ryokan`, `restaurant`, `train`, `taxi`, `shop`, `street`, `park`, `attraction` |
| `kind` | `place`, `activity`, `food` |
| `severity` | `important`, `helpful` |
| `time_slot` | `morning`, `lunch`, `afternoon`, `evening`, `dinner` |
| `language` | `en`, `hi` |
| `help_kind` | `hospital`, `clinic`, `pharmacy`, `police`, `atm`, `toilet`, `convenience_store` |

The app should send these as enums. When **reading** `place_type`, treat it as a plain string, so that a new place type added on the backend doesn't crash older app builds.

## Running the backend for app development

```sh
cd backend
make run/api port=4001
```

Then, with the emulator running or a phone connected over USB:

```sh
adb reverse tcp:4001 tcp:4001
```

Debug builds use `http://localhost:4001/` (set in `android/gradle.properties`). `adb reverse` forwards that from the device to your Mac. Run it again each time the emulator restarts or the phone is reconnected.

Why not the emulator's usual `10.0.2.2` alias? Android 17 (API 37, our target SDK) blocks apps from reaching local-network addresses unless they hold the `ACCESS_LOCAL_NETWORK` runtime permission. The connection just times out. Loopback (`localhost`) isn't restricted. Release builds call a public HTTPS server, so they aren't affected.

Debug builds allow plain HTTP to `localhost`, `127.0.0.1` and `10.0.2.2` only. Release builds require HTTPS.

## Not built yet

Everything in the project brief's endpoint list now exists. Auth (Firebase ID tokens) isn't built yet.
