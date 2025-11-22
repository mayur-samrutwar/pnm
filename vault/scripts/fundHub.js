const hre = require("hardhat");
const { ethers } = require("ethers");

async function main() {
  const hubAddress = "0x4d53bf06bB637208A3422cDdff9A168e23e7B22a";
  
  // Connect to the running Hardhat node
  const provider = new ethers.JsonRpcProvider("http://127.0.0.1:8545");
  const deployer = new ethers.Wallet("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80", provider);
  
  console.log("Funding hub wallet:", hubAddress);
  console.log("From:", deployer.address);
  
  const balance = await provider.getBalance(hubAddress);
  console.log("Current balance:", ethers.formatEther(balance), "ETH");
  
  if (balance < ethers.parseEther("1.0")) {
    console.log("Sending 2 ETH...");
    const tx = await deployer.sendTransaction({
      to: hubAddress,
      value: ethers.parseEther("2.0")
    });
    const receipt = await tx.wait();
    console.log("✅ Transaction mined! Block:", receipt.blockNumber);
    console.log("Transaction hash:", tx.hash);
    
    const newBalance = await provider.getBalance(hubAddress);
    console.log("New balance:", ethers.formatEther(newBalance), "ETH");
  } else {
    console.log("Hub wallet already has sufficient ETH");
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

