package com.assetvault.network

import android.util.Log
import com.assetvault.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.security.Security

/**
 * BlockchainManager - Module 4
 * Real Web3j integration with the ProvenanceRegistry contract on Polygon Amoy.
 */
object BlockchainManager {

    private const val TAG = "BlockchainManager"

    private var web3j: Web3j? = null
    private var credentials: Credentials? = null

    init {
        setupBouncyCastle()
    }

    private fun setupBouncyCastle() {
        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (provider == null || provider.javaClass.name != BouncyCastleProvider::class.java.name) {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    /**
     * Initialize Web3j connection with a private key hex string.
     */
    fun initialize(privateKeyHex: String) {
        try {
            web3j = Web3j.build(HttpService(Constants.POLYGON_RPC_URL))
            credentials = Credentials.create(privateKeyHex)
            Log.d(TAG, "Initialized — wallet: ${credentials?.address}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Web3j", e)
            throw e
        }
    }

    /**
     * Generate a deterministic wallet keypair based on the user's Google email.
     * This guarantees 1 Google ID = 1 Wallet FOREVER across all devices.
     */
    fun generateDeterministicWallet(email: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest((email + "SecretVaultSalt2026").toByteArray(Charsets.UTF_8))
        val privateKeyInt = BigInteger(1, hash)
        val ecKeyPair = ECKeyPair.create(privateKeyInt)
        val privateKeyHex = Numeric.toHexStringNoPrefixZeroPadded(ecKeyPair.privateKey, 64)
        Log.d(TAG, "Generated deterministic wallet for $email: 0x${Keys.getAddress(ecKeyPair)}")
        return privateKeyHex
    }

    /**
     * Get the current balance in wei.
     */
    suspend fun getBalance(): BigInteger = withContext(Dispatchers.IO) {
        val w3 = web3j ?: return@withContext BigInteger.ZERO
        val creds = credentials ?: return@withContext BigInteger.ZERO
        try {
            val response = w3.ethGetBalance(creds.address, DefaultBlockParameterName.LATEST).send()
            response.balance
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get balance", e)
            BigInteger.ZERO
        }
    }

    /**
     * Register an asset's pHash on the blockchain.
     * Calls: registerAsset(bytes32 _signatureHash)
     * Returns the transaction hash.
     */
    suspend fun registerAsset(pHash: String): String = withContext(Dispatchers.IO) {
        val w3 = web3j ?: throw IllegalStateException("BlockchainManager not initialized")
        val creds = credentials ?: throw IllegalStateException("No credentials loaded")

        Log.d(TAG, "Registering on-chain: pHash=${pHash.take(16)}...")

        // Convert pHash hex to bytes32 (right-pad to 32 bytes = 64 hex chars)
        val paddedHash = pHash.padEnd(64, '0')
        val signatureBytes = Numeric.hexStringToByteArray(paddedHash)
        val bytes32Value = Bytes32(signatureBytes)

        // Encode: registerAsset(bytes32)
        val function = Function(
            "registerAsset",
            listOf(bytes32Value),
            emptyList()
        )
        val encodedFunction = FunctionEncoder.encode(function)

        // Nonce
        val nonce = w3.ethGetTransactionCount(
            creds.address, DefaultBlockParameterName.LATEST
        ).send().transactionCount

        // Gas price
        val gasPrice = w3.ethGasPrice().send().gasPrice

        // Build raw transaction
        val rawTransaction = RawTransaction.createTransaction(
            nonce,
            gasPrice,
            BigInteger.valueOf(200_000), // gas limit
            Constants.CONTRACT_ADDRESS,
            encodedFunction
        )

        // Sign with EIP-155 (chain ID)
        val signedMessage = TransactionEncoder.signMessage(
            rawTransaction, Constants.CHAIN_ID, creds
        )
        val hexValue = Numeric.toHexString(signedMessage)

        // Send
        val response = w3.ethSendRawTransaction(hexValue).send()

        if (response.hasError()) {
            throw Exception("Tx failed: ${response.error.message}")
        }

        val txHash = response.transactionHash
        Log.d(TAG, "Transaction sent: $txHash")
        txHash
    }

    /**
     * Check if an asset exists on-chain.
     * Calls: assets(bytes32) → returns (bytes32, address, uint256, bool)
     */
    suspend fun checkAsset(pHash: String): Boolean = withContext(Dispatchers.IO) {
        val w3 = web3j ?: throw IllegalStateException("BlockchainManager not initialized")
        val creds = credentials ?: throw IllegalStateException("No credentials loaded")

        val paddedHash = pHash.padEnd(64, '0')
        val signatureBytes = Numeric.hexStringToByteArray(paddedHash)
        val bytes32Value = Bytes32(signatureBytes)

        val function = Function(
            "assets",
            listOf(bytes32Value),
            listOf(
                object : TypeReference<Bytes32>() {},
                object : TypeReference<Address>() {},
                object : TypeReference<Uint256>() {},
                object : TypeReference<Bool>() {}
            )
        )
        val encodedFunction = FunctionEncoder.encode(function)

        val ethCallResponse = w3.ethCall(
            Transaction.createEthCallTransaction(
                creds.address,
                Constants.CONTRACT_ADDRESS,
                encodedFunction
            ),
            DefaultBlockParameterName.LATEST
        ).send()

        val results = FunctionReturnDecoder.decode(
            ethCallResponse.value, function.outputParameters
        )

        if (results.size >= 4) {
            results[3].value as Boolean
        } else {
            false
        }
    }

    /**
     * Get the current wallet address as a checksummed string (for Web3.py compatibility).
     */
    fun getWalletAddress(): String? {
        val address = credentials?.address ?: return null
        return try {
            Keys.toChecksumAddress(address)
        } catch (e: Exception) {
            try {
                // Sometimes it expects the address without 0x
                Keys.toChecksumAddress(Numeric.cleanHexPrefix(address))
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to checksum address", e2)
                address
            }
        }
    }

    /**
     * Shutdown Web3j connection.
     */
    fun shutdown() {
        web3j?.shutdown()
        web3j = null
        credentials = null
    }
}