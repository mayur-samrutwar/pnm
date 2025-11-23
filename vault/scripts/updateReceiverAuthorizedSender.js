const hre = require("hardhat");

/**
 * Update the authorized sender address for CrossChainReceiver
 * This should be called after deploying to both chains to set the hub's address
 * on the opposite chain as the authorized sender
 */
async function main() {
  const receiverAddress = process.argv[2];
  const newAuthorizedSender = process.argv[3];
  
  if (!receiverAddress || !newAuthorizedSender) {
    console.error("Usage: npx hardhat run scripts/updateReceiverAuthorizedSender.js --network <network> <receiverAddress> <newAuthorizedSender>");
    console.error("\nExample:");
    console.error("  npx hardhat run scripts/updateReceiverAuthorizedSender.js --network basesepolia 0x... 0x...");
    process.exit(1);
  }
  
  console.log("Updating CrossChainReceiver authorized sender...");
  console.log("Receiver address:", receiverAddress);
  console.log("New authorized sender:", newAuthorizedSender);
  
  const [deployer] = await hre.ethers.getSigners();
  console.log("Deployer:", deployer.address);
  
  // Note: The current CrossChainReceiver contract doesn't have a setter for authorizedSender
  // This would need to be added to the contract, or you need to redeploy with the correct address
  // For now, this script just shows what needs to be done
  
  console.log("\n⚠️  Note: CrossChainReceiver.authorizedSender is immutable.");
  console.log("   You need to either:");
  console.log("   1. Redeploy CrossChainReceiver with the correct authorizedSender, OR");
  console.log("   2. Add a setter function to the contract (requires contract modification)");
  
  // If you add a setter function, uncomment this:
  /*
  const CrossChainReceiver = await hre.ethers.getContractFactory("CrossChainReceiver");
  const receiver = CrossChainReceiver.attach(receiverAddress);
  
  const tx = await receiver.setAuthorizedSender(newAuthorizedSender);
  await tx.wait();
  console.log("✅ Authorized sender updated!");
  */
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

