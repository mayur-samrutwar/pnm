const hre = require("hardhat");

// Chain configurations
const CHAIN_CONFIGS = {
  basesepolia: {
    chainId: 84532,
    chainName: "Base Sepolia",
    nativeUSDC: "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
    warpRouteUSDC: "0x020dEE96414703c457322eed8504946583a7dd24",
  },
  sepolia: {
    chainId: 11155111,
    chainName: "Ethereum Sepolia",
    nativeUSDC: "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238",
    warpRouteUSDC: "0x352f1c7ffa598d0698c1D8D2fCAb02511c6fF3e9",
  },
};

/**
 * Deploy Vault contract
 */
async function deployVault(deployer) {
  console.log("\n📦 Deploying Vault contract...");
  const Vault = await hre.ethers.getContractFactory("Vault");
  console.log("   Deploying...");
  const vault = await Vault.deploy();
  console.log("   Waiting for deployment transaction...");
  const deploymentTx = vault.deploymentTransaction();
  if (deploymentTx) {
    const receipt = await deploymentTx.wait();
    console.log("   Transaction confirmed in block:", receipt.blockNumber);
  }
  await vault.waitForDeployment();
  const vaultAddress = await vault.getAddress();
  console.log("   Contract address:", vaultAddress);
  
  // Wait a bit for the contract to be fully available
  await new Promise(resolve => setTimeout(resolve, 2000));
  
  // Verify deployment
  try {
    const owner = await vault.owner();
    console.log("✅ Vault deployed:", vaultAddress);
    console.log("   Owner:", owner);
  } catch (error) {
    console.warn("   ⚠️  Could not verify owner (contract may still be deploying):", error.message);
    console.log("✅ Vault deployed:", vaultAddress);
  }
  
  return { vault, vaultAddress };
}

/**
 * Deploy CrossChainReceiver contract
 */
async function deployCrossChainReceiver(deployer, vaultAddress, nativeUSDC, authorizedSender) {
  console.log("\n🌉 Deploying CrossChainReceiver contract...");
  console.log("   Vault address:", vaultAddress);
  console.log("   Native USDC:", nativeUSDC);
  console.log("   Authorized sender (hub):", authorizedSender);
  
  const CrossChainReceiver = await hre.ethers.getContractFactory("CrossChainReceiver");
  console.log("   Deploying...");
  const receiver = await CrossChainReceiver.deploy(
    vaultAddress,
    nativeUSDC,
    authorizedSender
  );
  console.log("   Waiting for deployment transaction...");
  const deploymentTx = receiver.deploymentTransaction();
  if (deploymentTx) {
    const receipt = await deploymentTx.wait();
    console.log("   Transaction confirmed in block:", receipt.blockNumber);
  }
  await receiver.waitForDeployment();
  const receiverAddress = await receiver.getAddress();
  console.log("   Contract address:", receiverAddress);
  
  // Wait a bit for the contract to be fully available
  await new Promise(resolve => setTimeout(resolve, 2000));
  
  console.log("✅ CrossChainReceiver deployed:", receiverAddress);
  
  return { receiver, receiverAddress };
}

/**
 * Deploy to a specific chain
 */
async function deployToChain(chainName, networkName) {
  console.log("\n" + "=".repeat(60));
  console.log(`🚀 Deploying to ${chainName}`);
  console.log("=".repeat(60));
  
  const config = CHAIN_CONFIGS[networkName];
  if (!config) {
    throw new Error(`Unknown chain: ${networkName}`);
  }
  
  // Get deployer account
  const [deployer] = await hre.ethers.getSigners();
  console.log("Deployer address:", deployer.address);
  
  // Check balance
  const balance = await hre.ethers.provider.getBalance(deployer.address);
  console.log("Balance:", hre.ethers.formatEther(balance), "ETH");
  
  if (balance === 0n) {
    throw new Error("Insufficient balance. Please fund your account.");
  }
  
  // Verify chain ID
  const network = await hre.ethers.provider.getNetwork();
  const chainId = Number(network.chainId);
  console.log("Network chain ID:", chainId);
  
  if (chainId !== config.chainId) {
    throw new Error(`Chain ID mismatch! Expected ${config.chainId}, got ${chainId}`);
  }
  
  // Get hub address (authorized sender for CrossChainReceiver)
  // This should be the hub's address on the OTHER chain
  // For now, we'll use the deployer address (since we're using same key)
  // In production, set HUB_ADDRESS to the hub's address on the opposite chain
  let hubAddress = process.env.HUB_ADDRESS;
  if (!hubAddress || hubAddress === "0x0000000000000000000000000000000000000000") {
    // Use deployer address as fallback (for development)
    hubAddress = deployer.address;
    console.log("⚠️  HUB_ADDRESS not set, using deployer address as hub address");
  }
  console.log("Hub address (authorized sender):", hubAddress);
  
  // Deploy Vault
  const { vaultAddress } = await deployVault(deployer);
  
  // Deploy CrossChainReceiver
  const { receiverAddress } = await deployCrossChainReceiver(
    deployer,
    vaultAddress,
    config.nativeUSDC,
    hubAddress
  );
  
  return {
    chainName,
    chainId,
    vaultAddress,
    receiverAddress,
    nativeUSDC: config.nativeUSDC,
    warpRouteUSDC: config.warpRouteUSDC,
  };
}

/**
 * Main deployment function
 */
async function main() {
  const networkName = hre.network.name.toLowerCase();
  
  console.log("\n" + "=".repeat(60));
  console.log("🔧 Multichain Deployment Script");
  console.log("=".repeat(60));
  console.log("Network:", networkName);
  
  // Check if we're deploying to a supported chain
  if (!CHAIN_CONFIGS[networkName]) {
    console.error("\n❌ Unsupported network:", networkName);
    console.log("\nSupported networks:");
    Object.keys(CHAIN_CONFIGS).forEach(name => {
      console.log(`  - ${name} (${CHAIN_CONFIGS[name].chainName})`);
    });
    console.log("\nUsage:");
    console.log("  npx hardhat run scripts/deployMultichain.js --network basesepolia");
    console.log("  npx hardhat run scripts/deployMultichain.js --network sepolia");
    process.exit(1);
  }
  
  try {
    const deployment = await deployToChain(
      CHAIN_CONFIGS[networkName].chainName,
      networkName
    );
    
    // Output deployment summary
    console.log("\n" + "=".repeat(60));
    console.log("✅ DEPLOYMENT COMPLETE");
    console.log("=".repeat(60));
    console.log("\n📋 Deployment Summary:");
    console.log(`   Chain: ${deployment.chainName} (${deployment.chainId})`);
    console.log(`   Vault Address: ${deployment.vaultAddress}`);
    console.log(`   CrossChainReceiver Address: ${deployment.receiverAddress}`);
    console.log(`   Native USDC: ${deployment.nativeUSDC}`);
    console.log(`   Warp Route USDC: ${deployment.warpRouteUSDC}`);
    
    // Generate .env file entries
    console.log("\n📝 Add these to your .env file:");
    console.log("\n# " + deployment.chainName);
    if (networkName === "basesepolia") {
      console.log(`VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA=${deployment.vaultAddress}`);
      console.log(`CROSS_CHAIN_RECEIVER_ADDRESS_BASE_SEPOLIA=${deployment.receiverAddress}`);
      console.log(`BASE_SEPOLIA_RPC_URL=${process.env.BASE_SEPOLIA_RPC_URL || "https://sepolia.base.org"}`);
    } else if (networkName === "sepolia") {
      console.log(`VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA=${deployment.vaultAddress}`);
      console.log(`CROSS_CHAIN_RECEIVER_ADDRESS_ETHEREUM_SEPOLIA=${deployment.receiverAddress}`);
      console.log(`ETHEREUM_SEPOLIA_RPC_URL=${process.env.ETHEREUM_SEPOLIA_RPC_URL || process.env.SEPOLIA_RPC_URL || ""}`);
    }
    
    console.log("\n💡 Next steps:");
    console.log("   1. Deploy to the other chain:");
    if (networkName === "basesepolia") {
      console.log("      npx hardhat run scripts/deployMultichain.js --network sepolia");
    } else {
      console.log("      npx hardhat run scripts/deployMultichain.js --network basesepolia");
    }
    console.log("   2. Update CrossChainReceiver authorized sender addresses");
    console.log("   3. Update hub chain registry with all addresses");
    console.log("   4. Set HYPERLANE_MAILBOX_ADDRESS in .env");
    
  } catch (error) {
    console.error("\n❌ Deployment failed:", error);
    process.exit(1);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

