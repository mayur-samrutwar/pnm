const hre = require("hardhat");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  const recipientAddress = "0xdef24ea5b813f14c2cbca63747df446f4883c788";
  
  console.log("Funding address:", recipientAddress);
  console.log("From:", deployer.address);
  
  // Send ETH
  const ethBalance = await hre.ethers.provider.getBalance(recipientAddress);
  console.log("Current ETH balance:", hre.ethers.formatEther(ethBalance), "ETH");
  
  if (ethBalance === 0n) {
    const tx = await deployer.sendTransaction({
      to: recipientAddress,
      value: hre.ethers.parseEther("1.0")
    });
    await tx.wait();
    console.log("✅ ETH sent! Transaction:", tx.hash);
  } else {
    console.log("Address already has ETH");
  }
  
  // Send USDC
  const tokenAddress = "0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0";
  const erc20Abi = ['function balanceOf(address owner) external view returns (uint256)', 'function transfer(address to, uint256 amount) external returns (bool)'];
  const token = await hre.ethers.getContractAt(erc20Abi, tokenAddress, deployer);
  
  const usdcBalance = await token.balanceOf(recipientAddress);
  console.log("Current USDC balance:", hre.ethers.formatUnits(usdcBalance, 6), "USDC");
  
  if (usdcBalance === 0n) {
    const usdcTx = await token.transfer(recipientAddress, hre.ethers.parseUnits("1000", 6));
    await usdcTx.wait();
    console.log("✅ USDC sent! Transaction:", usdcTx.hash);
  } else {
    console.log("Address already has USDC");
  }
  
  // Final balances
  const finalEth = await hre.ethers.provider.getBalance(recipientAddress);
  const finalUsdc = await token.balanceOf(recipientAddress);
  console.log("\n✅ Final balances:");
  console.log("  ETH:", hre.ethers.formatEther(finalEth), "ETH");
  console.log("  USDC:", hre.ethers.formatUnits(finalUsdc, 6), "USDC");
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

