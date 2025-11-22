const hre = require("hardhat");

async function main() {
  // Get addresses from environment or use defaults
  const vaultAddress = process.env.VAULT_ADDRESS || "0x8A791620dd6260079BF849Dc5567aDC3F2FdC318";
  const newOwnerAddress = process.env.NEW_OWNER_ADDRESS || "0x4d53bf06bB637208A3422cDdff9A168e23e7B22a";

  console.log("Transferring ownership of Vault contract...");
  console.log("Vault address:", vaultAddress);
  console.log("New owner address:", newOwnerAddress);

  // Get the deployer account
  const [deployer] = await hre.ethers.getSigners();
  console.log("Current owner (deployer):", deployer.address);

  // Get the contract using getContractAt (works with deployed contracts)
  const vault = await hre.ethers.getContractAt("Vault", vaultAddress);

  // Check current owner
  const currentOwner = await vault.owner();
  console.log("Current contract owner:", currentOwner);

  if (currentOwner.toLowerCase() !== deployer.address.toLowerCase()) {
    throw new Error("Deployer is not the current owner. Cannot transfer ownership.");
  }

  // Transfer ownership
  console.log("\nTransferring ownership...");
  const tx = await vault.transferOwnership(newOwnerAddress);
  await tx.wait();

  // Verify new owner
  const newOwner = await vault.owner();
  console.log("\n✅ Ownership transferred successfully!");
  console.log("New contract owner:", newOwner);
  console.log("New owner matches target:", newOwner.toLowerCase() === newOwnerAddress.toLowerCase());
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

