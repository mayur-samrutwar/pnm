/**
 * Chain Registry Service
 * Manages configuration for supported chains
 */

export interface ChainConfig {
  chainId: number;
  chainName: string;
  rpcUrl: string;
  vaultAddress: string;
  nativeUSDCAddress: string;
  warpRouteUSDCAddress: string;
  crossChainReceiverAddress?: string;
  mailboxAddress?: string; // Hyperlane Mailbox contract address
  domainId: number; // Hyperlane domain ID
}

// Chain configurations for Base Sepolia and Ethereum Sepolia
// Lazy-loaded to ensure env vars are available
function getChainConfigs(): Record<number, ChainConfig> {
  return {
    // Base Sepolia
    84532: {
      chainId: 84532,
      chainName: 'Base Sepolia',
      rpcUrl: process.env.BASE_SEPOLIA_RPC_URL || 'https://base-sepolia.drpc.org',
      vaultAddress: process.env.VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA || '',
      nativeUSDCAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
      warpRouteUSDCAddress: '0x020dEE96414703c457322eed8504946583a7dd24',
      crossChainReceiverAddress: process.env.CROSS_CHAIN_RECEIVER_ADDRESS_BASE_SEPOLIA || '',
      mailboxAddress: process.env.HYPERLANE_MAILBOX_ADDRESS_BASE_SEPOLIA || process.env.HYPERLANE_MAILBOX_ADDRESS || '',
      domainId: 84532,
    },
    // Ethereum Sepolia
    11155111: {
      chainId: 11155111,
      chainName: 'Ethereum Sepolia',
      rpcUrl: process.env.ETHEREUM_SEPOLIA_RPC_URL || process.env.SEPOLIA_RPC_URL || 'https://sepolia.drpc.org',
      vaultAddress: process.env.VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA || '',
      nativeUSDCAddress: '0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238',
      warpRouteUSDCAddress: '0x352f1c7ffa598d0698c1D8D2fCAb02511c6fF3e9',
      crossChainReceiverAddress: process.env.CROSS_CHAIN_RECEIVER_ADDRESS_ETHEREUM_SEPOLIA || '',
      mailboxAddress: process.env.HYPERLANE_MAILBOX_ADDRESS_ETHEREUM_SEPOLIA || process.env.HYPERLANE_MAILBOX_ADDRESS || '',
      domainId: 11155111,
    },
  };
}

export class ChainRegistry {
  /**
   * Get chain configuration by chain ID
   */
  static getChainConfig(chainId: number): ChainConfig | null {
    const configs = getChainConfigs();
    return configs[chainId] || null;
  }

  /**
   * Get all supported chain IDs
   */
  static getSupportedChainIds(): number[] {
    const configs = getChainConfigs();
    return Object.keys(configs).map(Number);
  }

  /**
   * Get all chain configurations
   */
  static getAllChainConfigs(): ChainConfig[] {
    const configs = getChainConfigs();
    return Object.values(configs);
  }

  /**
   * Check if a chain is supported
   */
  static isChainSupported(chainId: number): boolean {
    const configs = getChainConfigs();
    return chainId in configs;
  }

  /**
   * Get RPC URL for a chain
   */
  static getRpcUrl(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.rpcUrl || null;
  }

  /**
   * Get vault address for a chain
   */
  static getVaultAddress(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.vaultAddress || null;
  }

  /**
   * Get native USDC address for a chain
   */
  static getNativeUSDCAddress(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.nativeUSDCAddress || null;
  }

  /**
   * Get Warp Route USDC address for a chain
   */
  static getWarpRouteUSDCAddress(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.warpRouteUSDCAddress || null;
  }

  /**
   * Get Hyperlane domain ID for a chain
   */
  static getDomainId(chainId: number): number | null {
    const config = this.getChainConfig(chainId);
    return config?.domainId || null;
  }

  /**
   * Get CrossChainReceiver address for a chain
   */
  static getCrossChainReceiverAddress(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.crossChainReceiverAddress || null;
  }

  /**
   * Get Mailbox address for a chain
   */
  static getMailboxAddress(chainId: number): string | null {
    const config = this.getChainConfig(chainId);
    return config?.mailboxAddress || null;
  }

  /**
   * Set CrossChainReceiver address for a chain (after deployment)
   * Note: This modifies the runtime config but doesn't persist to env vars
   */
  static setCrossChainReceiverAddress(chainId: number, address: string): void {
    // This would need to be implemented differently if we want to persist changes
    // For now, the address should be set in .env file
    console.warn(`setCrossChainReceiverAddress called for chain ${chainId} with address ${address}, but changes are not persisted. Update .env file instead.`);
  }
}

