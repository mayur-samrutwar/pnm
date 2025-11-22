require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.20",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
    },
  },
  networks: {
    hardhat: {
      chainId: 1337,
    },
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 1337,
      // Accounts are provided by Hardhat node
    },
    baseTestnet: {
      url: process.env.BASE_TESTNET_RPC_URL || process.env.RPC_URL || "https://sepolia.base.org",
      accounts: process.env.PRIVATE_KEY && process.env.PRIVATE_KEY.length === 64 ? [process.env.PRIVATE_KEY] : [],
      chainId: 84532,
    },
    polygonMumbai: {
      url: process.env.POLYGON_MUMBAI_RPC_URL || process.env.RPC_URL || "https://rpc-mumbai.maticvigil.com",
      accounts: process.env.PRIVATE_KEY && process.env.PRIVATE_KEY.length === 64 ? [process.env.PRIVATE_KEY] : [],
      chainId: 80001,
    },
    sepolia: {
      url: process.env.SEPOLIA_RPC_URL || process.env.RPC_URL || "",
      accounts: process.env.PRIVATE_KEY && process.env.PRIVATE_KEY.length === 64 ? [process.env.PRIVATE_KEY] : [],
    },
    mainnet: {
      url: process.env.MAINNET_RPC_URL || process.env.RPC_URL || "",
      accounts: process.env.PRIVATE_KEY && process.env.PRIVATE_KEY.length === 64 ? [process.env.PRIVATE_KEY] : [],
    },
  },
  etherscan: {
    apiKey: process.env.ETHERSCAN_API_KEY || "",
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts",
  },
};

