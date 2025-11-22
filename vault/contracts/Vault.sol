// SPDX-License-Identifier: MIT
pragma solidity >=0.8.18;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/MessageHashUtils.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title Vault
 * @dev A contract that manages ERC20 token deposits and voucher redemption system.
 * Users can deposit tokens and redeem vouchers signed by payers.
 */
contract Vault is Ownable {
    using ECDSA for bytes32;
    using MessageHashUtils for bytes32;

    /// @dev Mapping to track deposits per user address
    mapping(address => uint256) public deposits;

    /// @dev Mapping to track the token address for each user's deposits
    mapping(address => address) public userTokens;

    /// @dev Mapping to track used voucher slip IDs per payer
    mapping(address => mapping(bytes32 => bool)) public usedSlip;

    /// @dev Event emitted when a user deposits tokens
    /// @param user The address of the user making the deposit
    /// @param amount The amount of tokens deposited
    event Deposit(address indexed user, uint256 amount);

    /// @dev Event emitted when a voucher is successfully redeemed
    /// @param payer The address of the payer who signed the voucher
    /// @param payee The address receiving the payment
    /// @param amount The amount transferred
    /// @param slipId The unique slip ID of the voucher
    event VoucherRedeemed(
        address indexed payer,
        address indexed payee,
        uint256 amount,
        bytes32 slipId
    );

    /**
     * @dev Constructor that sets the contract deployer as the initial owner
     */
    constructor() Ownable(msg.sender) {}

    /**
     * @dev Allows a user to deposit ERC20 tokens into the vault
     * @param user The address to credit the deposit to
     * @param token The address of the ERC20 token contract
     * @param amount The amount of tokens to deposit
     * 
     * Requirements:
     * - The caller must have approved this contract to spend at least `amount` tokens
     * - The amount must be greater than zero
     */
    function deposit(address user, address token, uint256 amount) external {
        require(amount > 0, "Vault: amount must be greater than zero");
        
        IERC20 erc20Token = IERC20(token);
        require(
            erc20Token.transferFrom(msg.sender, address(this), amount),
            "Vault: transfer failed"
        );
        
        // Track token address for user (assumes single token per user)
        if (userTokens[user] == address(0)) {
            userTokens[user] = token;
        } else {
            require(userTokens[user] == token, "Vault: token mismatch");
        }
        
        deposits[user] += amount;
        emit Deposit(user, amount);
    }

    /**
     * @dev Redeems a voucher by verifying the signature and transferring tokens
     * @param voucherPayload The ABI-encoded voucher data (without signature)
     * @param signature The EIP-191 signature of the voucher payload
     * 
     * Voucher structure (encoded in voucherPayload):
     * - contractAddress: address - Must match this contract's address
     * - expiry: uint256 - Must be greater than block.timestamp
     * - chainId: uint256 - Must match block.chainid
     * - payerAddress: address - The address that signed the voucher
     * - payeeAddress: address - The address to receive the payment
     * - amount: uint256 - The amount to transfer
     * - cumulative: uint256 - The cumulative amount spent (must be <= deposits[payer])
     * - slipId: bytes32 - Unique identifier to prevent double-spending
     * 
     * Requirements:
     * - The signature must be valid and recover to the payer address
     * - The voucher must not be expired
     * - The chainId must match the current chain
     * - The contractAddress must match this contract
     * - The slipId must not have been used before
     * - The payer must have sufficient deposits (>= cumulative)
     */
    function redeemVoucher(bytes calldata voucherPayload, bytes calldata signature) external {
        // Decode voucher fields
        (
            address contractAddress,
            uint256 expiry,
            uint256 chainId,
            address payerAddress,
            address payeeAddress,
            uint256 amount,
            uint256 cumulative,
            bytes32 slipId
        ) = abi.decode(voucherPayload, (address, uint256, uint256, address, address, uint256, uint256, bytes32));

        // Verify contract address
        require(contractAddress == address(this), "Vault: invalid contract address");

        // Verify expiry
        require(expiry > block.timestamp, "Vault: voucher expired");

        // Verify chain ID
        require(chainId == block.chainid, "Vault: invalid chain ID");

        // Recover payer address from signature using EIP-191
        bytes32 messageHash = keccak256(voucherPayload);
        bytes32 ethSignedMessageHash = messageHash.toEthSignedMessageHash();
        address recoveredPayer = ECDSA.recover(ethSignedMessageHash, signature);

        require(recoveredPayer == payerAddress, "Vault: invalid signature");
        require(recoveredPayer != address(0), "Vault: invalid payer address");

        // Check if slip has been used
        require(!usedSlip[payerAddress][slipId], "Vault: voucher already used");

        // Check sufficient deposits
        require(deposits[payerAddress] >= cumulative, "Vault: insufficient deposits");

        // Mark slip as used
        usedSlip[payerAddress][slipId] = true;

        // Decrease deposits by the amount being redeemed
        deposits[payerAddress] -= amount;

        // Transfer tokens to payee
        address token = userTokens[payerAddress];
        require(token != address(0), "Vault: no token found for payer");
        
        IERC20 erc20Token = IERC20(token);
        require(
            erc20Token.transfer(payeeAddress, amount),
            "Vault: transfer to payee failed"
        );
        
        emit VoucherRedeemed(payerAddress, payeeAddress, amount, slipId);
    }

    /**
     * @dev Allows the owner to withdraw ERC20 tokens from the contract
     * @param token The address of the ERC20 token to withdraw
     * @param amount The amount of tokens to withdraw
     * 
     * Requirements:
     * - Only the owner can call this function
     * - The contract must have sufficient balance
     */
    function withdraw(address token, uint256 amount) external onlyOwner {
        IERC20 erc20Token = IERC20(token);
        require(
            erc20Token.transfer(owner(), amount),
            "Vault: withdrawal transfer failed"
        );
    }
}

