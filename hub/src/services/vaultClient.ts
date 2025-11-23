import { ethers } from 'ethers';
import { Voucher } from './validator';
import { ChainRegistry } from './chainRegistry';

export class VaultClient {
  private provider: ethers.Provider;
  private wallet: ethers.Wallet;
  private contract: ethers.Contract;
  private contractAddress: string;
  // Transaction queue to ensure sequential processing and prevent nonce conflicts
  private transactionQueue: Promise<any> = Promise.resolve();
  // Mutex to ensure only one transaction is processed at a time
  private transactionLock: Promise<void> = Promise.resolve();

  constructor(
    rpcUrl: string,
    privateKey: string,
    vaultContractAddress: string,
    contractABI: readonly any[] | any[]
  ) {
    this.provider = new ethers.JsonRpcProvider(rpcUrl);
    this.wallet = new ethers.Wallet(privateKey, this.provider);
    this.contractAddress = vaultContractAddress;
    this.contract = new ethers.Contract(vaultContractAddress, contractABI, this.wallet);
  }

  /**
   * Queue a transaction to ensure sequential processing
   * This prevents nonce conflicts when multiple redemption requests come in concurrently
   * Uses a mutex pattern to ensure only one transaction executes at a time
   */
  private async queueTransaction<T>(fn: () => Promise<T>): Promise<T> {
    // Wait for the previous transaction to complete
    await this.transactionLock;
    
    // Create a new lock that will be released when this transaction completes
    let releaseLock: () => void;
    this.transactionLock = new Promise<void>((resolve) => {
      releaseLock = resolve;
    });
    
    try {
      // Execute the transaction
      const result = await fn();
      releaseLock!();
      return result;
    } catch (error) {
      releaseLock!();
      throw error;
    }
  }

  /**
   * Redeem a voucher on-chain
   * @param voucher The voucher to redeem
   * @param signature The signature (can be same as voucher.signature or different)
   * @returns Transaction hash
   */
  async redeemVoucher(voucher: Voucher, signature: string): Promise<string> {
    // Queue the transaction to prevent nonce conflicts
    return this.queueTransaction(async () => {
      // Get current nonce before encoding (helps with debugging)
      const currentNonce = await this.provider.getTransactionCount(this.wallet.address, 'pending');
      console.log('[VaultClient] Current nonce for redeemVoucher:', currentNonce);

      // Encode voucher payload (same format as in validator.ts)
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
          voucher.contractAddress || ethers.ZeroAddress,
          BigInt(voucher.expiry),
          BigInt(voucher.chainId || 0),
          voucher.payerAddress,
          voucher.payeeAddress,
          BigInt(voucher.amount),
          BigInt(voucher.cumulative),
          ethers.id(voucher.slipId), // Convert UUID to bytes32
        ]
      );

      // Call redeemVoucher on the contract
      // Nonce will be handled automatically by ethers.js since we're queuing transactions
      const tx = await this.contract.redeemVoucher(voucherPayload, signature);

      // Wait for transaction to be mined
      const receipt = await tx.wait();

      if (!receipt) {
        throw new Error('Transaction receipt not found');
      }

      return receipt.hash;
    });
  }

  /**
   * Create a VaultClient for a specific chain
   * @param chainId The chain ID
   * @param privateKey The hub's private key
   * @param contractABI The Vault contract ABI
   * @returns VaultClient instance for the chain
   */
  static createForChain(
    chainId: number,
    privateKey: string,
    contractABI: readonly any[] | any[]
  ): VaultClient {
    const config = ChainRegistry.getChainConfig(chainId);
    if (!config) {
      throw new Error(`Unsupported chain: ${chainId}`);
    }

    if (!config.vaultAddress) {
      throw new Error(`Vault address not configured for chain ${chainId}`);
    }

    return new VaultClient(
      config.rpcUrl,
      privateKey,
      config.vaultAddress,
      contractABI
    );
  }

  /**
   * Redeem a P-256 voucher on-chain using hub's authority
   * This function is called by the hub after verifying P-256 signatures off-chain
   * @param voucher The voucher to redeem (payerAddress should be Ethereum address)
   * @param chainId Optional chain ID for multichain support (if not provided, uses voucher.chainId)
   * @returns Transaction hash
   */
  async redeemVoucherByHub(voucher: Voucher, chainId?: number): Promise<string> {
    console.log('[VaultClient] redeemVoucherByHub called');
    console.log('[VaultClient] Contract address:', this.contractAddress);
    console.log('[VaultClient] Wallet address:', this.wallet.address);
    
    // Queue the transaction to prevent nonce conflicts
    return this.queueTransaction(async () => {
      try {
        // Get current nonce before encoding (helps with debugging)
        const currentNonce = await this.provider.getTransactionCount(this.wallet.address, 'pending');
        console.log('[VaultClient] Current nonce:', currentNonce);

        // Use provided chainId or fall back to voucher.chainId
        const targetChainId = chainId || voucher.chainId;
        const targetContractAddress = chainId 
          ? ChainRegistry.getVaultAddress(chainId) || voucher.contractAddress
          : voucher.contractAddress;

        // Encode voucher payload (same format as redeemVoucher)
        const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
          [
            'address', // contractAddress
            'uint256', // expiry
            'uint256', // chainId
            'address', // payerAddress (Ethereum address where deposits are)
            'address', // payeeAddress
            'uint256', // amount
            'uint256', // cumulative
            'bytes32', // slipId
          ],
          [
            targetContractAddress,
            BigInt(voucher.expiry),
            BigInt(targetChainId || (voucher.chainId ?? 0)),
            voucher.payerAddress, // This should be the Ethereum address
            voucher.payeeAddress,
            BigInt(voucher.amount),
            BigInt(voucher.cumulative),
            ethers.id(voucher.slipId), // Convert UUID to bytes32
          ]
    );

        console.log('[VaultClient] Voucher payload encoded, length:', voucherPayload.length);
        console.log('[VaultClient] Calling redeemVoucherByHub on contract...');

        // Call redeemVoucherByHub on the contract (hub signs as owner)
        // Nonce will be handled automatically by ethers.js since we're queuing transactions
        const tx = await this.contract.redeemVoucherByHub(voucherPayload);
        console.log('[VaultClient] Transaction sent, hash:', tx.hash);
        console.log('[VaultClient] Waiting for transaction to be mined...');

        // Wait for transaction to be mined
        const receipt = await tx.wait();
        console.log('[VaultClient] Transaction mined, block number:', receipt?.blockNumber);

        if (!receipt) {
          throw new Error('Transaction receipt not found');
        }

        return receipt.hash;
      } catch (error: any) {
        console.error('[VaultClient] Error in redeemVoucherByHub:', error);
        console.error('[VaultClient] Error message:', error.message);
        console.error('[VaultClient] Error code:', error.code);
        if (error.reason) {
          console.error('[VaultClient] Error reason:', error.reason);
        }
        if (error.data) {
          console.error('[VaultClient] Error data:', error.data);
        }
        throw error;
      }
    });
  }

  /**
   * Get the contract instance (for advanced usage)
   */
  getContract(): ethers.Contract {
    return this.contract;
  }

  /**
   * Get the wallet address
   */
  getWalletAddress(): string {
    return this.wallet.address;
  }

  /**
   * Check if a user is settled for a specific nonce
   * @param user The user address
   * @param nonce The settlement nonce
   * @returns true if settled, false otherwise
   */
  async isUserSettled(user: string, nonce: number): Promise<boolean> {
    try {
      const settledAmount = await this.contract.settlements(user, nonce);
      return settledAmount > 0n;
    } catch (error) {
      console.error('Error checking user settlement:', error);
      return false;
    }
  }

  /**
   * Get USDC balance for an Ethereum address
   * @param address The Ethereum address to check
   * @param tokenAddress The ERC20 token contract address (USDC)
   * @returns Balance in token units (will need to divide by 10^decimals for human-readable)
   */
  async getTokenBalance(address: string, tokenAddress: string): Promise<bigint> {
    try {
      // ERC20 balanceOf function ABI
      const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)'];
      const tokenContract = new ethers.Contract(tokenAddress, erc20Abi, this.provider);
      const balance = await tokenContract.balanceOf(address);
      return balance;
    } catch (error) {
      console.error('Error getting token balance:', error);
      throw error;
    }
  }

  /**
   * Redeem a chain-agnostic voucher using the multichain function
   * @param chainAgnosticPayload The chain-agnostic voucher payload (without chainId/contractAddress)
   * @param targetChainId The target chain ID where redemption is happening
   * @returns Transaction hash
   */
  async redeemVoucherByHubMultichain(
    chainAgnosticPayload: string,
    targetChainId: number
  ): Promise<string> {
    return this.queueTransaction(async () => {
      const targetContractAddress = ChainRegistry.getVaultAddress(targetChainId);
      if (!targetContractAddress) {
        throw new Error(`No vault address for chain ${targetChainId}`);
      }

      // Call the multichain redemption function
      const tx = await this.contract.redeemVoucherByHubMultichain(
        chainAgnosticPayload,
        targetChainId,
        targetContractAddress
      );

      const receipt = await tx.wait();
      if (!receipt) {
        throw new Error('Transaction receipt not found');
      }

      return receipt.hash;
    });
  }

  /**
   * Withdraw tokens from Vault (hub is owner)
   * @param tokenAddress The token address to withdraw
   * @param amount The amount to withdraw
   * @returns Transaction hash
   */
  async withdraw(tokenAddress: string, amount: bigint): Promise<string> {
    return this.queueTransaction(async () => {
      const vaultAbi = ['function withdraw(address token, uint256 amount) external'];
      const vault = new ethers.Contract(this.contractAddress, vaultAbi, this.wallet);
      
      const tx = await vault.withdraw(tokenAddress, amount);
      const receipt = await tx.wait();
      
      if (!receipt) {
        throw new Error('Transaction receipt not found');
      }
      
      return receipt.hash;
    });
  }
}

// Vault contract ABI (minimal interface for redeemVoucher)
export const VAULT_ABI = [
  'function redeemVoucher(bytes calldata voucherPayload, bytes calldata signature) external',
  'function redeemVoucherByHub(bytes calldata voucherPayload) external',
  'function redeemVoucherByHubMultichain(bytes calldata chainAgnosticPayload, uint256 targetChainId, address targetContractAddress) external',
  'function deposit(address user, address token, uint256 amount) external',
  'function deposits(address) external view returns (uint256)',
  'function usedSlip(address, bytes32) external view returns (bool)',
  'function recordSettlement(address user, uint256 nonce, uint256 totalSettled) external',
  'function isUserSettled(address user, uint256 nonce) external view returns (bool)',
  'function settlements(address, uint256) external view returns (uint256)',
  'function withdraw(address token, uint256 amount) external',
  'event VoucherRedeemed(address indexed payer, address indexed payee, uint256 amount, bytes32 slipId)',
  'event Deposit(address indexed user, uint256 amount)',
  'event SettlementRecorded(address indexed user, uint256 indexed nonce, uint256 totalSettled)',
] as const;

