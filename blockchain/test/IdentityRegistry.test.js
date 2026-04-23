// test/IdentityRegistry.test.js
const { expect } = require("chai");
const { ethers }  = require("hardhat");

describe("IdentityRegistry — Soulbound DID System", function () {
  let identityRegistry;
  let owner, alice, bob, carol;

  const CHAIN_ID = 31337n;
  const makeDID  = (addr) => `did:dap:${CHAIN_ID}:${addr.toLowerCase()}`;

  beforeEach(async function () {
    [owner, alice, bob, carol] = await ethers.getSigners();
    const Factory = await ethers.getContractFactory("IdentityRegistry");
    identityRegistry = await Factory.deploy(owner.address);
    await identityRegistry.waitForDeployment();
  });

  // ── Deployment ─────────────────────────────────────────────────────────────
  describe("Deployment", function () {
    it("should set the correct owner", async function () {
      expect(await identityRegistry.owner()).to.equal(owner.address);
    });
    it("should have zero identities initially", async function () {
      expect(await identityRegistry.totalIdentities()).to.equal(0n);
    });
    it("should return the correct name and symbol", async function () {
      expect(await identityRegistry.name()).to.equal("Digital Asset Protector Identity");
      expect(await identityRegistry.symbol()).to.equal("DAPI");
    });
  });

  // ── Minting ────────────────────────────────────────────────────────────────
  describe("mintIdentity()", function () {
    it("should mint a Creator Identity SBT and emit IdentityMinted", async function () {
      const did = makeDID(alice.address);
      await expect(identityRegistry.mintIdentity(alice.address, did, "ipfs://meta1"))
        .to.emit(identityRegistry, "IdentityMinted")
        .withArgs(alice.address, 1n, did);

      expect(await identityRegistry.hasIdentity(alice.address)).to.be.true;
      expect(await identityRegistry.walletToDID(alice.address)).to.equal(did);
      expect(await identityRegistry.walletToTokenId(alice.address)).to.equal(1n);
      expect(await identityRegistry.totalIdentities()).to.equal(1n);
    });

    it("should only allow the owner to mint", async function () {
      await expect(
        identityRegistry.connect(alice).mintIdentity(bob.address, makeDID(bob.address), "ipfs://meta")
      ).to.be.revertedWithCustomError(identityRegistry, "OwnableUnauthorizedAccount");
    });

    it("should revert if wallet already has an identity", async function () {
      await identityRegistry.mintIdentity(alice.address, makeDID(alice.address), "ipfs://meta1");
      await expect(
        identityRegistry.mintIdentity(alice.address, "did:dap:31337:different", "ipfs://meta2")
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__AlreadyHasIdentity");
    });

    it("should revert on duplicate DID", async function () {
      const did = makeDID(alice.address);
      await identityRegistry.mintIdentity(alice.address, did, "ipfs://meta1");
      await expect(
        identityRegistry.mintIdentity(bob.address, did, "ipfs://meta2")
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__DIDAlreadyRegistered");
    });

    it("should revert on zero address", async function () {
      await expect(
        identityRegistry.mintIdentity(ethers.ZeroAddress, "did:dap:31337:0x0", "ipfs://meta")
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__ZeroAddress");
    });

    it("should revert on empty DID string", async function () {
      await expect(
        identityRegistry.mintIdentity(alice.address, "", "ipfs://meta")
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__InvalidDID");
    });
  });

  // ── Soulbound ──────────────────────────────────────────────────────────────
  describe("Soulbound (Non-Transferable)", function () {
    beforeEach(async function () {
      await identityRegistry.mintIdentity(alice.address, makeDID(alice.address), "ipfs://meta");
    });

    it("should be locked per EIP-5192", async function () {
      expect(await identityRegistry.locked(1n)).to.be.true;
    });

    it("should revert transferFrom", async function () {
      await expect(
        identityRegistry.connect(alice).transferFrom(alice.address, bob.address, 1n)
      ).to.be.revertedWithCustomError(identityRegistry, "SoulboundToken__NonTransferable");
    });

    it("should revert safeTransferFrom", async function () {
      await expect(
        identityRegistry.connect(alice)["safeTransferFrom(address,address,uint256)"](
          alice.address, bob.address, 1n
        )
      ).to.be.revertedWithCustomError(identityRegistry, "SoulboundToken__NonTransferable");
    });

    it("should revert approve", async function () {
      await expect(
        identityRegistry.connect(alice).approve(bob.address, 1n)
      ).to.be.revertedWithCustomError(identityRegistry, "SoulboundToken__NonTransferable");
    });

    it("should revert setApprovalForAll", async function () {
      await expect(
        identityRegistry.connect(alice).setApprovalForAll(bob.address, true)
      ).to.be.revertedWithCustomError(identityRegistry, "SoulboundToken__NonTransferable");
    });

    it("should support EIP-5192 interface (0xb45a3c0e)", async function () {
      expect(await identityRegistry.supportsInterface("0xb45a3c0e")).to.be.true;
    });
  });

  // ── Revocation ────────────────────────────────────────────────────────────
  describe("revokeIdentity()", function () {
    beforeEach(async function () {
      await identityRegistry.mintIdentity(alice.address, makeDID(alice.address), "ipfs://meta");
    });

    it("should allow token holder to self-revoke", async function () {
      await expect(identityRegistry.connect(alice).revokeIdentity(1n))
        .to.emit(identityRegistry, "IdentityRevoked")
        .withArgs(alice.address, 1n, makeDID(alice.address));
      expect(await identityRegistry.hasIdentity(alice.address)).to.be.false;
    });

    it("should allow registry owner to revoke any identity", async function () {
      await expect(identityRegistry.connect(owner).revokeIdentity(1n))
        .to.emit(identityRegistry, "IdentityRevoked");
    });

    it("should prevent a third party from revoking", async function () {
      await expect(
        identityRegistry.connect(bob).revokeIdentity(1n)
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__NotTokenOwner");
    });

    it("should clear all identity mappings on revoke", async function () {
      await identityRegistry.connect(alice).revokeIdentity(1n);
      expect(await identityRegistry.walletToTokenId(alice.address)).to.equal(0n);
      expect(await identityRegistry.walletToDID(alice.address)).to.equal("");
    });

    it("should allow re-minting after revocation", async function () {
      await identityRegistry.connect(alice).revokeIdentity(1n);
      await expect(
        identityRegistry.mintIdentity(alice.address, makeDID(alice.address) + "_v2", "ipfs://meta2")
      ).to.not.be.reverted;
    });
  });

  // ── Verification ───────────────────────────────────────────────────────────
  describe("setVerifiedCreator()", function () {
    beforeEach(async function () {
      await identityRegistry.mintIdentity(alice.address, makeDID(alice.address), "ipfs://meta");
    });

    it("should grant verified status and emit event", async function () {
      await expect(identityRegistry.setVerifiedCreator(alice.address, true))
        .to.emit(identityRegistry, "CreatorVerificationUpdated")
        .withArgs(alice.address, true);
      expect(await identityRegistry.isVerifiedCreator(alice.address)).to.be.true;
    });

    it("should revoke verified status", async function () {
      await identityRegistry.setVerifiedCreator(alice.address, true);
      await identityRegistry.setVerifiedCreator(alice.address, false);
      expect(await identityRegistry.isVerifiedCreator(alice.address)).to.be.false;
    });

    it("should revert if wallet has no identity", async function () {
      await expect(
        identityRegistry.setVerifiedCreator(bob.address, true)
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__NoIdentityFound");
    });

    it("should only allow owner to set verification", async function () {
      await expect(
        identityRegistry.connect(alice).setVerifiedCreator(alice.address, true)
      ).to.be.revertedWithCustomError(identityRegistry, "OwnableUnauthorizedAccount");
    });

    it("should batch-set multiple verified creators", async function () {
      await identityRegistry.mintIdentity(bob.address, makeDID(bob.address), "ipfs://meta2");
      await identityRegistry.batchSetVerifiedCreators([alice.address, bob.address], true);
      expect(await identityRegistry.isVerifiedCreator(alice.address)).to.be.true;
      expect(await identityRegistry.isVerifiedCreator(bob.address)).to.be.true;
    });

    it("should auto-revoke verified status when identity is revoked", async function () {
      await identityRegistry.setVerifiedCreator(alice.address, true);
      await identityRegistry.revokeIdentity(1n);
      expect(await identityRegistry.isVerifiedCreator(alice.address)).to.be.false;
    });
  });

  // ── Query / Lookup ─────────────────────────────────────────────────────────
  describe("getIdentity() & resolveDID()", function () {
    it("should return correct identity data", async function () {
      const did = makeDID(alice.address);
      await identityRegistry.mintIdentity(alice.address, did, "ipfs://meta1");
      await identityRegistry.setVerifiedCreator(alice.address, true);

      const [tokenId, retDID, verified, metaURI] =
        await identityRegistry.getIdentity(alice.address);

      expect(tokenId).to.equal(1n);
      expect(retDID).to.equal(did);
      expect(verified).to.be.true;
      expect(metaURI).to.equal("ipfs://meta1");
    });

    it("should revert getIdentity for unknown wallet", async function () {
      await expect(
        identityRegistry.getIdentity(carol.address)
      ).to.be.revertedWithCustomError(identityRegistry, "IdentityRegistry__NoIdentityFound");
    });

    it("should resolve DID to correct wallet", async function () {
      const did = makeDID(alice.address);
      await identityRegistry.mintIdentity(alice.address, did, "ipfs://meta1");
      expect(await identityRegistry.resolveDID(did)).to.equal(alice.address);
    });

    it("should return zero address for unknown DID", async function () {
      expect(
        await identityRegistry.resolveDID("did:dap:31337:unknown")
      ).to.equal(ethers.ZeroAddress);
    });
  });
});
