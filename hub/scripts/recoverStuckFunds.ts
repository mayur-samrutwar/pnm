/**
 * Recovery script to deposit stuck USDC from CrossChainReceiver to Vault
 * This can happen if the bridge succeeds but the deposit fails
 */

import { ethers } from 'ethers';
import * as dotenv from 'dotenv';
import { ChainRegistry } from '../src/services/chainRegistry';

dotenv.config();

const RECEIVER_ABI = [
  'function depositToVaultDirect(address userAddress, uint256 amount) external',
  'function nativeUSDC() external view returns (address)',
  'function vault() external view returns (address)',
];

async function recoverStuckFunds(
  chainId: number,
  receiverAddress: string,
  userAddress: string,
  amount: bigint
) {
  const config = ChainRegistry.getChainConfig(chainId);
  if (!config) {
    throw new Error(`Chain configuration not found for chainId: ${chainId}`);
  }

  const provider = new ethers.JsonRpcProvider(config.rpcUrl);
  const wallet = new ethers.Wallet(process.env.HUB_PRIVATE_KEY!, provider);

  console.log(`\n=== Recovering stuck funds on chain ${chainId} ===`);
  console.log(`Receiver: ${receiverAddress}`);
  console.log(`User: ${userAddress}`);
  console.log(`Amount: ${ethers.formatUnits(amount, 6)} USDC`);

  // Check receiver balance
  const nativeUSDC = ChainRegistry.getNativeUSDCAddress(chainId)!;
  const usdcAbi = ['function balanceOf(address owner) external view returns (uint256)'];
  const usdcContract = new ethers.Contract(nativeUSDC, usdcAbi, provider);
  const receiverBalance = await usdcContract.balanceOf(receiverAddress);
  console.log(`\nReceiver USDC balance: ${ethers.formatUnits(receiverBalance, 6)} USDC`);

  if (receiverBalance < amount) {
    throw new Error(`Insufficient balance in receiver. Need ${ethers.formatUnits(amount, 6)} USDC, but only have ${ethers.formatUnits(receiverBalance, 6)} USDC`);
  }

  // Call depositToVaultDirect
  const receiver = new ethers.Contract(receiverAddress, RECEIVER_ABI, wallet);
  
  console.log(`\nCalling depositToVaultDirect...`);
  const tx = await receiver.depositToVaultDirect(userAddress, amount);
  console.log(`Transaction sent: ${tx.hash}`);
  
  console.log(`Waiting for confirmation...`);
  const receipt = await tx.wait();
  console.log(`✅ Transaction confirmed in block ${receipt.blockNumber}`);
  console.log(`✅ Successfully deposited ${ethers.formatUnits(amount, 6)} USDC to Vault for user ${userAddress}`);
}

// Main execution
async function main() {
  const args = process.argv.slice(2);
  
  if (args.length < 4) {
    console.log('Usage: ts-node scripts/recoverStuckFunds.ts <chainId> <receiverAddress> <userAddress> <amountInUSDC>');
    console.log('\nExample:');
    console.log('  ts-node scripts/recoverStuckFunds.ts 84532 0x06538A9654fa9eAbF96263eC42b4ca6c8677529e 0xa124d9f6faff5b9f441ba3d1c4941b4f779596a1 2.0');
    process.exit(1);
  }

  const chainId = parseInt(args[0]);
  const receiverAddress = args[1];
  const userAddress = args[2];
  const amountInUSDC = parseFloat(args[3]);
  const amount = ethers.parseUnits(amountInUSDC.toString(), 6);

  try {
    await recoverStuckFunds(chainId, receiverAddress, userAddress, amount);
  } catch (error: any) {
    console.error('❌ Error:', error.message);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

export { recoverStuckFunds };

