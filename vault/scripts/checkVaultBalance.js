const { ethers } = require('hardhat');
require('dotenv').config();

const USER_ADDRESS = '0x882ae71f5ea7cb6fb0c654bffafc862c929513b4';

// Vault ABI (minimal - just need deposits function)
const VAULT_ABI = [
  'function deposits(address) external view returns (uint256)'
];

// Chain configurations
const CHAINS = {
  BASE_SEPOLIA: {
    name: 'Base Sepolia',
    chainId: 84532,
    rpcUrl: process.env.BASE_SEPOLIA_RPC_URL || 'https://sepolia.base.org',
    vaultAddress: process.env.VAULT_CONTRACT_ADDRESS_BASE_SEPOLIA || '0x9251b4c1ea6e19178870208e5d92e724FC5A4B79',
  },
  ETHEREUM_SEPOLIA: {
    name: 'Ethereum Sepolia',
    chainId: 11155111,
    rpcUrl: process.env.ETHEREUM_SEPOLIA_RPC_URL || process.env.SEPOLIA_RPC_URL || 'https://sepolia.drpc.org',
    vaultAddress: process.env.VAULT_CONTRACT_ADDRESS_ETHEREUM_SEPOLIA || '0x0983966A5bCcE66cb3F488BB04A5198e799A2dB3',
  }
};

async function checkVaultBalance() {
  console.log(`\n🔍 Checking vault balance for address: ${USER_ADDRESS}\n`);
  console.log('='.repeat(60));

  let totalBalance = 0n;
  const balances = [];

  for (const [chainKey, chainConfig] of Object.entries(CHAINS)) {
    try {
      console.log(`\n📡 Checking ${chainConfig.name} (Chain ID: ${chainConfig.chainId})...`);
      console.log(`   RPC: ${chainConfig.rpcUrl}`);
      console.log(`   Vault: ${chainConfig.vaultAddress}`);

      const provider = new ethers.JsonRpcProvider(chainConfig.rpcUrl);
      const vault = new ethers.Contract(chainConfig.vaultAddress, VAULT_ABI, provider);

      const balance = await vault.deposits(USER_ADDRESS);
      const balanceUSDC = ethers.formatUnits(balance, 6); // USDC has 6 decimals

      console.log(`   ✅ Balance: ${balanceUSDC} USDC (${balance.toString()} raw)`);

      totalBalance += balance;
      balances.push({
        chain: chainConfig.name,
        chainId: chainConfig.chainId,
        balance: balance.toString(),
        balanceUSDC: balanceUSDC
      });
    } catch (error) {
      console.error(`   ❌ Error checking ${chainConfig.name}:`, error.message);
      balances.push({
        chain: chainConfig.name,
        chainId: chainConfig.chainId,
        balance: '0',
        balanceUSDC: '0.0',
        error: error.message
      });
    }
  }

  console.log('\n' + '='.repeat(60));
  console.log('\n📊 SUMMARY:\n');

  balances.forEach(({ chain, chainId, balanceUSDC, error }) => {
    if (error) {
      console.log(`   ${chain} (${chainId}): ❌ Error - ${error}`);
    } else {
      console.log(`   ${chain} (${chainId}): ${balanceUSDC} USDC`);
    }
  });

  const totalUSDC = ethers.formatUnits(totalBalance, 6);
  console.log(`\n   💰 TOTAL ACROSS ALL CHAINS: ${totalUSDC} USDC`);
  console.log('='.repeat(60));
  console.log();

  return {
    userAddress: USER_ADDRESS,
    balances,
    totalBalance: totalBalance.toString(),
    totalUSDC
  };
}

// Run the script
checkVaultBalance()
  .then((result) => {
    console.log('✅ Check complete!');
    process.exit(0);
  })
  .catch((error) => {
    console.error('❌ Script failed:', error);
    process.exit(1);
  });

