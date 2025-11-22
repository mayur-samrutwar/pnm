# Vault Contract Deployment Guide

This guide provides instructions for deploying the Vault.sol contract to test networks (Base Testnet or Polygon Mumbai) and integrating with the hub server.

## Prerequisites

- Node.js and npm installed
- Hardhat installed (`npm install` in vault directory)
- A funded wallet with test tokens for gas fees
- RPC URL for your target network

## Setup

### 1. Environment Configuration

Copy the example environment file and configure it:

```bash
cd vault
cp .env.example .env
```

Edit `.env` and set the following variables:

```env
# Required: RPC URL for your target network
RPC_URL=https://sepolia.base.org  # For Base Testnet
# OR
RPC_URL=https://rpc-mumbai.maticvigil.com  # For Polygon Mumbai

# Required: Private key of the account that will deploy (must have funds for gas)
PRIVATE_KEY=your_private_key_here_without_0x_prefix

# Optional: Network-specific RPC URLs (overrides RPC_URL)
BASE_TESTNET_RPC_URL=https://sepolia.base.org
POLYGON_MUMBAI_RPC_URL=https://rpc-mumbai.maticvigil.com
```

**⚠️ Security Note:** Never commit your `.env` file. It contains sensitive private keys.

### 2. Network Configuration

The Hardhat config supports the following networks:
- `localhost` - Local Hardhat node (Chain ID: 1337)
- `baseTestnet` - Base Sepolia Testnet (Chain ID: 84532)
- `polygonMumbai` - Polygon Mumbai Testnet (Chain ID: 80001)
- `sepolia` - Ethereum Sepolia Testnet
- `mainnet` - Ethereum Mainnet

## Deployment

### Deploy to Local Hardhat Chain

For local development and testing:

**Step 1: Start a local Hardhat node**

In a separate terminal:

```bash
cd vault
npm run node
# or
npx hardhat node
```

This will start a local blockchain node at `http://127.0.0.1:8545` with 20 pre-funded accounts. The node will display the private keys and addresses of these accounts.

**Step 2: Deploy the contract**

In another terminal:

```bash
cd vault
npm run deploy:local
# or
npx hardhat run scripts/deploy.js --network localhost
```

The deployment script will use the first account from the Hardhat node (which is automatically funded with 10,000 ETH).

**Expected Output:**

```
Deploying Vault contract...
Network: localhost
Chain ID: 1337n
Deploying with account: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
Account balance: 10000.0 ETH

✅ Vault deployed successfully!
Contract address: 0x5FbDB2315678afecb367f032d93F642f64180aa3

Save this address to your .env file:
VAULT_CONTRACT_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3

Contract owner: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
Owner matches deployer: true
```

**Step 3: Configure Hub Server for Local Network**

Update `hub/.env`:

```env
RPC_URL=http://127.0.0.1:8545
VAULT_CONTRACT_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3  # From deployment output
HUB_PRIVATE_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80  # Use one of the Hardhat node accounts
```

**Note:** The Hardhat node provides 20 test accounts with private keys. You can use any of these for testing. The private keys are displayed when you start the node.

### Deploy to Base Testnet

```bash
npx hardhat run scripts/deploy.js --network baseTestnet
```

### Deploy to Polygon Mumbai

```bash
npx hardhat run scripts/deploy.js --network polygonMumbai
```

### Using npm scripts

```bash
# Base Testnet
npm run deploy:baseTestnet

# Polygon Mumbai
npm run deploy:polygonMumbai
```

### Expected Output

```
Deploying Vault contract...
Network: baseTestnet
Chain ID: 84532n
Deploying with account: 0x...
Account balance: 0.1 ETH

✅ Vault deployed successfully!
Contract address: 0x1234567890123456789012345678901234567890

Save this address to your .env file:
VAULT_CONTRACT_ADDRESS=0x1234567890123456789012345678901234567890

Contract owner: 0x...
Owner matches deployer: true
```

**Important:** Save the contract address from the output. You'll need it for the hub server configuration.

## Hub Server Integration

### 1. Update Hub Server Environment

Add the following to `hub/.env`:

```env
# Blockchain Configuration
RPC_URL=https://sepolia.base.org  # Match your deployment network
VAULT_CONTRACT_ADDRESS=0x1234567890123456789012345678901234567890  # From deployment output
HUB_PRIVATE_KEY=your_hub_private_key_here  # Private key for hub server (must have funds for gas)
```

### 2. Sample Usage in vaultClient.ts

The `VaultClient` class already has a `redeemVoucher` method. Here's how to use it:

```typescript
import { VaultClient, VAULT_ABI } from './services/vaultClient';
import { Voucher } from './services/validator';

// Initialize the client
const vaultClient = new VaultClient(
  process.env.RPC_URL!,
  process.env.HUB_PRIVATE_KEY!,
  process.env.VAULT_CONTRACT_ADDRESS!,
  VAULT_ABI
);

// Example: Redeem a voucher
async function redeemVoucherExample(voucher: Voucher) {
  try {
    // The signature should be the EIP-191 signature from the voucher
    // This is typically stored in voucher.signature
    const signature = voucher.signature;
    
    // Call redeemVoucher on the contract
    const txHash = await vaultClient.redeemVoucher(voucher, signature);
    
    console.log('Voucher redeemed successfully!');
    console.log('Transaction hash:', txHash);
    
    return txHash;
  } catch (error) {
    console.error('Failed to redeem voucher:', error);
    throw error;
  }
}

// Example: Direct contract interaction using ethers.js
async function directContractCall(voucher: Voucher, signature: string) {
  const { ethers } = require('ethers');
  
  // Setup provider and signer
  const provider = new ethers.JsonRpcProvider(process.env.RPC_URL);
  const wallet = new ethers.Wallet(process.env.HUB_PRIVATE_KEY!, provider);
  
  // Create contract instance
  const contract = new ethers.Contract(
    process.env.VAULT_CONTRACT_ADDRESS!,
    VAULT_ABI,
    wallet
  );
  
  // Encode voucher payload (same format as VaultClient)
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
      voucher.contractAddress,
      BigInt(voucher.expiry),
      BigInt(voucher.chainId),
      voucher.payerAddress,
      voucher.payeeAddress,
      BigInt(voucher.amount),
      BigInt(voucher.cumulative),
      ethers.id(voucher.slipId), // Convert UUID to bytes32
    ]
  );
  
  // Call redeemVoucher
  const tx = await contract.redeemVoucher(voucherPayload, signature);
  const receipt = await tx.wait();
  
  console.log('Transaction confirmed:', receipt.hash);
  return receipt.hash;
}
```

## Testing Setup

### 1. Fund HUB Account with Test Tokens

The HUB account (address from `HUB_PRIVATE_KEY`) needs:
- Native tokens (ETH for Base, MATIC for Polygon) for gas fees
- ERC20 test tokens for deposits and voucher redemption

#### Get Test Native Tokens

**Base Testnet:**
- Visit [Base Sepolia Faucet](https://www.coinbase.com/faucets/base-ethereum-goerli-faucet)
- Request test ETH for your HUB account address

**Polygon Mumbai:**
- Visit [Polygon Faucet](https://faucet.polygon.technology/)
- Request test MATIC for your HUB account address

#### Deploy and Fund MockERC20 Token

If you need a test ERC20 token:

```bash
# In vault directory, create a script to deploy MockERC20
npx hardhat run scripts/deployMockERC20.js --network baseTestnet
```

Example `scripts/deployMockERC20.js`:

```javascript
const hre = require("hardhat");

async function main() {
  const MockERC20 = await hre.ethers.getContractFactory("MockERC20");
  const token = await MockERC20.deploy(
    "Test Token",
    "TEST",
    18,
    ethers.parseEther("1000000") // 1M tokens
  );

  await token.waitForDeployment();
  console.log("MockERC20 deployed to:", await token.getAddress());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
```

### 2. Approve Deposit Flows for Testing

To test the deposit and redemption flow:

#### Step 1: Deploy MockERC20 (if not already deployed)

```bash
npx hardhat run scripts/deployMockERC20.js --network baseTestnet
```

#### Step 2: Fund Payer Account

The payer account (the one that will sign vouchers) needs:
- Native tokens for gas
- ERC20 tokens to deposit into the vault

Transfer ERC20 tokens to the payer account:

```javascript
// Using ethers.js
const token = new ethers.Contract(
  MOCK_ERC20_ADDRESS,
  ['function transfer(address to, uint256 amount) external returns (bool)'],
  deployerWallet
);

await token.transfer(PAYER_ADDRESS, ethers.parseEther("1000"));
```

#### Step 3: Approve Vault to Spend Tokens

The payer must approve the Vault contract to spend their ERC20 tokens:

```javascript
const token = new ethers.Contract(
  MOCK_ERC20_ADDRESS,
  [
    'function approve(address spender, uint256 amount) external returns (bool)',
    'function allowance(address owner, address spender) external view returns (uint256)'
  ],
  payerWallet
);

// Approve vault to spend tokens
const approveTx = await token.approve(
  VAULT_CONTRACT_ADDRESS,
  ethers.parseEther("1000")
);
await approveTx.wait();
console.log('Approval confirmed');
```

#### Step 4: Deposit Tokens to Vault

```javascript
const vault = new ethers.Contract(
  VAULT_CONTRACT_ADDRESS,
  ['function deposit(address user, address token, uint256 amount) external'],
  payerWallet
);

const depositTx = await vault.deposit(
  PAYER_ADDRESS,  // user address
  MOCK_ERC20_ADDRESS,  // token address
  ethers.parseEther("500")  // amount
);
await depositTx.wait();
console.log('Deposit confirmed');

// Verify deposit
const depositAmount = await vault.deposits(PAYER_ADDRESS);
console.log('Deposit balance:', ethers.formatEther(depositAmount));
```

#### Step 5: Test Voucher Redemption

Now you can test voucher redemption using the HUB server:

```javascript
// In hub server
const vaultClient = new VaultClient(
  process.env.RPC_URL!,
  process.env.HUB_PRIVATE_KEY!,
  process.env.VAULT_CONTRACT_ADDRESS!,
  VAULT_ABI
);

// Redeem voucher (voucher must be signed by payer)
const txHash = await vaultClient.redeemVoucher(voucher, voucher.signature);
console.log('Redemption tx:', txHash);
```

## Complete Test Flow Summary

1. **Deploy Vault** → Get `VAULT_CONTRACT_ADDRESS`
2. **Deploy MockERC20** → Get `MOCK_ERC20_ADDRESS`
3. **Fund Accounts:**
   - Fund HUB account with native tokens (for gas)
   - Fund payer account with native tokens (for gas)
   - Transfer ERC20 tokens to payer account
4. **Approve & Deposit:**
   - Payer approves Vault to spend ERC20 tokens
   - Payer deposits ERC20 tokens into Vault
5. **Test Redemption:**
   - Create and sign a voucher (payer signs)
   - HUB server calls `redeemVoucher` with voucher and signature
   - Verify tokens are transferred to payee

## Troubleshooting

### Insufficient Balance Error

```
Error: Insufficient balance. Please fund your account with test tokens.
```

**Solution:** Fund your deployment account with native tokens from the network faucet.

### Contract Verification Failed

If you want to verify the contract on block explorers:

```bash
npx hardhat verify --network baseTestnet <CONTRACT_ADDRESS>
```

### Transaction Reverted

Common reasons:
- Insufficient deposits in vault
- Voucher expired
- Invalid signature
- Slip ID already used
- Chain ID mismatch

Check the transaction receipt for the specific revert reason.

## Network Information

### Base Testnet
- Chain ID: 84532
- RPC URL: `https://sepolia.base.org`
- Block Explorer: https://sepolia-explorer.base.org
- Faucet: https://www.coinbase.com/faucets/base-ethereum-goerli-faucet

### Polygon Mumbai
- Chain ID: 80001
- RPC URL: `https://rpc-mumbai.maticvigil.com`
- Block Explorer: https://mumbai.polygonscan.com
- Faucet: https://faucet.polygon.technology/

