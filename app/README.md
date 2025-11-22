# Mobile App

Android application built with Kotlin and Jetpack Compose.

## Prerequisites

- Android Studio (latest stable version)
- JDK 17 or higher
- Android SDK (API level 33+ recommended)
- Android Emulator or physical device

## Setup

1. Open the project in Android Studio
2. Sync Gradle files (File → Sync Project with Gradle Files)
3. Wait for dependencies to download

## Running the App

### Using Android Emulator

1. **Start Android Studio**
2. **Open AVD Manager**:
   - Tools → Device Manager
   - Or click the device manager icon in the toolbar
3. **Create a Virtual Device** (if needed):
   - Click "Create Device"
   - Select a device definition (e.g., Pixel 6)
   - Select a system image (e.g., API 33, Android 13)
   - Finish the setup
4. **Start the Emulator**:
   - Click the play button next to your virtual device
   - Wait for the emulator to boot
5. **Run the App**:
   ```bash
   ./gradlew installDebug
   ```
   Or use Android Studio's Run button (Shift+F10)

### Using Physical Device

1. Enable Developer Options on your Android device:
   - Settings → About Phone → Tap "Build Number" 7 times
2. Enable USB Debugging:
   - Settings → Developer Options → USB Debugging
3. Connect device via USB
4. Run the app:
   ```bash
   ./gradlew installDebug
   ```

## Gradle Commands

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing configuration)
./gradlew assembleRelease

# Build and install debug APK on connected device/emulator
./gradlew installDebug

# Build and install release APK
./gradlew installRelease
```

### Clean Commands

```bash
# Clean build artifacts
./gradlew clean

# Clean and rebuild
./gradlew clean build
```

### Test Commands

```bash
# Run unit tests
./gradlew test

# Run instrumented tests on connected device/emulator
./gradlew connectedAndroidTest

# Run all tests
./gradlew test connectedAndroidTest
```

### Lint and Check

```bash
# Run lint checks
./gradlew lint

# Check code quality
./gradlew check
```

### Other Useful Commands

```bash
# List all available tasks
./gradlew tasks

# Show project dependencies
./gradlew dependencies

# Generate build report
./gradlew build --scan
```

## Project Structure

```
mobile-app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/.../    # Kotlin source files
│   │   │   ├── res/              # Resources (layouts, drawables, etc.)
│   │   │   └── AndroidManifest.xml
│   │   └── test/                 # Unit tests
│   └── build.gradle.kts
├── build.gradle.kts              # Root build file
└── settings.gradle.kts
```

## Troubleshooting

### Emulator Issues
- **Emulator won't start**: Check if HAXM or Hypervisor is enabled in BIOS
- **App won't install**: Ensure emulator/device has enough storage
- **Build fails**: Clean and rebuild: `./gradlew clean build`

### Gradle Issues
- **Sync failed**: Check internet connection and Gradle version
- **Dependencies not found**: Invalidate caches: File → Invalidate Caches / Restart

## Running Tests

```bash
# Run instrumentation tests (requires emulator/device)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest --tests "com.pnm.mobileapp.SlipCreationTest"
```

## Installing APK

After building, the APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

To install manually:
```bash
# Install on connected device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Gradle
./gradlew installDebug
```

## Development Notes

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Compose Compiler: 1.5.3 (Compatible with Kotlin 1.9.10)
- Features: Wallet generation, QR code scanning, Room database, Retrofit API calls

