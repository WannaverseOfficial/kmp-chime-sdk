# kmp-chime-sdk

A Kotlin Multiplatform library for [Amazon Chime SDK](https://aws.amazon.com/chime/chime-sdk/) meetings on Android and iOS. Exposes a single shared API via Compose Multiplatform — join meetings, send and receive audio/video, and exchange real-time data messages without writing platform-specific code.

---

## Android Setup

**1. Add the dependency:**

```kotlin
// build.gradle.kts
implementation("com.wannaverse:chimesdk:<version>")
```

**2. Expose the application context.** The library needs an Android `Context` to initialise the Chime session. Set it in `MainActivity` before calling `setContent`:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        appContext = applicationContext   // provided by the library
        super.onCreate(savedInstanceState)
        setContent { /* your UI */ }
    }
}
```

**3. Request permissions** before joining:

```kotlin
// CAMERA and RECORD_AUDIO must be granted at runtime (Android 6+)
ActivityResultContracts.RequestMultiplePermissions()
```

The following permissions are declared in the library's manifest and merged automatically:
`CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `BLUETOOTH_CONNECT`

---

## iOS Setup

The iOS implementation uses the native [AmazonChimeSDK](https://github.com/aws/amazon-chime-sdk-ios) CocoaPod alongside three Swift bridge files from this repo: `ChimeMeeting.swift`, `ChimeSdkSetup.swift`, and `VideoTileManager.swift`.

**1. Add the pod:**

```ruby
# Podfile
target 'YourApp' do
  pod 'AmazonChimeSDK', '~> 0.25.0'
end
```

```bash
cd iosApp && pod install
```

**2. Copy the bridge files** from `iosApp/iosApp/` into your Xcode project:
- `ChimeMeeting.swift` — native Chime session management
- `ChimeSdkSetup.swift` — registers Swift closures with the Kotlin bridge
- `VideoTileManager.swift` — binds video tiles to render views

**3. Call `ChimeSdkSetup.configure()` once on launch:**

```swift
@main
struct YourApp: App {
    init() {
        ChimeSdkSetup.configure()
    }
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```

**4. Add usage descriptions to `Info.plist`:**

```xml
<key>NSCameraUsageDescription</key>
<string>Camera is used for video calls.</string>
<key>NSMicrophoneUsageDescription</key>
<string>Microphone is used for audio calls.</string>
```

---

## Usage

### Joining a meeting

Pass credentials from your backend (obtained via the AWS `CreateMeeting` + `CreateAttendee` APIs) alongside your event listener and callbacks:

```kotlin
joinMeeting(
    externalMeetingId = info.externalMeetingId,
    meetingId         = info.meetingId,
    audioHostURL      = info.audioHostURL,
    audioFallbackURL  = info.audioFallbackURL,
    turnControlURL    = info.turnControlURL,
    signalingURL      = info.signalingURL,
    ingestionURL      = info.ingestionURL,
    attendeeId        = info.attendeeId,
    externalUserId    = info.externalUserId,
    joinToken         = info.joinToken,

    realTimeListener = object : RealTimeEventListener {
        override fun onAttendeesJoined(attendeeIds: List<String>)  { /* ... */ }
        override fun onAttendeesLeft(attendeeIds: List<String>)    { /* ... */ }
        override fun onAttendeesDropped(attendeeIds: List<String>) { /* ... */ }
        override fun onAttendeesMuted(attendeeIds: List<String>)   { /* ... */ }
        override fun onAttendeesUnmuted(attendeeIds: List<String>) { /* ... */ }
        override fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int) { /* ... */ }
        override fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int)         { /* ... */ }
        override fun onAudioDevicesUpdated(devices: List<AudioDevice>, selected: AudioDevice?)            { /* ... */ }
    },

    onActiveSpeakersChanged   = { speakers -> /* highlight active speaker */ },
    onConnectionStatusChanged = { status ->
        if (status == ConnectionStatus.CONNECTED) { /* show UI */ }
    },
    onRemoteVideoAvailable    = { isAvailable, count -> /* show/hide remote grid */ },
    onSessionError            = { message, isRecoverable -> /* handle error */ },
    onLocalAttendeeIdAvailable = { id -> /* store local attendee ID */ },
    isJoiningOnMute           = false
)
```

### Data messages (topics)

Subscribe to topics after joining. Any number of topics can be active simultaneously.

```kotlin
// Subscribe
subscribeToTopic("chat") { message ->
    // message.senderId, message.content, message.timestamp
}

// Send
sendRealtimeMessage(topic = "chat", data = "Hello!")

// Unsubscribe
unsubscribeFromTopic("chat")
```

### Video

Place the composables anywhere in your layout. They render nothing when no tile is active.

```kotlin
@Composable
fun MeetingScreen() {
    Box(Modifier.fillMaxSize()) {
        // Full-screen remote video
        RemoteVideoView(
            modifier = Modifier.fillMaxSize(),
            tileId   = remoteTileId,
            isOnTop  = false
        )

        // Local camera overlay
        LocalVideoView(
            modifier     = Modifier.size(120.dp, 160.dp).align(Alignment.BottomEnd),
            cameraFacing = CameraFacing.FRONT,
            isOnTop      = true
        )
    }
}
```

Call `startLocalVideo()` after joining to begin sending camera frames.

### Meeting controls

```kotlin
startLocalVideo()                  // start sending camera frames
stopLocalVideo()                   // stop camera
setMute(true)                      // mute microphone (returns true on success)
switchCamera()                     // toggle front / back camera
switchAudioDevice(device.id)       // route audio to a specific output device
sendRealtimeMessage("topic", "data", lifetimeMs = 0)  // broadcast to all participants
leaveMeeting()                     // end session and release all resources
```

---

## MeetingInformation

A convenience data class for passing credentials around your app:

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

There is also a `MeetingInformation.joinMeeting(...)` extension in the demo (`AppViewModel.kt`) that spreads the fields into `joinMeeting()` for you.

---

## License

MIT
