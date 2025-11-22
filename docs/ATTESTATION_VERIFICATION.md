# Android Attestation Certificate Verification

This document describes how to verify Android hardware attestation certificates from the mobile app on the hub server.

## Overview

When the mobile app generates a hardware-backed key (using StrongBox or regular hardware-backed Keystore), Android provides an attestation certificate chain that proves:
1. The key was generated in secure hardware
2. The device integrity (not rooted, not compromised)
3. The app's package name and signature

## Mobile App: Generating Attestation Request

The `HardwareKeystoreManager.attestationCertificate()` method returns the certificate chain as a JSON array of base64-encoded certificates:

```kotlin
val certificateChain = hardwareKeystoreManager.attestationCertificate("pnm_voucher_key")
// Returns: ["base64_cert1", "base64_cert2", ...]
```

**Include in API request:**
```json
{
  "voucher": {...},
  "attestation": {
    "certificateChain": ["base64_cert1", "base64_cert2"],
    "challenge": "random_nonce_from_server"
  }
}
```

## Hub Server: Verification Setup

### 1. Install Dependencies

```bash
cd hub
npm install @google-cloud/attestation google-auth-library
```

Or use the Play Integrity API:

```bash
npm install googleapis
```

### 2. Verification Options

#### Option A: Google Play Integrity API (Recommended)

The Play Integrity API is the modern way to verify Android attestations:

```typescript
import { google } from 'googleapis';

const playintegrity = google.playintegrity('v1');

async function verifyAttestation(
  packageName: string,
  certificateChain: string[],
  challenge: string
): Promise<boolean> {
  try {
    // Decode certificate chain
    const certificates = certificateChain.map(cert => 
      Buffer.from(cert, 'base64')
    );

    // Verify certificate chain
    // 1. Check certificate chain is valid
    // 2. Extract attestation extension
    // 3. Verify challenge matches
    // 4. Verify package name matches
    // 5. Verify key is hardware-backed

    // For full implementation, use Play Integrity API
    const auth = new google.auth.GoogleAuth({
      keyFile: 'path/to/service-account.json',
      scopes: ['https://www.googleapis.com/auth/playintegrity']
    });

    const response = await playintegrity.v1.decodeIntegrityToken({
      packageName: packageName,
      requestBody: {
        integrityToken: challenge // This would be from Play Integrity API
      }
    });

    return response.data.tokenPayloadExternal?.accountDetails?.appLicensingVerdict === 'LICENSED';
  } catch (error) {
    console.error('Attestation verification failed:', error);
    return false;
  }
}
```

#### Option B: Manual Certificate Chain Verification

For basic verification without Play Integrity API:

```typescript
import * as crypto from 'crypto';
import * as fs from 'fs';

interface AttestationExtension {
  attestationChallenge: Buffer;
  softwareEnforced: any;
  teeEnforced: any;
}

async function verifyAttestationCertificate(
  certificateChainBase64: string[],
  expectedChallenge: string,
  expectedPackageName: string
): Promise<boolean> {
  try {
    // Decode certificates
    const certificates = certificateChainBase64.map(cert => 
      crypto.createCertificate(cert, 'base64')
    );

    // Verify certificate chain
    // 1. Root certificate should be Google's attestation root
    const rootCert = certificates[certificates.length - 1];
    const rootFingerprint = crypto.createHash('sha256')
      .update(rootCert.raw)
      .digest('hex');

    // Google's attestation root CA fingerprint (example - verify actual)
    const GOOGLE_ATTESTATION_ROOT = 'expected_root_fingerprint';
    if (rootFingerprint !== GOOGLE_ATTESTATION_ROOT) {
      console.error('Invalid root certificate');
      return false;
    }

    // 2. Verify certificate chain
    // (Simplified - full implementation requires X.509 chain verification)
    for (let i = 0; i < certificates.length - 1; i++) {
      const cert = certificates[i];
      const issuer = certificates[i + 1];
      
      // Verify signature
      const verify = crypto.createVerify('RSA-SHA256');
      verify.update(cert.tbsCertificate);
      if (!verify.verify(issuer.publicKey, cert.signature)) {
        console.error('Certificate chain verification failed');
        return false;
      }
    }

    // 3. Extract attestation extension from leaf certificate
    const leafCert = certificates[0];
    const attestationExtension = extractAttestationExtension(leafCert);
    
    if (!attestationExtension) {
      console.error('No attestation extension found');
      return false;
    }

    // 4. Verify challenge
    const challengeMatch = attestationExtension.attestationChallenge.toString('hex') === 
      Buffer.from(expectedChallenge).toString('hex');
    if (!challengeMatch) {
      console.error('Challenge mismatch');
      return false;
    }

    // 5. Verify package name
    const packageNameMatch = attestationExtension.teeEnforced?.attestationApplicationId?.packageInfos
      ?.some((pkg: any) => pkg.packageName === expectedPackageName);
    
    if (!packageNameMatch) {
      console.error('Package name mismatch');
      return false;
    }

    // 6. Verify key is hardware-backed
    const isHardwareBacked = attestationExtension.teeEnforced?.origin === 1; // KM_ORIGIN_GENERATED
    if (!isHardwareBacked) {
      console.error('Key is not hardware-backed');
      return false;
    }

    return true;
  } catch (error) {
    console.error('Attestation verification error:', error);
    return false;
  }
}

function extractAttestationExtension(certificate: any): AttestationExtension | null {
  // Extract X.509 extension with OID 1.3.6.1.4.1.11129.2.1.17
  // This is the Android Key Attestation extension
  // Full implementation requires ASN.1 parsing
  // For now, return null (placeholder)
  return null;
}
```

## Implementation in Hub Server

### Update validator.ts

```typescript
import { verifyAttestationCertificate } from './attestation';

export interface AttestationRequest {
  certificateChain: string[];
  challenge: string;
}

export function verifyVoucherWithAttestation(
  voucher: Voucher,
  attestation: AttestationRequest,
  expectedPackageName: string = 'com.pnm.mobileapp'
): { valid: boolean; message: string } {
  // 1. Verify voucher signature
  const signatureValid = verifySignatureP256(
    voucher.rawJson || JSON.stringify(voucher),
    voucher.publicKey!,
    voucher.signature
  );

  if (!signatureValid) {
    return { valid: false, message: 'Invalid voucher signature' };
  }

  // 2. Verify attestation certificate
  const attestationValid = verifyAttestationCertificate(
    attestation.certificateChain,
    attestation.challenge,
    expectedPackageName
  );

  if (!attestationValid) {
    return { valid: false, message: 'Invalid attestation certificate' };
  }

  return { valid: true, message: 'Voucher and attestation verified' };
}
```

## Challenge Generation

The hub server should generate a random challenge (nonce) for each attestation request:

```typescript
import * as crypto from 'crypto';

function generateChallenge(): string {
  return crypto.randomBytes(32).toString('hex');
}

// In API endpoint
app.post('/api/v1/attestation/challenge', (req, res) => {
  const challenge = generateChallenge();
  // Store challenge with expiration (e.g., 5 minutes)
  challengeStore.set(challenge, { expires: Date.now() + 300000 });
  res.json({ challenge });
});
```

## Mobile App: Request Challenge and Attest

```kotlin
// 1. Request challenge from hub
val challengeResponse = hubApiService.getAttestationChallenge()
val challenge = challengeResponse.challenge

// 2. Generate key (if not exists)
hardwareKeystoreManager.generateHardwareKey("pnm_voucher_key")

// 3. Get attestation certificate
val certificateChain = hardwareKeystoreManager.attestationCertificate("pnm_voucher_key")

// 4. Include in voucher request
val attestationRequest = AttestationRequest(
    certificateChain = certificateChain,
    challenge = challenge
)
```

## Google Play Integrity API Setup

1. **Create Google Cloud Project**
2. **Enable Play Integrity API**
3. **Create Service Account**
4. **Download Service Account JSON**
5. **Configure in hub server:**

```typescript
// hub/src/services/attestation.ts
import { google } from 'googleapis';

const auth = new google.auth.GoogleAuth({
  keyFile: process.env.GOOGLE_SERVICE_ACCOUNT_KEY,
  scopes: ['https://www.googleapis.com/auth/playintegrity']
});

export async function verifyPlayIntegrity(
  packageName: string,
  integrityToken: string
): Promise<boolean> {
  const playintegrity = google.playintegrity({ version: 'v1', auth });
  
  try {
    const response = await playintegrity.v1.decodeIntegrityToken({
      packageName: packageName,
      requestBody: {
        integrityToken: integrityToken
      }
    });

    const payload = response.data.tokenPayloadExternal;
    return payload?.accountDetails?.appLicensingVerdict === 'LICENSED' &&
           payload?.deviceIntegrity?.deviceRecognitionVerdict?.contains('MEETS_STRONG_INTEGRITY') === true;
  } catch (error) {
    console.error('Play Integrity verification failed:', error);
    return false;
  }
}
```

## Testing on Emulator

**Note:** Emulators typically don't have StrongBox or hardware-backed keys. For testing:

1. **Use software-backed keys** (fallback mode)
2. **Mock attestation certificates** in tests
3. **Use physical device** for real hardware attestation testing

## Security Considerations

1. **Challenge Expiration:** Challenges should expire quickly (5-10 minutes)
2. **Replay Prevention:** Store used challenges to prevent replay attacks
3. **Certificate Chain Validation:** Always verify the full chain to Google's root
4. **Package Name Verification:** Ensure the package name matches exactly
5. **Hardware Verification:** Verify `origin` field indicates hardware-backed key

## References

- [Android Key Attestation](https://developer.android.com/training/articles/security-key-attestation)
- [Google Play Integrity API](https://developer.android.com/google/play/integrity)
- [Key Attestation Extension Format](https://source.android.com/docs/security/features keystore/attestation)

