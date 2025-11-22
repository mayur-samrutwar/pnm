# PNM Test Plan

This document provides exact terminal commands and steps for testing the PNM offline payment system end-to-end.

## Prerequisites

- Node.js v18+ installed
- Android SDK installed
- ADB in PATH
- Two Android Virtual Devices (AVDs) created (e.g., `Pixel_6_API_33`)
- Java JDK 17+ installed

## Test Environment Setup

### 1. Start Hub Server

```bash
cd hub
npm install
npm run dev
```

**Expected Output:**
```
Server is running on port 3000
Environment: development
Redeem on-chain: disabled
Database initialized
```

**Verify:** 
```bash
curl http://localhost:3000/health
# Should return: {"status":"ok","message":"Hub server is running"}
```

### 2. Start Hardhat Node (Optional - for on-chain testing)

```bash
cd vault
npm install
npx hardhat node
```

**Expected Output:**
```
Started HTTP and WebSocket JSON-RPC server at http://127.0.0.1:8545/
```

**Keep this terminal open** - Hardhat node must remain running.

### 3. Deploy Vault Contract (if using Hardhat)

In a **new terminal**:

```bash
cd vault
npx hardhat run scripts/deploy.js --network localhost
```

**Expected Output:**
```
Vault deployed to: 0x5FbDB2315678afecb367f032d93F642f64180aa3
```

**Note:** Copy the contract address for `.env` configuration.

### 4. Configure Hub Server Environment

Create/update `hub/.env`:

```bash
cd hub
cat > .env << EOF
PORT=3000
NODE_ENV=development
RPC_URL=http://127.0.0.1:8545
HUB_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
VAULT_CONTRACT_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3
REDEEM_ON_CHAIN=false
REFILL_LIMIT=100000
REFILL_EXPIRY_DAYS=30
EOF
```

**Note:** Use the actual deployed contract address and a valid private key.

### 5. Build and Install APK

```bash
cd app
./gradlew assembleDebug
```

**Install on both emulators:**

```bash
# Install on Emulator A (port 5554)
adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk

# Install on Emulator B (port 5556)
adb -s emulator-5556 install app/build/outputs/apk/debug/app-debug.apk
```

**Alternative (install on both at once):**
```bash
./gradlew installDebug
```

### 6. Start Two Emulators

**Terminal 1 - Emulator A (User):**
```bash
emulator -avd Pixel_6_API_33 -port 5554
```

**Terminal 2 - Emulator B (Merchant):**
```bash
emulator -avd Pixel_6_API_33 -port 5556
```

**Wait for both to fully boot**, then verify:

```bash
adb devices
```

**Expected Output:**
```
List of devices attached
emulator-5554    device
emulator-5556    device
```

### 7. Launch Apps on Both Emulators

```bash
# Launch on Emulator A (User)
adb -s emulator-5554 shell am start -n com.pnm.mobileapp/.MainActivity

# Launch on Emulator B (Merchant)
adb -s emulator-5556 shell am start -n com.pnm.mobileapp/.MainActivity
```

**On Emulator A:** Ensure role toggle shows "USER"
**On Emulator B:** Tap role toggle to select "MERCHANT"

## Test Scenarios

### Scenario 1: Basic Flow - Deposit, Create Slip, Scan, Redeem

#### Step 1.1: Enable Airplane Mode on Both Emulators

```bash
# Emulator A (User)
adb -s emulator-5554 shell settings put global airplane_mode_on 1
adb -s emulator-5554 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Emulator B (Merchant)
adb -s emulator-5556 shell settings put global airplane_mode_on 1
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true
```

**Verify:** Both emulators show airplane icon in status bar.

#### Step 1.2: Generate Wallet on Emulator A

**On Emulator A:**
1. Tap "Generate Wallet"
2. Authenticate if biometric prompt appears
3. **Copy the wallet address** displayed (e.g., `0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb`)

#### Step 1.3: Set Offline Limit

**On Emulator A:**
1. Tap "Setup" under "Offline Limit"
2. Enter limit: `50000`
3. Tap "Set Limit"

#### Step 1.4: Simulate Deposit via Hub Webhook

```bash
# Replace USER_WALLET_ADDRESS with actual address from Step 1.2
USER_WALLET_ADDRESS="0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"

curl -X POST http://localhost:3000/api/v1/depositWebhook \
  -H "Content-Type: application/json" \
  -d "{
    \"user\": \"$USER_WALLET_ADDRESS\",
    \"amount\": \"100000\",
    \"token\": \"0x0987654321098765432109876543210987654321\",
    \"txHash\": \"0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\"
  }"
```

**Expected Response:**
```json
{"status":"success","message":"Deposit recorded"}
```

**Verify in hub logs:**
```
POST /api/v1/depositWebhook
Deposit recorded for USER_WALLET_ADDRESS
```

#### Step 1.5: Create Slip on Emulator A

**On Emulator A:**
1. Enter amount: `40000`
2. Tap "Create Offline Payment"
3. **QR code dialog appears** - Keep it open
4. Note: Cumulative should show `40000`, Counter = `1`

**Verify:** QR code is displayed, voucher JSON is shown.

#### Step 1.6: Scan QR on Emulator B (Merchant)

**On Emulator B:**
1. Tap "Scan QR" button
2. Grant camera permission if prompted

**Note:** Since emulators can't scan each other's screens, use workaround:

**Option A - Manual JSON Input (for testing):**
```bash
# On Emulator A, tap "Copy voucher JSON" button
# Then manually input the JSON in Merchant app (or use ADB)
```

**Option B - Use ADB to simulate (advanced):**
```bash
# Get voucher JSON from Emulator A logs
adb -s emulator-5554 logcat -d | grep -i voucher
```

**Expected Result:**
- Slip appears in "Pending Slips" list on Emulator B
- Status: PENDING
- Amount: 40000

#### Step 1.7: Bring Emulator B Online and Redeem

```bash
# Disable airplane mode on Emulator B only
adb -s emulator-5556 shell settings put global airplane_mode_on 0
adb -s emulator-5556 shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false
```

**On Emulator B:**
1. Verify network connectivity (WiFi icon appears)
2. In "Pending Slips" section:
   - Check the checkbox next to the slip
   - Toggle "Online (Validate)" switch ON
   - Tap "Sync selected with Hub"

**Alternative - Direct API Call:**

Get the slip JSON from Emulator B (or use the one from Emulator A):

```bash
# Replace with actual voucher JSON from the app
VOUCHER_JSON='{"slipId":"...","payer":"...","amount":"40000","cumulative":40000,"counter":1,"signature":"...","timestamp":...}'

curl -X POST http://localhost:3000/api/v1/redeem \
  -H "Content-Type: application/json" \
  -d "{
    \"voucher\": $VOUCHER_JSON
  }"
```

**Expected Response:**
```json
{"status":"reserved","txHash":null}
```

**Verify Hub Server Logs:**
```bash
# In hub server terminal, you should see:
POST /api/v1/redeem
Voucher redeemed successfully
Slip marked as used: SLIP_ID
```

**Assert Success:**
```bash
# Check hub database
cat hub/data/db.json | jq '.usedSlips'
# Should contain the slip ID

# Check slip status
cat hub/data/db.json | jq '.slips[] | select(.slipId == "SLIP_ID")'
# Should show status: "redeemed"
```

### Scenario 2: Duplicate Slip Rejection

#### Step 2.1: Attempt to Redeem Same Slip Again

```bash
# Use the same voucher JSON from Scenario 1
curl -X POST http://localhost:3000/api/v1/redeem \
  -H "Content-Type: application/json" \
  -d "{
    \"voucher\": $VOUCHER_JSON
  }"
```

**Expected Response:**
```json
{"status":"error","reason":"Voucher slip already used"}
```

**Verify Hub Server Logs:**
```
POST /api/v1/redeem
Error: Voucher slip already used
```

**Assert:**
```bash
# Verify slip is in usedSlips set
cat hub/data/db.json | jq '.usedSlips | contains(["SLIP_ID"])'
# Should return: true
```

### Scenario 3: Over-Limit Attempt

#### Step 3.1: Reset Emulator A State (Optional)

```bash
# Clear app data to start fresh
adb -s emulator-5554 shell pm clear com.pnm.mobileapp
```

**On Emulator A:**
1. Generate new wallet
2. Set offline limit: `50000`
3. Create slip: `40000` (should succeed)
4. Attempt to create another slip: `20000` (should fail)

**Expected Result:**
- First slip: Success, Cumulative = 40000
- Second slip: Toast message "Offline limit exceeded"
- No QR code generated
- Cumulative remains at 40000

**Verify in App Logs:**
```bash
adb -s emulator-5554 logcat -d | grep -i "limit exceeded"
# Should show: "Offline limit exceeded: cannot sign amount 20000"
```

#### Step 3.2: Verify Counter State

**On Emulator A:**
- Check cumulative value displayed
- Should show: `40000 / 50000` or similar

**Verify via ADB:**
```bash
# Check encrypted preferences (requires root or debug build)
adb -s emulator-5554 shell run-as com.pnm.mobileapp cat /data/data/com.pnm.mobileapp/shared_prefs/pnm_counter_secure.xml
```

### Scenario 4: Refill Flow

#### Step 4.1: Ensure All Slips Are Redeemed

**Prerequisites:**
- All previous slips from user must be redeemed
- User must be settled (if using on-chain settlement)

**Check settlement status:**
```bash
# Check hub database for user's slips
USER_ADDRESS="0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
cat hub/data/db.json | jq ".slips[] | select(.payer == \"$USER_ADDRESS\" or .userAddress == \"$USER_ADDRESS\") | .status"
# All should be "redeemed"
```

#### Step 4.2: Request Refill

**On Emulator A:**
1. Ensure airplane mode is OFF (bring online)
2. Tap "Request Refill" button (if available in UI)

**Alternative - Direct API Call:**

```bash
USER_ADDRESS="0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"

curl -X POST http://localhost:3000/api/v1/requestRefill \
  -H "Content-Type: application/json" \
  -d "{
    \"userAddress\": \"$USER_ADDRESS\",
    \"proof\": null
  }"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Refill token issued successfully",
  "refillToken": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiMC4uLiIsIm5ld0xpbWl0IjoxMDAwMDAsImV4cGlyeSI6MTczNTY4OTYwMCwibm9uY2UiOiIuLi4ifQ..."
}
```

**Verify Hub Server Logs:**
```
POST /api/v1/requestRefill
Refill token issued for USER_ADDRESS, new limit: 100000
```

#### Step 4.3: Verify Refill Token in Database

```bash
cat hub/data/db.json | jq '.refillRequests[-1]'
# Should show the refill request with token
```

#### Step 4.4: Verify Counter Reset on Mobile App

**On Emulator A:**
- After refill token is processed:
  - Cumulative should reset to `0`
  - Counter should reset to `0`
  - Limit should update to new limit (e.g., `100000`)

**Verify:**
1. Create new slip: `30000` (should succeed)
2. Cumulative should show: `30000`
3. Counter should show: `1`

**Assert Counter Reset:**
```bash
# Check app logs for reset confirmation
adb -s emulator-5554 logcat -d | grep -i "counter reset"
# Should show: "Counter reset: cumulative=0, counter=0, limit=100000"
```

## Unit Tests

### Hub Server Unit Tests

```bash
cd hub
npm test
```

**Expected Output:**
```
PASS  src/services/__tests__/validator.test.ts
  Validator Service
    ✓ should validate correct voucher schema
    ✓ should reject invalid voucher schema
    ✓ should verify valid P-256 signature
    ✓ should reject invalid P-256 signature
    ...

Test Suites: 1 passed, 1 total
Tests:       10 passed, 10 total
```

**Run specific test file:**
```bash
npm test -- validator.test.ts
```

**Run with coverage:**
```bash
npm test -- --coverage
```

### Hardhat Contract Tests

```bash
cd vault
npx hardhat test
```

**Expected Output:**
```
  Vault
    ✓ Should deploy Vault contract
    ✓ Should allow deposit
    ✓ Should redeem voucher
    ✓ Should prevent double-spend
    ✓ Should record settlement
    ✓ Should check user settlement status
    ...

  10 passing (2s)
```

**Run specific test file:**
```bash
npx hardhat test test/Vault.test.js
```

**Run with gas reporting:**
```bash
REPORT_GAS=true npx hardhat test
```

### Mobile App Unit Tests

```bash
cd app
./gradlew test
```

**Expected Output:**
```
> Task :app:testDebugUnitTest

com.pnm.mobileapp.crypto.CounterManagerTest
  ✓ testInitCounter
  ✓ testIncrementCounterSafely
  ✓ testIncrementCounterRefusesWhenLimitExceeded
  ✓ testCanSign

com.pnm.mobileapp.secure.HardwareKeystoreManagerTest
  ✓ testDetectStrongBoxAvailable
  ✓ testGenerateHardwareKey
  ✓ testSignWithHardwareKey
  ✓ testAttestationCertificate

BUILD SUCCESSFUL
```

**Run specific test class:**
```bash
./gradlew test --tests "com.pnm.mobileapp.crypto.CounterManagerTest"
```

**Run with coverage:**
```bash
./gradlew test jacocoTestReport
```

## Integration Test Script

Create a complete integration test script:

```bash
#!/bin/bash
# integration_test.sh

set -e

echo "=== PNM Integration Test ==="

# 1. Start hub server (background)
echo "Starting hub server..."
cd hub
npm install > /dev/null 2>&1
npm run dev &
HUB_PID=$!
sleep 5

# 2. Verify hub is running
curl -f http://localhost:3000/health || exit 1

# 3. Test deposit webhook
echo "Testing deposit webhook..."
USER_ADDRESS="0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb"
DEPOSIT_RESPONSE=$(curl -s -X POST http://localhost:3000/api/v1/depositWebhook \
  -H "Content-Type: application/json" \
  -d "{\"user\":\"$USER_ADDRESS\",\"amount\":\"100000\",\"token\":\"0x0987654321098765432109876543210987654321\",\"txHash\":\"0x1234\"}")

if echo "$DEPOSIT_RESPONSE" | grep -q "success"; then
  echo "✓ Deposit webhook test passed"
else
  echo "✗ Deposit webhook test failed"
  exit 1
fi

# 4. Test refill request (should fail - no slips redeemed yet)
echo "Testing refill request (should fail)..."
REFILL_RESPONSE=$(curl -s -X POST http://localhost:3000/api/v1/requestRefill \
  -H "Content-Type: application/json" \
  -d "{\"userAddress\":\"$USER_ADDRESS\"}")

if echo "$REFILL_RESPONSE" | grep -q "unsettled"; then
  echo "✓ Refill rejection test passed (correctly rejected)"
else
  echo "✗ Refill rejection test failed"
  exit 1
fi

# 5. Cleanup
echo "Cleaning up..."
kill $HUB_PID 2>/dev/null || true

echo "=== Integration test complete ==="
```

**Run integration test:**
```bash
chmod +x integration_test.sh
./integration_test.sh
```

## Test Data Cleanup

### Reset Hub Database

```bash
cd hub
rm -f data/db.json
# Database will be recreated on next server start
```

### Reset Mobile App Data

```bash
# Clear app data on both emulators
adb -s emulator-5554 shell pm clear com.pnm.mobileapp
adb -s emulator-5556 shell pm clear com.pnm.mobileapp
```

### Reset Hardhat Node

```bash
# Stop Hardhat node (Ctrl+C)
# Restart to get fresh state
cd vault
npx hardhat node --reset
```

## Troubleshooting

### Issue: Hub server not starting

```bash
# Check if port 3000 is in use
lsof -i :3000

# Kill process if needed
kill -9 $(lsof -t -i:3000)
```

### Issue: Emulators not connecting

```bash
# Restart ADB server
adb kill-server
adb start-server
adb devices
```

### Issue: APK installation fails

```bash
# Uninstall existing app first
adb -s emulator-5554 uninstall com.pnm.mobileapp
adb -s emulator-5556 uninstall com.pnm.mobileapp

# Reinstall
./gradlew installDebug
```

### Issue: Tests failing

```bash
# Clear test caches
cd hub && rm -rf node_modules/.cache
cd vault && rm -rf cache
cd app && ./gradlew clean
```

## Test Checklist

- [ ] Hub server starts successfully
- [ ] Hardhat node starts (if using)
- [ ] Vault contract deploys
- [ ] APK builds and installs on both emulators
- [ ] Both emulators connect via ADB
- [ ] Apps launch on both emulators
- [ ] Deposit webhook accepts requests
- [ ] Slip creation works offline
- [ ] QR code scanning works (or manual input)
- [ ] Slip redemption succeeds
- [ ] Duplicate slip rejection works
- [ ] Over-limit prevention works
- [ ] Refill flow completes successfully
- [ ] Counter resets after refill
- [ ] All unit tests pass
- [ ] All contract tests pass

## Test Results Template

```
Test Date: _______________
Tester: _______________

Scenario 1 - Basic Flow: [ ] PASS [ ] FAIL
  Notes: ________________________________

Scenario 2 - Duplicate Rejection: [ ] PASS [ ] FAIL
  Notes: ________________________________

Scenario 3 - Over-Limit Prevention: [ ] PASS [ ] FAIL
  Notes: ________________________________

Scenario 4 - Refill Flow: [ ] PASS [ ] FAIL
  Notes: ________________________________

Unit Tests - Hub: [ ] PASS [ ] FAIL
  Notes: ________________________________

Unit Tests - Contracts: [ ] PASS [ ] FAIL
  Notes: ________________________________

Overall: [ ] PASS [ ] FAIL
```

