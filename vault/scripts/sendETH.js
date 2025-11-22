const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  const recipientAddress = process.argv[2] || "0xA1f53aA1db7116355D06576435Ab4E33CC308D11";
  
  console.log("Sending ETH for gas fees...");
  console.log("From:", deployer.address);
  console.log("To:", recipientAddress);
  
  // Check current balance
  const balance = await hre.ethers.provider.getBalance(recipientAddress);
  console.log("Current balance:", hre.ethers.formatEther(balance), "ETH");
  
  // Send 1 ETH for gas fees
  const amount = hre.ethers.parseEther("1.0");
  const tx = await deployer.sendTransaction({
    to: recipientAddress,
    value: amount
  });
  await tx.wait();
  
  console.log("✅ ETH sent successfully!");
  console.log("Transaction hash:", tx.hash);
  
  // Check new balance
  const newBalance = await hre.ethers.provider.getBalance(recipientAddress);
  console.log("New balance:", hre.ethers.formatEther(newBalance), "ETH");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

