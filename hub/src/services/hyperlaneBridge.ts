/**
 * Hyperlane Bridge Service
 * Handles cross-chain USDC bridging using Hyperlane Warp Routes
 */

import { ethers } from 'ethers';
import { ChainRegistry } from './chainRegistry';

// Warp Route ABI (simplified - we'll use the transferRemote function)
const WARP_ROUTE_ABI = [
  'function transferRemote(uint32 destination, bytes32 recipient, uint256 amount) external returns (bytes32)',
  'function quoteTransferRemote(uint32 destination) external view returns (uint256)',
];

// Mailbox ABI for sending messages
const MAILBOX_ABI = [
  'function dispatch(uint32 destinationDomain, bytes32 recipient, bytes calldata messageBody) external payable returns (bytes32)',
  'function quoteDispatch(uint32 destinationDomain, bytes calldata messageBody) external view returns (uint256)',
];

// CrossChainReceiver ABI
const RECEIVER_ABI = [
  'function handle(uint32 origin, bytes32 sender, bytes calldata messageBody) external',
  'function depositToVaultDirect(address userAddress, uint256 amount) external',
];

export interface BridgeResult {
  messageTxHash: string;
  transferTxHash: string;
  messageId: string;
}

export class HyperlaneBridge {
  private wallets: Map<number, ethers.Wallet> = new Map();

  /**
   * Get or create wallet for a chain
   */
  private getWallet(chainId: number): ethers.Wallet {
    if (!this.wallets.has(chainId)) {
      const rpcUrl = ChainRegistry.getRpcUrl(chainId);
      if (!rpcUrl) {
        throw new Error(`No RPC URL configured for chain ${chainId}`);
      }

      const privateKey = process.env.HUB_PRIVATE_KEY;
      if (!privateKey) {
        throw new Error('HUB_PRIVATE_KEY not configured');
      }

      const provider = new ethers.JsonRpcProvider(rpcUrl);
      const wallet = new ethers.Wallet(privateKey, provider);
      this.wallets.set(chainId, wallet);
    }

    return this.wallets.get(chainId)!;
  }

  /**
   * Bridge USDC from source chain to target chain
   * This sends a message with user info and then transfers USDC
   * 
   * @param sourceChainId Source chain ID
   * @param targetChainId Target chain ID
   * @param userAddress User address to credit on target chain
   * @param amount Amount to bridge (in token units, 6 decimals for USDC)
   * @returns Bridge result with transaction hashes
   */
  async bridgeUSDC(
    sourceChainId: number,
    targetChainId: number,
    userAddress: string,
    amount: bigint
  ): Promise<BridgeResult> {
    // Validate chains
    if (!ChainRegistry.isChainSupported(sourceChainId)) {
      throw new Error(`Unsupported source chain: ${sourceChainId}`);
    }
    if (!ChainRegistry.isChainSupported(targetChainId)) {
      throw new Error(`Unsupported target chain: ${targetChainId}`);
    }

    const sourceConfig = ChainRegistry.getChainConfig(sourceChainId)!;
    const targetConfig = ChainRegistry.getChainConfig(targetChainId)!;
    const targetDomainId = targetConfig.domainId;

    // Get wallet for source chain
    const wallet = this.getWallet(sourceChainId);

    // Get receiver address on target chain
    const receiverAddress = ChainRegistry.getCrossChainReceiverAddress(targetChainId);
    if (!receiverAddress) {
      throw new Error(`CrossChainReceiver not deployed on chain ${targetChainId}`);
    }

    // Step 1: Send message with user address and amount via Mailbox
    // Get Mailbox address from chain registry
    const mailboxAddress = ChainRegistry.getMailboxAddress(sourceChainId);
    if (!mailboxAddress) {
      throw new Error(`Hyperlane Mailbox address not configured for chain ${sourceChainId}`);
    }

    const mailbox = new ethers.Contract(mailboxAddress, MAILBOX_ABI, wallet);

    // Encode message: userAddress, amount
    const messageBody = ethers.AbiCoder.defaultAbiCoder().encode(
      ['address', 'uint256'],
      [userAddress, amount]
    );

    // Quote message fee
    let messageFee = 0n;
    try {
      messageFee = await mailbox.quoteDispatch(targetDomainId, messageBody);
      console.log(`[HyperlaneBridge] Quoted message fee: ${ethers.formatEther(messageFee)} ETH`);
    } catch (error: any) {
      console.error('[HyperlaneBridge] Failed to quote message fee:', error.message);
      // Check wallet balance
      const balance = await wallet.provider.getBalance(wallet.address);
      console.log(`[HyperlaneBridge] Wallet balance: ${ethers.formatEther(balance)} ETH`);
      if (balance === 0n) {
        throw new Error(`Hub wallet has no ETH on chain ${sourceChainId}. Need ETH to pay Hyperlane fees. Please fund the hub wallet: ${wallet.address}`);
      }
      // For testnet, Hyperlane fees might require a minimum amount
      // Use a more generous fallback to ensure we cover the protocol fee
      // Hyperlane testnet typically requires at least 0.001 ETH for protocol fees
      messageFee = ethers.parseEther('0.001');
      console.warn(`[HyperlaneBridge] Using fallback message fee: ${ethers.formatEther(messageFee)} ETH`);
    }

    // Send message
    const messageTx = await mailbox.dispatch(
      targetDomainId,
      ethers.zeroPadValue(receiverAddress, 32), // Convert address to bytes32
      messageBody,
      { value: messageFee }
    );
    const messageReceipt = await messageTx.wait();
    const messageId = messageReceipt.logs[0]?.topics[1] || ethers.id(messageTx.hash);

    console.log(`[HyperlaneBridge] Message sent: ${messageTx.hash}, messageId: ${messageId}`);

    // Step 2: Transfer USDC via Warp Route
    const warpRouteAddress = sourceConfig.warpRouteUSDCAddress;
    const warpRoute = new ethers.Contract(warpRouteAddress, WARP_ROUTE_ABI, wallet);

    // Convert receiver address to bytes32
    const recipientBytes32 = ethers.zeroPadValue(receiverAddress, 32);

    // Quote transfer fee (if needed)
    let transferFee = 0n;
    try {
      transferFee = await warpRoute.quoteTransferRemote(targetDomainId);
      console.log(`[HyperlaneBridge] Quoted transfer fee: ${ethers.formatEther(transferFee)} ETH`);
    } catch (error: any) {
      console.error('[HyperlaneBridge] Failed to quote transfer fee:', error.message);
      // For testnet, Warp Route fees might require a minimum amount
      // Use a more generous fallback to ensure we cover the protocol fee
      transferFee = ethers.parseEther('0.001');
      console.warn(`[HyperlaneBridge] Using fallback transfer fee: ${ethers.formatEther(transferFee)} ETH`);
    }
    
    // Verify we have enough balance to cover all fees
    const balance = await wallet.provider.getBalance(wallet.address);
    const totalFees = messageFee + transferFee;
    console.log(`[HyperlaneBridge] Total fees needed: ${ethers.formatEther(totalFees)} ETH, Wallet balance: ${ethers.formatEther(balance)} ETH`);
    if (balance < totalFees) {
      throw new Error(`Insufficient ETH balance. Need ${ethers.formatEther(totalFees)} ETH for fees (message: ${ethers.formatEther(messageFee)} ETH, transfer: ${ethers.formatEther(transferFee)} ETH), but only have ${ethers.formatEther(balance)} ETH. Please fund hub wallet: ${wallet.address}`);
    }

    // First, we need to approve the Warp Route to spend USDC
    // Get native USDC address on source chain
    const nativeUSDC = ChainRegistry.getNativeUSDCAddress(sourceChainId);
    if (!nativeUSDC) {
      throw new Error(`No native USDC address for chain ${sourceChainId}`);
    }
    
    // Check hub wallet's USDC balance
    const usdcAbi = ['function balanceOf(address owner) external view returns (uint256)', 'function approve(address spender, uint256 amount) external returns (bool)', 'function allowance(address owner, address spender) external view returns (uint256)'];
    const usdcContract = new ethers.Contract(nativeUSDC, usdcAbi, wallet);
    const usdcBalance = await usdcContract.balanceOf(wallet.address);
    console.log(`[HyperlaneBridge] Hub wallet USDC balance: ${ethers.formatUnits(usdcBalance, 6)} USDC`);
    
    if (usdcBalance < amount) {
      throw new Error(`Insufficient USDC balance. Need ${ethers.formatUnits(amount, 6)} USDC, but only have ${ethers.formatUnits(usdcBalance, 6)} USDC`);
    }
    
    // Check current allowance
    const currentAllowance = await usdcContract.allowance(wallet.address, warpRouteAddress);
    console.log(`[HyperlaneBridge] Current Warp Route allowance: ${ethers.formatUnits(currentAllowance, 6)} USDC`);
    
    // Approve Warp Route if needed
    if (currentAllowance < amount) {
      console.log(`[HyperlaneBridge] Approving Warp Route to spend ${ethers.formatUnits(amount, 6)} USDC...`);
      const approveTx = await usdcContract.approve(warpRouteAddress, amount);
      await approveTx.wait();
      console.log(`[HyperlaneBridge] Approval confirmed: ${approveTx.hash}`);
    }

    // Transfer USDC via Warp Route
    const transferTx = await warpRoute.transferRemote(
      targetDomainId,
      recipientBytes32,
      amount,
      { value: transferFee }
    );
    const transferReceipt = await transferTx.wait();

    console.log(`[HyperlaneBridge] USDC transferred: ${transferTx.hash}`);

    // Step 3: Wait for USDC to arrive at CrossChainReceiver on target chain
    // Hyperlane Warp Routes need time for the relayer to process the transfer
    const targetWallet = this.getWallet(targetChainId);
    const targetProvider = new ethers.JsonRpcProvider(ChainRegistry.getRpcUrl(targetChainId)!);
    const targetNativeUSDC = ChainRegistry.getNativeUSDCAddress(targetChainId)!;
    const targetUsdcAbi = ['function balanceOf(address owner) external view returns (uint256)'];
    const targetUSDC = new ethers.Contract(targetNativeUSDC, targetUsdcAbi, targetProvider);
    
    console.log(`[HyperlaneBridge] Waiting for USDC to arrive at CrossChainReceiver (${receiverAddress})...`);
    
    // Poll for USDC arrival (up to 5 minutes)
    const maxWaitTime = 5 * 60 * 1000; // 5 minutes
    const pollInterval = 5000; // 5 seconds
    const startTime = Date.now();
    let receiverBalance = 0n;
    
    while (Date.now() - startTime < maxWaitTime) {
      receiverBalance = await targetUSDC.balanceOf(receiverAddress);
      console.log(`[HyperlaneBridge] CrossChainReceiver USDC balance: ${ethers.formatUnits(receiverBalance, 6)} USDC (waiting for ${ethers.formatUnits(amount, 6)} USDC)...`);
      
      if (receiverBalance >= amount) {
        console.log(`[HyperlaneBridge] ✅ USDC arrived at CrossChainReceiver!`);
        break;
      }
      
      await new Promise(resolve => setTimeout(resolve, pollInterval));
    }
    
    if (receiverBalance < amount) {
      throw new Error(`USDC did not arrive at CrossChainReceiver within timeout. Expected: ${ethers.formatUnits(amount, 6)} USDC, Current balance: ${ethers.formatUnits(receiverBalance, 6)} USDC`);
    }

    // Step 4: Trigger deposit on target chain
    // Now that USDC has arrived, we can call depositToVaultDirect
    const receiver = new ethers.Contract(receiverAddress, RECEIVER_ABI, targetWallet);

    try {
      console.log(`[HyperlaneBridge] Depositing ${ethers.formatUnits(amount, 6)} USDC to Vault for user ${userAddress}...`);
      const depositTx = await receiver.depositToVaultDirect(userAddress, amount);
      await depositTx.wait();
      console.log(`[HyperlaneBridge] ✅ Deposited to Vault on target chain: ${depositTx.hash}`);
    } catch (error: any) {
      console.error('[HyperlaneBridge] Failed to deposit to Vault on target chain:', error.message);
      throw new Error(`Failed to deposit USDC to Vault: ${error.message}`);
    }

    return {
      messageTxHash: messageTx.hash,
      transferTxHash: transferTx.hash,
      messageId: ethers.hexlify(messageId),
    };
  }

  /**
   * Check if a message has been delivered (simplified - in production use Hyperlane SDK)
   */
  async waitForMessageDelivery(
    messageId: string,
    targetChainId: number,
    timeoutMs: number = 300000 // 5 minutes
  ): Promise<boolean> {
    const startTime = Date.now();
    const pollInterval = 5000; // 5 seconds

    while (Date.now() - startTime < timeoutMs) {
      // In production, check Hyperlane Mailbox for message delivery
      // For now, just wait
      await new Promise(resolve => setTimeout(resolve, pollInterval));

      // TODO: Implement actual message delivery check using Hyperlane SDK
      // For now, assume message is delivered after timeout
      if (Date.now() - startTime > 60000) {
        // After 1 minute, assume delivered (for testing)
        return true;
      }
    }

    return false;
  }

  /**
   * Get deposit balance for a user on a specific chain
   */
  async getDepositBalance(chainId: number, userAddress: string): Promise<bigint> {
    const vaultAddress = ChainRegistry.getVaultAddress(chainId);
    if (!vaultAddress) {
      throw new Error(`No vault address for chain ${chainId}`);
    }

    const rpcUrl = ChainRegistry.getRpcUrl(chainId);
    if (!rpcUrl) {
      throw new Error(`No RPC URL for chain ${chainId}`);
    }

    const provider = new ethers.JsonRpcProvider(rpcUrl);
    const vaultAbi = ['function deposits(address) external view returns (uint256)'];
    const vault = new ethers.Contract(vaultAddress, vaultAbi, provider);

    return await vault.deposits(userAddress);
  }

  /**
   * Find chain with sufficient deposits for a user
   */
  async findChainWithDeposits(
    userAddress: string,
    requiredAmount: bigint
  ): Promise<number | null> {
    const supportedChains = ChainRegistry.getSupportedChainIds();

    for (const chainId of supportedChains) {
      try {
        const balance = await this.getDepositBalance(chainId, userAddress);
        if (balance >= requiredAmount) {
          return chainId;
        }
      } catch (error) {
        console.warn(`Failed to check balance on chain ${chainId}:`, error);
      }
    }

    return null;
  }
}

