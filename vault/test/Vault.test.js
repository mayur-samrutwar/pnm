const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("Vault", function () {
  let Vault, vault;
  let MockERC20, mockERC20;
  let owner, payer, payee;
  let chainId;

  beforeEach(async function () {
    [owner, payer, payee] = await ethers.getSigners();
    chainId = (await ethers.provider.getNetwork()).chainId;

    // Deploy MockERC20
    MockERC20 = await ethers.getContractFactory("MockERC20");
    mockERC20 = await MockERC20.deploy(
      "Mock Token",
      "MTK",
      18,
      ethers.parseEther("1000000")
    );
    await mockERC20.waitForDeployment();

    // Deploy Vault
    Vault = await ethers.getContractFactory("Vault");
    vault = await Vault.deploy();
    await vault.waitForDeployment();

    // Transfer some tokens to payer for testing
    await mockERC20.transfer(payer.address, ethers.parseEther("1000"));
  });

  describe("Deposit", function () {
    it("Should allow user to deposit tokens", async function () {
      const depositAmount = ethers.parseEther("100");
      
      // Approve vault to spend tokens
      await mockERC20.connect(payer).approve(vault.target, depositAmount);
      
      // Deposit tokens
      await expect(vault.connect(payer).deposit(payer.address, mockERC20.target, depositAmount))
        .to.emit(vault, "Deposit")
        .withArgs(payer.address, depositAmount);

      // Check deposit balance
      expect(await vault.deposits(payer.address)).to.equal(depositAmount);
      expect(await vault.userTokens(payer.address)).to.equal(mockERC20.target);
    });

    it("Should revert if amount is zero", async function () {
      await mockERC20.connect(payer).approve(vault.target, ethers.parseEther("100"));
      
      await expect(
        vault.connect(payer).deposit(payer.address, mockERC20.target, 0)
      ).to.be.revertedWith("Vault: amount must be greater than zero");
    });

    it("Should revert if approval is insufficient", async function () {
      await expect(
        vault.connect(payer).deposit(payer.address, mockERC20.target, ethers.parseEther("100"))
      ).to.be.revertedWithCustomError(mockERC20, "ERC20InsufficientAllowance");
    });
  });

  describe("RedeemVoucher", function () {
    let depositAmount;
    let voucherAmount;
    let cumulativeAmount;
    let slipId;
    let expiry;

    beforeEach(async function () {
      depositAmount = ethers.parseEther("100");
      voucherAmount = ethers.parseEther("50");
      cumulativeAmount = ethers.parseEther("50");
      slipId = ethers.id("550e8400-e29b-41d4-a716-446655440000"); // Convert UUID to bytes32
      expiry = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now

      // Deposit tokens
      await mockERC20.connect(payer).approve(vault.target, depositAmount);
      await vault.connect(payer).deposit(payer.address, mockERC20.target, depositAmount);
    });

    it("Should redeem a valid voucher", async function () {
      // Create voucher payload
      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address", // contractAddress
          "uint256", // expiry
          "uint256", // chainId
          "address", // payerAddress
          "address", // payeeAddress
          "uint256", // amount
          "uint256", // cumulative
          "bytes32"  // slipId
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      // Sign voucher using EIP-191
      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      // Get initial balances
      const initialPayeeBalance = await mockERC20.balanceOf(payee.address);
      const initialPayerDeposit = await vault.deposits(payer.address);

      // Redeem voucher
      await expect(vault.connect(payee).redeemVoucher(voucherPayload, signature))
        .to.emit(vault, "VoucherRedeemed")
        .withArgs(payer.address, payee.address, voucherAmount, slipId);

      // Check balances
      expect(await mockERC20.balanceOf(payee.address)).to.equal(
        initialPayeeBalance + voucherAmount
      );
      expect(await vault.deposits(payer.address)).to.equal(
        initialPayerDeposit - voucherAmount
      );
      expect(await vault.usedSlip(payer.address, slipId)).to.be.true;
    });

    it("Should revert if voucher is expired", async function () {
      const expiredExpiry = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago

      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiredExpiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: voucher expired");
    });

    it("Should revert if chainId is invalid", async function () {
      const invalidChainId = 9999;

      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          invalidChainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: invalid chain ID");
    });

    it("Should revert if contract address is invalid", async function () {
      const invalidContract = ethers.Wallet.createRandom().address;

      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          invalidContract,
          expiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: invalid contract address");
    });

    it("Should revert if signature is invalid", async function () {
      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      // Sign with wrong signer
      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payee.signMessage(ethers.getBytes(messageHash));

      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: invalid signature");
    });

    it("Should revert if slipId is already used", async function () {
      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          cumulativeAmount,
          slipId
        ]
      );

      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      // First redemption should succeed
      await vault.connect(payee).redeemVoucher(voucherPayload, signature);

      // Second redemption should fail
      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: voucher already used");
    });

    it("Should revert if cumulative exceeds deposits", async function () {
      const excessiveCumulative = ethers.parseEther("200"); // More than deposit

      const voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          voucherAmount,
          excessiveCumulative,
          slipId
        ]
      );

      const messageHash = ethers.keccak256(voucherPayload);
      const signature = await payer.signMessage(ethers.getBytes(messageHash));

      await expect(
        vault.connect(payee).redeemVoucher(voucherPayload, signature)
      ).to.be.revertedWith("Vault: insufficient deposits");
    });

    it("Should handle incremental cumulative amounts", async function () {
      // First voucher with cumulative = 30
      const firstAmount = ethers.parseEther("30");
      const firstCumulative = ethers.parseEther("30");
      const firstSlipId = ethers.id("first-slip-id");

      let voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          firstAmount,
          firstCumulative,
          firstSlipId
        ]
      );

      let messageHash = ethers.keccak256(voucherPayload);
      let signature = await payer.signMessage(ethers.getBytes(messageHash));

      await vault.connect(payee).redeemVoucher(voucherPayload, signature);

      // Second voucher with cumulative = 50 (incremental)
      const secondAmount = ethers.parseEther("20");
      const secondCumulative = ethers.parseEther("50");
      const secondSlipId = ethers.id("second-slip-id");

      voucherPayload = ethers.AbiCoder.defaultAbiCoder().encode(
        [
          "address",
          "uint256",
          "uint256",
          "address",
          "address",
          "uint256",
          "uint256",
          "bytes32"
        ],
        [
          vault.target,
          expiry,
          chainId,
          payer.address,
          payee.address,
          secondAmount,
          secondCumulative,
          secondSlipId
        ]
      );

      messageHash = ethers.keccak256(voucherPayload);
      signature = await payer.signMessage(ethers.getBytes(messageHash));

      await expect(vault.connect(payee).redeemVoucher(voucherPayload, signature))
        .to.emit(vault, "VoucherRedeemed")
        .withArgs(payer.address, payee.address, secondAmount, secondSlipId);

      // Check final balances
      expect(await vault.deposits(payer.address)).to.equal(
        depositAmount - firstAmount - secondAmount
      );
      expect(await mockERC20.balanceOf(payee.address)).to.equal(
        firstAmount + secondAmount
      );
    });
  });

  describe("Withdraw", function () {
    it("Should allow owner to withdraw tokens", async function () {
      const depositAmount = ethers.parseEther("100");
      const withdrawAmount = ethers.parseEther("10");

      // Deposit tokens
      await mockERC20.connect(payer).approve(vault.target, depositAmount);
      await vault.connect(payer).deposit(payer.address, mockERC20.target, depositAmount);

      // Transfer some tokens directly to vault (simulating fees)
      await mockERC20.transfer(vault.target, withdrawAmount);

      const initialOwnerBalance = await mockERC20.balanceOf(owner.address);

      // Owner withdraws
      await vault.connect(owner).withdraw(mockERC20.target, withdrawAmount);

      expect(await mockERC20.balanceOf(owner.address)).to.equal(
        initialOwnerBalance + withdrawAmount
      );
    });

    it("Should revert if non-owner tries to withdraw", async function () {
      await expect(
        vault.connect(payer).withdraw(mockERC20.target, ethers.parseEther("10"))
      ).to.be.revertedWithCustomError(vault, "OwnableUnauthorizedAccount");
    });
  });
});

