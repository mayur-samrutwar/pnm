# Hub Server

Node.js TypeScript Express server for voucher validation and redemption, serving as the middleware between clients and the Vault smart contract.

## Prerequisites

- Node.js v18 or higher
- npm v9 or higher

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create a `.env` file in the root directory with the following **REQUIRED** variables:

   ```env
   # REQUIRED: RPC URL for blockchain connection
   RPC_URL=https://eth-mainnet.g.alchemy.com/v2/YOUR_API_KEY
   
   # REQUIRED: Private key for signing transactions (without 0x prefix)
   HUB_PRIVATE_KEY=your_private_key_here
   
   # REQUIRED: Vault contract address
   VAULT_CONTRACT_ADDRESS=0x1234567890123456789012345678901234567890
   
   # Optional: Server port (default: 3000)
   PORT=3000
   
   # Optional: Enable on-chain redemption (default: false)
   REDEEM_ON_CHAIN=false
   
   # Optional: Node environment
   NODE_ENV=development
   ```

## Available Scripts

- `npm start` - Start production server (requires `npm run build` first)
- `npm run dev` - Start development server with hot reload
- `npm test` - Run Jest tests

## Project Structure

```
hub/
├── src/
│   ├── index.ts              # Express server entry point
│   ├── routes/
│   │   └── voucher.ts        # Voucher API routes
│   ├── services/
│   │   ├── validator.ts      # Voucher validation logic
│   │   └── vaultClient.ts    # Ethers.js client for Vault contract
│   └── db/
│       └── inMemory.ts       # In-memory database with JSON persistence
├── data/
│   └── db.json               # Persistent storage (auto-created)
├── json/
│   ├── voucher-schema.json   # JSON schema for vouchers
│   └── example-voucher.json  # Example voucher
├── package.json
├── tsconfig.json
└── jest.config.js
```

## API Endpoints

### POST /api/v1/validate

Validates a voucher's JSON schema, signature, expiry, and contract address.

**Request Body:**
```json
{
  "voucher": {
    "payerAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "payeeAddress": "0x8ba1f109551bD432803012645Hac136c22C5e2",
    "amount": 1000000000000000000,
    "chainId": 1,
    "cumulative": 5000000000000000000,
    "counter": 42,
    "expiry": 1735689600,
    "slipId": "550e8400-e29b-41d4-a716-446655440000",
    "contractAddress": "0x1234567890123456789012345678901234567890",
    "signature": "0x1a2b3c4d5e6f7890abcdef..."
  }
}
```

**Response:**
```json
{
  "valid": true
}
```
or
```json
{
  "valid": false,
  "reason": "Voucher has expired"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/api/v1/validate \
  -H "Content-Type: application/json" \
  -d '{
    "voucher": {
      "payerAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
      "payeeAddress": "0x8ba1f109551bD432803012645Hac136c22C5e2",
      "amount": 1000000000000000000,
      "chainId": 1,
      "cumulative": 5000000000000000000,
      "counter": 42,
      "expiry": 1735689600,
      "slipId": "550e8400-e29b-41d4-a716-446655440000",
      "contractAddress": "0x1234567890123456789012345678901234567890",
      "signature": "0x1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12"
    }
  }'
```

### POST /api/v1/redeem

Atomically redeems a voucher by checking the database for used slips, marking it as used, and optionally calling the on-chain redeem function.

**Request Body:**
```json
{
  "voucher": {
    "payerAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "payeeAddress": "0x8ba1f109551bD432803012645Hac136c22C5e2",
    "amount": 1000000000000000000,
    "chainId": 1,
    "cumulative": 5000000000000000000,
    "counter": 42,
    "expiry": 1735689600,
    "slipId": "550e8400-e29b-41d4-a716-446655440000",
    "contractAddress": "0x1234567890123456789012345678901234567890",
    "signature": "0x1a2b3c4d5e6f7890abcdef..."
  }
}
```

**Response (when REDEEM_ON_CHAIN=false):**
```json
{
  "status": "reserved"
}
```

**Response (when REDEEM_ON_CHAIN=true):**
```json
{
  "status": "reserved",
  "txHash": "0xabc123..."
}
```

**Response (error):**
```json
{
  "status": "error",
  "reason": "Voucher slip already used"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/api/v1/redeem \
  -H "Content-Type: application/json" \
  -d '{
    "voucher": {
      "payerAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
      "payeeAddress": "0x8ba1f109551bD432803012645Hac136c22C5e2",
      "amount": 1000000000000000000,
      "chainId": 1,
      "cumulative": 5000000000000000000,
      "counter": 42,
      "expiry": 1735689600,
      "slipId": "550e8400-e29b-41d4-a716-446655440000",
      "contractAddress": "0x1234567890123456789012345678901234567890",
      "signature": "0x1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12"
    }
  }'
```

### POST /api/v1/depositWebhook

Records a deposit in the database. This endpoint is typically called by a webhook when a deposit transaction is confirmed on-chain.

**Request Body:**
```json
{
  "user": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
  "amount": "1000000000000000000",
  "token": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
  "txHash": "0xabc123def456..."
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Deposit recorded"
}
```

**cURL Example:**
```bash
curl -X POST http://localhost:3000/api/v1/depositWebhook \
  -H "Content-Type: application/json" \
  -d '{
    "user": "0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb",
    "amount": "1000000000000000000",
    "token": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
    "txHash": "0xabc123def4567890123456789012345678901234567890123456789012345678"
  }'
```

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "ok",
  "message": "Hub server is running"
}
```

**cURL Example:**
```bash
curl http://localhost:3000/health
```

## Testing

Run tests with Jest:

```bash
npm test
```

Tests cover:
- Valid signature verification
- Expired voucher rejection
- Duplicate slip rejection (simulating two redeem calls)
- Schema validation

## Environment Variables

### Required

- `RPC_URL` - Ethereum RPC endpoint URL (e.g., Alchemy, Infura)
- `HUB_PRIVATE_KEY` - Private key for signing transactions (without 0x prefix)
- `VAULT_CONTRACT_ADDRESS` - Address of the deployed Vault contract

### Optional

- `PORT` - Server port (default: 3000)
- `REDEEM_ON_CHAIN` - Set to "true" to enable on-chain redemption (default: false)
- `NODE_ENV` - Node environment (development/production)

## Database

The in-memory database persists data to `data/db.json`. This file is automatically created on first use and stores:
- Used slip IDs (to prevent double-spending)
- Deposit records (user, amount, token, txHash, timestamp)

## Security Notes

- Never commit `.env` file to version control
- Keep `HUB_PRIVATE_KEY` secure and never expose it
- Use environment-specific RPC URLs
- Consider using a hardware wallet or secure key management service in production

## Development

```bash
# Start development server with hot reload
npm run dev

# Build TypeScript
npm run build

# Run tests
npm test
```

The server will start on `http://localhost:3000` (or the port specified in `.env`).
