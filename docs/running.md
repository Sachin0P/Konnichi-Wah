# Running Konnichi-Wah!

Step-by-step instructions for every way of running the app: on the emulator, on a phone over USB, and on a phone anywhere through a tunnel, with the AI either in the cloud (Gemini and Groq) or on the Mac (Gemma). The commands assume the repository is at `~/japan-hackathon` on a Mac.

**Contents**
1. [What runs where](#1-what-runs-where)
2. [One-time setup](#2-one-time-setup)
3. [Start the backend](#3-start-the-backend)
4. [Run the app](#4-run-the-app)
5. [Stop everything](#5-stop-everything)
6. [Troubleshooting](#6-troubleshooting)
7. [Command cheat sheet](#7-command-cheat-sheet)

## 1. What runs where

```
 Phone / emulator                 Mac
┌──────────────┐   HTTP    ┌──────────────────────┐      ┌──────────────────────────┐
│ Android app  │ ────────► │ Go backend :4001     │ ───► │ PostgreSQL (trips, rules)│
│ (no AI keys) │           │ (backend/bin/api)    │ ───► │ AI: Gemini + Groq (cloud)│
└──────────────┘           └──────────────────────┘      │   or Ollama + Gemma (Mac)│
                                                         │ Transitous, Photon (web) │
                                                         └──────────────────────────┘
```

- **The app** never holds an API key and never calls an AI service directly. Everything goes through the backend.
- **The backend** always runs on port **4001**. Port 4000 belongs to another project on this Mac; don't stop whatever is using it.
- **The AI** runs in one of two modes, chosen when the backend starts:

  | | Local mode (Default, `-llm=local`) | Cloud mode (`-llm=cloud`) |
  |---|---|---|
  | Planner | Gemma 4 12B on the Mac | Gemini |
  | Chat, menu translation | Gemma 4 12B on the Mac | Groq |
  | Needs | Ollama running (`ollama serve &`) | API keys in `backend/.envrc`, internet |
  | Speed | 5–10 s per answer, about 25 s for a plan | 1–3 s per answer |
  | Limits | None (100% private, free, unlimited) | Free-tier quotas (Gemini: 20 plans a day per model; Groq says "busy" after many quick chats) |

- **How the phone reaches the Mac** depends on how you run it:

  | Setup | How the app reaches the backend | When to use it |
  |---|---|---|
  | Emulator | `adb reverse`, app calls `http://localhost:4001/` | Development |
  | Phone on USB | `adb reverse`, the same as the emulator | Quick testing on a real phone |
  | Phone anywhere | A public HTTPS tunnel; the app is built with the tunnel's address | Demos, testing outdoors on mobile data |

## 2. One-time setup

Skip anything already done on your Mac.

### 2.1 Tools

```sh
brew install go postgresql@17 golang-migrate
brew services start postgresql@17
```

Install **Android Studio**. It brings the Android SDK, `adb` and the Java runtime used for builds. Then add `adb` to your path, for example in `~/.zshrc`:

```sh
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
```

For a local AI and for the tunnel (both optional):

```sh
brew install ollama cloudflared
ollama pull gemma4:12b        # about 7.7 GB, downloaded once
```

### 2.2 Database

```sh
psql -d postgres <<'SQL'
CREATE DATABASE konnichiwah;
CREATE ROLE konnichiwah WITH LOGIN PASSWORD 'choose-a-password';
ALTER DATABASE konnichiwah OWNER TO konnichiwah;
SQL
```

### 2.3 Backend settings and keys

```sh
cd ~/japan-hackathon/backend
cp .envrc.example .envrc
```

Open `.envrc` and fill in:
- `KONNICHIWAH_DB_DSN`, with the password from 2.2: `postgres://konnichiwah:choose-a-password@localhost/konnichiwah?sslmode=disable`
- `GEMINI_API_KEY`, from <https://aistudio.google.com> (free), for the cloud planner.
- `GROQ_API_KEY`, from <https://console.groq.com> (free), for the cloud chat and menu translation.

The keys are optional for local mode. `.envrc` is gitignored: **never commit it, and never paste keys into the Android code, a chat or a screenshot.**

### 2.4 Create the tables and load the content

```sh
cd ~/japan-hackathon/backend
source .envrc
make db/migrations/up     # answer y
make db/seed              # the etiquette rulebook and the OpenStreetMap places
```

Run `make db/seed` again whenever anything in `backend/rulebook/` or `backend/osmdata/` changes, for example after a `git pull`.

### 2.5 Phone: switch on USB debugging

Only needed for a real phone, and only once:
1. **Settings → About phone**, then tap **Build number** seven times.
2. **Settings → System → Developer options**, then turn on **USB debugging**.
3. Plug the phone in and accept **Allow USB debugging?** on the phone.
4. Check it's connected: `adb devices` should list it as `device`, not `unauthorized`.

## 3. Start the backend

Use a terminal you can leave open. First build it; this takes a few seconds, and is needed after every `git pull`:

```sh
cd ~/japan-hackathon/backend
source .envrc
go build -o bin/api ./cmd/api
```

### 3.1 Local mode (Gemma on the Mac — Default)

1. In a separate terminal or background, start Ollama and pull the model:
   ```sh
   ollama serve &
   ollama pull gemma4:12b
   ```
2. Start the backend with local LLM (default):
   ```sh
   ./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -llm=local -local-llm-model=gemma4:12b
   ```
   *Or simply: `make run/api port=4001`*

The first request loads Gemma into memory (takes 10–20 seconds); after that answers take 5–10 seconds.

### 3.2 Cloud mode (Gemini and Groq — Optional)

```sh
./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -llm=cloud \
  -gemini-api-key="$GEMINI_API_KEY" -groq-api-key="$GROQ_API_KEY"
```
*Or simply: `make run/api/cloud port=4001`*

### 3.3 Check it's working

```sh
curl localhost:4001/v1/healthcheck
```

You should see `"status": "available"`. The log's first lines say which AI it's using: `using gemini planner` and `using groq companion…`, or `using local llm`.

### 3.4 Switching modes

Stop the running backend with **Ctrl+C** in its terminal, wait a second until it prints `stopped server`, then start it again with the other command. Starting the new one too early fails with `address already in use`.

## 4. Run the app

### 4.1 On the emulator

1. In Android Studio, **File → Open** and choose the **`android/`** folder, not the repository root. With the root open, the Run button stays greyed out.
2. Start the emulator (**Device Manager → Medium_Phone_API_37.0 → ▶**), or from a terminal: `~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 &`
3. Forward the port, again each time the emulator restarts:
   ```sh
   adb reverse tcp:4001 tcp:4001
   ```
4. Press **Run ▶**.

**Testing "My location" on the emulator.** The emulator's own location controls don't always reach the app. Set a fake location this way instead; this example uses Shinjuku Gyoen, Tokyo:

```sh
adb shell appops set com.android.shell android:mock_location allow
for p in gps fused network; do
  adb shell cmd location providers add-test-provider $p
  adb shell cmd location providers set-test-provider-enabled $p true
  adb shell cmd location providers set-test-provider-location $p --location 35.6852,139.7100
done
```

### 4.2 On a phone over USB

1. Plug the phone in. `adb devices` should list it.
2. Forward the port, again after every reconnect:
   ```sh
   adb reverse tcp:4001 tcp:4001
   ```
3. Install from Android Studio (choose the phone in the device menu, then **Run ▶**), or from a terminal:
   ```sh
   cd ~/japan-hackathon/android
   ./gradlew installDebug
   ```

The phone must stay plugged in, because the app reaches the backend through the cable.

### 4.3 On a phone anywhere (tunnel)

The Mac stays the server. A tunnel gives the backend a public HTTPS address, so the phone can use any Wi-Fi or mobile data, unplugged.

1. Start the backend (section 3) in either mode.
2. In another terminal, start a tunnel and leave it running:
   ```sh
   cloudflared tunnel --no-autoupdate --url http://localhost:4001
   ```
   After a few seconds it prints an address such as `https://camel-madonna-companies-prague.trycloudflare.com`. Check it from the Mac:
   ```sh
   curl https://YOUR-ADDRESS.trycloudflare.com/v1/healthcheck
   ```
3. Build the app with that address. The slash at the end matters:
   ```sh
   cd ~/japan-hackathon/android
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew assembleDebug -PapiBaseUrl=https://YOUR-ADDRESS.trycloudflare.com/
   ```
4. Install it. Plug the phone in once:
   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Or copy `app-debug.apk` to the phone some other way, such as Google Drive, and open it there. Android asks you to allow installs from that app.
5. Unplug. The app now works anywhere, as long as the Mac is awake and the backend and the tunnel are running. To keep the Mac awake with the lid open, run `caffeinate -d` in a spare terminal.

**Things to know about quick tunnels**
- **The address changes every time `cloudflared` restarts.** Repeat steps 3 and 4 after each restart. For an address that stays the same:
  - **ngrok's free account** includes one permanent address (`something.ngrok-free.app`). Install it with `brew install ngrok`, sign up, run `ngrok config add-authtoken …`, then `ngrok http --url=YOUR-NAME.ngrok-free.app 4001`.
  - **Cloudflare with your own domain:** add the domain to a Cloudflare account, then create a named tunnel (`cloudflared tunnel login`, `cloudflared tunnel create konnichiwah`, `cloudflared tunnel route dns konnichiwah api.yourdomain.com`, then `cloudflared tunnel run --url http://localhost:4001 konnichiwah`). A Cloudflare account without a domain can't give you a fixed address.
- **Anyone who has the address can use the backend.** The app has no sign-in yet. Don't post the address publicly, and stop the tunnel after the demo.
- **Cloudflare gives up on a request after 100 seconds.** In local mode, a long trip plan on a busy Mac can come close. If planning fails through the tunnel, try a shorter trip, or use cloud mode for the demo.
- **All requests reach the backend from the tunnel, on the same Mac.** So the rate limit is shared by everyone using the tunnel. That's fine for a demo with a few phones.

### 4.4 A real deployment

A public server can't run Gemma cheaply: it needs about 10 GB of memory, and a GPU to be quick. A real deployment would host the backend in cloud mode, on a service such as Fly.io or Render, with a hosted PostgreSQL such as Neon, and the keys set as the service's environment variables. Release builds read their server address from `-PreleaseApiBaseUrl=https://…` and allow HTTPS only. Not set up yet.

## 5. Stop everything

| What | How |
|---|---|
| Backend | Ctrl+C in its terminal, or `pkill -f 'bin/api -port=4001'` |
| Tunnel | Ctrl+C in its terminal, or `pkill cloudflared` |
| Gemma (frees about 10 GB of memory) | `ollama stop gemma4:12b`; Ollama itself: Ctrl+C in its terminal, or `pkill -f 'ollama serve'` |
| Emulator | Close its window, or `adb emu kill` |

Don't stop whatever is on port 4000; it belongs to another project.

## 6. Troubleshooting

| What you see | What to do |
|---|---|
| App: "Can't reach the server" | Is the backend running (`curl localhost:4001/v1/healthcheck`)? On USB or the emulator, run `adb reverse tcp:4001 tcp:4001` again. On a tunnel, check the tunnel address with `curl`, and that the app was built with the current address. |
| Backend: `address already in use` | The old backend is still stopping. Wait a second and try again. If it persists, see what's on the port with `lsof -nP -iTCP:4001 -sTCP:LISTEN`. |
| Backend: `pq: password authentication failed` | The password in `KONNICHIWAH_DB_DSN` doesn't match the database role. Did you run `source .envrc`? |
| Chat: "The assistant is busy" (cloud mode) | Groq's free tier is rate-limited. Wait a minute. |
| Planner returns the same generic plan | The AI failed and the catalogue fallback was used. The backend log says why: a Gemini quota, a missing key, or Ollama not running. |
| Local mode: every AI request fails | Is `ollama serve` running? Check with `curl localhost:11434/api/tags`. Is the model downloaded (`ollama list`)? |
| Local mode: very slow, Mac freezing | Memory is full. Close Android Studio and the emulator, or use cloud mode. |
| `adb devices` shows `unauthorized` | Unlock the phone and accept the USB debugging prompt. If no prompt appears, revoke USB debugging authorisations in Developer options and reconnect. |
| Install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An APK built on another computer is installed. Uninstall the app from the phone first; this deletes its saved trip. |
| Gradle build fails within a second | Run it again; the first build after an edit sometimes fails like this. For `jlink ... does not exist`, run `./gradlew --stop` and build with `JAVA_HOME` set as in 4.3. |
| Directions: "No route found" | Transitous has gaps, especially Kyoto city buses. Try a nearby station as the start or destination. |
| "My location" doesn't work on the emulator | Use the mock-location commands in 4.1. |
| Nearby search says nothing found | The live search (Photon) sometimes fails behind a VPN. The category buttons (hospital, pharmacy, and so on) use our own snapshot and always work. |

## 7. Command cheat sheet

```sh
# Backend, cloud mode
cd ~/japan-hackathon/backend && source .envrc && go build -o bin/api ./cmd/api
./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -gemini-api-key="$GEMINI_API_KEY" -groq-api-key="$GROQ_API_KEY"

# Backend, local mode (start `ollama serve` first)
./bin/api -port=4001 -db-dsn="$KONNICHIWAH_DB_DSN" -llm=local

# Emulator or phone on USB
adb reverse tcp:4001 tcp:4001

# Phone anywhere
cloudflared tunnel --no-autoupdate --url http://localhost:4001
cd ~/japan-hackathon/android && ./gradlew assembleDebug -PapiBaseUrl=https://YOUR-ADDRESS.trycloudflare.com/
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Tests
cd ~/japan-hackathon/backend && go test ./...
cd ~/japan-hackathon/android && ./gradlew testDebugUnitTest

# After changing rulebook/ or osmdata/
cd ~/japan-hackathon/backend && make db/seed
```
