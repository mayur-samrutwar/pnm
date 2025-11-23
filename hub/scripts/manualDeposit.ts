/**
 * Manual deposit script to credit a user's deposit in the Vault
 * This is needed when USDC is in the Vault but the deposits mapping wasn't updated
 */

import { ethers } from 'ethers';
import * as dotenv from 'dotenv';
import { ChainRegistry } from '../src/services/chainRegistry';

dotenv.config();

const VAULT_ABI = [
  'function deposit(address user, address token, uint256 amount) external',
  'function deposits(address) external view returns (uint256)',
];

async function manualDeposit(
  chainId: number,
  userAddress: string,
  amount: bigint
) {
  const config = ChainRegistry.getChainConfig(chainId);
  if (!config) {
    throw new Error(`Chain configuration not found for chainId: ${chainId}`);
  }

  const provider = new ethers.JsonRpcProvider(config.rpcUrl);
  const wallet = new ethers.Wallet(process.env.HUB_PRIVATE_KEY!, provider);

  console.log(`\n=== Manual Deposit on chain ${chainId} ===`);
  console.log(`Vault: ${config.vaultAddress}`);
  console.log(`User: ${userAddress}`);
  console.log(`Amount: ${ethers.formatUnits(amount, 6)} USDC`);

  // Check current deposits
  const vault = new ethers.Contract(config.vaultAddress, VAULT_ABI, provider);
  const currentDeposits = await vault.deposits(userAddress);
  console.log(`\nCurrent user deposits: ${ethers.formatUnits(currentDeposits, 6)} USDC`);

  // Check Vault USDC balance
  const nativeUSDC = ChainRegistry.getNativeUSDCAddress(chainId)!;
  const usdcAbi = [
    'function balanceOf(address owner) external view returns (uint256)',
    'function transfer(address to, uint256 amount) external returns (bool)',
    'function approve(address spender, uint256 amount) external returns (bool)',
  ];
  const usdcContract = new ethers.Contract(nativeUSDC, usdcAbi, provider);
  const vaultBalance = await usdcContract.balanceOf(config.vaultAddress);
  console.log(`Vault USDC balance: ${ethers.formatUnits(vaultBalance, 6)} USDC`);

  // Check hub wallet balance
  const hubBalance = await usdcContract.balanceOf(wallet.address);
  console.log(`Hub wallet USDC balance: ${ethers.formatUnits(hubBalance, 6)} USDC`);
  
  if (hubBalance < amount) {
    throw new Error(`Hub wallet doesn't have enough USDC. Need ${ethers.formatUnits(amount, 6)} USDC, but only have ${ethers.formatUnits(hubBalance, 6)} USDC`);
  }

  // Transfer USDC from hub to Vault and then call deposit
  // This is needed because Vault.deposit() does transferFrom(msg.sender, ...)
  console.log(`\nTransferring ${ethers.formatUnits(amount, 6)} USDC from hub to Vault...`);
  const usdcWithWallet = new ethers.Contract(nativeUSDC, usdcAbi, wallet);
  const transferTx = await usdcWithWallet.transfer(config.vaultAddress, amount);
  await transferTx.wait();
  console.log(`✅ Transferred to Vault`);
  
  console.log(`\nApproving Vault to spend USDC...`);
  const approveTx = await usdcWithWallet.approve(config.vaultAddress, amount);
  await approveTx.wait();
  console.log(`✅ Approved`);
  
  console.log(`\nCalling Vault.deposit()...`);
  const vaultWithWallet = new ethers.Contract(config.vaultAddress, VAULT_ABI, wallet);
  const depositTx = await vaultWithWallet.deposit(userAddress, nativeUSDC, amount);
  await depositTx.wait();
  console.log(`✅ Deposit successful!`);
  
  // Verify
  const newDeposits = await vault.deposits(userAddress);
  console.log(`\nNew user deposits: ${ethers.formatUnits(newDeposits, 6)} USDC`);
}

// Main execution
async function main() {
  const args = process.argv.slice(2);
  
  if (args.length < 3) {
    console.log('Usage: ts-node scripts/manualDeposit.ts <chainId> <userAddress> <amountInUSDC>');
    console.log('\nExample:');
    console.log('  ts-node scripts/manualDeposit.ts 84532 0xa124d9f6faff5b9f441ba3d1c4941b4f779596a1 5.0');
    process.exit(1);
  }

  const chainId = parseInt(args[0]);
  const userAddress = args[1];
  const amountInUSDC = parseFloat(args[2]);
  const amount = ethers.parseUnits(amountInUSDC.toString(), 6);

  try {
    await manualDeposit(chainId, userAddress, amount);
  } catch (error: any) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

export { manualDeposit };

