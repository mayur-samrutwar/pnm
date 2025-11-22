# PNM Project

A 3-part decentralized application consisting of a mobile Android app, a hub server, and smart contracts.

## Project Structure

```
pnm/
├── mobile-app/      # Kotlin Android app with Jetpack Compose
├── hub-server/      # Node.js + Express + TypeScript backend
└── contracts/       # Solidity smart contracts with Hardhat
```

## Overview

### Mobile App
Native Android application built with Kotlin and Jetpack Compose, providing the user interface for interacting with the decentralized system.

### Hub Server
RESTful API server built with Node.js, Express, and TypeScript, serving as the middleware between the mobile app and blockchain contracts.

### Contracts
Smart contracts written in Solidity, deployed and tested using Hardhat framework.

## Quick Start

### Prerequisites
- **Android Development**: Android Studio, JDK 17+, Android SDK
- **Node.js**: v18+ and npm
- **Blockchain**: Node.js environment for Hardhat

### Mobile App

```bash
cd mobile-app
./gradlew assembleDebug
./gradlew installDebug
```

See [mobile-app/README.md](./mobile-app/README.md) for detailed setup and emulator instructions.

### Hub Server

```bash
cd hub-server
npm install
npm run start
```

See [hub-server/README.md](./hub-server/README.md) for detailed setup and available scripts.

### Contracts

```bash
cd contracts
npm install
npx hardhat compile
npx hardhat test
```

See [contracts/README.md](./contracts/README.md) for detailed setup and deployment instructions.

## Development Workflow

1. **Start the Hub Server**: `cd hub-server && npm run start`
2. **Deploy Contracts**: `cd contracts && npx hardhat deploy`
3. **Run Mobile App**: `cd mobile-app && ./gradlew installDebug`

## Contributing

Each module has its own development guidelines. Please refer to the respective README files for module-specific instructions.

## License

[Add your license here]

