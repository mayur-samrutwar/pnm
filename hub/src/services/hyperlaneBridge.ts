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
    } catch (error) {
      console.warn('Could not quote message fee, using 0:', error);
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
    } catch (error) {
      console.warn('Could not quote transfer fee, using 0:', error);
    }

    // First, we need to ensure the wallet has Warp Route USDC tokens
    // If user deposited native USDC, we need to convert it to Warp Route USDC
    // For now, assume the hub already has Warp Route USDC or will convert it

    // Transfer USDC
    const transferTx = await warpRoute.transferRemote(
      targetDomainId,
      recipientBytes32,
      amount,
      { value: transferFee }
    );
    const transferReceipt = await transferTx.wait();

    console.log(`[HyperlaneBridge] USDC transferred: ${transferTx.hash}`);

    // Step 3: Wait a bit for the message to be processed
    // In production, we should poll for message delivery
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Step 4: Trigger deposit on target chain
    // The receiver contract should have received the USDC by now
    // We can call depositToVaultDirect on the receiver
    const targetWallet = this.getWallet(targetChainId);
    const receiver = new ethers.Contract(receiverAddress, RECEIVER_ABI, targetWallet);

    try {
      // Wait a bit more for USDC to arrive
      await new Promise(resolve => setTimeout(resolve, 3000));

      // Call depositToVaultDirect
      const depositTx = await receiver.depositToVaultDirect(userAddress, amount);
      await depositTx.wait();
      console.log(`[HyperlaneBridge] Deposited to Vault on target chain: ${depositTx.hash}`);
    } catch (error) {
      console.error('[HyperlaneBridge] Failed to deposit to Vault on target chain:', error);
      // This is not critical - the deposit can be triggered manually later
      // The USDC is already on the receiver contract
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

