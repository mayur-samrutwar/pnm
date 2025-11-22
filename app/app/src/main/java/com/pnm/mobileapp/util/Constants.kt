package com.pnm.mobileapp.util

object Constants {
    const val VAULT_CONTRACT_ADDRESS = "0x8A791620dd6260079BF849Dc5567aDC3F2FdC318" // Localhost vault
    const val USDC_TOKEN_CONTRACT = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0" // Mock USDC on localhost
    const val HUB_BASE_URL = "http://10.140.159.96:3000" // Use your computer's IP for physical device, or 10.0.2.2 for emulator
    const val RPC_URL = "${HUB_BASE_URL}/api/v1/rpc" // RPC proxy through hub server
    const val CHAIN_ID = 1337 // Hardhat localhost chain ID (0x539)
}

