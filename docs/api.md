# Konnichi-Wah! API (v1)

This is the contract between the Android app and the Go backend in `backend/`. If you change a request or response shape, update this file in the same commit.

- [Basics](#basics)
- [Errors](#errors)
- [GET /v1/healthcheck](#get-v1healthcheck)
- [GET /v1/etiquette](#get-v1etiquette)
- [POST /v1/plan/suggestions](#post-v1plansuggestions)
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
| 422 | Validation failed | object of field → message |
| 429 | Rate limited | string: `"rate limit exceeded"` |
| 500 | A bug on our side | string (generic; the details are only in the server log) |

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
      { "name": "Tokyo", "days": 4, "reason": "..." },
      { "name": "Kyoto", "days": 3, "reason": "..." }
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

## Permitted values

These exact strings are the contract. They are defined in `backend/internal/data/profiles.go`, `etiquette.go` and `plans.go`.

| Field | Values |
|---|---|
| `interests` | `temples_shrines`, `anime`, `food`, `nature`, `shopping`, `snow`, `nightlife`, `history` |
| `budget_band` | `1-2L`, `2-3L`, `3L+` (₹ per person) |
| `pace` | `relaxed`, `balanced`, `packed` |
| `group_type` | `solo`, `couple`, `friends`, `family_kids`, `family_elders` |
| `diet` | `veg`, `jain`, `eggetarian`, `halal`, `non_veg` |
| `avoid` | `beef`, `pork`, `seafood`, `egg`, `onion_garlic` |
| `spice_level` | `mild`, `medium`, `indian_spicy` |
| `japanese_level` | `none`, `basic` |
| `place_type` | `temple`, `shrine`, `onsen`, `ryokan`, `restaurant`, `train`, `taxi`, `shop`, `street`, `park`, `attraction` |
| `kind` | `place`, `activity`, `food` |
| `severity` | `important`, `helpful` |

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

These endpoints are in the project brief but don't exist yet, so their shapes may change:

- `POST /v1/itineraries`, `GET /v1/itineraries/:id`, `PATCH /v1/itineraries/:id`
- `GET /v1/food?lat=&lng=`
- `POST /v1/companion/chat`
