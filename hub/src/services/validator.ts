import Ajv from 'ajv';
import { ethers } from 'ethers';
import voucherSchema from '../../../json/voucher-schema.json';

const ajv = new Ajv({ allErrors: true });
const validateSchema = ajv.compile(voucherSchema);

export interface Voucher {
  payerAddress: string;
  payeeAddress: string;
  amount: number;
  chainId: number;
  cumulative: number;
  counter: number;
  expiry: number;
  slipId: string;
  contractAddress: string;
  metadata?: Record<string, any>;
  signature: string;
}

/**
 * Verify voucher against JSON schema
 */
export function verifySchema(voucher: any): boolean {
  return validateSchema(voucher) === true;
}

/**
 * Verify voucher signature using ethers.js
 * Returns the recovered address and validation result
 * 
 * Matches the Vault.sol contract logic:
 * 1. keccak256(voucherPayload) -> messageHash
 * 2. messageHash.toEthSignedMessageHash() -> ethSignedMessageHash
 * 3. ECDSA.recover(ethSignedMessageHash, signature) -> recovered address
 */
export function verifySignature(voucher: Voucher): { valid: boolean; recovered: string } {
  try {
    // Reconstruct the message hash (same as Vault.sol does)
    // The contract expects ABI-encoded data without signature
    const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
      [
        'address', // contractAddress
        'uint256', // expiry
        'uint256', // chainId
        'address', // payerAddress
        'address', // payeeAddress
        'uint256', // amount
        'uint256', // cumulative
        'bytes32', // slipId (convert UUID string to bytes32)
      ],
      [
        voucher.contractAddress,
        BigInt(voucher.expiry),
        BigInt(voucher.chainId),
        voucher.payerAddress,
        voucher.payeeAddress,
        BigInt(voucher.amount),
        BigInt(voucher.cumulative),
        ethers.id(voucher.slipId), // Convert UUID to bytes32 hash
      ]
    );

    // Hash the payload (same as keccak256 in Solidity)
    const messageHash = ethers.keccak256(voucherPayload);

    // Apply EIP-191 prefix manually (same as toEthSignedMessageHash in Solidity)
    // OpenZeppelin's MessageHashUtils.toEthSignedMessageHash does:
    // keccak256(abi.encodePacked("\x19Ethereum Signed Message:\n32", hash))
    // Note: "32" is the string representation of the length, not a byte
    const messageBytes = ethers.getBytes(messageHash);
    const prefix = ethers.toUtf8Bytes('\x19Ethereum Signed Message:\n32');
    const ethSignedMessageHash = ethers.keccak256(ethers.concat([prefix, messageBytes]));

    // Recover address from signature
    const recovered = ethers.recoverAddress(ethSignedMessageHash, voucher.signature);

    return {
      valid: recovered.toLowerCase() === voucher.payerAddress.toLowerCase(),
      recovered,
    };
  } catch (error) {
    return {
      valid: false,
      recovered: '',
    };
  }
}

/**
 * Verify voucher expiry
 */
export function verifyExpiry(voucher: Voucher): boolean {
  return voucher.expiry > Math.floor(Date.now() / 1000);
}

/**
 * Verify contract address matches expected address
 */
export function verifyContractAddress(
  voucher: Voucher,
  expectedAddress: string
): boolean {
  return voucher.contractAddress.toLowerCase() === expectedAddress.toLowerCase();
}

