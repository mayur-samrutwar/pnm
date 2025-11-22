# ECDSA P-256 Signature Setup

## Overview

This document describes the ECDSA P-256 signature implementation for vouchers/slips between the mobile app and hub server.

## Mobile App (Android/Kotlin)

### Signer.kt

Location: `app/src/main/java/com/pnm/mobileapp/crypto/Signer.kt`

**Key Features:**
- Uses Android Keystore (hardware-backed) when available
- Falls back to software-backed keys if Keystore unavailable
- Generates P-256 (secp256r1) key pairs
- Signs vouchers using SHA256withECDSA
- Exports public keys in uncompressed format

### Public Key Format

The mobile app exports public keys in **uncompressed format**:
```
0x04 || x (32 bytes) || y (32 bytes) = 65 bytes = 130 hex characters
```

Example:
```
04a1b2c3d4e5f6... (x coordinate, 64 hex chars) ...7890abcdef (y coordinate, 64 hex chars)
```

### Signature Format

Signatures are exported in **raw r||s format**:
```
r (32 bytes) || s (32 bytes) = 64 bytes = 128 hex characters
```

The Android `Signature` class returns DER-encoded signatures, which are converted to raw format internally.

### Usage

```kotlin
val signer = Signer(context)
val keyPair = signer.generateKeyPair()
val publicKeyHex = signer.exportPublicKeyHex(keyPair) // 130 hex chars
val signatureHex = signer.signVoucher(voucherJson, keyPair) // 128 hex chars
```

## Hub Server (Node.js/TypeScript)

### validator.ts

Location: `hub/src/services/validator.ts`

**Function:** `verifySignatureP256(voucherJson, publicKeyHex, signatureHex)`

### Public Key Format Conversion

The hub server expects the same format as Android generates:
- **Input:** `04` + x (64 hex) + y (64 hex) = 130 hex chars
- **With or without `0x` prefix:** Both formats are accepted
- **Parsing:** 
  - First 2 chars must be `04` (uncompressed format)
  - Next 64 chars = x coordinate (32 bytes)
  - Last 64 chars = y coordinate (32 bytes)

### Signature Format

The hub server expects raw r||s format:
- **Input:** r (64 hex) + s (64 hex) = 128 hex chars
- **With or without `0x` prefix:** Both formats are accepted
- **Parsing:**
  - First 64 chars = r (32 bytes)
  - Last 64 chars = s (32 bytes)

### Dependencies

```json
{
  "elliptic": "^6.5.4"
}
```

The `elliptic` package is used with curve `p256` (secp256r1, same as Android's P-256).

### Usage

```typescript
import { verifySignatureP256 } from './services/validator';

const isValid = verifySignatureP256(
  voucherJson,      // Original JSON string that was signed
  publicKeyHex,     // 130 hex chars: 04 + x + y
  signatureHex      // 128 hex chars: r + s
);
```

## Format Conversion Guide

### Android → Node.js

**Public Key:**
1. Android generates: `0x04 || x (32 bytes) || y (32 bytes)`
2. Export as hex: `04` + x_hex (64 chars) + y_hex (64 chars)
3. Node.js accepts: Same format (with or without `0x` prefix)

**Signature:**
1. Android signs with `SHA256withECDSA` → DER format
2. Convert DER to raw: Extract r and s, pad to 32 bytes each
3. Concatenate: r_hex (64 chars) + s_hex (64 chars)
4. Node.js accepts: Same format (with or without `0x` prefix)

### Example

**Android:**
```kotlin
val publicKey = "04a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
val signature = "1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
```

**Node.js:**
```typescript
const isValid = verifySignatureP256(
  '{"amount":"100","userAddress":"0x123..."}',
  '04a1b2c3d4e5f67890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890',
  '1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890'
);
```

## Testing

### Mobile App

The mobile app uses the `Signer` class directly. Test by:
1. Generate key pair
2. Export public key
3. Sign a voucher
4. Verify signature matches expected format

### Hub Server

Run tests:
```bash
cd hub
npm test
```

The test suite includes:
- Valid signature verification
- Invalid signature rejection
- Wrong message rejection
- Public key with `0x` prefix handling

## Security Notes

1. **Android Keystore:** When available, keys are hardware-backed and more secure
2. **Software Fallback:** Keys are stored in app memory (less secure but functional)
3. **Message Hashing:** Both Android and Node.js use SHA-256 before signing/verifying
4. **Key Format:** Uncompressed format (0x04 prefix) is standard and interoperable

## Troubleshooting

### Signature Verification Fails

1. **Check message format:** Must be exact same JSON string (whitespace matters)
2. **Check public key format:** Must start with `04` and be 130 hex chars
3. **Check signature format:** Must be 128 hex chars (r||s)
4. **Check curve:** Both must use P-256 (secp256r1)

### Public Key Mismatch

- Ensure Android exports in uncompressed format (starts with `04`)
- Ensure Node.js parses correctly (first 2 chars = `04`, then x, then y)

### Signature Format Issues

- Android DER → Raw conversion must pad r and s to exactly 32 bytes each
- Node.js expects raw r||s format, not DER

