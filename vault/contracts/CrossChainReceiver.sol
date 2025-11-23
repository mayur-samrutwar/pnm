// SPDX-License-Identifier: MIT
pragma solidity >=0.8.18;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/**
 * @title CrossChainReceiver
 * @dev Receives bridged USDC from Hyperlane Warp Routes and deposits to Vault
 * This contract handles the callback from Hyperlane when tokens are bridged
 */
contract CrossChainReceiver {
    using SafeERC20 for IERC20;

    /// @dev The Vault contract address on this chain
    address public immutable vault;
    
    /// @dev Native USDC token address on this chain
    address public immutable nativeUSDC;
    
    /// @dev Authorized sender address (hub's address on source chain)
    address public immutable authorizedSender;
    
    /// @dev Mapping to track pending deposits: messageId => (userAddress, amount)
    mapping(bytes32 => PendingDeposit) public pendingDeposits;
    
    /// @dev Struct to store pending deposit info
    struct PendingDeposit {
        address userAddress;
        uint256 amount;
        bool exists;
    }
    
    /// @dev Event emitted when funds are received and deposited
    event FundsDeposited(
        bytes32 indexed messageId,
        address indexed userAddress,
        uint256 amount
    );

    /**
     * @dev Constructor
     * @param _vault The Vault contract address on this chain
     * @param _nativeUSDC The native USDC token address on this chain
     * @param _authorizedSender The hub's address on source chain (for verification)
     */
    constructor(
        address _vault,
        address _nativeUSDC,
        address _authorizedSender
    ) {
        require(_vault != address(0), "Invalid vault address");
        require(_nativeUSDC != address(0), "Invalid USDC address");
        require(_authorizedSender != address(0), "Invalid sender address");
        
        vault = _vault;
        nativeUSDC = _nativeUSDC;
        authorizedSender = _authorizedSender;
    }

    /**
     * @dev Called by Hyperlane Mailbox when a message arrives
     * This receives the user address and amount info, stores it for matching with USDC transfer
     * @param origin The origin chain domain ID
     * @param sender The sender address (should be authorizedSender)
     * @param messageBody Encoded (userAddress, amount)
     */
    function handle(
        uint32 origin,
        bytes32 sender,
        bytes calldata messageBody
    ) external {
        // Verify sender is authorized (hub on source chain)
        require(
            sender == bytes32(uint256(uint160(authorizedSender))),
            "Unauthorized sender"
        );
        
        // Decode message: userAddress, amount
        (address userAddress, uint256 amount) = 
            abi.decode(messageBody, (address, uint256));
        
        require(userAddress != address(0), "Invalid user address");
        require(amount > 0, "Invalid amount");
        
        // Generate message ID from origin and message data
        bytes32 messageId = keccak256(abi.encodePacked(origin, sender, messageBody));
        
        // Store pending deposit (will be matched when USDC arrives)
        pendingDeposits[messageId] = PendingDeposit({
            userAddress: userAddress,
            amount: amount,
            exists: true
        });
        
        emit FundsDeposited(messageId, userAddress, amount);
    }

    /**
     * @dev Called when USDC arrives from Warp Route transferRemote
     * This function should be called by the Warp Route contract or via a callback
     * For now, we'll use a manual trigger after USDC balance increases
     * @param messageId The message ID from the handle() call
     */
    function depositToVault(bytes32 messageId) external {
        PendingDeposit memory pending = pendingDeposits[messageId];
        require(pending.exists, "Pending deposit not found");
        
        // Check contract has sufficient USDC balance
        IERC20 usdc = IERC20(nativeUSDC);
        uint256 balance = usdc.balanceOf(address(this));
        require(balance >= pending.amount, "Insufficient USDC balance");
        
        // Approve USDC to Vault using SafeERC20 forceApprove (OpenZeppelin v5.0)
        usdc.forceApprove(vault, pending.amount);
        
        // Call Vault.deposit() - this will transferFrom this contract
        // Vault.deposit(user, token, amount) expects msg.sender to have approved tokens
        (bool success, ) = vault.call(
            abi.encodeWithSignature(
                "deposit(address,address,uint256)",
                pending.userAddress,
                nativeUSDC,
                pending.amount
            )
        );
        
        require(success, "Vault deposit failed");
        
        // Clear pending deposit
        delete pendingDeposits[messageId];
    }

    /**
     * @dev Alternative: Direct deposit function that can be called after USDC arrives
     * This is simpler - just deposit when USDC balance increases
     * @param userAddress The user address to credit
     * @param amount The amount to deposit
     */
    function depositToVaultDirect(address userAddress, uint256 amount) external {
        require(userAddress != address(0), "Invalid user address");
        require(amount > 0, "Invalid amount");
        
        // Check contract has sufficient USDC balance
        IERC20 usdc = IERC20(nativeUSDC);
        uint256 balance = usdc.balanceOf(address(this));
        require(balance >= amount, "Insufficient USDC balance");
        
        // Approve USDC to Vault using SafeERC20 forceApprove (OpenZeppelin v5.0)
        usdc.forceApprove(vault, amount);
        
        // Call Vault.deposit()
        (bool success, ) = vault.call(
            abi.encodeWithSignature(
                "deposit(address,address,uint256)",
                userAddress,
                nativeUSDC,
                amount
            )
        );
        
        require(success, "Vault deposit failed");
    }
}

