# KE Android Player (Unofficial)

Android TV app for Karaoke Eternal - native player replacing Kodi addon.

## Quick Reference

### Build & Deploy
```bash
# Build
./gradlew assembleDebug

# Deploy to TV box via ADB
adb connect 192.168.1.86:5555
adb -s 192.168.1.86:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.1.86:5555 shell am start -n com.thecrazylegs.keplayer/.MainActivity

# Logs (PowerShell)
adb -s 192.168.1.86:5555 logcat -d | findstr "VideoPlayer PlayerViewModel SocketManager"
```

### Key Dependencies
- **Media3/ExoPlayer** 1.3.1 - Video playback
- **Socket.io** 2.1.0 - Real-time events
- **Retrofit** 2.11.0 - REST API
- **Jetpack Compose** - UI
- **Material Icons Extended** - Menu icons (BugReport, Tv, Logout, Link, Login, Refresh, Settings)
- **Coil** 2.6.0 - Image loading (avatars)
- **ZXing** 3.5.3 - QR code generation (auto-pairing)
- **jmDNS** 3.5.9 - mDNS server discovery
- **EncryptedSharedPreferences** - Token storage

---

## Architecture

```
app/src/main/java/com/thecrazylegs/keplayer/
├── data/
│   ├── api/
│   │   ├── ApiClient.kt      # Retrofit setup
│   │   └── KaraokeApi.kt     # REST endpoints (login)
│   ├── model/
│   │   ├── ApiModels.kt      # LoginRequest, UserResponse
│   │   └── PlayerModels.kt   # PlayerStatus, QueueItem, QueueState
│   ├── socket/
│   │   └── SocketManager.kt  # Socket.io client + SocketEvent model
│   └── storage/
│       └── TokenStorage.kt   # EncryptedSharedPreferences
├── ui/
│   ├── login/
│   │   ├── LoginScreen.kt    # Login form UI (TV-friendly)
│   │   └── LoginViewModel.kt # Login logic
│   ├── welcome/
│   │   ├── WelcomeScreen.kt    # Welcome screen (mDNS + pairing code + QR)
│   │   ├── WelcomeViewModel.kt # jmDNS discovery + pairing polling
│   │   └── NsdDiscoveryManager.kt # jmDNS wrapper
│   └── player/
│       ├── PlayerScreen.kt   # Main player + debug panel (accent pink) + transitions
│       ├── PlayerViewModel.kt # Socket events, commands (play/pause/next/volume/replay)
│       ├── SideMenu.kt       # Side menu (Material Icons, AccentPink theme)
│       ├── VideoPlayer.kt    # ExoPlayer composable + position + volume control
│       └── WaitingScreen.kt  # Waiting screen between songs (like Kodi)
└── MainActivity.kt           # Navigation host
```

---

## Authentication Flow

1. **Login**: `POST /api/login` with `{username, password, roomId}`
2. **Token**: Extract from `Set-Cookie: keToken=xxx` header
3. **Socket.io**: Send token via `extraHeaders: Cookie: keToken=$token`
4. **Media streaming**: Send token via `Cookie` header in ExoPlayer

**Important**: Uses standard browser auth (cookie), NOT Kodi addon auth (`auth.token`).

---

## Socket.io Events

### Emitted (to server)
| Event | Type | Payload | Notes |
|-------|------|---------|-------|
| `action` | `server/PLAYER_EMIT_STATUS` | See below | Every 1s - **Required to be recognized as player** |
| `action` | `server/PLAYER_EMIT_LEAVE` | - | On disconnect |

### PLAYER_EMIT_STATUS Payload (CRITICAL)
```json
{
  "queueId": 42,                    // Current queueId being played (or null)
  "isPlaying": true,                // Is media playing?
  "position": 123.45,               // Position in seconds
  "isAtQueueEnd": false,            // True if queue exhausted
  "mediaType": "mp4",               // "mp4" or "cdg"
  "volume": 100,                    // 0-100
  "historyJSON": "[42, 15]",        // STRING JSON - queueIds already played
  "nextUserId": 5                   // userId of next singer (locks position)
}
```

**IMPORTANT - historyJSON format:**
- Must be a **STRING** (JSON serialized array): `"[42, 15, 38]"`
- NOT a JavaScript/JSON array: `[42, 15, 38]` ❌
- The server relays this as-is to all clients
- Clients use this to recalculate queue order (round-robin)

### Received (from server)
| Event | Type | Payload |
|-------|------|---------|
| `action` | `queue/PUSH` | `{result: [ids], entities: {id: QueueItem}}` |
| `action` | `status/PLAYER_STATUS` | `{isPlaying, position, queueId, ...}` (echo - ignored) |
| `action` | `player/CMD_PLAY` | - |
| `action` | `player/CMD_PAUSE` | - |
| `action` | `player/CMD_NEXT` | - |
| `action` | `player/CMD_VOLUME` | `payload: float` (0-1, from web volume slider) |
| `action` | `player/CMD_REPLAY` | `payload: int` (queueId to replay) |

### QueueItem Fields
```kotlin
queueId, userId, userDisplayName, title, artist, mediaId, mediaType, coSingers
```

---

## Key Patterns

### ExoPlayer with Auth Cookie
```kotlin
val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
    setDefaultRequestProperties(mapOf("Cookie" to "keToken=$token"))
}
ExoPlayer.Builder(context)
    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
    .build()
```

### Media URL Format
```
http://{serverUrl}/api/media/{mediaId}?type=video
```
**Note**: Uses `?type=video` for MP4 files (requires admin user).

### Position Tracking from ExoPlayer
```kotlin
// VideoPlayer.kt - reports position every 250ms
LaunchedEffect(exoPlayer) {
    while (isActive) {
        if (exoPlayer.playbackState == Player.STATE_READY && exoPlayer.isPlaying) {
            val positionSeconds = exoPlayer.currentPosition / 1000.0
            onPositionChange(positionSeconds)
        }
        delay(250)
    }
}
```

### Event Parsing (case-insensitive)
```kotlin
val type = json.optString("type", "").lowercase()
when {
    type == CMD_PLAY -> handlePlay()
    type == CMD_PAUSE -> handlePause()
    type == CMD_NEXT -> handleNext()
    type == CMD_VOLUME -> handleVolume(json.optDouble("payload", 1.0).toFloat())
    type == CMD_REPLAY -> handleReplay(json.optInt("payload", -1))
    type.contains("queue/push") -> updateQueue(...)
    type.contains("player_status") -> // Ignored (echo)
}
```

### Chrome-style Event Grouping
```kotlin
// SocketEvent.kt - groups identical consecutive events with counter
data class SocketEvent(
    val type: String,
    val data: String,
    val timestamp: Long,
    val count: Int = 1  // Incremented for repeated events
) {
    fun isSameAs(other: SocketEvent): Boolean { ... }
}
```

---

## TV Remote / D-pad Navigation

### Side Menu (D-pad LEFT)
- Slides in from left with scrim overlay
- Material Icons per item (BugReport/Tv, Logout, Link, Login, Refresh, Settings)
- Focus: AccentPink border + Yellow text/icon
- Dismiss: D-pad RIGHT or BACK
- Both PlayerScreen and WelcomeScreen have their own side menu

### UI Theme
- **AccentPink**: `#FD80D8` - brand color for borders, badges, headers
- **Focus**: Yellow text + AccentPink border on D-pad focus
- **Debug panel**: AccentPink section headers, colored left borders per event type, focusable items for D-pad scroll

### Transitions
- **WaitingScreen**: `AnimatedVisibility` with `fadeIn(500ms)` / `fadeOut(400ms)`
- **SideMenu**: `slideInHorizontally(300ms)` + scrim `fadeIn(300ms)`

---

## Waiting Screen

Between songs (or when queue is empty), displays a waiting screen similar to Kodi addon:
- Background image (`waiting_screen.png` from drawable)
- Bottom banner with:
  - User avatar (loaded via Coil from `/api/user/{userId}/image`)
  - "Up next:" label
  - Singer name (UPPERCASE) + co-singers
  - Song title - ARTIST
  - Queue remaining counter
- Empty queue shows centered "WAITING FOR A SINGER..." message

**Flow (like Kodi addon):**
1. NEXT command → stops playback, stores `pendingItem`, shows waiting screen
2. PLAY command → plays `pendingItem` if exists
3. Video ends → shows waiting screen with next item info

---

## Debug Mode

Toggle "Debug/Player" button shows (default: Player view):
- Connection state (badge)
- Player state (playing, position, queueId)
- Current song info (title, artist, singer)
- Media URL
- ExoPlayer state (IDLE/LOADING/BUFFERING/READY/ENDED/ERROR)
- Volume percentage
- Socket events list (Chrome-style grouping with counters)

Logcat tags: `VideoPlayer`, `PlayerViewModel`, `SocketManager`

---

## Common Issues

| Issue | Solution |
|-------|----------|
| CLEARTEXT blocked | `android:usesCleartextTraffic="true"` in manifest |
| 401 on media | Check Cookie header in ExoPlayer DataSource |
| No events | Verify roomId in login, check Socket.io connection |
| Black screen | Check Logcat for ExoPlayer errors (VideoPlayer tag) |
| Wrong field names | Use `userDisplayName` not `visitorName` |
| "No player in room" | Must emit `PLAYER_EMIT_STATUS` every 1s |
| No audio on emulator | Known emulator issue - test on real device |
| Can't navigate with remote | Use TvButton with focusable() modifier |

---

## Server Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/login` | POST | Auth, returns user + Set-Cookie |
| `/api/media/{id}?type=video` | GET | Video stream (requires cookie + admin) |
| `/api/pair/code` | POST | Request pairing code (non-auth) |
| `/api/pair/confirm` | POST | Confirm pairing manually (auth) |
| `/api/pair/link/:code` | GET | QR auto-pairing (auth, returns HTML) |
| `/api/pair/status/:pairId` | GET | Poll pairing status (non-auth) |
| `/api/user/:userId/image` | GET | Avatar image (auth) |

---

## QR Code Auto-Pairing

The welcome screen displays a QR code that encodes `http://{serverUrl}/api/pair/link/{CODE}`.

**Flow**: scan QR → browser opens URL → if logged in, pairing confirmed automatically → TV receives token on next poll → connected.

The QR is generated using ZXing `QRCodeWriter` (white on transparent, 256x256 bitmap).

---

## Test Credentials (dev only)
Hardcoded in `LoginUiState` - remove before release.

---

## Round-Robin Queue Order Protocol

### How it works (Web Player Reference)

1. **Player emits status** with `historyJSON` (STRING of played queueIds)
2. **Server relays** to all clients as `PLAYER_STATUS`
3. **Clients recalculate** queue order using `getRoundRobinQueue` selector
4. **UI updates** to show reordered queue

### historyJSON Lifecycle

```
Initial:        historyJSON = "[]"
After song 42:  historyJSON = "[42]"
After song 15:  historyJSON = "[42, 15]"
```

### When to update historyJSON

| Event | Action |
|-------|--------|
| Song ends (`STATE_ENDED`) | Add currentQueueId to history |
| NEXT command received | Add currentQueueId to history |
| Replay command | Truncate history at replayed queueId |

### Client-side recalculation (getRoundRobinQueue)

1. Parse `historyJSON` → `[42, 15]`
2. Filter out queueIds no longer in queue
3. Add `currentQueueId` if not in history
4. Lock `nextUserId`'s first item (don't reorder)
5. Round-robin remaining items by "distance since last song"

### Result on Admin Interface

Before any songs:
```
[MUSE] → [Miley] → [Louane] → [Elton]  (DB order)
```

After MUSE (queueId=3) played, historyJSON="[3]":
```
[MUSE ✓] → [Miley] → [Louane] → [Elton]  (MUSE marked as played, stays first)
```

After Miley (queueId=5) played, historyJSON="[3, 5]":
```
[MUSE ✓] → [Miley ✓] → [Louane] → [Elton]  (both marked)
```

### Key Implementation Points

1. **historyJSON must be a STRING**: `"[3, 5]"` not `[3, 5]`
2. **Emit every 1 second** (throttled) via PLAYER_EMIT_STATUS
3. **Persist history** locally (SharedPreferences) for app restarts
4. **Clean history** when queue changes (remove deleted queueIds)
