# ChimeSDK KMP

A Kotlin Multiplatform library that integrates [Amazon Chime SDK](https://aws.amazon.com/chime/chime-sdk/) for Android and iOS using Compose Multiplatform. Includes a full-featured demo app (`App.kt`) that exercises the entire API.

---

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Project Structure](#project-structure)
- [Setup](#setup)
  - [Android](#android-setup)
  - [iOS](#ios-setup)
- [Architecture](#architecture)
  - [Common API (expect/actual)](#common-api)
  - [Android Implementation](#android-implementation)
  - [iOS Bridge](#ios-bridge)
- [API Reference](#api-reference)
  - [joinMeeting](#joinmeeting)
  - [Meeting Controls](#meeting-controls)
  - [Video Composables](#video-composables)
  - [Data Types](#data-types)
- [Demo App](#demo-app)
- [Usage Example](#usage-example)

---

## Features

| Feature | Android | iOS |
|---------|:-------:|:---:|
| Audio (mic + speaker) | ✅ | ✅ |
| Local video | ✅ | ✅ |
| Remote video | ✅ | ✅ |
| Camera switch (front/back) | ✅ | ✅ |
| Mute / unmute | ✅ | ✅ |
| Audio device selection (Bluetooth, earpiece, speaker) | ✅ | ✅ |
| Real-time chat (data messages) | ✅ | ✅ |
| Emoji reactions | ✅ | ✅ |
| Active speaker detection | ✅ | ✅ |
| Attendee presence events | ✅ | ✅ |
| Connection quality monitoring | ✅ | ✅ |
| Auto-reconnect handling | ✅ | ✅ |
| Video composables (Compose Multiplatform) | ✅ | ✅ |

---

## Requirements

| | Minimum |
|---|---|
| Android | API 24 (Android 7.0) |
| iOS | 16.0 |
| Kotlin | 2.3.20 |
| Compose Multiplatform | 1.10.3 |
| Amazon Chime SDK | 0.25.2 |

**Tooling:** Android Studio / IntelliJ IDEA with the Kotlin Multiplatform plugin. Xcode 15+ for iOS.

---

## Project Structure

```
ChimeSDK/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/wannaverse/chimesdk/
│       │   ├── ChimeSDK.kt              # All expect declarations
│       │   ├── App.kt                   # Demo UI (JoinScreen, InMeetingScreen)
│       │   ├── AppViewModel.kt          # ViewModel + CallState + RealTimeEventListener
│       │   ├── MeetingInformation.kt    # Meeting credentials data class
│       │   ├── AudioDevice.kt           # Audio device model
│       │   ├── CameraFacing.kt          # FRONT / BACK enum
│       │   ├── ConnectionStatus.kt      # Connection state enum
│       │   ├── RealTimeEventListener.kt # Attendee event callbacks interface
│       │   └── TextMessage.kt           # Chat / emoji / system message model
│       │
│       ├── androidMain/kotlin/com/wannaverse/chimesdk/
│       │   ├── ChimeSDK.android.kt      # Full Android implementation
│       │   ├── ChimeSDKContext.kt       # App context holder (set in MainActivity)
│       │   ├── ChimeLogger.kt           # Chime SDK logger adapter
│       │   ├── AudioVideoObserverImpl.kt
│       │   ├── DeviceObserver.kt
│       │   ├── RealTimeObserver.kt
│       │   ├── MeetingActiveSpeakerObserver.kt
│       │   ├── ChatObserver.kt
│       │   ├── VideoTileManager.kt
│       │   ├── AudioDeviceType.kt
│       │   ├── composables/
│       │   │   └── VideoTileView.kt     # AndroidView wrapping DefaultVideoRenderView
│       │   └── MainActivity.kt
│       │
│       └── iosMain/kotlin/com/wannaverse/chimesdk/
│           ├── ChimeSDK.ios.kt          # iOS actual implementations + bridge
│           └── MainViewController.kt
│
└── iosApp/
    ├── Podfile                          # AmazonChimeSDK CocoaPod
    └── iosApp/
        ├── iOSApp.swift                 # App entry — calls ChimeSdkSetup.configure()
        ├── ContentView.swift            # Hosts Compose via MainViewController
        ├── ChimeSdkSetup.swift          # Registers Swift↔Kotlin function bridges
        ├── ChimeMeeting.swift           # Full native Chime SDK implementation
        ├── VideoTileManager.swift       # Binds tiles to DefaultVideoRenderView
        └── Info.plist                   # Camera / microphone usage descriptions
```

---

## Setup

### Android Setup

No extra setup is required. The Chime SDK Maven dependencies are already declared in `composeApp/build.gradle.kts`:

```kotlin
androidMain.dependencies {
    implementation(libs.amazon.chime.sdk)
    implementation(libs.amazon.chime.sdk.media)
}
```

The required permissions are declared in `AndroidManifest.xml`:
- `CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- `BLUETOOTH`, `BLUETOOTH_CONNECT`, `WAKE_LOCK`

At runtime you must still request `CAMERA` and `RECORD_AUDIO` from the user (Android 6+). The demo app leaves this to the platform's permission dialog triggered by the SDK itself.

---

### iOS Setup

AmazonChimeSDK is integrated via CocoaPods.

**1. Install CocoaPods** (if not already installed):
```bash
sudo gem install cocoapods
```

**2. Install the pod:**
```bash
cd iosApp
pod install
```

**3. Open the workspace** (not the `.xcodeproj`):
```bash
open iosApp.xcworkspace
```

**4. Build the KMP framework first** (required before the first Xcode build):
```bash
# from the repo root
./gradlew :composeApp:assembleDebug        # Android
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64   # iOS simulator
```

The `ChimeSdkSetup.configure()` call in `iOSApp.swift` runs automatically on launch — no manual wiring needed.

> **Note:** AmazonChimeSDK requires a physical device or a simulator with camera/audio capabilities for real meetings. Simulator builds compile fine but will not produce video.

---

## Architecture

### Common API

All platform-agnostic declarations live in `ChimeSDK.kt` as `expect` functions. The shared `App.kt` demo calls `LocalVideoView(...)` and `RemoteVideoView(...)` directly — these are `@Composable expect fun` declarations that each platform resolves to its own native view.

```
commonMain                      androidMain / iosMain
─────────────────               ─────────────────────
expect fun joinMeeting(...)  →  actual fun joinMeeting(...)
expect fun leaveMeeting()    →  actual fun leaveMeeting()
@Composable
expect fun LocalVideoView()  →  actual fun LocalVideoView()   (AndroidView / UIKitView)
@Composable
expect fun RemoteVideoView() →  actual fun RemoteVideoView()
```

### Android Implementation

The Android side wires up a set of Chime SDK observer classes. Each observer is created fresh per session and cleaned up on `leaveMeeting()`.

```
joinMeeting()
  ├─ MeetingSessionConfiguration (credentials + URLs)
  ├─ DefaultMeetingSession (with DefaultEglCoreFactory)
  ├─ RealTimeObserver         → RealTimeEventListener callbacks
  ├─ DeviceObserver           → onAudioDevicesUpdated
  ├─ AudioVideoObserverImpl   → connection status, video availability
  ├─ MeetingActiveSpeakerObserver → onActiveSpeakersChanged
  ├─ ChatObserver             → chat / emoji / system messages
  └─ VideoTileManager         → tile bind/unbind → mutableStateOf (triggers recompose)
```

Local video uses `DefaultCameraCaptureSource` + `DefaultSurfaceTextureCaptureSourceFactory` so the camera feed flows through the EGL pipeline into Chime's encoder.

### iOS Bridge

Because the Chime iOS SDK is native Swift/ObjC, the Kotlin layer cannot use it directly. Instead, a two-way bridge is used:

```
Kotlin (iosMain)                     Swift (iosApp)
────────────────────────────         ─────────────────────────────
ChimeSdkBridge.joinMeetingNative  ←  ChimeSdkSetup sets closure
ChimeSdkBridge.eventDelegate      →  ChimeMeeting calls its methods

joinMeeting() {                      ChimeMeeting.joinMeeting() {
  eventDelegate =                      // sets up DefaultMeetingSession
    IOSDelegateToCallbacks(...)         // registers all Chime delegates
  joinMeetingNative(credentials)     }
}                                    
                                     // When a Chime event fires in Swift:
                                     ChimeSdkBridge.shared
                                       .eventDelegate?
                                       .onConnectionStatusChanged("CONNECTED")
                                     // → flows back to Kotlin callback
```

Video rendering uses two pre-created `DefaultVideoRenderView` instances on `ChimeMeeting.shared`. Their factories are registered with `ChimeSdkBridge` so Compose's `UIKitView` can embed them. `VideoTileManager.swift` binds tiles to those views as they appear.

---

## API Reference

### `joinMeeting`

```kotlin
fun joinMeeting(
    // Meeting identity
    externalMeetingId: String,
    meetingId: String,

    // Connection URLs (from your Chime backend)
    audioHostURL: String,
    audioFallbackURL: String,
    turnControlURL: String,
    signalingURL: String,
    ingestionURL: String,

    // Attendee credentials (from your Chime backend)
    attendeeId: String,
    externalUserId: String,
    joinToken: String,

    // Event listener (implement to receive attendee/audio events)
    realTimeListener: RealTimeEventListener,

    // Callbacks
    onChatMessageReceived: (TextMessage) -> Unit,
    onActiveSpeakersChanged: (Set<String>) -> Unit,
    onEmojiReceived: (TextMessage) -> Unit,
    cameraFacing: CameraFacing = CameraFacing.FRONT,
    onLocalVideoTileAdded: ((Int?) -> Unit)? = null,
    onConnectionStatusChanged: (ConnectionStatus) -> Unit = {},
    onRemoteVideoAvailable: (isAvailable: Boolean, sourceCount: Int) -> Unit = { _, _ -> },
    onCameraSendAvailable: (available: Boolean) -> Unit = {},
    onSessionError: (message: String, isRecoverable: Boolean) -> Unit = { _, _ -> },
    onVideoNeedsRestart: () -> Unit = {},
    onLocalVideoTileRemoved: (() -> Unit)?,
    preferredAudioInputDeviceType: String? = null,
    onRemoteTileAdded: ((Int?) -> Unit)? = null,
    onRemoteTileRemoved: (() -> Unit)? = null,
    onSystemMessage: (TextMessage) -> Unit,
    isJoiningOnMute: Boolean,
    onLocalAttendeeIdAvailable: (String) -> Unit
)
```

This call is **synchronous** — it configures the session and starts connecting. Connection progress is reported via `onConnectionStatusChanged`. Throws on unrecoverable setup errors.

---

### Meeting Controls

| Function | Description |
|---|---|
| `leaveMeeting()` | Stops all media, removes all observers, cleans up the session |
| `startLocalVideo()` | Starts the camera and begins sending local video |
| `stopLocalVideo()` | Stops the camera and stops sending local video |
| `setMute(shouldMute: Boolean): Boolean` | Mutes or unmutes the local microphone. Returns `true` on success |
| `switchCamera()` | Toggles between front and back camera |
| `switchAudioDevice(deviceId: String?)` | Switches to a specific audio output device by ID |
| `sendRealtimeMessage(topic: String, data: String, lifetimeMs: Long)` | Sends a data message to all participants on the given topic (`"chat"`, `"emoji"`, `"system"`, or custom) |

---

### Video Composables

```kotlin
@Composable
fun LocalVideoView(modifier: Modifier, cameraFacing: CameraFacing, isOnTop: Boolean)

@Composable
fun RemoteVideoView(modifier: Modifier, isOnTop: Boolean)
```

Both composables render nothing when there is no active tile (safe to include in the layout at all times). `isOnTop = true` renders the view above other surfaces — use this for the local video overlay.

---

### Data Types

#### `MeetingInformation`
```kotlin
data class MeetingInformation(
    val externalMeetingId: String,
    val meetingId: String,
    val audioHostURL: String,
    val audioFallbackURL: String,
    val turnControlURL: String,
    val signalingURL: String,
    val ingestionURL: String,
    val attendeeId: String,
    val externalUserId: String,
    val joinToken: String
)
```

#### `ConnectionStatus`
```kotlin
enum class ConnectionStatus {
    CONNECTING, CONNECTED, RECONNECTING, POOR_CONNECTION, DISCONNECTED, ERROR
}
```

#### `CameraFacing`
```kotlin
enum class CameraFacing { FRONT, BACK }
```

#### `TextMessage`
Used for chat messages, emoji reactions, and system messages.
```kotlin
data class TextMessage(
    val senderId: String,   // Chime attendee ID of the sender
    val content: String,
    val timestamp: Long     // milliseconds since epoch
)
```

#### `AudioDevice`
```kotlin
data class AudioDevice(
    val type: Int,          // MediaDeviceType ordinal
    val label: String,      // human-readable name
    val id: String?,        // device ID (may be null on some Android devices)
    val isSelected: Boolean
)
```

#### `RealTimeEventListener`
```kotlin
interface RealTimeEventListener {
    fun onAttendeesJoined(attendeeIds: List<String>)
    fun onAttendeesDropped(attendeeIds: List<String>)
    fun onAttendeesLeft()
    fun onAttendeesMuted(attendeeIds: List<String>)
    fun onAttendeesUnmuted(attendeeIds: List<String>)
    fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int)
    fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int)
    fun onAudioDevicesUpdated(audioDevices: List<AudioDevice>, selectedDevice: AudioDevice?)
}
```

---

## Demo App

`App.kt` is a self-contained Compose demo that exercises every part of the library.

**Join Screen** — scrollable form with all meeting credential fields, camera facing selector, and "Join Muted" toggle.

**In-Meeting Screen:**

```
┌────────────────────────────────────────┐
│  🔄 Reconnecting...     (status chip) │
│                                        │
│                                        │
│        Remote Video (full screen)      │
│     or "Waiting for other participant" │
│                                        │
│                      ┌────────────┐   │
│                      │ Local Video│   │
│                      │  (overlay) │   │
│                      └────────────┘   │
├────────────────────────────────────────┤
│  🎤 Mute  📷 Cam  🔄 Flip  🔊 Audio  │
│  💬 Chat                     📵 Leave │
└────────────────────────────────────────┘
```

The chat panel and audio device picker slide up as overlays from the bottom of the screen.

---

## Usage Example

Below is a minimal integration using the library directly (without the demo ViewModel):

```kotlin
// 1. Implement the listener
val listener = object : RealTimeEventListener {
    override fun onAttendeesJoined(attendeeIds: List<String>) { /* ... */ }
    override fun onAttendeesLeft() { /* handle call end */ }
    // ... implement remaining methods
}

// 2. Join a meeting
joinMeeting(
    externalMeetingId = "my-meeting",
    meetingId = meeting.meetingId,
    audioHostURL = meeting.audioHostURL,
    audioFallbackURL = meeting.audioFallbackURL,
    turnControlURL = meeting.turnControlURL,
    signalingURL = meeting.signalingURL,
    ingestionURL = meeting.ingestionURL,
    attendeeId = attendee.attendeeId,
    externalUserId = attendee.externalUserId,
    joinToken = attendee.joinToken,
    realTimeListener = listener,
    onChatMessageReceived = { msg -> /* update chat list */ },
    onActiveSpeakersChanged = { speakers -> /* highlight speaker */ },
    onEmojiReceived = { emoji -> /* show reaction */ },
    onConnectionStatusChanged = { status ->
        if (status == ConnectionStatus.CONNECTED) showMeetingUI()
    },
    onLocalVideoTileAdded = { _ -> /* camera is active */ },
    onLocalVideoTileRemoved = { /* camera stopped */ },
    onSessionError = { message, recoverable ->
        if (!recoverable) showError(message)
    },
    onVideoNeedsRestart = {
        stopLocalVideo()
        startLocalVideo()
    },
    onSystemMessage = { /* handle system message */ },
    isJoiningOnMute = false,
    onLocalAttendeeIdAvailable = { id -> localAttendeeId = id }
)

// 3. Render video in your Composable
@Composable
fun MeetingScreen() {
    Box(Modifier.fillMaxSize()) {
        RemoteVideoView(Modifier.fillMaxSize(), isOnTop = false)
        LocalVideoView(
            modifier = Modifier.size(120.dp, 180.dp).align(Alignment.BottomEnd),
            cameraFacing = CameraFacing.FRONT,
            isOnTop = true
        )
    }
}

// 4. Start camera
startLocalVideo()

// 5. Leave
leaveMeeting()
```

For a full implementation using `ViewModel` and `StateFlow`, see `AppViewModel.kt` and `App.kt`.

---

## Building

### Android

```bash
# Debug APK
./gradlew :composeApp:assembleDebug

# Install on connected device
./gradlew :composeApp:installDebug
```

### iOS

```bash
# From repo root — build the KMP framework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Then open Xcode
cd iosApp && pod install && open iosApp.xcworkspace
```

Select your target device/simulator in Xcode and press **Run**.
