import { Router, Request, Response } from 'express';
import { Voucher, verifySchema, verifySignature, verifyExpiry, verifyContractAddress, getSchemaErrors } from '../services/validator';
import { VaultClient, VAULT_ABI } from '../services/vaultClient';
import { ChainRegistry } from '../services/chainRegistry';
import { HyperlaneBridge } from '../services/hyperlaneBridge';
import { getDB } from '../db/inMemory';
import { ethers } from 'ethers';

const router = Router();

// Configuration: set to true to actually call on-chain redeem, false to just reserve
// Use a function to get the value dynamically in case env vars are loaded after module init
function getRedeemOnChain(): boolean {
  const value = process.env.REDEEM_ON_CHAIN;
  return value === 'true' || value === '1' || value === 'True' || value === 'TRUE';
}
const REDEEM_ON_CHAIN = getRedeemOnChain();

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
 * Supports multichain: accepts preferredChainId and bridges funds if needed
 */
router.post('/redeem', async (req: Request, res: Response) => {
  try {
    const { voucher, preferredChainId } = req.body;

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
      // Check if this slip was only validated (not redeemed) - allow retrying on-chain redemption
      const existingSlip = db.getState().slips.find(s => s.slipId === voucher.slipId);
      if (existingSlip && existingSlip.status === 'validated') {
        // Allow retrying on-chain redemption for previously validated slips
        console.log('[REDEEM] Slip was previously validated but not redeemed, allowing retry...');
        await db.removeUsedSlip(voucher.slipId);
      } else if (existingSlip && existingSlip.status === 'redeemed') {
        // Already redeemed on-chain - don't allow retrying
        return res.status(400).json({
          status: 'error',
          reason: 'Voucher slip already redeemed on-chain',
        });
      } else {
        // Unknown state - don't allow
        return res.status(400).json({
          status: 'error',
          reason: 'Voucher slip already used',
        });
      }
    }

    let txHash: string | undefined;
    let redemptionStatus = 'validated'; // Default: only validated, not redeemed
    let bridgeInfo: any = undefined;

    // Determine target chain (preferred chain or default)
    const targetChainId = preferredChainId || voucher.chainId || 84532; // Default to Base Sepolia
    
    if (!ChainRegistry.isChainSupported(targetChainId)) {
      return res.status(400).json({
        status: 'error',
        reason: `Unsupported chain: ${targetChainId}`,
      });
    }

    // Optionally redeem on-chain
    const redeemOnChain = getRedeemOnChain();
    console.log('[REDEEM] Starting multichain redemption process...');
    console.log('[REDEEM] Target chain:', targetChainId);
    console.log('[REDEEM] Payer address:', voucher.payerAddress);
    console.log('[REDEEM] Required cumulative:', voucher.cumulative);
    
    if (redeemOnChain) {
      try {
        // Initialize Hyperlane bridge
        const bridge = new HyperlaneBridge();
        
        // Step 1: Check deposits on target chain
        console.log('[REDEEM] Checking deposits on target chain...');
        const depositsOnTarget = await bridge.getDepositBalance(targetChainId, voucher.payerAddress);
        console.log('[REDEEM] Deposits on target chain:', ethers.formatUnits(depositsOnTarget, 6), 'USDC');
        
        // The contract now checks: deposits >= amount (not cumulative)
        // It also checks cumulative >= maxRedeemedCumulative to ensure vouchers are redeemed in order
        const voucherAmount = BigInt(voucher.amount);
        const voucherCumulative = BigInt(voucher.cumulative);
        
        console.log('[REDEEM] Voucher amount (to redeem):', ethers.formatUnits(voucherAmount, 6), 'USDC');
        console.log('[REDEEM] Voucher cumulative:', ethers.formatUnits(voucherCumulative, 6), 'USDC');
        
        // Check if we have enough deposits to cover this voucher's amount
        let needsBridging = false;
        let sourceChainId: number | null = null;
        let amountToBridge = BigInt(0);
          
        if (depositsOnTarget < voucherAmount) {
          // Step 2: Calculate how much we need to bridge
          const shortfall = voucherAmount - depositsOnTarget;
          console.log('[REDEEM] Shortfall on target chain:', ethers.formatUnits(shortfall, 6), 'USDC');
          
          // Find chain with deposits to bridge from
          const supportedChains = ChainRegistry.getSupportedChainIds();
          let bestSourceChain: number | null = null;
          let maxAvailable = BigInt(0);
          
          for (const chainId of supportedChains) {
            if (chainId === targetChainId) continue; // Skip target chain
            
            try {
              const balance = await bridge.getDepositBalance(chainId, voucher.payerAddress);
              if (balance > maxAvailable) {
                maxAvailable = balance;
                bestSourceChain = chainId;
              }
              console.log(`[REDEEM] Deposits on chain ${chainId}:`, ethers.formatUnits(balance, 6), 'USDC');
            } catch (error) {
              console.warn(`[REDEEM] Failed to check balance on chain ${chainId}:`, error);
            }
          }
          
          if (!bestSourceChain || maxAvailable === BigInt(0)) {
            return res.status(400).json({
              status: 'error',
              reason: `Insufficient deposits to redeem voucher. Required: ${ethers.formatUnits(voucherAmount, 6)} USDC, Available on target chain: ${ethers.formatUnits(depositsOnTarget, 6)} USDC, No deposits found on other chains.`,
            });
          }
          
          // Bridge the shortfall amount (or as much as available, whichever is less)
          amountToBridge = shortfall < maxAvailable ? shortfall : maxAvailable;
          sourceChainId = bestSourceChain;
          needsBridging = true;
          
          console.log('[REDEEM] Found source chain:', sourceChainId, 'with', ethers.formatUnits(maxAvailable, 6), 'USDC');
          console.log('[REDEEM] Will bridge:', ethers.formatUnits(amountToBridge, 6), 'USDC');
        }
        
        // Step 3: Bridge funds if needed
        if (needsBridging && sourceChainId && amountToBridge > 0) {
          console.log('[REDEEM] Bridging funds from chain', sourceChainId, 'to', targetChainId);
            
          // Get VaultClient for source chain to withdraw
          const sourceConfig = ChainRegistry.getChainConfig(sourceChainId)!;
          const sourceClient = VaultClient.createForChain(
            sourceChainId,
            process.env.HUB_PRIVATE_KEY!,
            VAULT_ABI
          );
          
          // Get native USDC address on source chain
          const nativeUSDC = ChainRegistry.getNativeUSDCAddress(sourceChainId);
          if (!nativeUSDC) {
            throw new Error(`No native USDC address for chain ${sourceChainId}`);
          }
          
          // Get actual available balance on source chain
          const depositsOnSource = await bridge.getDepositBalance(sourceChainId, voucher.payerAddress);
          const actualBridgeAmount = amountToBridge < depositsOnSource ? amountToBridge : depositsOnSource;
          
          console.log('[REDEEM] Deposits on source chain:', ethers.formatUnits(depositsOnSource, 6), 'USDC');
          console.log('[REDEEM] Withdrawing', ethers.formatUnits(actualBridgeAmount, 6), 'USDC from Vault on source chain...');
          
          // Withdraw from source chain Vault - only withdraw what we need to bridge
          const withdrawTxHash = await sourceClient.withdraw(nativeUSDC, actualBridgeAmount);
          console.log('[REDEEM] Withdrawn from Vault:', withdrawTxHash);
          
          // Wait for withdrawal to be confirmed
          await new Promise(resolve => setTimeout(resolve, 3000));
          
          // Bridge USDC - bridge the actual amount withdrawn
          console.log('[REDEEM] Bridging', ethers.formatUnits(actualBridgeAmount, 6), 'USDC...');
          const bridgeResult = await bridge.bridgeUSDC(
            sourceChainId,
            targetChainId,
            voucher.payerAddress,
            actualBridgeAmount
          );
          
          bridgeInfo = {
            sourceChain: sourceChainId,
            targetChain: targetChainId,
            messageTxHash: bridgeResult.messageTxHash,
            transferTxHash: bridgeResult.transferTxHash,
          };
          
          console.log('[REDEEM] Bridge initiated:', bridgeResult);
          
          // Wait for bridge confirmation (simplified - in production use proper polling)
          console.log('[REDEEM] Waiting for bridge confirmation...');
          const bridgeConfirmed = await bridge.waitForMessageDelivery(
            bridgeResult.messageId,
            targetChainId,
            300000 // 5 minutes timeout
          );
          
          if (!bridgeConfirmed) {
            console.warn('[REDEEM] Bridge confirmation timeout, but continuing...');
              }
          
          console.log('[REDEEM] Bridge confirmed, proceeding with redemption...');
        }
        
        // Step 4: Redeem on target chain
        const targetClient = VaultClient.createForChain(
          targetChainId,
          process.env.HUB_PRIVATE_KEY!,
          VAULT_ABI
        );
        
        // Check if this is a P-256 voucher
        const isP256Voucher = !!(voucher.publicKey && voucher.originalVoucherJson);
        console.log('[REDEEM] Is P-256 voucher:', isP256Voucher);
        
        if (isP256Voucher) {
          // For P-256 vouchers, use redeemVoucherByHub
          // Add chainId and contractAddress for target chain
          const targetConfig = ChainRegistry.getChainConfig(targetChainId)!;
          const voucherWithChain = {
            ...voucher,
            chainId: targetChainId,
            contractAddress: targetConfig.vaultAddress,
          };
          
          txHash = await targetClient.redeemVoucherByHub(voucherWithChain, targetChainId);
            console.log('[REDEEM] ✅ P-256 voucher redeemed on-chain successfully. Transaction hash:', txHash);
          } else {
          // Secp256k1 voucher
          const targetConfig = ChainRegistry.getChainConfig(targetChainId)!;
          const voucherWithChain = {
            ...voucher,
            chainId: targetChainId,
            contractAddress: targetConfig.vaultAddress,
          };
          
          txHash = await targetClient.redeemVoucher(voucherWithChain, voucher.signature);
            console.log('[REDEEM] ✅ On-chain redemption successful. Transaction hash:', txHash);
        }
        
            redemptionStatus = 'redeemed';
        } catch (error: any) {
        console.error('[REDEEM] ❌ Error in multichain redemption:', error);
          console.error('[REDEEM] Error message:', error.message);
          console.error('[REDEEM] Error stack:', error.stack);
          
        redemptionStatus = 'validated';
      }
      } else {
        console.log('[REDEEM] On-chain redemption disabled (REDEEM_ON_CHAIN=false)');
    }

    // Always store the slip record (validated or redeemed)
    // Update existing slip or create new one
    const existingSlip = db.getState().slips.find(s => s.slipId === voucher.slipId);
    if (existingSlip) {
      // Update existing slip status
      await db.updateSlipStatus(voucher.slipId, redemptionStatus === 'redeemed' ? 'redeemed' : 'validated');
    } else {
      // Store new slip record
      await db.addSlip({
        slipId: voucher.slipId,
        payer: voucher.payerAddress,
        amount: voucher.amount.toString(),
        status: redemptionStatus === 'redeemed' ? 'redeemed' : 'validated',
        timestamp: Date.now()
      });
    }

    // Only mark slip as used if redemption actually succeeded
    // If on-chain redemption failed, the slip will be marked as 'validated' status
    // and can be retried (the check above will allow retrying validated slips)
    if (redemptionStatus === 'redeemed') {
      await db.markSlipUsed(voucher.slipId);
    }

    return res.status(200).json({
      status: redemptionStatus === 'redeemed' ? 'redeemed' : 'validated',
      txHash: txHash || undefined,
      bridgeInfo: bridgeInfo || undefined,
      targetChainId: targetChainId,
      message: txHash 
        ? (bridgeInfo ? 'Bridged and redeemed on-chain successfully' : 'Redeemed on-chain successfully')
        : redemptionStatus === 'validated' 
          ? (voucher.publicKey && voucher.originalVoucherJson 
              ? 'Validated but on-chain redemption failed. Check hub logs for details.'
              : 'Validated but not redeemed on-chain')
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
 * Supports multichain: accepts chainId query parameter (defaults to combined balance from all chains)
 * If chainId is not provided, returns combined balance from all supported chains
 */
router.get('/vaultBalance/:address', async (req: Request, res: Response) => {
  try {
    const { address } = req.params;
    const chainId = req.query.chainId ? Number(req.query.chainId) : null;

    // Validate address format
    const addressRegex = /^0x[a-fA-F0-9]{40}$/;
    if (!addressRegex.test(address)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid address format',
      });
    }

    // If chainId is provided, return balance for that specific chain
    if (chainId && ChainRegistry.isChainSupported(chainId)) {
      const config = ChainRegistry.getChainConfig(chainId);
      if (!config || !config.vaultAddress || !config.rpcUrl) {
      return res.status(500).json({
        status: 'error',
          reason: `Chain configuration missing for chain ${chainId}`,
      });
    }

    // Get vault deposit balance
      const provider = new ethers.JsonRpcProvider(config.rpcUrl);
    const vaultAbi = ['function deposits(address) external view returns (uint256)'];
      const vaultContract = new ethers.Contract(config.vaultAddress, vaultAbi, provider);
    
      console.log(`[vaultBalance] Querying vault ${config.vaultAddress} on chain ${chainId} for address ${address}`);
    const depositBalance = await vaultContract.deposits(address);
    const depositBalanceStr = depositBalance.toString();
    console.log(`[vaultBalance] Raw balance: ${depositBalanceStr}`);
    
      // Get token decimals (USDC has 6 decimals)
      let decimals = 6;
      if (config.nativeUSDCAddress) {
      try {
        const erc20Abi = ['function decimals() external view returns (uint8)'];
          const tokenContract = new ethers.Contract(config.nativeUSDCAddress, erc20Abi, provider);
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
        chainId: chainId,
      });
    }

    // If no chainId provided, fetch combined balance from all supported chains
    const supportedChains = ChainRegistry.getSupportedChainIds();
    let totalBalance = BigInt(0);
    const balancesByChain: Array<{ chainId: number; balance: string; balanceFormatted: string }> = [];
    let decimals = 6; // Default USDC decimals

    console.log(`[vaultBalance] Fetching combined vault balance from all chains for address ${address}`);

    for (const chainId of supportedChains) {
      try {
        const config = ChainRegistry.getChainConfig(chainId);
        if (!config || !config.vaultAddress || !config.rpcUrl) {
          console.warn(`[vaultBalance] Skipping chain ${chainId}: configuration missing`);
          continue;
        }

        const provider = new ethers.JsonRpcProvider(config.rpcUrl);
        const vaultAbi = ['function deposits(address) external view returns (uint256)'];
        const vaultContract = new ethers.Contract(config.vaultAddress, vaultAbi, provider);
        
        console.log(`[vaultBalance] Querying vault ${config.vaultAddress} on chain ${chainId} for address ${address}`);
        const depositBalance = await vaultContract.deposits(address);
        
        // Get token decimals (USDC has 6 decimals)
        let chainDecimals = 6;
        if (config.nativeUSDCAddress) {
          try {
            const erc20Abi = ['function decimals() external view returns (uint8)'];
            const tokenContract = new ethers.Contract(config.nativeUSDCAddress, erc20Abi, provider);
            chainDecimals = Number(await tokenContract.decimals());
          } catch (e) {
            console.warn(`[vaultBalance] Could not fetch token decimals for chain ${chainId}, using default 6`);
          }
        }
        decimals = chainDecimals; // Use decimals from first chain (should be same for USDC)
        
        const balanceFormatted = ethers.formatUnits(depositBalance, decimals);
        totalBalance += depositBalance;
        
        balancesByChain.push({
          chainId: chainId,
          balance: depositBalance.toString(),
          balanceFormatted: balanceFormatted,
        });
        
        console.log(`[vaultBalance] Chain ${chainId}: ${balanceFormatted} USDC`);
      } catch (error: any) {
        console.error(`[vaultBalance] Error fetching vault balance from chain ${chainId}:`, error.message);
        // Continue with other chains even if one fails
      }
    }

    const totalBalanceFormatted = ethers.formatUnits(totalBalance, decimals);
    console.log(`[vaultBalance] Combined vault balance: ${totalBalanceFormatted} USDC across ${balancesByChain.length} chains`);

    return res.status(200).json({
      status: 'success',
      balance: totalBalance.toString(),
      balanceFormatted: totalBalanceFormatted,
      decimals: decimals,
      chainId: null, // null indicates combined balance
      balancesByChain: balancesByChain,
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
 * Supports multichain: accepts chainId query parameter (defaults to combined balance from all chains)
 * If chainId is not provided, returns combined balance from all supported chains
 */
router.get('/balance/:address', async (req: Request, res: Response) => {
  try {
    const { address } = req.params;
    const chainId = req.query.chainId ? Number(req.query.chainId) : null;

    // Validate address format
    const addressRegex = /^0x[a-fA-F0-9]{40}$/;
    if (!addressRegex.test(address)) {
      return res.status(400).json({
        status: 'error',
        reason: 'Invalid address format',
      });
    }

    // If chainId is provided, return balance for that specific chain
    if (chainId && ChainRegistry.isChainSupported(chainId)) {
      const config = ChainRegistry.getChainConfig(chainId);
      if (!config || !config.nativeUSDCAddress || !config.rpcUrl) {
      return res.status(500).json({
        status: 'error',
          reason: `Chain configuration missing for chain ${chainId}`,
      });
    }

      // Use native USDC address for the chain
      const tokenAddress = config.nativeUSDCAddress;
      const rpcUrl = config.rpcUrl;

      // Query token balance directly
    const provider = new ethers.JsonRpcProvider(rpcUrl);
    const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)', 'function decimals() external view returns (uint8)'];
    const tokenContract = new ethers.Contract(tokenAddress, erc20Abi, provider);
    
      console.log(`[balance] Querying USDC balance on chain ${chainId} for address ${address}`);
    const balance = await tokenContract.balanceOf(address);
    const decimals = await tokenContract.decimals();
    
    // Convert BigInt to string for JSON serialization
    const balanceStr = balance.toString();
    const decimalsNum = Number(decimals);
    const balanceFormatted = ethers.formatUnits(balance, decimalsNum);
      
      console.log(`[balance] Balance: ${balanceFormatted} USDC on chain ${chainId}`);
    
    return res.status(200).json({
      status: 'success',
      balance: balanceStr,
      balanceFormatted: balanceFormatted,
      decimals: decimalsNum,
      tokenAddress: tokenAddress,
        chainId: chainId,
      });
    }

    // If no chainId provided, fetch combined balance from all supported chains
    const supportedChains = ChainRegistry.getSupportedChainIds();
    let totalBalance = BigInt(0);
    const balancesByChain: Array<{ chainId: number; balance: string; balanceFormatted: string }> = [];
    let decimals = 6; // Default USDC decimals

    console.log(`[balance] Fetching combined USDC balance from all chains for address ${address}`);

    for (const chainId of supportedChains) {
      try {
        const config = ChainRegistry.getChainConfig(chainId);
        if (!config || !config.nativeUSDCAddress || !config.rpcUrl) {
          console.warn(`[balance] Skipping chain ${chainId}: configuration missing`);
          continue;
        }

        const provider = new ethers.JsonRpcProvider(config.rpcUrl);
        const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)', 'function decimals() external view returns (uint8)'];
        const tokenContract = new ethers.Contract(config.nativeUSDCAddress, erc20Abi, provider);
        
        const balance = await tokenContract.balanceOf(address);
        const chainDecimals = await tokenContract.decimals();
        decimals = Number(chainDecimals); // Use decimals from first chain (should be same for USDC)
        
        const balanceFormatted = ethers.formatUnits(balance, decimals);
        totalBalance += balance;
        
        balancesByChain.push({
          chainId: chainId,
          balance: balance.toString(),
          balanceFormatted: balanceFormatted,
        });
        
        console.log(`[balance] Chain ${chainId}: ${balanceFormatted} USDC`);
      } catch (error: any) {
        console.error(`[balance] Error fetching balance from chain ${chainId}:`, error.message);
        // Continue with other chains even if one fails
      }
    }

    const totalBalanceFormatted = ethers.formatUnits(totalBalance, decimals);
    console.log(`[balance] Combined balance: ${totalBalanceFormatted} USDC across ${balancesByChain.length} chains`);

    return res.status(200).json({
      status: 'success',
      balance: totalBalance.toString(),
      balanceFormatted: totalBalanceFormatted,
      decimals: decimals,
      chainId: null, // null indicates combined balance
      balancesByChain: balancesByChain,
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
 * Expects: { userAddress, amount, signedApproveTx, signedDepositTx, chainId? }
 * Supports multichain: accepts optional chainId (defaults to Base Sepolia)
 */
router.post('/deposit', async (req: Request, res: Response) => {
  try {
    const { userAddress, amount, signedApproveTx, signedDepositTx, chainId } = req.body;

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

    // Determine chain to use
    let targetChainId: number;
    if (chainId && ChainRegistry.isChainSupported(chainId)) {
      targetChainId = chainId;
    } else {
      // Default to Base Sepolia
      const supportedChains = ChainRegistry.getSupportedChainIds();
      targetChainId = supportedChains[0] || 84532;
    }

    const config = ChainRegistry.getChainConfig(targetChainId);
    if (!config) {
      console.error(`[DEPOSIT] Chain config not found for chain ${targetChainId}`);
      return res.status(500).json({
        status: 'error',
        reason: `Chain configuration not found for chain ${targetChainId}`,
      });
    }
    
    if (!config.rpcUrl || !config.vaultAddress || !config.nativeUSDCAddress) {
      console.error(`[DEPOSIT] Chain config incomplete for chain ${targetChainId}:`, {
        rpcUrl: config.rpcUrl ? 'set' : 'missing',
        vaultAddress: config.vaultAddress ? 'set' : 'missing',
        nativeUSDCAddress: config.nativeUSDCAddress ? 'set' : 'missing',
      });
      return res.status(500).json({
        status: 'error',
        reason: `Chain configuration incomplete for chain ${targetChainId}. Missing: ${!config.rpcUrl ? 'rpcUrl ' : ''}${!config.vaultAddress ? 'vaultAddress ' : ''}${!config.nativeUSDCAddress ? 'nativeUSDCAddress' : ''}`,
      });
    }

    // Use chain-specific configuration
    const rpcUrl = config.rpcUrl;
    const vaultAddress = config.vaultAddress;
    const tokenAddress = config.nativeUSDCAddress;

    console.log(`[DEPOSIT] Processing deposit on chain ${targetChainId}`);
    console.log(`[DEPOSIT] RPC: ${rpcUrl}`);
    console.log(`[DEPOSIT] Vault: ${vaultAddress}`);
    console.log(`[DEPOSIT] Token: ${tokenAddress}`);

    const { ethers } = require('ethers');
    const provider = new ethers.JsonRpcProvider(rpcUrl);

    try {
      // Helper function to handle transaction errors
      const handleTransaction = async (signedTx: string, txType: string): Promise<string> => {
        try {
          const tx = await provider.broadcastTransaction(signedTx);
          // Wait for transaction with 3 minute timeout
          await Promise.race([
            tx.wait(),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Transaction timeout')), 180000))
          ]);
          console.log(`[DEPOSIT] ${txType} transaction confirmed:`, tx.hash);
          return tx.hash;
        } catch (error: any) {
          const errorMessage = error.error?.message || error.message || '';
          const errorCode = error.code || error.error?.code;
          
          // Handle "already known" error
          if (errorMessage.includes('already known')) {
            console.log(`[DEPOSIT] ${txType} transaction already known, attempting to find it...`);
            
            try {
              const decodedTx = ethers.Transaction.from(signedTx);
              const txHash = decodedTx.hash;
              
              console.log(`[DEPOSIT] Decoded ${txType} transaction hash:`, txHash);
              
              const existingTx = await provider.getTransaction(txHash);
              if (existingTx) {
                console.log(`[DEPOSIT] Found existing ${txType} transaction, waiting for confirmation...`);
                const receipt = await Promise.race([
                  provider.waitForTransaction(txHash),
                  new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), 180000)) // 3 minutes timeout
                ]) as ethers.TransactionReceipt;
                
                if (receipt) {
                  console.log(`[DEPOSIT] ${txType} transaction confirmed:`, txHash);
                  return txHash;
                }
              } else {
                console.log(`[DEPOSIT] ${txType} transaction not found, waiting for mempool...`);
                await new Promise(resolve => setTimeout(resolve, 2000));
                const retryTx = await provider.getTransaction(txHash);
                if (retryTx) {
                  const receipt = await Promise.race([
                    provider.waitForTransaction(txHash),
                    new Promise((_, reject) => setTimeout(() => reject(new Error('Timeout')), 180000)) // 3 minutes timeout
                  ]) as ethers.TransactionReceipt;
                  if (receipt) {
                    console.log(`[DEPOSIT] ${txType} transaction confirmed:`, txHash);
                    return txHash;
                  }
                }
              }
              
              console.log(`[DEPOSIT] ${txType} transaction hash (may be pending):`, txHash);
              return txHash;
            } catch (decodeError: any) {
              console.error(`[DEPOSIT] Error handling ${txType} transaction:`, decodeError.message);
              throw error;
            }
          }
          
          // Handle "replacement transaction underpriced" error
          if (errorCode === 'REPLACEMENT_UNDERPRICED' || 
              errorMessage.includes('replacement') && errorMessage.includes('underpriced')) {
            console.log(`[DEPOSIT] ${txType} transaction replacement underpriced, checking for pending transaction...`);
            
            try {
              const decodedTx = ethers.Transaction.from(signedTx);
              const fromAddress = decodedTx.from;
              const nonce = decodedTx.nonce;
              
              console.log(`[DEPOSIT] Checking pending transaction for nonce ${nonce} from ${fromAddress}`);
              
              // Check if there's a pending transaction with this nonce
              const pendingTx = await provider.getTransactionCount(fromAddress, 'pending');
              if (pendingTx > nonce) {
                console.log(`[DEPOSIT] Found pending transaction with nonce ${nonce}, waiting for it to complete...`);
                
                // Wait for the pending transaction to be mined
                // We'll poll for the nonce to increase
                let attempts = 0;
                while (attempts < 120) { // Wait up to 2 minutes (120 seconds)
                  await new Promise(resolve => setTimeout(resolve, 1000));
                  const currentNonce = await provider.getTransactionCount(fromAddress, 'pending');
                  if (currentNonce > nonce) {
                    console.log(`[DEPOSIT] Pending ${txType} transaction completed (nonce advanced)`);
                    // Transaction completed, but we don't have its hash
                    // Return a placeholder - the actual transaction should be checked separately
                    throw new Error(`Previous transaction with nonce ${nonce} was completed. Please check transaction status or retry with a new nonce.`);
                  }
                  attempts++;
                }
                
                throw new Error(`Pending transaction with nonce ${nonce} is taking too long. Please wait and try again.`);
              }
              
              throw error; // Re-throw if we can't handle it
            } catch (decodeError: any) {
              if (decodeError.message.includes('Previous transaction') || 
                  decodeError.message.includes('Pending transaction')) {
                throw decodeError;
              }
              console.error(`[DEPOSIT] Error handling ${txType} replacement:`, decodeError.message);
              throw new Error(`Transaction replacement failed. A transaction with the same nonce is pending. Please wait for it to complete or use a higher gas price.`);
            }
          }
          
          throw error; // Re-throw other errors
        }
      };

      // Send approve transaction
      const approveTxHash = await handleTransaction(signedApproveTx, 'Approve');

      // Send deposit transaction
      const depositTxHash = await handleTransaction(signedDepositTx, 'Deposit');

      return res.status(200).json({
        status: 'success',
        message: 'Deposit successful',
        approveTxHash: approveTxHash,
        depositTxHash: depositTxHash,
        chainId: targetChainId,
      });
    } catch (error: any) {
      console.error('[DEPOSIT] Error broadcasting transactions:', error);
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
// Admin endpoint to clear used slips (for testing)
router.post('/admin/clearUsedSlips', async (req: Request, res: Response) => {
  try {
    const db = getDB();
    await db.clearUsedSlips();
    return res.status(200).json({
      status: 'success',
      message: 'All used slips cleared',
      count: 0
    });
  } catch (error) {
    console.error('Error clearing used slips:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error'
    });
  }
});

// Admin endpoint to get database stats
router.get('/admin/stats', async (req: Request, res: Response) => {
  try {
    const db = getDB();
    const state = db.getState();
    return res.status(200).json({
      status: 'success',
      usedSlipsCount: state.usedSlips.length,
      slipsCount: state.slips.length,
      depositsCount: state.deposits.length,
      redeemedCount: state.slips.filter(s => s.status === 'redeemed').length,
      validatedCount: state.slips.filter(s => s.status === 'validated').length
    });
  } catch (error) {
    console.error('Error getting stats:', error);
    return res.status(500).json({
      status: 'error',
      reason: 'Internal server error'
    });
  }
});

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

