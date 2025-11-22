# Smart Contracts

Solidity smart contracts deployed and tested using the Hardhat framework.

## Prerequisites

- Node.js v18 or higher
- npm v9 or higher

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create a `.env` file:
   ```bash
   cp .env.example .env
   ```
   Edit `.env` with your network configuration and private keys.

## Hardhat Scripts

### Compilation

```bash
# Compile all contracts
npx hardhat compile

# Force recompilation
npx hardhat clean
npx hardhat compile
```

### Testing

```bash
# Run all tests
npx hardhat test

# Run specific test file
npx hardhat test test/YourContract.test.js

# Run tests with gas reporting
REPORT_GAS=true npx hardhat test

# Run tests with verbose output
npx hardhat test --verbose
```

### Deployment

```bash
# Deploy to local Hardhat network
npx hardhat run scripts/deploy.js

# Deploy to specific network (e.g., sepolia)
npx hardhat run scripts/deploy.js --network sepolia

# Deploy to mainnet (be careful!)
npx hardhat run scripts/deploy.js --network mainnet
```

### Network Interaction

```bash
# Start local Hardhat node
npx hardhat node

# Verify contract on Etherscan
npx hardhat verify --network sepolia DEPLOYED_CONTRACT_ADDRESS "Constructor Arg 1" "Constructor Arg 2"

# Run a script on a network
npx hardhat run scripts/interact.js --network localhost
```

### Other Useful Commands

```bash
# Show Hardhat version
npx hardhat --version

# Show available tasks
npx hardhat help

# Run console (interactive JavaScript console)
npx hardhat console --network localhost

# Flatten contract (for verification)
npx hardhat flatten contracts/YourContract.sol > flattened.sol

# Check coverage
npx hardhat coverage
```

## Project Structure

```
contracts/
├── contracts/              # Solidity source files
│   └── YourContract.sol
├── scripts/                # Deployment and interaction scripts
│   ├── deploy.js
│   └── interact.js
├── test/                   # Test files
│   └── YourContract.test.js
├── artifacts/              # Compiled contracts (generated)
├── cache/                  # Hardhat cache (generated)
├── hardhat.config.js       # Hardhat configuration
├── package.json
└── .env.example
```

## Network Configuration

Configure networks in `hardhat.config.js`:

```javascript
networks: {
  hardhat: {
    // Local development network
  },
  sepolia: {
    url: process.env.SEPOLIA_RPC_URL,
    accounts: [process.env.PRIVATE_KEY],
  },
  mainnet: {
    url: process.env.MAINNET_RPC_URL,
    accounts: [process.env.PRIVATE_KEY],
  },
}
```

## Environment Variables

Create a `.env` file with:

```env
# Network RPC URLs
SEPOLIA_RPC_URL=https://sepolia.infura.io/v3/YOUR_PROJECT_ID
MAINNET_RPC_URL=https://mainnet.infura.io/v3/YOUR_PROJECT_ID

# Private Keys (NEVER commit these!)
PRIVATE_KEY=your_private_key_here

# Etherscan API Key (for contract verification)
ETHERSCAN_API_KEY=your_etherscan_api_key

# Gas Reporter (optional)
COINMARKETCAP_API_KEY=your_coinmarketcap_api_key
```

## Testing

Tests are written using Mocha and Chai. Example test structure:

```javascript
describe("YourContract", function () {
  it("Should deploy successfully", async function () {
    const YourContract = await ethers.getContractFactory("YourContract");
    const contract = await YourContract.deploy();
    await contract.deployed();
    // Add assertions
  });
});
```

Run tests:
```bash
npx hardhat test
```

## Deployment Workflow

1. **Compile contracts**:
   ```bash
   npx hardhat compile
   ```

2. **Run tests**:
   ```bash
   npx hardhat test
   ```

3. **Deploy to testnet**:
   ```bash
   npx hardhat run scripts/deploy.js --network sepolia
   ```

4. **Verify on Etherscan**:
   ```bash
   npx hardhat verify --network sepolia DEPLOYED_ADDRESS "Constructor Args"
   ```

5. **Deploy to mainnet** (after thorough testing):
   ```bash
   npx hardhat run scripts/deploy.js --network mainnet
   ```

## Gas Optimization

- Use `REPORT_GAS=true` to see gas usage in tests
- Review gas reports and optimize contract code
- Consider using libraries for common functionality

## Security Best Practices

- Always test contracts thoroughly before deployment
- Use OpenZeppelin contracts for security-critical functionality
- Get contracts audited before mainnet deployment
- Never commit private keys or `.env` files
- Use multi-sig wallets for contract ownership

## Troubleshooting

### Compilation Errors
- Check Solidity version compatibility
- Ensure all imports are correct
- Run `npx hardhat clean` and recompile

### Network Connection Issues
- Verify RPC URL is correct
- Check network configuration in `hardhat.config.js`
- Ensure sufficient balance for gas fees

### Test Failures
- Check that local node is running (if using `--network localhost`)
- Verify test accounts have sufficient balance
- Review error messages for specific issues

## Development Notes

- Solidity version: ^0.8.0 (check `hardhat.config.js`)
- Uses Hardhat for development and testing
- Supports multiple networks (local, testnet, mainnet)
- Includes gas reporting and contract verification

