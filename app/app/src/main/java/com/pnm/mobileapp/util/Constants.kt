package com.pnm.mobileapp.util

object Constants {
    // Hub server URL
    const val HUB_BASE_URL = "https://0b55311a3ca0.ngrok-free.app" // ngrok tunnel to localhost:3000
    
    // Chain IDs
    const val CHAIN_ID_BASE_SEPOLIA = 84532
    const val CHAIN_ID_ETHEREUM_SEPOLIA = 11155111
    const val CHAIN_ID_LOCALHOST = 1337 // Hardhat localhost chain ID (0x539)
    
    // Default chain (for backward compatibility)
    const val CHAIN_ID = CHAIN_ID_BASE_SEPOLIA // Default to Base Sepolia
    
    // Base Sepolia Configuration
    const val VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA = "0x9251b4c1ea6e19178870208e5d92e724FC5A4B79"
    const val USDC_TOKEN_CONTRACT_BASE_SEPOLIA = "0x036CbD53842c5426634e7929541eC2318f3dCF7e" // Native USDC
    const val RPC_URL_BASE_SEPOLIA = "${HUB_BASE_URL}/api/v1/rpc" // RPC proxy through hub server
    const val RPC_URL_BASE_SEPOLIA_DIRECT = "https://sepolia.base.org" // Direct RPC for nonce checks (more reliable)
    
    // Ethereum Sepolia Configuration
    const val VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA = "0x0983966A5bCcE66cb3F488BB04A5198e799A2dB3"
    const val USDC_TOKEN_CONTRACT_ETHEREUM_SEPOLIA = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238" // Native USDC
    const val RPC_URL_ETHEREUM_SEPOLIA = "${HUB_BASE_URL}/api/v1/rpc" // RPC proxy through hub server
    
    // Legacy (for backward compatibility)
    const val VAULT_CONTRACT_ADDRESS = VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA
    const val USDC_TOKEN_CONTRACT = USDC_TOKEN_CONTRACT_BASE_SEPOLIA
    const val RPC_URL = RPC_URL_BASE_SEPOLIA
    
    /**
     * Get vault address for a specific chain
     */
    fun getVaultAddress(chainId: Int): String {
        return when (chainId) {
            CHAIN_ID_BASE_SEPOLIA -> VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA
            CHAIN_ID_ETHEREUM_SEPOLIA -> VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA
            else -> VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA // Default to Base Sepolia
        }
    }
    
    /**
     * Get USDC token address for a specific chain
     */
    fun getUSDCAddress(chainId: Int): String {
        return when (chainId) {
            CHAIN_ID_BASE_SEPOLIA -> USDC_TOKEN_CONTRACT_BASE_SEPOLIA
            CHAIN_ID_ETHEREUM_SEPOLIA -> USDC_TOKEN_CONTRACT_ETHEREUM_SEPOLIA
            else -> USDC_TOKEN_CONTRACT_BASE_SEPOLIA // Default to Base Sepolia
        }
    }
    
    /**
     * Get RPC URL for a specific chain
     */
    fun getRpcUrl(chainId: Int): String {
        return when (chainId) {
            CHAIN_ID_BASE_SEPOLIA -> RPC_URL_BASE_SEPOLIA
            CHAIN_ID_ETHEREUM_SEPOLIA -> RPC_URL_ETHEREUM_SEPOLIA
            else -> RPC_URL_BASE_SEPOLIA // Default to Base Sepolia
        }
    }
    
    /**
     * Get chain name for display
     */
    fun getChainName(chainId: Int): String {
        return when (chainId) {
            CHAIN_ID_BASE_SEPOLIA -> "Base Sepolia"
            CHAIN_ID_ETHEREUM_SEPOLIA -> "Ethereum Sepolia"
            CHAIN_ID_LOCALHOST -> "Localhost"
            else -> "Unknown Chain"
        }
    }
    
    /**
     * Get all supported chain IDs
     */
    fun getSupportedChainIds(): List<Int> {
        return listOf(CHAIN_ID_BASE_SEPOLIA, CHAIN_ID_ETHEREUM_SEPOLIA)
    }
}

