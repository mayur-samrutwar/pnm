import Ajv from 'ajv';
import { ethers } from 'ethers';
import { ec as EC } from 'elliptic';
import voucherSchema from '../../../json/voucher-schema.json';

// Initialize P-256 curve
const ec = new EC('p256');

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
 * Returns validation result with detailed errors
 */
export function verifySchema(voucher: any): boolean {
  return validateSchema(voucher) === true;
}

/**
 * Get detailed schema validation errors
 */
export function getSchemaErrors(voucher: any): string[] {
  const valid = validateSchema(voucher);
  if (valid) {
    return [];
  }
  const errors = validateSchema.errors || [];
  return errors.map((err: any) => {
    const path = err.instancePath || err.dataPath || 'root';
    return `${path} ${err.message || 'is invalid'}`;
  });
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

/**
 * Verify ECDSA P-256 signature for vouchers/slips
 * 
 * @param voucherJson - The JSON string that was signed (original message)
 * @param publicKeyHex - Public key in uncompressed format: "04" + x (64 hex) + y (64 hex) = 130 hex chars
 * @param signatureHex - Signature in raw r||s format: r (64 hex) + s (64 hex) = 128 hex chars
 * @returns true if signature is valid
 * 
 * Public Key Format:
 * - Android generates: 0x04 || x (32 bytes) || y (32 bytes) = 65 bytes = 130 hex chars
 * - This function expects the same format (with or without 0x prefix)
 * 
 * Signature Format:
 * - Android generates: r (32 bytes) || s (32 bytes) = 64 bytes = 128 hex chars
 * - This is the raw format, not DER-encoded
 */
export function verifySignatureP256(
  voucherJson: string,
  publicKeyHex: string,
  signatureHex: string
): boolean {
  try {
    // Normalize hex strings (remove 0x prefix if present)
    const normalizedPubKey = publicKeyHex.replace(/^0x/, '');
    const normalizedSig = signatureHex.replace(/^0x/, '');

    // Validate lengths
    if (normalizedPubKey.length !== 130) {
      throw new Error(`Invalid public key length: expected 130 hex chars, got ${normalizedPubKey.length}`);
    }
    if (normalizedSig.length !== 128) {
      throw new Error(`Invalid signature length: expected 128 hex chars, got ${normalizedSig.length}`);
    }

    // Parse public key: 0x04 || x || y
    if (normalizedPubKey.substring(0, 2) !== '04') {
      throw new Error('Public key must start with 04 (uncompressed format)');
    }
    const xHex = normalizedPubKey.substring(2, 66); // 64 hex chars = 32 bytes
    const yHex = normalizedPubKey.substring(66, 130); // 64 hex chars = 32 bytes

    // Create key pair from public key
    // elliptic expects x and y as hex strings when using object format
    const keyPair = ec.keyFromPublic({
      x: xHex,
      y: yHex,
    } as any, 'hex');

    // Parse signature: r || s
    const rHex = normalizedSig.substring(0, 64); // 64 hex chars = 32 bytes
    const sHex = normalizedSig.substring(64, 128); // 64 hex chars = 32 bytes

    // Create signature object
    const signature = {
      r: Buffer.from(rHex, 'hex'),
      s: Buffer.from(sHex, 'hex'),
    };

    // Hash the message (same as Android does: SHA-256)
    const messageHash = Buffer.from(voucherJson, 'utf8');
    const hash = require('crypto').createHash('sha256').update(messageHash).digest();

    // Verify signature
    const isValid = keyPair.verify(hash, signature);
    if (!isValid) {
      console.error('P-256 signature verification details:');
      console.error('  Public key length:', normalizedPubKey.length);
      console.error('  Signature length:', normalizedSig.length);
      console.error('  Public key starts with 04:', normalizedPubKey.substring(0, 2) === '04');
      console.error('  Message hash (hex):', hash.toString('hex'));
      console.error('  Signature r (hex):', rHex);
      console.error('  Signature s (hex):', sHex);
    }
    return isValid;
  } catch (error) {
    console.error('P-256 signature verification error:', error);
    if (error instanceof Error) {
      console.error('  Error message:', error.message);
      console.error('  Error stack:', error.stack);
    }
    return false;
  }
}

