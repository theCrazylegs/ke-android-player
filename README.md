# KE Player - Android TV

**Unofficial** Android TV player for [Karaoke Eternal](https://github.com/bhj/karaoke-eternal).

This is a community-built, third-party player app. It is not developed or maintained by the Karaoke Eternal team.

## Features

- Automatic server discovery via mDNS
- Device pairing with QR code (scan to pair instantly)
- Video playback with ExoPlayer (MP4 karaoke files)
- Real-time queue sync via Socket.io
- Round-robin queue ordering with play history
- Remote control from the web app (play/pause/next/volume/replay)
- Waiting screen with next singer info and avatar
- Debug panel for troubleshooting

## Requirements

- Android TV or Android TV Box
- Android 7.0+ (API 24)
- Karaoke Eternal server running on the same network

## Build

```bash
# From Git Bash (not PowerShell)
JAVA_HOME="/c/Program Files/Java/jdk-19" \
  bash ./gradlew assembleDebug
```

## Deploy to TV Box

```bash
adb connect <TV_IP>:5555
adb -s <TV_IP>:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <TV_IP>:5555 shell am start -n com.thecrazylegs.keplayer/.MainActivity
```

## Tech Stack

- Kotlin
- Jetpack Compose
- ExoPlayer (Media3)
- Socket.io client
- ZXing (QR code generation)
- jmDNS (mDNS discovery)
- Coil (image loading)

## License

ISC
