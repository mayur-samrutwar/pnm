const hre = require("hardhat");

async function main() {
  const vaultAddress = process.env.VAULT_ADDRESS || "0x8A791620dd6260079BF849Dc5567aDC3F2FdC318";
  
  console.log("Checking deposits in vault contract:", vaultAddress);
  console.log("");

  const vault = await hre.ethers.getContractAt("Vault", vaultAddress);
  
  // Get all signers to check their deposits
  const signers = await hre.ethers.getSigners();
  
  for (let i = 0; i < Math.min(10, signers.length); i++) {
    const address = signers[i].address;
    const deposit = await vault.deposits(address);
    if (deposit > 0n) {
      console.log(`Address ${address}: ${hre.ethers.formatUnits(deposit, 6)} USDC`);
    }
  }
  
  // Also check a specific address if provided
  const checkAddress = process.env.CHECK_ADDRESS;
  if (checkAddress) {
    console.log(`\nChecking specific address: ${checkAddress}`);
    const deposit = await vault.deposits(checkAddress);
    console.log(`Deposit: ${hre.ethers.formatUnits(deposit, 6)} USDC`);
    console.log(`Deposit (raw): ${deposit.toString()}`);
  }
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

