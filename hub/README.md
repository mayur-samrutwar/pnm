# Hub Server

Node.js backend server built with Express and TypeScript, serving as the middleware between the mobile app and blockchain contracts.

## Prerequisites

- Node.js v18 or higher
- npm v9 or higher (comes with Node.js)

## Setup

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create a `.env` file in the root directory:
   ```bash
   cp .env.example .env
   ```
   Edit `.env` with your configuration values.

## Available Scripts

### Development

```bash
# Start development server with hot reload
npm run dev

# Start production server
npm start

# Build TypeScript to JavaScript
npm run build

# Watch mode - rebuild on file changes
npm run watch
```

### Testing

```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Run tests with coverage
npm run test:coverage

# Run tests in verbose mode
npm run test:verbose
```

### Code Quality

```bash
# Run ESLint
npm run lint

# Fix ESLint issues automatically
npm run lint:fix

# Run TypeScript type checking
npm run type-check

# Format code with Prettier
npm run format
```

### Production

```bash
# Build for production
npm run build

# Start production server
npm start

# Start with PM2 (if configured)
npm run start:pm2
```

## Project Structure

```
hub-server/
├── src/
│   ├── controllers/      # Request handlers
│   ├── services/         # Business logic
│   ├── models/           # Data models
│   ├── routes/           # API routes
│   ├── middleware/       # Express middleware
│   ├── utils/            # Utility functions
│   ├── types/            # TypeScript type definitions
│   └── index.ts          # Entry point
├── tests/                # Test files
├── dist/                 # Compiled JavaScript (generated)
├── package.json
├── tsconfig.json
└── .env.example
```

## Environment Variables

Create a `.env` file with the following variables:

```env
# Server Configuration
PORT=3000
NODE_ENV=development

# Database (if applicable)
DATABASE_URL=your_database_url

# Blockchain Configuration
RPC_URL=your_rpc_url
CONTRACT_ADDRESS=your_contract_address
PRIVATE_KEY=your_private_key

# API Keys
API_KEY=your_api_key
```

## API Endpoints

[Document your API endpoints here]

Example:
- `GET /api/health` - Health check endpoint
- `POST /api/users` - Create a new user

## Running the Server

### Development Mode

```bash
npm run dev
```

The server will start on `http://localhost:3000` (or the port specified in `.env`).

### Production Mode

```bash
npm run build
npm start
```

## Testing

Tests are written using [Jest/Mocha/Vitest - specify your test framework].

```bash
# Run all tests
npm test

# Run specific test file
npm test -- path/to/test/file.test.ts
```

## Troubleshooting

### Port Already in Use
```bash
# Find process using port 3000
lsof -ti:3000

# Kill the process
kill -9 $(lsof -ti:3000)
```

### TypeScript Errors
- Ensure all dependencies are installed: `npm install`
- Check `tsconfig.json` configuration
- Run type checking: `npm run type-check`

### Module Not Found
- Delete `node_modules` and `package-lock.json`
- Run `npm install` again

## Development Notes

- Uses Express.js for routing and middleware
- TypeScript for type safety
- Hot reload enabled in development mode
- CORS enabled for mobile app communication

