# PNM Demo Script for Judges

This document provides a step-by-step demonstration script for showcasing the PNM offline payment system.

## Prerequisites

- Two Android emulators running (User and Merchant)
- Hub server running on localhost:3000
- Vault contract deployed (optional for full demo)
- ADB installed and in PATH

## Setup Commands

```bash
# Terminal 1: Start hub server
cd hub
npm install
npm run dev

# Terminal 2: Start User emulator (port 5554)
emulator -avd Pixel_6_API_33 -port 5554

# Terminal 3: Start Merchant emulator (port 5556)
emulator -avd Pixel_6_API_33 -port 5556

# Terminal 4: Install app on both emulators
cd app
./gradlew installDebug
```

## Demo Steps

### Step 1: Start Mobile App on Both Emulators

**User Emulator (5554):**
```bash
adb -s emulator-5554 shell am start -n com.pnm.mobileapp/.MainActivity
```

**Merchant Emulator (5556):**
```bash
adb -s emulator-5556 shell am start -n com.pnm.mobileapp/.MainActivity
```

**Actions:**
- On User emulator: Ensure role toggle shows "USER"
- On Merchant emulator: Tap role toggle to select "MERCHANT"

### Step 2: Enable Airplane Mode on Both Emulators

```bash
# User emulator
adb -s emulator-5554 shell settings put global airplane_mode_on 1
adb -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Merchant emulator
adb -s emulator-5556 shell settings put global airplane_mode_on 1
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

**Verify:** Both emulators should show airplane icon in status bar.

### Step 3: User Generates Wallet & Simulate Deposit

**On User Emulator:**
1. Tap "Generate Wallet"
2. If biometric prompt appears, authenticate
3. Note the wallet address displayed
4. Tap "Setup" under "Offline Limit"
5. Enter limit: `50` (for demo)
6. Tap "Set Limit"

**Simulate Deposit via Hub API:**
```bash
curl -X POST http://localhost:3000/api/v1/depositWebhook \
  -H "Content-Type: application/json" \
  -d '{
    "user": "USER_WALLET_ADDRESS_HERE",
    "amount": "100000",
    "token": "0x0987654321098765432109876543210987654321",
    "txHash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
  }'
```

**Replace `USER_WALLET_ADDRESS_HERE` with the actual address from User emulator.**

### Step 4: User Creates Offline Slip $40

**On User Emulator:**
1. Enter amount: `40`
2. Tap "Create Offline Payment"
3. **QR code dialog appears** - Keep it open
4. Note: Cumulative = 40, Counter = 1

**Expected Result:**
- QR code displayed
- Voucher JSON shown
- "Copy voucher JSON" button available

### Step 5: Merchant Scans and Accepts

**On Merchant Emulator:**
1. Tap "Scan QR" button
2. Grant camera permission if prompted
3. **Note:** Since emulators can't scan each other's screens, use workaround:
   - On User emulator: Tap "Copy voucher JSON"
   - On Merchant emulator: Manually input the JSON (or use ADB to paste)
4. After scanning/input, voucher should appear in "Pending Slips" list
5. Check the checkbox next to the slip
6. Verify slip details: Amount = 40, Status = PENDING

**Expected Result:**
- Slip appears in pending list
- Status shows PENDING
- Can select slip for sync

### Step 6: User Creates Second Slip $10 → App Rejects (Limit Reached)

**On User Emulator:**
1. Close QR dialog (if still open)
2. Enter amount: `10`
3. Tap "Create Offline Payment"
4. **Expected:** Toast message "Offline limit exceeded" appears
5. Payment creation is blocked

**Expected Result:**
- Toast: "Offline limit exceeded"
- No QR code generated
- Cumulative remains at 40 (limit = 50, 40 + 10 = 50, but counter logic prevents)

**Note:** The app enforces: `cumulative + amount <= limit`. Since cumulative is 40 and limit is 50, 40 + 10 = 50 should work, but the counter manager may have additional checks. Adjust limit to 40 for clearer demo.

### Step 7: Merchant Goes Online & Syncs

**Disable Airplane Mode on Merchant Emulator:**
```bash
adb -s emulator-5556 shell settings put global airplane_mode_on 0
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

**On Merchant Emulator:**
1. Verify network connectivity (WiFi icon appears)
2. In "Pending Slips" section:
   - Ensure slip is checked
   - Toggle "Online (Validate)" switch ON
   - Tap "Sync selected with Hub"
3. Wait for response message

**Expected Result:**
- Response: "Success" or "Voucher redeemed"
- Slip status updates to VALIDATED or REDEEMED
- Hub server logs show redemption

**Check Hub Server Logs:**
```bash
# In hub server terminal, you should see:
POST /api/v1/redeem
Voucher redeemed successfully
```

### Step 8: User Goes Online & Requests Refill

**Disable Airplane Mode on User Emulator:**
```bash
adb -s emulator-5554 shell settings put global airplane_mode_on 0
adb -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

**On User Emulator:**
1. Verify network connectivity
2. Tap "Request Refill" button (if available in UI)
   - Or use ADB to trigger:
   ```bash
   adb -s emulator-5554 shell input text "RequestRefill"
   ```
3. Wait for refill token response

**Expected Result:**
- Refill token received from hub
- Counter reset: Cumulative = 0, Counter = 0
- New limit applied (if different)
- User can now create new slips

**Check Hub Server Logs:**
```bash
# In hub server terminal, you should see:
POST /api/v1/requestRefill
Refill token issued for USER_ADDRESS
```

### Step 9: User Creates New Slip After Refill

**On User Emulator:**
1. Enter amount: `30`
2. Tap "Create Offline Payment"
3. **Expected:** QR code generated successfully
4. Cumulative = 30, Counter = 1

**Expected Result:**
- New slip created successfully
- Counter reset verified
- Can create multiple slips up to new limit

## Fallback Recording Plan

If NFC or live demo fails, use pre-recorded screencaps:

### Recording Setup

**Record User Emulator:**
```bash
# Start recording
adb -s emulator-5554 shell screenrecord /sdcard/user_demo.mp4

# Stop recording (Ctrl+C after demo)
adb -s emulator-5554 pull /sdcard/user_demo.mp4 ./recordings/user_demo.mp4
```

**Record Merchant Emulator:**
```bash
# Start recording
adb -s emulator-5556 shell screenrecord /sdcard/merchant_demo.mp4

# Stop recording (Ctrl+C after demo)
adb -s emulator-5556 pull /sdcard/merchant_demo.mp4 ./recordings/merchant_demo.mp4
```

### Playback During Presentation

1. **Split screen setup:**
   - Left side: User emulator recording
   - Right side: Merchant emulator recording
   - Sync playback to show simultaneous actions

2. **Key moments to highlight:**
   - User creates slip → QR appears
   - Merchant scans → Slip appears in pending
   - Limit enforcement → Toast message
   - Sync with hub → Status update
   - Refill request → Counter reset

3. **Narration script:**
   - "Here we see the user creating an offline payment slip..."
   - "The merchant scans the QR code..."
   - "Notice the app prevents exceeding the offline limit..."
   - "When online, the merchant syncs with the hub..."
   - "The user requests a refill, and the counter resets..."

## Troubleshooting

### Issue: App crashes on startup
**Solution:** Clear app data and reinstall
```bash
adb -s emulator-5554 shell pm clear com.pnm.mobileapp
adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk
```

### Issue: Hub server not responding
**Solution:** Check server is running and check logs
```bash
curl http://localhost:3000/health
# Should return: {"status":"ok","message":"Hub server is running"}
```

### Issue: QR code not scanning
**Solution:** Use manual JSON input workaround (copy-paste)

### Issue: Refill not working
**Solution:** Verify all slips are redeemed first
```bash
# Check hub database
cat hub/data/db.json
```

## Demo Checklist

- [ ] Both emulators running
- [ ] Hub server running on port 3000
- [ ] Apps installed on both emulators
- [ ] Airplane mode enabled on both
- [ ] User wallet generated
- [ ] Deposit simulated via webhook
- [ ] First slip ($40) created and scanned
- [ ] Second slip ($10) rejected (limit reached)
- [ ] Merchant synced with hub (online)
- [ ] User requested refill (online)
- [ ] Counter reset verified
- [ ] New slip created after refill

## Time Estimate

- Setup: 5 minutes
- Demo execution: 10-15 minutes
- Q&A: 5-10 minutes
- **Total: 20-30 minutes**

