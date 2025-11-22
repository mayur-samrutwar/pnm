package com.pnm.mobileapp.crypto

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Manages Ethereum deposits to vault using web3j
 */
class EthereumDepositManager {
    companion object {
        private const val TAG = "EthereumDepositManager"
    }

    /**
     * Create signed approve transaction
     */
    suspend fun createApproveTransaction(
        privateKey: ByteArray,
        rpcUrl: String,
        tokenAddress: String,
        spenderAddress: String,
        amount: BigInteger,
        nonce: Long
    ): String = withContext(Dispatchers.IO) {
        try {
            val web3j = Web3j.build(HttpService(rpcUrl))
            val credentials = Credentials.create(Numeric.toHexString(privateKey))
            
            // ERC20 approve function: approve(address spender, uint256 amount)
            val functionSignature = "095ea7b3" // approve(address,uint256)
            val spenderPadded = spenderAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val amountPadded = amount.toString(16).padStart(64, '0')
            val data = "0x$functionSignature$spenderPadded$amountPadded"
            
            // Get gas price
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            
            // Create raw transaction
            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                BigInteger.valueOf(100000), // Gas limit for approve
                tokenAddress,
                BigInteger.ZERO,
                data
            )
            
            // Sign transaction
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
            val hexValue = Numeric.toHexString(signedMessage)
            
            Log.d(TAG, "Created approve transaction")
            return@withContext hexValue
        } catch (e: Exception) {
            Log.e(TAG, "Error creating approve transaction", e)
            throw e
        }
    }

    /**
     * Create signed deposit transaction
     */
    suspend fun createDepositTransaction(
        privateKey: ByteArray,
        rpcUrl: String,
        vaultAddress: String,
        userAddress: String,
        tokenAddress: String,
        amount: BigInteger,
        nonce: Long
    ): String = withContext(Dispatchers.IO) {
        try {
            val web3j = Web3j.build(HttpService(rpcUrl))
            val credentials = Credentials.create(Numeric.toHexString(privateKey))
            
            // Vault deposit function: deposit(address user, address token, uint256 amount)
            val functionSignature = "8340f549" // deposit(address,address,uint256) - correct selector
            val userPadded = userAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val tokenPadded = tokenAddress.removePrefix("0x").lowercase().padStart(64, '0')
            val amountPadded = amount.toString(16).padStart(64, '0')
            val data = "0x$functionSignature$userPadded$tokenPadded$amountPadded"
            
            // Get gas price
            val gasPrice = web3j.ethGasPrice().send().gasPrice
            
            // Create raw transaction
            val rawTransaction = RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                BigInteger.valueOf(200000), // Gas limit for deposit
                vaultAddress,
                BigInteger.ZERO,
                data
            )
            
            // Sign transaction
            val signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials)
            val hexValue = Numeric.toHexString(signedMessage)
            
            Log.d(TAG, "Created deposit transaction")
            return@withContext hexValue
        } catch (e: Exception) {
            Log.e(TAG, "Error creating deposit transaction", e)
            throw e
        }
    }

    /**
     * Get transaction nonce for an address
     */
    suspend fun getNonce(rpcUrl: String, address: String): Long = withContext(Dispatchers.IO) {
        try {
            val web3j = Web3j.build(HttpService(rpcUrl))
            val ethGetTransactionCount = web3j.ethGetTransactionCount(address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send()
            return@withContext ethGetTransactionCount.transactionCount.longValueExact()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nonce", e)
            throw e
        }
    }

    /**
     * Derive Ethereum address from private key
     * This verifies that the private key matches the expected address
     */
    suspend fun deriveAddressFromPrivateKey(privateKey: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            val credentials = Credentials.create(Numeric.toHexString(privateKey))
            val address = credentials.address
            Log.d(TAG, "Derived address from private key: $address")
            return@withContext address
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving address from private key", e)
            throw e
        }
    }
}

