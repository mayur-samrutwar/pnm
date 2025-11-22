# Judges Checklist: PNM Offline Payment System

This checklist verifies the key security and functionality features of the PNM system.

## ✅ Security Verification

### 1. Double-Spend Prevention
- [ ] **Verified:** User cannot create slips exceeding offline limit
  - **Test:** Create slip for $40, then attempt $10 when limit is $50
  - **Expected:** Second slip creation blocked with "Offline limit exceeded" message
  - **Evidence:** Toast notification and app refusal to generate QR code

- [ ] **Verified:** Slip IDs are unique and cannot be reused
  - **Test:** Attempt to redeem same slip twice
  - **Expected:** Hub server rejects second redemption with "Voucher slip already used"
  - **Evidence:** Hub server logs show duplicate slip rejection

- [ ] **Verified:** Counter is monotonic and tamper-resistant
  - **Test:** Check counter increments only forward (1, 2, 3...)
  - **Expected:** Counter never decreases, even after app restart
  - **Evidence:** Counter value persists in encrypted storage

### 2. Merchant Protection
- [ ] **Verified:** Merchant can validate slips before accepting
  - **Test:** Merchant scans QR code and validates locally
  - **Expected:** JSON schema validation passes, signature verified
  - **Evidence:** Slip appears in pending list with VALID status

- [ ] **Verified:** Merchant can sync multiple slips in batch
  - **Test:** Scan multiple slips, select all, sync with hub
  - **Expected:** All selected slips synced successfully
  - **Evidence:** Hub server processes all slips, status updates to REDEEMED

- [ ] **Verified:** Merchant protected from expired vouchers
  - **Test:** Attempt to sync expired voucher (if available)
  - **Expected:** Hub rejects expired voucher
  - **Evidence:** Hub response: "Voucher has expired"

- [ ] **Verified:** Merchant protected from invalid signatures
  - **Test:** Attempt to sync voucher with tampered signature
  - **Expected:** Hub rejects invalid signature
  - **Evidence:** Hub response: "Invalid signature"

### 3. Refill Validation
- [ ] **Verified:** Refill requires all previous slips to be settled
  - **Test:** Request refill with unsettled slips
  - **Expected:** Hub rejects: "User has X unsettled slips"
  - **Evidence:** Hub server response shows unsettled slip count

- [ ] **Verified:** Refill token is signed by hub private key
  - **Test:** Inspect refill token structure
  - **Expected:** Token contains header.payload.signature format
  - **Evidence:** Token can be verified using hub public key

- [ ] **Verified:** Mobile app verifies hub signature locally
  - **Test:** Attempt to use tampered refill token
  - **Expected:** App rejects token: "Invalid refill token signature"
  - **Evidence:** App logs show signature verification failure

- [ ] **Verified:** Counter resets only after valid refill token
  - **Test:** Request refill, verify counter reset
  - **Expected:** Cumulative = 0, Counter = 0, Limit = new limit
  - **Evidence:** App UI shows reset values, can create new slips

- [ ] **Verified:** Refill token has expiry
  - **Test:** Check token expiry field
  - **Expected:** Token contains expiry timestamp
  - **Evidence:** Token payload shows expiry > current time

### 4. Attestation Logs
- [ ] **Verified:** Hardware attestation certificates are generated
  - **Test:** Generate wallet on device with StrongBox
  - **Expected:** Attestation certificate chain available
  - **Evidence:** `HardwareKeystoreManager.attestationCertificate()` returns certificate chain

- [ ] **Verified:** Attestation proves hardware-backed keys
  - **Test:** Inspect certificate chain
  - **Expected:** Certificate chain includes hardware attestation extension
  - **Evidence:** Certificate contains Android Key Attestation OID

- [ ] **Verified:** Software fallback warning displayed
  - **Test:** Run on device/emulator without StrongBox
  - **Expected:** Orange warning banner: "Using software-backed security"
  - **Evidence:** UI shows `SoftwareFallbackBanner` component

## ✅ Functionality Verification

### 5. Offline Operation
- [ ] **Verified:** User can create slips offline
  - **Test:** Enable airplane mode, create slip
  - **Expected:** QR code generated, slip created successfully
  - **Evidence:** QR code displayed, cumulative updated

- [ ] **Verified:** Merchant can scan slips offline
  - **Test:** Enable airplane mode, scan QR code
  - **Expected:** Slip parsed and saved to pending list
  - **Evidence:** Slip appears in Room database, status = PENDING

- [ ] **Verified:** Offline limit enforced
  - **Test:** Create slips up to limit, attempt to exceed
  - **Expected:** App blocks creation when limit reached
  - **Evidence:** Toast message, button disabled, or exception thrown

### 6. Online Synchronization
- [ ] **Verified:** Merchant can sync slips when online
  - **Test:** Disable airplane mode, sync pending slips
  - **Expected:** Slips synced to hub, status updated
  - **Evidence:** Hub server logs show redemption, app shows success

- [ ] **Verified:** User can request refill when online
  - **Test:** Disable airplane mode, request refill
  - **Expected:** Refill token received, counter reset
  - **Evidence:** App shows new limit, counter = 0

### 7. Data Persistence
- [ ] **Verified:** Counter persists across app restarts
  - **Test:** Create slip, close app, reopen app
  - **Expected:** Cumulative and counter values preserved
  - **Evidence:** App shows previous values on startup

- [ ] **Verified:** Pending slips persist across app restarts
  - **Test:** Scan slip, close app, reopen app
  - **Expected:** Slip still in pending list
  - **Evidence:** Room database contains slip record

## ✅ Technical Implementation

### 8. Cryptographic Security
- [ ] **Verified:** ECDSA P-256 signatures used
  - **Test:** Inspect signature format
  - **Expected:** Signatures use secp256r1 curve
  - **Evidence:** Code uses `SHA256withECDSA`, curve = `secp256r1`

- [ ] **Verified:** Keys stored in Android Keystore
  - **Test:** Inspect key generation code
  - **Expected:** Keys use `AndroidKeyStore` provider
  - **Evidence:** `HardwareKeystoreManager` uses `AndroidKeyStore`

- [ ] **Verified:** Counter state is signed
  - **Test:** Inspect counter manager
  - **Expected:** Counter state includes signature
  - **Evidence:** `MonotonicCounterManager` signs state with hardware key

### 9. Database Integrity
- [ ] **Verified:** Slips stored in encrypted Room database
  - **Test:** Inspect database schema
  - **Expected:** `pending_slips` table with encrypted fields
  - **Evidence:** Room database version 2, encrypted SharedPreferences

- [ ] **Verified:** Hub database tracks used slips
  - **Test:** Check hub database after redemption
  - **Expected:** `usedSlips` set contains redeemed slip IDs
  - **Evidence:** `hub/data/db.json` shows slip ID in usedSlips array

## Final Verification Statement

**Double-spend prevented:** ✅
- Offline limit enforcement prevents exceeding available balance
- Slip ID uniqueness prevents reuse
- Monotonic counter prevents rollback attacks

**Merchant protected:** ✅
- Local validation before accepting slips
- Batch sync capability
- Hub verification of signatures and expiry
- Protection from invalid/expired vouchers

**Refill validated:** ✅
- Hub-signed refill tokens with cryptographic verification
- Settlement verification before refill
- Counter reset only after valid token
- Token expiry enforcement

**Attestation logs:** ✅
- Hardware attestation certificates generated
- Software fallback warning displayed
- Certificate chain available for hub verification

---

## Sign-off

**Judge Name:** _________________________

**Date:** _________________________

**Overall Assessment:**
- [ ] Pass
- [ ] Pass with minor issues
- [ ] Fail (specify issues below)

**Notes:**
_________________________________________________________
_________________________________________________________
_________________________________________________________

