package com.pnm.mobileapp.util

object Constants {
    const val VAULT_CONTRACT_ADDRESS = "0x8A791620dd6260079BF849Dc5567aDC3F2FdC318" // Localhost vault
    const val USDC_TOKEN_CONTRACT = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0" // Mock USDC on localhost
    const val HUB_BASE_URL = "https://0b55311a3ca0.ngrok-free.app" // ngrok tunnel to localhost:3000
    const val RPC_URL = "${HUB_BASE_URL}/api/v1/rpc" // RPC proxy through hub server
    const val CHAIN_ID = 1337 // Hardhat localhost chain ID (0x539)
}

