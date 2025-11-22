const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("YourContract", function () {
  it("Should deploy and set the name correctly", async function () {
    const YourContract = await ethers.getContractFactory("YourContract");
    const contract = await YourContract.deploy("Test Contract");

    await contract.waitForDeployment();

    expect(await contract.name()).to.equal("Test Contract");
  });

  it("Should allow changing the name", async function () {
    const YourContract = await ethers.getContractFactory("YourContract");
    const contract = await YourContract.deploy("Initial Name");

    await contract.waitForDeployment();

    await contract.setName("New Name");
    expect(await contract.name()).to.equal("New Name");
  });
});

