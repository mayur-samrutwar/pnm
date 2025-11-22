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

    // Store slip record
    await db.addSlip({
      slipId: voucher.slipId,
      payer: voucher.payerAddress,
      amount: voucher.amount.toString(),
      status: 'redeemed',
      timestamp: Date.now()
    });

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

/**
 * GET /api/v1/balance/:address
 * Get USDC balance for an Ethereum address
 */
router.get('/balance/:address', async (req: Request, res: Response) => {
  try {
    const { address } = req.params;

    // Validate address format
    const addressRegex = /^0x[a-fA-F0-9]{40}$/;
    if (!addressRegex.test(address)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid address format',
      });
    }

    // Get token address from env (Mock USDC or real USDC)
    const tokenAddress = process.env.MOCK_ERC20_ADDRESS || process.env.USDC_ADDRESS;
    if (!tokenAddress) {
      return res.status(500).json({
        status: 'error',
        reason: 'Token address not configured',
      });
    }

    // Get RPC URL
    const rpcUrl = process.env.RPC_URL;
    if (!rpcUrl) {
      return res.status(500).json({
        status: 'error',
        reason: 'RPC URL not configured',
      });
    }

    // Get balance using VaultClient
    const client = getVaultClient();
    if (!client) {
      // Create a temporary provider for balance query
      const { ethers } = require('ethers');
      const provider = new ethers.JsonRpcProvider(rpcUrl);
      const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)', 'function decimals() external view returns (uint8)'];
      const tokenContract = new ethers.Contract(tokenAddress, erc20Abi, provider);
      
      const balance = await tokenContract.balanceOf(address);
      const decimals = await tokenContract.decimals();
      
      // Convert BigInt to string for JSON serialization
      const balanceStr = balance.toString();
      const decimalsNum = Number(decimals);
      const balanceFormatted = ethers.formatUnits(balance, decimalsNum);
      
      return res.status(200).json({
        status: 'success',
        balance: balanceStr,
        balanceFormatted: balanceFormatted,
        decimals: decimalsNum,
        tokenAddress: tokenAddress,
      });
    }

    // Use VaultClient if available
    const balance = await client.getTokenBalance(address, tokenAddress);
    
    // Get decimals (assuming 6 for USDC, but we should query it)
    const { ethers } = require('ethers');
    const provider = new ethers.JsonRpcProvider(rpcUrl);
    const erc20Abi = ['function decimals() external view returns (uint8)'];
    const tokenContract = new ethers.Contract(tokenAddress, erc20Abi, provider);
    const decimals = await tokenContract.decimals();
    
    // Convert BigInt to string for JSON serialization
    const balanceStr = balance.toString();
    const decimalsNum = Number(decimals);
    const balanceFormatted = ethers.formatUnits(balance, decimalsNum);
    
    return res.status(200).json({
      status: 'success',
      balance: balanceStr,
      balanceFormatted: balanceFormatted,
      decimals: decimalsNum,
      tokenAddress: tokenAddress,
    });
  } catch (error) {
    console.error('Error getting balance:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error',
    });
  }
});

export default router;

