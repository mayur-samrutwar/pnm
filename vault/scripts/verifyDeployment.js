const hre = require("hardhat");

/**
 * Verify deployment by checking contract addresses and configurations
 */
async function main() {
  const networkName = hre.network.name.toLowerCase();
  const network = await hre.ethers.provider.getNetwork();
  const chainId = Number(network.chainId);
  
  console.log("\n" + "=".repeat(60));
  console.log("🔍 Verifying Deployment");
  console.log("=".repeat(60));
  console.log("Network:", networkName);
  console.log("Chain ID:", chainId);
  
  // Get addresses from environment or command line
  const vaultAddress = process.argv[2] || process.env.VAULT_CONTRACT_ADDRESS;
  const receiverAddress = process.argv[3] || process.env.CROSS_CHAIN_RECEIVER_ADDRESS;
  
  if (!vaultAddress) {
    console.error("❌ Vault address not provided");
    console.log("Usage: npx hardhat run scripts/verifyDeployment.js --network <network> [vaultAddress] [receiverAddress]");
    process.exit(1);
  }
  
  console.log("\n📋 Checking contracts...");
  console.log("Vault:", vaultAddress);
  if (receiverAddress) {
    console.log("CrossChainReceiver:", receiverAddress);
  }
  
  try {
    // Check Vault
    console.log("\n1️⃣ Checking Vault contract...");
    const Vault = await hre.ethers.getContractFactory("Vault");
    const vault = Vault.attach(vaultAddress);
    
    const owner = await vault.owner();
    console.log("   ✅ Vault owner:", owner);
    
    // Check CrossChainReceiver if provided
    if (receiverAddress) {
      console.log("\n2️⃣ Checking CrossChainReceiver contract...");
      const CrossChainReceiver = await hre.ethers.getContractFactory("CrossChainReceiver");
      const receiver = CrossChainReceiver.attach(receiverAddress);
      
      const vaultFromReceiver = await receiver.vault();
      const nativeUSDC = await receiver.nativeUSDC();
      const authorizedSender = await receiver.authorizedSender();
      
      console.log("   ✅ Vault address:", vaultFromReceiver);
      console.log("   ✅ Native USDC:", nativeUSDC);
      console.log("   ✅ Authorized sender:", authorizedSender);
      
      // Verify vault address matches
      if (vaultFromReceiver.toLowerCase() !== vaultAddress.toLowerCase()) {
        console.log("   ⚠️  Warning: Vault address mismatch!");
      } else {
        console.log("   ✅ Vault address matches");
      }
    }
    
    console.log("\n✅ Verification complete!");
    
  } catch (error) {
    console.error("\n❌ Verification failed:", error.message);
    if (error.code === "CALL_EXCEPTION") {
      console.error("   Contract may not be deployed at this address");
    }
    process.exit(1);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

