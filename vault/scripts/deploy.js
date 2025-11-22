const hre = require("hardhat");

async function main() {
  const YourContract = await hre.ethers.getContractFactory("YourContract");
  const contract = await YourContract.deploy("PNM Contract");

  await contract.waitForDeployment();

  console.log("YourContract deployed to:", await contract.getAddress());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

