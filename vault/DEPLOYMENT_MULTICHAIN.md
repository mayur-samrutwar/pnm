# Multichain Deployment Guide

This guide walks you through deploying the Vault and CrossChainReceiver contracts to Base Sepolia and Ethereum Sepolia.

## Prerequisites

1. **Funded accounts** on both chains (for gas fees)
2. **Environment variables** set in `.env`:
   ```bash
   PRIVATE_KEY=your_private_key_here
   BASE_SEPOLIA_RPC_URL=https://sepolia.base.org
   ETHEREUM_SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY
   HUB_ADDRESS=0x... # Hub's address (will be used as authorized sender)
   ```

## Step 1: Compile Contracts

```bash
cd vault
npx hardhat compile
```

## Step 2: Deploy to Base Sepolia

```bash
npx hardhat run scripts/deployMultichain.js --network basesepolia
```

This will:
- Deploy Vault contract
- Deploy CrossChainReceiver contract
- Output addresses and .env entries

**Save the output addresses!**

## Step 3: Deploy to Ethereum Sepolia

```bash
npx hardhat run scripts/deployMultichain.js --network sepolia
```

**Save the output addresses!**

## Step 4: Update CrossChainReceiver Authorized Senders

After deploying to both chains, you need to update the `authorizedSender` in each CrossChainReceiver:

- **Base Sepolia Receiver**: Should authorize the hub's address on Ethereum Sepolia
- **Ethereum Sepolia Receiver**: Should authorize the hub's address on Base Sepolia

**Note**: The current CrossChainReceiver contract has `authorizedSender` as immutable. You have two options:

### Option A: Redeploy with Correct Addresses (Recommended)

1. Note the hub's address on each chain
2. Redeploy CrossChainReceiver with the correct `authorizedSender`:
   - On Base Sepolia: Use hub's Ethereum Sepolia address
   - On Ethereum Sepolia: Use hub's Base Sepolia address

### Option B: Modify Contract (Advanced)

Add a setter function to CrossChainReceiver and redeploy.

## Step 5: Verify Deployments

```bash
# Verify Base Sepolia
npx hardhat run scripts/verifyDeployment.js --network basesepolia <vaultAddress> <receiverAddress>

# Verify Ethereum Sepolia
npx hardhat run scripts/verifyDeployment.js --network sepolia <vaultAddress> <receiverAddress>
```

## Step 6: Update Hub Configuration

Add these to your hub's `.env` file:

```bash
# Base Sepolia
VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA=0x...
CROSS_CHAIN_RECEIVER_ADDRESS_BASE_SEPOLIA=0x...
BASE_SEPOLIA_RPC_URL=https://sepolia.base.org

# Ethereum Sepolia
VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA=0x...
CROSS_CHAIN_RECEIVER_ADDRESS_ETHEREUM_SEPOLIA=0x...
ETHEREUM_SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY

# Hyperlane
HYPERLANE_MAILBOX_ADDRESS=0x... # Get from Hyperlane docs
```

## Step 7: Update Chain Registry

The chain registry in `hub/src/services/chainRegistry.ts` should automatically use these environment variables. Verify they're set correctly.

## Testing the Deployment

1. **Test deposit on Base Sepolia**:
   ```bash
   # Use your existing deposit script
   ```

2. **Test deposit on Ethereum Sepolia**:
   ```bash
   # Use your existing deposit script with sepolia network
   ```

3. **Test cross-chain redemption**:
   - Create a voucher from a user who deposited on Base Sepolia
   - Redeem it with `preferredChainId: 11155111` (Ethereum Sepolia)
   - Check that funds are bridged and redeemed correctly

## Troubleshooting

### "Insufficient balance"
- Fund your deployer account with test ETH on both chains

### "Chain ID mismatch"
- Verify your RPC URLs are correct
- Check network configuration in hardhat.config.js

### "Contract deployment failed"
- Check gas prices
- Verify RPC endpoint is working
- Ensure account has sufficient balance

### CrossChainReceiver not receiving funds
- Verify `authorizedSender` is set correctly
- Check that hub address matches on both chains
- Verify Warp Route addresses are correct

## Next Steps

1. Deploy to production chains (Base Mainnet, Ethereum Mainnet)
2. Set up monitoring for cross-chain transactions
3. Test end-to-end flow with real users

