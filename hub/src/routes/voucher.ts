import { Router, Request, Response } from 'express';
import { Voucher, verifySchema, verifySignature, verifyExpiry, verifyContractAddress, getSchemaErrors } from '../services/validator';
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

  // Validate that private key is a real key, not a placeholder
  if (rpcUrl && privateKey && contractAddress) {
    // Check if private key is a placeholder
    if (privateKey.includes('your_hub_private_key') || 
        privateKey.includes('placeholder') ||
        privateKey.length < 64) {
      console.warn('HUB_PRIVATE_KEY is not configured properly. VaultClient will not be initialized.');
      return null;
    }

    try {
      vaultClient = new VaultClient(rpcUrl, privateKey, contractAddress, VAULT_ABI);
      return vaultClient;
    } catch (error) {
      console.error('Failed to initialize VaultClient:', error);
      return null;
    }
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
      const errors = getSchemaErrors(voucher);
      console.error('Schema validation errors:', errors);
      console.error('Voucher received:', JSON.stringify(voucher, null, 2));
      return res.status(200).json({
        valid: false,
        reason: `Voucher does not match required schema: ${errors.join(', ')}`,
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
      const errors = getSchemaErrors(voucher);
      console.error('Schema validation errors:', errors);
      console.error('Voucher received:', JSON.stringify(voucher, null, 2));
      return res.status(400).json({
        status: 'error',
        reason: `Invalid voucher schema: ${errors.join(', ')}`,
      });
    }

    // Try P-256 verification first (if publicKey and originalVoucherJson are provided)
    let sigValid = false;
    if (voucher.publicKey && voucher.originalVoucherJson) {
      const { verifySignatureP256 } = require('../services/validator');
      // Extract signature from original JSON (it might not have 0x prefix)
      let signatureToVerify = voucher.signature;
      // Remove 0x prefix if present (verifySignatureP256 handles it)
      if (signatureToVerify.startsWith('0x')) {
        signatureToVerify = signatureToVerify.substring(2);
      }
      
      // Extract public key - remove 0x prefix if present
      let publicKeyToVerify = voucher.publicKey;
      if (publicKeyToVerify.startsWith('0x')) {
        publicKeyToVerify = publicKeyToVerify.substring(2);
      }
      
      sigValid = verifySignatureP256(
        voucher.originalVoucherJson,
        publicKeyToVerify,
        signatureToVerify
      );
      if (!sigValid) {
        console.error('P-256 signature verification failed');
        console.error('Public key (normalized):', publicKeyToVerify);
        console.error('Signature (normalized):', signatureToVerify);
        console.error('Original JSON length:', voucher.originalVoucherJson.length);
        // Try to parse and check the JSON structure
        try {
          const parsed = JSON.parse(voucher.originalVoucherJson);
          console.error('Parsed JSON signature:', parsed.signature);
          console.error('Parsed JSON publicKey:', parsed.publicKey);
        } catch (e) {
          console.error('Failed to parse original JSON:', e);
        }
      }
    } else {
      // Fallback to secp256k1 verification (for Ethereum-style signatures)
      const sigResult = verifySignature(voucher);
      sigValid = sigResult.valid;
      if (!sigValid) {
        console.error('secp256k1 signature verification failed');
        console.error('Expected payer:', voucher.payerAddress);
        console.error('Recovered:', sigResult.recovered);
      }
    }
    
    if (!sigValid) {
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

    let txHash: string | undefined;
    let redemptionStatus = 'validated'; // Default: only validated, not redeemed

    // Optionally redeem on-chain
    if (REDEEM_ON_CHAIN) {
      const client = getVaultClient();
      if (client) {
        try {
          // Check if this is a P-256 voucher (has publicKey and originalVoucherJson)
          if (voucher.publicKey && voucher.originalVoucherJson) {
            // For P-256 vouchers, use redeemVoucherByHub which allows the hub to redeem
            // after verifying P-256 signature off-chain
            console.log('Redeeming P-256 voucher on-chain using hub authority...');
            console.log('Payer address (Ethereum):', voucher.payerAddress);
            console.log('Payee address:', voucher.payeeAddress);
            console.log('Amount:', voucher.amount);
            
            txHash = await client.redeemVoucherByHub(voucher);
            console.log('P-256 voucher redeemed on-chain successfully. Transaction hash:', txHash);
            redemptionStatus = 'redeemed';
          } else {
            // This is a secp256k1 voucher - can redeem on-chain
            txHash = await client.redeemVoucher(voucher, voucher.signature);
            console.log('On-chain redemption successful. Transaction hash:', txHash);
            redemptionStatus = 'redeemed';
          }
        } catch (error: any) {
          console.error('Error redeeming on-chain:', error);
          console.error('Error details:', error.message);
          if (error.data) {
            console.error('Error data:', error.data);
          }
          // Don't mark as redeemed if on-chain redemption failed
          redemptionStatus = 'validated';
        }
      } else {
        console.warn('Vault client not initialized, skipping on-chain redeem');
        redemptionStatus = 'validated';
      }
    }

    // Always store the slip record (validated or redeemed)
    // Store slip record
    await db.addSlip({
      slipId: voucher.slipId,
      payer: voucher.payerAddress,
      amount: voucher.amount.toString(),
      status: redemptionStatus === 'redeemed' ? 'redeemed' : 'validated',
      timestamp: Date.now()
    });

    // Mark slip as used (atomic operation) - prevents double redemption
    await db.markSlipUsed(voucher.slipId);

    return res.status(200).json({
      status: redemptionStatus === 'redeemed' ? 'redeemed' : 'validated',
      txHash: txHash || undefined,
      message: txHash 
        ? 'Redeemed on-chain successfully' 
        : redemptionStatus === 'validated' 
          ? 'Validated but not redeemed on-chain (P-256 voucher requires contract modification)'
          : 'Validated',
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
 * GET /api/v1/vaultBalance/:address
 * Get vault deposit balance for a user address
 */
router.get('/vaultBalance/:address', async (req: Request, res: Response) => {
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

    // Get RPC URL and vault address
    const rpcUrl = process.env.RPC_URL;
    const vaultAddress = process.env.VAULT_CONTRACT_ADDRESS;

    if (!rpcUrl || !vaultAddress) {
      return res.status(500).json({
        status: 'error',
        reason: 'Server configuration missing: RPC_URL or VAULT_CONTRACT_ADDRESS',
      });
    }

    // Get vault deposit balance
    const { ethers } = require('ethers');
    const provider = new ethers.JsonRpcProvider(rpcUrl);
    const vaultAbi = ['function deposits(address) external view returns (uint256)'];
    const vaultContract = new ethers.Contract(vaultAddress, vaultAbi, provider);
    
    console.log(`[vaultBalance] Querying vault ${vaultAddress} for address ${address}`);
    const depositBalance = await vaultContract.deposits(address);
    const depositBalanceStr = depositBalance.toString();
    console.log(`[vaultBalance] Raw balance: ${depositBalanceStr}`);
    
    // Get token decimals (assuming USDC has 6 decimals)
    const tokenAddress = process.env.MOCK_ERC20_ADDRESS || process.env.USDC_ADDRESS;
    let decimals = 6; // Default for USDC
    if (tokenAddress) {
      try {
        const erc20Abi = ['function decimals() external view returns (uint8)'];
        const tokenContract = new ethers.Contract(tokenAddress, erc20Abi, provider);
        decimals = Number(await tokenContract.decimals());
      } catch (e) {
        console.warn('Could not fetch token decimals, using default 6');
      }
    }
    
    const depositFormatted = ethers.formatUnits(depositBalance, decimals);
    
    return res.status(200).json({
      status: 'success',
      balance: depositBalanceStr,
      balanceFormatted: depositFormatted,
      decimals: decimals,
    });
  } catch (error) {
    console.error('Error getting vault balance:', error);
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

    // Query token balance directly (no need for VaultClient)
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
  } catch (error) {
    console.error('Error getting balance:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error',
    });
  }
});

/**
 * POST /api/v1/deposit
 * Deposit USDC tokens to vault
 * Expects: { userAddress, amount, signedApproveTx, signedDepositTx }
 */
router.post('/deposit', async (req: Request, res: Response) => {
  try {
    const { userAddress, amount, signedApproveTx, signedDepositTx } = req.body;

    if (!userAddress || !amount || !signedApproveTx || !signedDepositTx) {
      return res.status(400).json({
        status: 'error',
        reason: 'Missing required fields: userAddress, amount, signedApproveTx, signedDepositTx',
      });
    }

    // Validate addresses
    const addressRegex = /^0x[a-fA-F0-9]{40}$/;
    if (!addressRegex.test(userAddress)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid user address format',
      });
    }

    // Get RPC URL and contract addresses
    const rpcUrl = process.env.RPC_URL;
    const vaultAddress = process.env.VAULT_CONTRACT_ADDRESS;
    const tokenAddress = process.env.MOCK_ERC20_ADDRESS || process.env.USDC_ADDRESS;

    if (!rpcUrl || !vaultAddress || !tokenAddress) {
      return res.status(500).json({
        status: 'error',
        reason: 'Server configuration missing: RPC_URL, VAULT_CONTRACT_ADDRESS, or token address',
      });
    }

    const { ethers } = require('ethers');
    const provider = new ethers.JsonRpcProvider(rpcUrl);

    try {
      // Send approve transaction
      const approveTx = await provider.broadcastTransaction(signedApproveTx);
      await approveTx.wait();
      console.log('Approve transaction confirmed:', approveTx.hash);

      // Send deposit transaction
      const depositTx = await provider.broadcastTransaction(signedDepositTx);
      const receipt = await depositTx.wait();
      console.log('Deposit transaction confirmed:', depositTx.hash);

      return res.status(200).json({
        status: 'success',
        message: 'Deposit successful',
        approveTxHash: approveTx.hash,
        depositTxHash: depositTx.hash,
      });
    } catch (error: any) {
      console.error('Error broadcasting transactions:', error);
      return res.status(500).json({
        status: 'error',
        reason: `Transaction failed: ${error.message}`,
      });
    }
  } catch (error) {
    console.error('Error processing deposit:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error',
    });
  }
});

/**
 * POST /api/v1/rpc
 * Proxy Ethereum RPC requests to the local Hardhat node
 * This allows the mobile app to make RPC calls through the hub server
 */
router.post('/rpc', async (req: Request, res: Response) => {
  try {
    const rpcUrl = process.env.RPC_URL;
    if (!rpcUrl) {
      return res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32603, message: 'RPC URL not configured' },
        id: req.body.id || null
      });
    }

    // Forward the JSON-RPC request directly to the local Hardhat node
    const rpcRequest = req.body;
    
    try {
      // Use Node.js built-in fetch (available in Node 18+)
      const response = await fetch(rpcUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(rpcRequest)
      });
      
      if (!response.ok) {
        throw new Error(`RPC request failed: ${response.status} ${response.statusText}`);
      }
      
      const data = await response.json();
      return res.json(data);
    } catch (error: any) {
      console.error('RPC proxy error:', error);
      return res.json({
        jsonrpc: '2.0',
        error: { code: -32603, message: error.message || 'Internal error' },
        id: rpcRequest.id || null
      });
    }
  } catch (error) {
    console.error('Error in RPC proxy:', error);
    return res.status(500).json({
      jsonrpc: '2.0',
      error: { code: -32603, message: 'Internal server error' },
      id: req.body.id || null
    });
  }
});

export default router;

