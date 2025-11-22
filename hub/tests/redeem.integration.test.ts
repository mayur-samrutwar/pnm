import request from 'supertest';
import { ethers } from 'ethers';
import { app } from '../src/index';
import { getDB } from '../src/db/inMemory';
import { Voucher } from '../src/services/validator';
import * as fs from 'fs';
import * as path from 'path';
import { randomUUID } from 'crypto';

describe('Redeem Integration Tests', () => {
  let testWallet: ReturnType<typeof ethers.Wallet.createRandom>;
  let db: ReturnType<typeof getDB>;
  const testDbPath = path.join(__dirname, '../data/test-db-export.json');

  beforeAll(async () => {
    // Create a test wallet for signing vouchers
    testWallet = ethers.Wallet.createRandom();
    
    // Initialize database
    db = getDB();
    await db.initialize();
  });

  // Helper to get a valid payee address
  function getPayeeAddress(): string {
    return ethers.getAddress('0xAf8822Da0EF804036353d942b2dADd5a763E179D');
  }

  beforeEach(() => {
    // Reset database before each test
    db.reset();
  });

  afterAll(async () => {
    // Clean up test DB file if it exists
    if (fs.existsSync(testDbPath)) {
      fs.unlinkSync(testDbPath);
    }
  });

  /**
   * Helper function to create a signed voucher
   */
  async function createSignedVoucher(
    overrides: Partial<Voucher> = {}
  ): Promise<Voucher> {
    // Build base voucher with all required fields
    const baseVoucher: Omit<Voucher, 'signature'> = {
      payerAddress: testWallet.address,
      payeeAddress: getPayeeAddress(),
      amount: 1000000000000000000,
      chainId: 1,
      cumulative: 5000000000000000000,
      counter: 42,
      expiry: Math.floor(Date.now() / 1000) + 3600, // 1 hour from now
      slipId: randomUUID(),
      contractAddress: '0x1234567890123456789012345678901234567890',
    };

    // Apply overrides to get final voucher data (without signature)
    const finalVoucherData = {
      ...baseVoucher,
      ...overrides,
    };

    // Create the voucher payload (same as in validator.ts)
    const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
      [
        'address', // contractAddress
        'uint256', // expiry
        'uint256', // chainId
        'address', // payerAddress
        'address', // payeeAddress
        'uint256', // amount
        'uint256', // cumulative
        'bytes32', // slipId
      ],
      [
        finalVoucherData.contractAddress,
        BigInt(finalVoucherData.expiry),
        BigInt(finalVoucherData.chainId),
        finalVoucherData.payerAddress,
        finalVoucherData.payeeAddress,
        BigInt(finalVoucherData.amount),
        BigInt(finalVoucherData.cumulative),
        ethers.id(finalVoucherData.slipId), // Convert UUID to bytes32
      ]
    );

    // Sign the voucher with the final values
    const messageHash = ethers.keccak256(voucherPayload);
    const signature = await testWallet.signMessage(ethers.getBytes(messageHash));

    // Return complete voucher with signature
    const finalVoucher: Voucher = {
      ...finalVoucherData,
      signature,
    };

    return finalVoucher;
  }

  describe('Concurrent Redemption (Race Condition)', () => {
    it('should only allow one redemption when same voucher is redeemed concurrently', async () => {
      // Create a voucher
      const voucher = await createSignedVoucher();
      const slipId = voucher.slipId;

      // Make two concurrent redemption requests
      const [response1, response2] = await Promise.all([
        request(app)
          .post('/api/v1/redeem')
          .send({ voucher })
          .expect((res) => {
            // One should succeed, one should fail
            expect([200, 400]).toContain(res.status);
          }),
        request(app)
          .post('/api/v1/redeem')
          .send({ voucher })
          .expect((res) => {
            // One should succeed, one should fail
            expect([200, 400]).toContain(res.status);
          }),
      ]);

      // Exactly one should succeed (status: reserved)
      const successCount = [response1, response2].filter(
        (res) => res.status === 200 && res.body.status === 'reserved'
      ).length;
      expect(successCount).toBe(1);

      // Exactly one should fail (duplicate/rejected)
      const failureCount = [response1, response2].filter(
        (res) => res.status === 400 && res.body.status === 'error'
      ).length;
      expect(failureCount).toBe(1);

      // Verify the error message for the failed one
      const failedResponse = [response1, response2].find(
        (res) => res.status === 400
      );
      expect(failedResponse?.body.reason).toBe('Voucher slip already used');

      // Verify slip is marked as used in DB
      expect(db.isSlipUsed(slipId)).toBe(true);

      // Export DB state for debugging
      await db.exportToFile(testDbPath);
      expect(fs.existsSync(testDbPath)).toBe(true);
    });

    it('should reject vouchers with same slipId (voucher A and B)', async () => {
      // Create voucher A
      const slipId = randomUUID();
      const voucherA = await createSignedVoucher({ slipId });

      // Create voucher B with same slipId but different other fields
      // Note: Changing amount/counter requires new signature, but same slipId
      const voucherB = await createSignedVoucher({
        slipId, // Same slipId
        amount: 2000000000000000000, // Different amount
        counter: 43, // Different counter
        cumulative: 6000000000000000000, // Different cumulative to match new amount
      });

      // Redeem voucher A first
      const responseA = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucherA })
        .expect(200);

      expect(responseA.body.status).toBe('reserved');

      // Try to redeem voucher B (same slipId) - should fail
      const responseB = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucherB })
        .expect(400);

      expect(responseB.body.status).toBe('error');
      expect(responseB.body.reason).toBe('Voucher slip already used');

      // Export DB state for debugging
      await db.exportToFile(testDbPath);
    });

    it('should handle vouchers with same cumulative/counter conflict', async () => {
      // Create voucher A with specific cumulative and counter
      const cumulative = 1000000000000000000;
      const counter = 1;
      const voucherA = await createSignedVoucher({
        cumulative,
        counter,
        slipId: randomUUID(),
      });

      // Create voucher B with same cumulative and counter but different slipId
      const voucherB = await createSignedVoucher({
        cumulative,
        counter,
        slipId: randomUUID(),
        amount: 2000000000000000000, // Different amount
      });

      // Both should redeem successfully since they have different slipIds
      // (The current implementation only checks slipId, not cumulative/counter)
      const responseA = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucherA })
        .expect(200);

      const responseB = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucherB })
        .expect(200);

      expect(responseA.body.status).toBe('reserved');
      expect(responseB.body.status).toBe('reserved');

      // Export DB state for debugging
      await db.exportToFile(testDbPath);
    });
  });

  describe('Sequential Redemption', () => {
    it('should return reserved on first redeem, then duplicate rejected on second redeem', async () => {
      // Create a valid voucher
      const voucher = await createSignedVoucher();

      // First redemption - should succeed
      const firstResponse = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher })
        .expect(200);

      expect(firstResponse.body.status).toBe('reserved');
      expect(firstResponse.body.txHash).toBeUndefined(); // No on-chain redeem in test

      // Second redemption of same voucher - should fail
      const secondResponse = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher })
        .expect(400);

      expect(secondResponse.body.status).toBe('error');
      expect(secondResponse.body.reason).toBe('Voucher slip already used');

      // Verify slip is marked as used
      expect(db.isSlipUsed(voucher.slipId)).toBe(true);

      // Export DB state for debugging
      await db.exportToFile(testDbPath);
      const dbState = JSON.parse(fs.readFileSync(testDbPath, 'utf-8'));
      expect(dbState.usedSlips).toContain(voucher.slipId);
    });

    it('should allow redeeming different vouchers sequentially', async () => {
      // Create two different vouchers
      const voucher1 = await createSignedVoucher({
        slipId: randomUUID(),
      });
      const voucher2 = await createSignedVoucher({
        slipId: randomUUID(),
      });

      // Redeem first voucher
      const response1 = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucher1 })
        .expect(200);

      expect(response1.body.status).toBe('reserved');

      // Redeem second voucher (different slipId) - should succeed
      const response2 = await request(app)
        .post('/api/v1/redeem')
        .send({ voucher: voucher2 })
        .expect(200);

      expect(response2.body.status).toBe('reserved');

      // Both slips should be marked as used
      expect(db.isSlipUsed(voucher1.slipId)).toBe(true);
      expect(db.isSlipUsed(voucher2.slipId)).toBe(true);

      // Export DB state for debugging
      await db.exportToFile(testDbPath);
    });
  });
});

