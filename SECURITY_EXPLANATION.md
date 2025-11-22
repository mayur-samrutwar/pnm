# PNM Security Architecture - Explanation for Judges

## Overview
PNM (Payment Network Mobile) is an **offline-first payment system** that enables secure payments without internet connectivity. The security is built on **hardware-backed cryptography** and **cryptographic proofs** that prevent tampering and double-spending.

---

## 🔐 Core Security Principles

### 1. **Hardware-Backed Security (Android Keystore)**
- **What it is**: Android Keystore is a hardware security module (HSM) that stores cryptographic keys in a **dedicated secure hardware chip** (Trusted Execution Environment or StrongBox)
- **Why it matters**: Private keys **never leave the hardware chip**. Even if the phone is compromised, malware cannot extract the private key
- **How we use it**:
  - **Voucher Signing Key**: Used to sign payment vouchers (P-256 ECDSA)
  - **Counter State Signing Key**: Used to sign the spending counter state
  - **Ethereum Wallet Key**: Used for on-chain transactions (secp256k1)

### 2. **Monotonic Counter with Signed State**
- **What it is**: A counter that can only **increase**, never decrease
- **How it prevents tampering**:
  - Every counter state (cumulative spending, counter value, limit) is **signed with a hardware key**
  - Before updating, the system **verifies the signature** of the current state
  - If tampering is detected (signature doesn't match), the update is **rejected**
  - The new state is **signed atomically** with the hardware key

### 3. **Offline Spending Limit Enforcement**
- **Limit is set by vault balance**: When you deposit $100 to the vault, your offline limit becomes $100
- **Limit is hardware-signed**: The limit is stored with a hardware signature, preventing users from increasing it
- **Cumulative tracking**: Every payment adds to cumulative spending
- **Enforcement**: `cumulative + new_amount <= limit` - checked **before** signing
- **Atomic updates**: Counter increment and signature happen together - if one fails, both fail

---

## 📄 What are Vouchers and Slips?

### **Voucher** (What the User Creates)
A **voucher** is a **cryptographically signed payment promise** that contains:
- `slipId`: Unique identifier (UUID) to prevent double-spending
- `payer`: User's device address (derived from P-256 public key)
- `ethAddress`: User's Ethereum address (where deposits are stored)
- `amount`: Payment amount in micro USDC
- `cumulative`: Total amount spent so far (prevents overspending)
- `counter`: Monotonic sequence number (prevents replay attacks)
- `publicKey`: User's public key (for signature verification)
- `signature`: **Hardware-signed** proof that the user authorized this payment
- `timestamp`: When the voucher was created

**Key Security Properties**:
- ✅ **Cryptographically signed** with hardware key (cannot be forged)
- ✅ **Contains cumulative spending** (prevents overspending)
- ✅ **Unique slipId** (prevents double-spending)
- ✅ **Monotonic counter** (prevents replay attacks)

### **Slip** (What the Merchant Receives)
A **slip** is a **merchant's local record** of a scanned voucher:
- Contains all voucher data
- Stores the **raw JSON** of the voucher
- Tracks **status**: PENDING → VALIDATED → REDEEMED
- Stored in **Room database** (local to merchant's device)

**Difference**:
- **Voucher** = The signed payment promise (QR code)
- **Slip** = Merchant's local record of that voucher

---

## 🛡️ How Tampering is Prevented

### Attack 1: User tries to increase their spending limit
**Prevention**:
- Limit is stored with a **hardware signature**
- To change the limit, you need to sign a new state with the hardware key
- But the hardware key requires **user authentication** (biometric/PIN)
- Even if someone modifies the stored limit value, the **signature won't match**
- System verifies signature before every operation → **rejects tampered state**

### Attack 2: User tries to decrease cumulative spending
**Prevention**:
- Cumulative is part of the **signed state**
- If cumulative is decreased, the signature won't match
- System verifies signature before incrementing → **rejects tampered state**
- Counter is **monotonic** (can only increase)

### Attack 3: User tries to reuse an old voucher (replay attack)
**Prevention**:
- Each voucher has a **monotonic counter** that increases with each payment
- Vouchers are validated with `cumulative` - if you try to use an old voucher, the cumulative won't match
- On-chain redemption checks `usedSlip[payer][slipId]` → **prevents double-spending**

### Attack 4: User tries to create vouchers without incrementing counter
**Prevention**:
- Signing a voucher and incrementing counter are **atomic** (happen together)
- If counter increment fails (e.g., limit exceeded), **signature is not returned**
- Counter increment verifies current state signature → **rejects if tampered**

### Attack 5: User tries to spend more than their limit
**Prevention**:
- Before signing, system checks: `cumulative + amount <= limit`
- This check happens **before** the hardware signature is created
- Counter increment also checks this → **rejects if limit exceeded**

---

## 🔄 Payment Flow Security

### Step 1: User Creates Payment (Offline)
1. User enters amount: $10
2. System checks: `cumulative + $10 <= limit` ✅
3. Creates voucher JSON (without signature)
4. **Hardware signs** the voucher JSON → signature
5. **Atomically increments** counter and cumulative (with signature verification)
6. Voucher is complete with signature
7. QR code is generated from signed voucher

### Step 2: Merchant Scans QR (Offline)
1. Merchant scans QR code
2. Extracts voucher JSON
3. Validates JSON schema
4. Stores as **Slip** in local database (status: PENDING)

### Step 3: Merchant Validates (Online)
1. Merchant goes online
2. Sends voucher to hub server
3. Hub **verifies signature** using public key
4. Hub checks:
   - Signature is valid ✅
   - Cumulative <= user's vault balance ✅
   - SlipId not used before ✅
5. Hub marks slip as **VALIDATED**

### Step 4: Merchant Redeems (On-Chain)
1. Hub calls smart contract `redeemVoucher()`
2. Contract verifies:
   - Signature is valid (ECDSA recovery) ✅
   - SlipId not used before ✅
   - User has sufficient deposits (cumulative <= deposits) ✅
3. Contract transfers tokens to merchant
4. Contract marks slipId as used
5. Slip status → **REDEEMED**

---

## 🔑 Hardware Security Layers

### Layer 1: StrongBox (Best - Dedicated Hardware Chip)
- Available on newer Android devices (Pixel 3+, Samsung S9+)
- Keys stored in **dedicated secure hardware chip**
- **Physically isolated** from main processor
- Even if OS is compromised, keys are safe

### Layer 2: Hardware-Backed Keystore (Good)
- Available on all Android devices with hardware security
- Keys stored in **Trusted Execution Environment (TEE)**
- Isolated from main Android OS
- Requires user authentication for key usage

### Layer 3: Encrypted SharedPreferences (Fallback)
- Used if hardware security is unavailable
- Keys encrypted with **AES-256-GCM**
- Still secure, but not hardware-backed
- App shows warning if using software fallback

---

## 📊 Security Guarantees

### What the System Guarantees:
1. ✅ **Private keys cannot be extracted** (hardware-backed)
2. ✅ **Spending limit cannot be increased** (hardware-signed)
3. ✅ **Cumulative spending cannot be decreased** (hardware-signed)
4. ✅ **Vouchers cannot be forged** (requires hardware signature)
5. ✅ **Double-spending is prevented** (unique slipId + on-chain tracking)
6. ✅ **Replay attacks are prevented** (monotonic counter + cumulative)
7. ✅ **Overspending is prevented** (limit check before signing)

### What the System Does NOT Guarantee:
- ❌ Protection against physical device theft (but requires biometric/PIN)
- ❌ Protection if user willingly shares their device/PIN
- ❌ Protection if hardware security is disabled/bypassed (rare)

---

## 🎯 Key Takeaways for Judges

1. **Hardware Security**: Private keys never leave the secure hardware chip
2. **Tamper-Proof Counter**: Every state change is signed and verified
3. **Cryptographic Proofs**: Vouchers are cryptographically signed, cannot be forged
4. **Double-Spending Prevention**: Unique slipId + on-chain tracking
5. **Overspending Prevention**: Limit enforced before signing, cumulative tracked
6. **Offline-First**: Works without internet, security doesn't depend on network

---

## 📝 Technical Details

### Signature Algorithm
- **Voucher Signing**: ECDSA P-256 (secp256r1) with SHA-256
- **Counter State Signing**: ECDSA P-256 with SHA-256
- **Ethereum Transactions**: secp256k1 (standard Ethereum curve)

### Storage
- **Keys**: Android Keystore (hardware-backed)
- **Counter State**: EncryptedSharedPreferences (AES-256-GCM)
- **Slips**: Room Database (local SQLite)

### Validation
- **Offline**: JSON schema validation + signature verification
- **Online**: Hub server validates signature + cumulative <= vault balance
- **On-Chain**: Smart contract verifies signature + prevents double-spending

---

This architecture provides **military-grade security** for offline payments while maintaining usability and performance.

