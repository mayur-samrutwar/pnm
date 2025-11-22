const hre = require("hardhat");

async function main() {
  console.log("Deploying MockERC20 contract...");
  console.log("Network:", hre.network.name);
  console.log("Chain ID:", (await hre.ethers.provider.getNetwork()).chainId);

  // Get the deployer account
  const [deployer] = await hre.ethers.getSigners();
  console.log("Deploying with account:", deployer.address);

  // Check balance
  const balance = await hre.ethers.provider.getBalance(deployer.address);
  console.log("Account balance:", hre.ethers.formatEther(balance), "ETH");

  if (balance === 0n) {
    throw new Error("Insufficient balance. Please fund your account with test tokens.");
  }

  // Deploy MockERC20 contract
  // Parameters: name, symbol, decimals, initialSupply
  const MockERC20 = await hre.ethers.getContractFactory("MockERC20");
  const token = await MockERC20.deploy(
    "Test Token",
    "TEST",
    18,
    hre.ethers.parseEther("1000000") // 1M tokens
  );

  await token.waitForDeployment();

  const tokenAddress = await token.getAddress();
  console.log("\n✅ MockERC20 deployed successfully!");
  console.log("Contract address:", tokenAddress);
  console.log("Token name: Test Token");
  console.log("Token symbol: TEST");
  console.log("Total supply: 1,000,000 TEST");
  console.log("\nSave this address to your .env file:");
  console.log(`MOCK_ERC20_ADDRESS=${tokenAddress}`);
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

