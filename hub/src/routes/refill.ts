import { Router, Request, Response } from 'express';
import { ethers } from 'ethers';
import * as crypto from 'crypto';
import { getVaultClient } from './voucher';
import { getDB } from '../db/inMemory';

const router = Router();

interface RefillRequest {
  userAddress: string;
  proof?: string;
}

interface RefillTokenPayload {
  user: string;
  newLimit: number;
  expiry: number; // Unix timestamp
  nonce: string;
}

/**
 * POST /api/v1/requestRefill
 * 
 * Verifies that all previous slip claims from user are settled,
 * then returns a signed refill token
 */
router.post('/requestRefill', async (req: Request, res: Response) => {
  try {
    const { userAddress, proof }: RefillRequest = req.body;

    if (!userAddress) {
      return res.status(400).json({
        success: false,
        message: 'userAddress is required'
      });
    }

    // Normalize address
    const normalizedAddress = ethers.getAddress(userAddress);

    // Get database instance
    const db = getDB();

    // Check if all previous slips are settled
    const userSlips = db.slips.filter(slip => 
      slip.payer?.toLowerCase() === normalizedAddress.toLowerCase() ||
      slip.userAddress?.toLowerCase() === normalizedAddress.toLowerCase()
    );

    const unsettledSlips = userSlips.filter(slip => 
      slip.status !== 'redeemed' && slip.status !== 'settled'
    );

    if (unsettledSlips.length > 0) {
      return res.status(400).json({
        success: false,
        message: `User has ${unsettledSlips.length} unsettled slips. All slips must be redeemed before refill.`
      });
    }

    // Check on-chain settlement if vault client is available
    const vaultClient = getVaultClient();
    if (vaultClient) {
      try {
        // Use nonce 0 for now (in production, use proper nonce management)
        const nonce = 0;
        const isSettled = await vaultClient.isUserSettled(normalizedAddress, nonce);
        if (!isSettled) {
          return res.status(400).json({
            success: false,
            message: 'User not settled on-chain. Please wait for settlement transaction to be confirmed.'
          });
        }
      } catch (error) {
        console.error('Error checking on-chain settlement:', error);
        // Continue with off-chain check if on-chain check fails
      }
    }

    // Generate refill token
    const hubPrivateKey = process.env.HUB_PRIVATE_KEY;
    if (!hubPrivateKey) {
      return res.status(500).json({
        success: false,
        message: 'Hub private key not configured'
      });
    }

    // Generate nonce
    const nonce = crypto.randomBytes(32).toString('hex');

    // Set new limit (default: 100000, or from config)
    const newLimit = parseInt(process.env.REFILL_LIMIT || '100000', 10);

    // Set expiry (default: 30 days from now)
    const expiryDays = parseInt(process.env.REFILL_EXPIRY_DAYS || '30', 10);
    const expiry = Math.floor(Date.now() / 1000) + (expiryDays * 24 * 60 * 60);

    // Create token payload
    const payload: RefillTokenPayload = {
      user: normalizedAddress,
      newLimit,
      expiry,
      nonce
    };

    // Sign token with hub private key
    const wallet = new ethers.Wallet(hubPrivateKey);
    const payloadJson = JSON.stringify(payload);
    const payloadHash = ethers.sha256(ethers.toUtf8Bytes(payloadJson));
    const signature = await wallet.signMessage(ethers.getBytes(payloadHash));

    // Create JWT-like token: header.payload.signature
    const header = Buffer.from(JSON.stringify({ alg: 'ES256', typ: 'JWT' })).toString('base64url');
    const payloadBase64 = Buffer.from(payloadJson).toString('base64url');
    const signatureBase64 = Buffer.from(signature.slice(2)).toString('base64url'); // Remove 0x prefix
    const refillToken = `${header}.${payloadBase64}.${signatureBase64}`;

    // Store refill request
    const refillRequest = {
      userAddress: normalizedAddress,
      nonce,
      newLimit,
      expiry,
      token: refillToken,
      timestamp: Date.now(),
      proof
    };

    if (!db.refillRequests) {
      db.refillRequests = [];
    }
    db.refillRequests.push(refillRequest);

    // Save to database
    db.save();

    console.log(`Refill token issued for ${normalizedAddress}, new limit: ${newLimit}`);

    return res.json({
      success: true,
      message: 'Refill token issued successfully',
      refillToken
    });
  } catch (error: any) {
    console.error('Error processing refill request:', error);
    return res.status(500).json({
      success: false,
      message: `Error: ${error.message}`
    });
  }
});

export default router;

