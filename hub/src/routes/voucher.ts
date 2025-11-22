import { Router, Request, Response } from 'express';
import { Voucher, verifySchema, verifySignature, verifyExpiry, verifyContractAddress } from '../services/validator';
import { VaultClient, VAULT_ABI } from '../services/vaultClient';
import { getDB } from '../db/inMemory';

const router = Router();

// Configuration: set to true to actually call on-chain redeem, false to just reserve
const REDEEM_ON_CHAIN = process.env.REDEEM_ON_CHAIN === 'true';

let vaultClient: VaultClient | null = null;

// Initialize vault client if required env vars are present
function getVaultClient(): VaultClient | null {
  if (vaultClient) {
    return vaultClient;
  }

  const rpcUrl = process.env.RPC_URL;
  const privateKey = process.env.HUB_PRIVATE_KEY;
  const contractAddress = process.env.VAULT_CONTRACT_ADDRESS;

  if (rpcUrl && privateKey && contractAddress) {
    vaultClient = new VaultClient(rpcUrl, privateKey, contractAddress, VAULT_ABI);
    return vaultClient;
  }

  return null;
}

/**
 * POST /api/v1/validate
 * Validates a voucher's schema, signature, expiry, and contract address
 */
router.post('/validate', async (req: Request, res: Response) => {
  try {
    const { voucher } = req.body;

    if (!voucher) {
      return res.status(400).json({
        valid: false,
        reason: 'Missing voucher in request body',
      });
    }

    // Verify JSON schema
    if (!verifySchema(voucher)) {
      return res.status(200).json({
        valid: false,
        reason: 'Voucher does not match required schema',
      });
    }

    // Verify signature
    const sigResult = verifySignature(voucher);
    if (!sigResult.valid) {
      return res.status(200).json({
        valid: false,
        reason: `Invalid signature. Expected payer: ${voucher.payerAddress}, Recovered: ${sigResult.recovered}`,
      });
    }

    // Verify expiry
    if (!verifyExpiry(voucher)) {
      return res.status(200).json({
        valid: false,
        reason: 'Voucher has expired',
      });
    }

    // Verify contract address matches expected
    const expectedContractAddress = process.env.VAULT_CONTRACT_ADDRESS;
    if (expectedContractAddress && !verifyContractAddress(voucher, expectedContractAddress)) {
      return res.status(200).json({
        valid: false,
        reason: `Contract address mismatch. Expected: ${expectedContractAddress}, Got: ${voucher.contractAddress}`,
      });
    }

    return res.status(200).json({
      valid: true,
    });
  } catch (error) {
    console.error('Error validating voucher:', error);
    return res.status(500).json({
      valid: false,
      reason: 'Internal server error during validation',
    });
  }
});

/**
 * POST /api/v1/redeem
 * Redeems a voucher atomically (checks DB, marks used, optionally calls on-chain)
 */
router.post('/redeem', async (req: Request, res: Response) => {
  try {
    const { voucher } = req.body;

    if (!voucher) {
      return res.status(400).json({
        status: 'error',
        reason: 'Missing voucher in request body',
      });
    }

    // Validate voucher first
    if (!verifySchema(voucher)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid voucher schema',
      });
    }

    const sigResult = verifySignature(voucher);
    if (!sigResult.valid) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid signature',
      });
    }

    if (!verifyExpiry(voucher)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Voucher expired',
      });
    }

    // Check if slip has already been used (atomic check)
    const db = getDB();
    if (db.isSlipUsed(voucher.slipId)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Voucher slip already used',
      });
    }

    // Mark slip as used (atomic operation)
    await db.markSlipUsed(voucher.slipId);

    let txHash: string | undefined;

    // Optionally redeem on-chain
    if (REDEEM_ON_CHAIN) {
      const client = getVaultClient();
      if (client) {
        try {
          txHash = await client.redeemVoucher(voucher, voucher.signature);
        } catch (error) {
          console.error('Error redeeming on-chain:', error);
          // Note: slip is already marked as used, so we return reserved status
          // In production, you might want to implement a rollback mechanism
        }
      } else {
        console.warn('Vault client not initialized, skipping on-chain redeem');
      }
    }

    return res.status(200).json({
      status: 'reserved',
      txHash,
    });
  } catch (error) {
    console.error('Error redeeming voucher:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error during redemption',
    });
  }
});

/**
 * POST /api/v1/depositWebhook
 * Records a deposit in the database
 */
router.post('/depositWebhook', async (req: Request, res: Response) => {
  try {
    const { user, amount, token, txHash } = req.body;

    if (!user || !amount || !token || !txHash) {
      return res.status(400).json({
        status: 'error',
        reason: 'Missing required fields: user, amount, token, txHash',
      });
    }

    // Validate addresses
    const addressRegex = /^0x[a-fA-F0-9]{40}$/;
    if (!addressRegex.test(user) || !addressRegex.test(token)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid address format',
      });
    }

    // Record deposit
    const db = getDB();
    await db.recordDeposit(user, amount.toString(), token, txHash);

    return res.status(200).json({
      status: 'success',
      message: 'Deposit recorded',
    });
  } catch (error) {
    console.error('Error recording deposit:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error',
    });
  }
});

export default router;

