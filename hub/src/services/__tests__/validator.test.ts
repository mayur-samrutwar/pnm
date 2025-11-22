import { verifySchema, verifySignature, verifyExpiry, verifySignatureP256, Voucher } from '../validator';
import { ethers } from 'ethers';
import { ec as EC } from 'elliptic';

describe('Validator Service', () => {
  let testWallet: ethers.Wallet;
  let testVoucher: Voucher;

  beforeAll(() => {
    // Create a test wallet for signing
    testWallet = ethers.Wallet.createRandom();
  });

  beforeEach(() => {
    // Create a valid test voucher
    testVoucher = {
      payerAddress: testWallet.address,
      payeeAddress: '0x8ba1f109551bD432803012645Hac136c22C5e2',
      amount: 1000000000000000000,
      chainId: 1,
      cumulative: 5000000000000000000,
      counter: 42,
      expiry: Math.floor(Date.now() / 1000) + 3600, // 1 hour from now
      slipId: '550e8400-e29b-41d4-a716-446655440000',
      contractAddress: '0x1234567890123456789012345678901234567890',
      signature: '',
    };
  });

  describe('verifySchema', () => {
    it('should validate a correct voucher schema', () => {
      expect(verifySchema(testVoucher)).toBe(true);
    });

    it('should reject voucher with missing required fields', () => {
      const invalidVoucher = { ...testVoucher };
      delete (invalidVoucher as any).payerAddress;
      expect(verifySchema(invalidVoucher)).toBe(false);
    });

    it('should reject voucher with invalid address format', () => {
      const invalidVoucher = {
        ...testVoucher,
        payerAddress: 'invalid-address',
      };
      expect(verifySchema(invalidVoucher)).toBe(false);
    });

    it('should reject voucher with invalid slipId format', () => {
      const invalidVoucher = {
        ...testVoucher,
        slipId: 'not-a-uuid',
      };
      expect(verifySchema(invalidVoucher)).toBe(false);
    });
  });

  describe('verifySignature', () => {
    it('should verify a valid signature', async () => {
      // Create the voucher payload (same as in validator.ts)
      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          'address',
          'uint256',
          'uint256',
          'address',
          'address',
          'uint256',
          'uint256',
          'bytes32',
        ],
        [
          testVoucher.contractAddress,
          BigInt(testVoucher.expiry),
          BigInt(testVoucher.chainId),
          testVoucher.payerAddress,
          testVoucher.payeeAddress,
          BigInt(testVoucher.amount),
          BigInt(testVoucher.cumulative),
          ethers.id(testVoucher.slipId),
        ]
      );

      // Sign using the same method as in Vault.test.js
      // Hash the payload first, then sign the hash (signMessage applies EIP-191)
      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await testWallet.signMessage(ethers.getBytes(messageHash));

      testVoucher.signature = signature;

      const result = verifySignature(testVoucher);
      expect(result.valid).toBe(true);
      expect(result.recovered.toLowerCase()).toBe(testWallet.address.toLowerCase());
    });

    it('should reject invalid signature', () => {
      testVoucher.signature = '0x' + '1'.repeat(130); // Invalid signature
      const result = verifySignature(testVoucher);
      expect(result.valid).toBe(false);
    });

    it('should reject signature from different address', async () => {
      const otherWallet = ethers.Wallet.createRandom();
      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          'address',
          'uint256',
          'uint256',
          'address',
          'address',
          'uint256',
          'uint256',
          'bytes32',
        ],
        [
          testVoucher.contractAddress,
          BigInt(testVoucher.expiry),
          BigInt(testVoucher.chainId),
          testVoucher.payerAddress,
          testVoucher.payeeAddress,
          BigInt(testVoucher.amount),
          BigInt(testVoucher.cumulative),
          ethers.id(testVoucher.slipId),
        ]
      );

      // Sign with different wallet
      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await otherWallet.signMessage(ethers.getBytes(messageHash));

      testVoucher.signature = signature;
      const result = verifySignature(testVoucher);
      expect(result.valid).toBe(false);
    });
  });

  describe('verifyExpiry', () => {
    it('should accept non-expired voucher', () => {
      testVoucher.expiry = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
      expect(verifyExpiry(testVoucher)).toBe(true);
    });

    it('should reject expired voucher', () => {
      testVoucher.expiry = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago
      expect(verifyExpiry(testVoucher)).toBe(false);
    });
  });

  describe('verifySignatureP256', () => {
    it('should verify a valid P-256 signature', () => {
      // Create a P-256 key pair (same as Android would generate)
      const ec = new EC('p256');
      const keyPair = ec.genKeyPair();

      // Export public key in uncompressed format: 0x04 || x || y
      const pubPoint = keyPair.getPublic();
      const x = pubPoint.getX().toArray('be', 32); // 32 bytes, big-endian
      const y = pubPoint.getY().toArray('be', 32); // 32 bytes, big-endian
      const publicKeyHex = '04' + Buffer.from(x).toString('hex') + Buffer.from(y).toString('hex');
      // Should be 130 hex chars: 2 (04) + 64 (x) + 64 (y)

      // Create a voucher JSON string (what Android would sign)
      const voucherJson = JSON.stringify({
        amount: '100',
        userAddress: '0x1234567890123456789012345678901234567890',
        timestamp: Date.now(),
      });

      // Sign the voucher (same as Android Signer does)
      const messageHash = require('crypto').createHash('sha256').update(Buffer.from(voucherJson, 'utf8')).digest();
      const signature = keyPair.sign(messageHash);
      
      // Export signature in raw r||s format (same as Android)
      const r = signature.r.toArray('be', 32); // 32 bytes
      const s = signature.s.toArray('be', 32); // 32 bytes
      const signatureHex = Buffer.from(r).toString('hex') + Buffer.from(s).toString('hex');
      // Should be 128 hex chars: 64 (r) + 64 (s)

      // Verify the signature
      const isValid = verifySignatureP256(voucherJson, publicKeyHex, signatureHex);
      expect(isValid).toBe(true);
    });

    it('should reject invalid P-256 signature', () => {
      const ec = new EC('p256');
      const keyPair = ec.genKeyPair();

      const pubPoint = keyPair.getPublic();
      const x = pubPoint.getX().toArray('be', 32);
      const y = pubPoint.getY().toArray('be', 32);
      const publicKeyHex = '04' + Buffer.from(x).toString('hex') + Buffer.from(y).toString('hex');

      const voucherJson = JSON.stringify({ amount: '100' });
      const invalidSignature = '1'.repeat(128); // Invalid signature

      const isValid = verifySignatureP256(voucherJson, publicKeyHex, invalidSignature);
      expect(isValid).toBe(false);
    });

    it('should reject signature for different message', () => {
      const ec = new EC('p256');
      const keyPair = ec.genKeyPair();

      const pubPoint = keyPair.getPublic();
      const x = pubPoint.getX().toArray('be', 32);
      const y = pubPoint.getY().toArray('be', 32);
      const publicKeyHex = '04' + Buffer.from(x).toString('hex') + Buffer.from(y).toString('hex');

      // Sign message A
      const messageA = JSON.stringify({ amount: '100' });
      const messageAHash = require('crypto').createHash('sha256').update(Buffer.from(messageA, 'utf8')).digest();
      const signature = keyPair.sign(messageAHash);
      const r = signature.r.toArray('be', 32);
      const s = signature.s.toArray('be', 32);
      const signatureHex = Buffer.from(r).toString('hex') + Buffer.from(s).toString('hex');

      // Try to verify with message B
      const messageB = JSON.stringify({ amount: '200' });
      const isValid = verifySignatureP256(messageB, publicKeyHex, signatureHex);
      expect(isValid).toBe(false);
    });

    it('should handle public key with 0x prefix', () => {
      const ec = new EC('p256');
      const keyPair = ec.genKeyPair();

      const pubPoint = keyPair.getPublic();
      const x = pubPoint.getX().toArray('be', 32);
      const y = pubPoint.getY().toArray('be', 32);
      const publicKeyHex = '0x04' + Buffer.from(x).toString('hex') + Buffer.from(y).toString('hex');

      const voucherJson = JSON.stringify({ amount: '100' });
      const messageHash = require('crypto').createHash('sha256').update(Buffer.from(voucherJson, 'utf8')).digest();
      const signature = keyPair.sign(messageHash);
      const r = signature.r.toArray('be', 32);
      const s = signature.s.toArray('be', 32);
      const signatureHex = Buffer.from(r).toString('hex') + Buffer.from(s).toString('hex');

      const isValid = verifySignatureP256(voucherJson, publicKeyHex, signatureHex);
      expect(isValid).toBe(true);
    });
  });

  describe('Duplicate slip rejection simulation', () => {
    it('should handle duplicate redeem attempts', async () => {
      // This test simulates the behavior that would happen in the redeem endpoint
      // First redeem should succeed, second should fail
      
      const { getDB } = await import('../../db/inMemory');
      const db = getDB();
      await db.initialize();

      const slipId = 'test-slip-' + Date.now();

      // First attempt: slip not used
      expect(db.isSlipUsed(slipId)).toBe(false);
      await db.markSlipUsed(slipId);

      // Second attempt: slip already used
      expect(db.isSlipUsed(slipId)).toBe(true);
    });
  });
});

