import { verifySchema, verifySignature, verifyExpiry, Voucher } from '../validator';
import { ethers } from 'ethers';

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

