const hre = require("hardhat");

async function main() {
  const tokenAddress = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0";
  const recipientAddress = process.argv[2] || "0xA1f53aA1db7116355D06576435Ab4E33CC308D11";
  
  const [deployer] = await hre.ethers.getSigners();
  console.log("Sending USDC to:", recipientAddress);
  
  const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)', 'function transfer(address to, uint256 amount) external returns (bool)'];
  const token = await hre.ethers.getContractAt(erc20Abi, tokenAddress, deployer);
  
  // Check balance
  const balance = await token.balanceOf(recipientAddress);
  console.log("Current USDC balance:", hre.ethers.formatUnits(balance, 6), "USDC");
  
  if (balance === 0n) {
    console.log("Sending 1000 USDC...");
    const tx = await token.transfer(recipientAddress, hre.ethers.parseUnits("1000", 6));
    await tx.wait();
    console.log("✅ USDC sent! Transaction:", tx.hash);
    
    const newBalance = await token.balanceOf(recipientAddress);
    console.log("New USDC balance:", hre.ethers.formatUnits(newBalance, 6), "USDC");
  } else {
    console.log("Address already has USDC balance");
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

