# How to Test with Two Emulators

This guide walks you through testing the PNM mobile app with two Android emulators - one configured as USER and one as MERCHANT - to test the complete offline payment flow.

## Prerequisites

- Android SDK installed
- Two Android Virtual Devices (AVDs) created (e.g., `Pixel_6_API_33`)
- Hub server running on localhost (port 3000)
- ADB in your PATH

## Step 1: Start Two Emulators

Open two terminal windows and start emulators on different ports:

**Terminal 1 - Emulator A (USER):**
```bash
emulator -avd Pixel_6_API_33 -port 5554
```

**Terminal 2 - Emulator B (MERCHANT):**
```bash
emulator -avd Pixel_6_API_33 -port 5556
```

Wait for both emulators to fully boot (you'll see the home screen).

**Verify both emulators are running:**
```bash
adb devices
```

You should see:
```
List of devices attached
emulator-5554    device
emulator-5556    device
```

## Step 2: Build and Install APK

Build the debug APK and install on both emulators:

```bash
cd /Users/tonystark/Desktop/pnm/app

# Build APK
./gradlew assembleDebug

# Install on Emulator A (USER)
adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk

# Install on Emulator B (MERCHANT)
adb -s emulator-5556 install app/build/outputs/apk/debug/app-debug.apk
```

**Alternative: Install directly via Gradle**
```bash
# Install on both (requires both emulators connected)
./gradlew installDebug
```

## Step 3: Configure Retrofit Base URL

The app uses `10.0.2.2` to access localhost from the emulator. Verify the hub server base URL in:

**File:** `app/src/main/java/com/pnm/mobileapp/util/Constants.kt`

```kotlin
const val HUB_BASE_URL = "http://10.0.2.2:3000"
```

**Start the hub server:**
```bash
cd /Users/tonystark/Desktop/pnm/hub
npm install
npm run dev
```

The hub server should be running on `http://localhost:3000`.

## Step 4: Configure Emulator A as USER

1. **Open the app on Emulator A:**
   ```bash
   adb -s emulator-5554 shell am start -n com.pnm.mobileapp/.MainActivity
   ```

2. **In the app:**
   - Ensure role toggle shows "USER" (top right)
   - Tap "Generate Wallet" to create a keypair
   - Tap "Setup" under "Offline Limit" and set a limit (e.g., `100`)
   - Tap "Set Limit"

## Step 5: Configure Emulator B as MERCHANT

1. **Open the app on Emulator B:**
   ```bash
   adb -s emulator-5556 shell am start -n com.pnm.mobileapp/.MainActivity
   ```

2. **In the app:**
   - Tap role toggle to select "MERCHANT"
   - The screen should switch to MerchantScreen

## Step 6: Enable Airplane Mode on Both Emulators

Enable airplane mode on both emulators to simulate offline mode:

**Emulator A (USER):**
```bash
adb -s emulator-5554 shell settings put global airplane_mode_on 1
adb -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

**Emulator B (MERCHANT):**
```bash
adb -s emulator-5556 shell settings put global airplane_mode_on 1
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

**Verify airplane mode is enabled:**
- Check the status bar on both emulators - you should see the airplane icon
- Or verify via ADB:
  ```bash
  adb -s emulator-5554 shell settings get global airplane_mode_on  # Should return 1
  adb -s emulator-5556 shell settings get global airplane_mode_on  # Should return 1
  ```

## Step 7: Create Slip on Emulator A (USER)

1. **On Emulator A:**
   - Enter an amount (e.g., `40`)
   - Tap "Create Offline Payment"
   - A dialog will appear showing:
     - QR code
     - Voucher details
     - "Copy voucher JSON" button
   - **Keep this dialog open** - you'll need to scan the QR code

## Step 8: Scan QR Code on Emulator B (MERCHANT)

### Option A: Using Emulator Camera (Recommended)

1. **On Emulator B:**
   - Tap "Scan QR" button
   - Grant camera permission if prompted
   - The camera will open

2. **To simulate QR scanning in emulator:**
   - You can't actually scan the QR from another emulator screen
   - **Workaround:** Use the "Copy voucher JSON" button on Emulator A, then manually input it

### Option B: Manual QR Input (For Testing)

Since emulators can't scan each other's screens, use this workaround:

1. **On Emulator A:**
   - Tap "Copy voucher JSON" button
   - The voucher JSON is now in clipboard

2. **On Emulator B:**
   - Instead of scanning, you can manually test by:
     - Using ADB to input the JSON directly into the app
     - Or modify the app temporarily to accept manual JSON input

### Option C: Use Physical Device + Emulator

If you have a physical device:
1. Install app on physical device (as USER)
2. Generate QR code on physical device
3. Scan with emulator camera (this works!)

## Step 9: View Pending Slip on Emulator B

After scanning (or manual input), on Emulator B:

1. The scanned slip should appear in the "Pending Slips" section
2. You should see:
   - Slip ID
   - Amount
   - Payer address
   - Status: PENDING

## Step 10: Disable Airplane Mode on Emulator B Only

To sync with the hub server, disable airplane mode only on Emulator B:

```bash
adb -s emulator-5556 shell settings put global airplane_mode_on 0
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

**Verify:**
- Emulator B should show network connectivity (WiFi icon)
- Emulator A should still be in airplane mode

## Step 11: Sync with Hub on Emulator B

1. **On Emulator B:**
   - Scroll to "Pending Slips" section
   - Check the checkbox next to the slip you want to sync
   - Toggle "Online (Validate)" or "Offline (Redeem)" switch
   - Tap "Sync selected with Hub"
   - Wait for response message

2. **Check hub server logs** to see the API call:
   ```bash
   # In hub server terminal, you should see:
   POST /api/v1/validate or /api/v1/redeem
   ```

## Useful ADB Commands

### Check Connected Devices
```bash
adb devices
```

### Open App on Specific Emulator
```bash
adb -s emulator-5554 shell am start -n com.pnm.mobileapp/.MainActivity
adb -s emulator-5556 shell am start -n com.pnm.mobileapp/.MainActivity
```

### Clear App Data (Reset State)
```bash
adb -s emulator-5554 shell pm clear com.pnm.mobileapp
adb -s emulator-5556 shell pm clear com.pnm.mobileapp
```

### View App Logs
```bash
# Emulator A
adb -s emulator-5554 logcat -s PNM:D

# Emulator B
adb -s emulator-5556 logcat -s PNM:D

# Both
adb logcat | grep -i pnm
```

### Take Screenshot
```bash
adb -s emulator-5554 exec-out screencap -p > emulator_a.png
adb -s emulator-5556 exec-out screencap -p > emulator_b.png
```

### Input Text (For Testing)
```bash
adb -s emulator-5554 shell input text "100"
```

### Simulate Button Press
```bash
# Back button
adb -s emulator-5554 shell input keyevent KEYCODE_BACK

# Home button
adb -s emulator-5554 shell input keyevent KEYCODE_HOME
```

## Troubleshooting

### Camera/QR Scanning Issues

**Problem: Camera permission denied**
```bash
# Grant camera permission
adb -s emulator-5556 shell pm grant com.pnm.mobileapp android.permission.CAMERA
```

**Problem: Camera not working in emulator**
- Emulators have limited camera support
- **Solution:** Use a physical device for QR scanning, or use the manual JSON input workaround

**Problem: QR scanner doesn't open**
- Check camera permission: `adb -s emulator-5556 shell dumpsys package com.pnm.mobileapp | grep permission`
- Restart the app after granting permission

**Problem: Can't scan QR from another emulator**
- This is a limitation - emulators can't use camera to scan another screen
- **Workarounds:**
  1. Use physical device + emulator combination
  2. Copy voucher JSON manually and input via ADB
  3. Use a QR code generator website to create a test QR code

### Network Issues

**Problem: Can't connect to hub server**
- Verify hub server is running: `curl http://localhost:3000/health`
- Check Retrofit base URL is `http://10.0.2.2:3000` (not `localhost`)
- Verify airplane mode is disabled on the emulator making the request

**Problem: Airplane mode not working**
```bash
# Force disable airplane mode
adb -s emulator-5556 shell settings put global airplane_mode_on 0
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false

# Restart network
adb -s emulator-5556 shell svc wifi enable
adb -s emulator-5556 shell svc data enable
```

### App Installation Issues

**Problem: App won't install**
```bash
# Uninstall existing app first
adb -s emulator-5554 uninstall com.pnm.mobileapp
adb -s emulator-5556 uninstall com.pnm.mobileapp

# Then reinstall
./gradlew installDebug
```

**Problem: App crashes on startup**
- Check logs: `adb -s emulator-5554 logcat | grep -i crash`
- Clear app data and restart
- Verify all dependencies are installed

### Database Issues

**Problem: Pending slips not showing**
- Check Room database version matches (should be version 2)
- Clear app data to reset database:
  ```bash
  adb -s emulator-5556 shell pm clear com.pnm.mobileapp
  ```

## Testing Workflow Summary

1. ✅ Start two emulators (ports 5554 and 5556)
2. ✅ Install APK on both
3. ✅ Start hub server on localhost:3000
4. ✅ Configure Emulator A as USER (generate wallet, set limit)
5. ✅ Configure Emulator B as MERCHANT
6. ✅ Enable airplane mode on both
7. ✅ Create slip on Emulator A → QR code displayed
8. ✅ Scan QR on Emulator B (or use workaround)
9. ✅ Verify slip appears in pending list
10. ✅ Disable airplane mode on Emulator B only
11. ✅ Sync selected slip with hub
12. ✅ Verify response from hub server

## Quick Reference: All Commands

```bash
# Start emulators
emulator -avd Pixel_6_API_33 -port 5554 &
emulator -avd Pixel_6_API_33 -port 5556 &

# Install app
cd /Users/tonystark/Desktop/pnm/app
./gradlew installDebug

# Enable airplane mode (both)
adb -s emulator-5554 shell settings put global airplane_mode_on 1
adb -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
adb -s emulator-5556 shell settings put global airplane_mode_on 1
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Disable airplane mode (Emulator B only)
adb -s emulator-5556 shell settings put global airplane_mode_on 0
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false

# Open apps
adb -s emulator-5554 shell am start -n com.pnm.mobileapp/.MainActivity
adb -s emulator-5556 shell am start -n com.pnm.mobileapp/.MainActivity

# Grant camera permission (Emulator B)
adb -s emulator-5556 shell pm grant com.pnm.mobileapp android.permission.CAMERA
```

## Notes

- **Emulator Camera Limitation:** Android emulators cannot scan QR codes from another screen. Use a physical device or manual JSON input for testing.
- **Network Mapping:** `10.0.2.2` is the special IP that maps to `localhost` (127.0.0.1) on the host machine from within an Android emulator.
- **Port Numbers:** Emulator ports 5554, 5556, etc. are standard. Each emulator uses two consecutive ports (e.g., 5554-5555, 5556-5557).
- **Airplane Mode:** This simulates offline mode. The app should work offline for creating/scanning slips, but needs network for hub sync.

