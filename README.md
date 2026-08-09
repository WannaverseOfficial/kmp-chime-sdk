# kmp-chime-sdk

A Kotlin Multiplatform library for [Amazon Chime SDK](https://aws.amazon.com/chime/chime-sdk/) meetings on Android and iOS. Exposes a single shared API via Compose Multiplatform: join meetings, send and receive audio/video, and exchange real-time data messages without writing platform-specific code.

## Installation

### Gradle dependencies

Add these to your KMP module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        // Shared API + iOS implementation (cinterop baked in)
        commonMain.dependencies {
            implementation("com.wannaverse:chimesdk:<version>")
        }

        // Android implementation
        androidMain.dependencies {
            implementation("com.wannaverse:chimesdk-android:<version>")
        }
    }
}
```

> The `chimesdk` artifact covers all targets including iOS. The separate `chimesdk-android` artifact is needed on Android because the Chime SDK ships native media libraries that must be declared explicitly for the Android build.

---

### Android setup

**1. Set the application context** before calling `setContent`. The library needs it to initialize the Chime session:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ChimeSDK.initialize()
        // ...
    }
}
```

**2. Request runtime permissions** before joining a meeting:

```kotlin
// CAMERA and RECORD_AUDIO must be granted at runtime (Android 6+)
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { /* handle results */ }

launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
```

The following permissions are declared in the library manifest and merged automatically:
`CAMERA`, `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `INTERNET`, `ACCESS_NETWORK_STATE`, `BLUETOOTH_CONNECT`

---

### iOS setup

The iOS implementation is compiled entirely in Kotlin via cinterop. **No Swift files are required in your project.** You only need the AmazonChimeSDK framework available for the Xcode linker.

#### Option A: Swift Package Manager (recommended)

1. In Xcode: **File → Add Package Dependencies**
2. Enter `https://github.com/aws/amazon-chime-sdk-ios-spm`
3. Select version rule `Up to Next Minor` from `0.27.0`
4. Add **AmazonChimeSDK** and **AmazonChimeSDKMedia** to your app target

#### Option B: CocoaPods

```ruby
# iosApp/Podfile
platform :ios, '16.0'
use_frameworks!

target 'iosApp' do
  pod 'AmazonChimeSDK', '~> 0.27.0'
end
```

```bash
cd iosApp && pod install
```

#### Info.plist usage descriptions

Both options require these keys in `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Camera is used for video calls.</string>
<key>NSMicrophoneUsageDescription</key>
<string>Microphone is used for audio calls.</string>
```

## Usage

### Create a session

Pass credentials from your backend (obtained via the AWS `CreateMeeting` + `CreateAttendee` APIs):

```kotlin
val session = ChimeSDK.createSession(
    externalMeetingId = info.externalMeetingId,
    meetingId = info.meetingId,
    audioHostURL = info.audioHostURL,
    audioFallbackURL = info.audioFallbackURL,
    turnControlURL = info.turnControlURL,
    signalingURL = info.signalingURL,
    ingestionURL = info.ingestionURL,
    attendeeId = info.attendeeId,
    externalUserId = info.externalUserId,
    joinToken = info.joinToken
)
```

The session can then be used to fetch available audio devices, before joining the meeting:

```kotlinkotlin
val audioDevices = session.getAudioInputDevices()
```

### Joining a meeting

```kotlin
session.joinMeeting(
    realTimeListener = object : RealTimeEventListener {
        override fun onAttendeesJoined(attendeeIds: List<String>) { /* ... */ }
        override fun onAttendeesLeft(attendeeIds: List<String>) { /* ... */ }
        override fun onAttendeesDropped(attendeeIds: List<String>) { /* ... */ }
        override fun onAttendeesMuted(attendeeIds: List<String>) { /* ... */ }
        override fun onAttendeesUnmuted(attendeeIds: List<String>) { /* ... */ }
        override fun onSignalStrengthChanged(attendeeId: String, externalAttendeeId: String, signal: Int) { /* ... */ }
        override fun onVolumeChanged(attendeeId: String, externalAttendeeId: String, volume: Int) { /* ... */ }
        override fun onAudioDevicesUpdated(devices: List<AudioDevice>, selected: AudioDevice?) { /* ... */ }
    },
    
    onActiveSpeakersChanged = { speakers -> /* highlight active speaker */ },
    onConnectionStatusChanged = { status ->
        if (status == ConnectionStatus.CONNECTED) { /* update UI */ }
    },
    onRemoteVideoAvailable = { isAvailable, count -> /* show/hide remote grid */ },
    onCameraSendAvailable = { isAvailable -> /* start local video */ },
    onSessionError = { message, isRecoverable -> /* handle error */ },
    selectedAudioDevice = audioDevice.label,
    isJoiningOnMute = false,
    onLocalTileAdded = { tileId -> /* show local camera preview */ },
    onLocalTileRemoved = { tileId -> /* hide local camera preview */ },
    onRemoteTileAdded = { tileId -> /* show remote video */ },
    onRemoteTileRemoved = { tileId -> /* hide remote video */ }
)
```

### Leaving

```kotlin
session.leaveMeeting()   // ends the session and releases all resources
```

---

### Video

Render video anywhere in your Compose layout. The composables render nothing when no tile is active.

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

        // Local camera preview
        LocalVideoView(
            modifier     = Modifier.size(120.dp, 160.dp).align(Alignment.BottomEnd),
            cameraFacing = CameraFacing.FRONT,
            isOnTop      = true
        )
    }
}
```

Call `session.startLocalVideo()` after joining to begin sending camera frames.

---

### Meeting controls

```kotlin
session.startLocalVideo()              // start sending camera frames
session.stopLocalVideo()               // stop camera
session.setMute(true)                  // mute microphone
session.switchCamera()                 // toggle front / back camera
session.switchAudioDevice(device.id)   // route audio to a specific output
session.leaveMeeting()                 // end session and release all resources
```

---

### Data messages

Subscribe to named topics after joining. Any number of topics can be active simultaneously.

```kotlin
// Subscribe
session.subscribeToTopic("chat") { message ->
    println("${message.senderId}: ${message.content}")
}

// Send
session.sendRealtimeMessage(topic = "chat", data = "Hello!", lifetimeMs = 0)

// Unsubscribe
session.unsubscribeFromTopic("chat")
```

## License

MIT
