const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("ProvenanceRegistry", function () {
    let IdentityRegistry, identityRegistry;
    let ProvenanceRegistry, provenanceRegistry;
    let owner, verifiedCreator, unverifiedCreator, reporter;
    let signatureHash = ethers.id("test_signature_data");
    let signatureHash2 = ethers.id("test_signature_data_2");
    
    beforeEach(async function () {
        [owner, verifiedCreator, unverifiedCreator, reporter] = await ethers.getSigners();

        // Deploy IdentityRegistry
        IdentityRegistry = await ethers.getContractFactory("IdentityRegistry");
        identityRegistry = await IdentityRegistry.deploy(owner.address);
        await identityRegistry.waitForDeployment();

        // Mint identity and verify creator
        await identityRegistry.connect(owner).mintIdentity(verifiedCreator.address, "did:dap:31337:verified", "ipfs://metadata");
        await identityRegistry.connect(owner).setVerifiedCreator(verifiedCreator.address, true);

        // Deploy ProvenanceRegistry
        ProvenanceRegistry = await ethers.getContractFactory("ProvenanceRegistry");
        provenanceRegistry = await ProvenanceRegistry.deploy(await identityRegistry.getAddress(), owner.address);
        await provenanceRegistry.waitForDeployment();
    });

    describe("Deployment", function () {
        it("Should set the right owner", async function () {
            expect(await provenanceRegistry.owner()).to.equal(owner.address);
        });

        it("Should set the correct IdentityRegistry address", async function () {
            expect(await provenanceRegistry.identityRegistry()).to.equal(await identityRegistry.getAddress());
        });
    });

    describe("Asset Registration", function () {
        it("Should allow a verified creator to register an asset", async function () {
            const tx = await provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash);
            const receipt = await tx.wait();
            
            // Get block timestamp to verify
            const block = await ethers.provider.getBlock(receipt.blockNumber);
            
            await expect(tx)
                .to.emit(provenanceRegistry, "AssetRegistered")
                .withArgs(signatureHash, verifiedCreator.address, block.timestamp);

            const asset = await provenanceRegistry.assets(signatureHash);
            expect(asset.owner).to.equal(verifiedCreator.address);
            expect(asset.isProtected).to.be.true;
        });

        it("Should revert if an unverified creator tries to register an asset", async function () {
            await expect(provenanceRegistry.connect(unverifiedCreator).registerAsset(signatureHash))
                .to.be.revertedWithCustomError(provenanceRegistry, "NotVerifiedCreator");
        });

        it("Should revert if the asset is already registered", async function () {
            await provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash);
            await expect(provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash))
                .to.be.revertedWithCustomError(provenanceRegistry, "AssetAlreadyRegistered");
        });
    });

    describe("Challenge and Reporting", function () {
        beforeEach(async function () {
            await provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash);
        });

        it("Should allow anyone to report a suspicious signature", async function () {
            await expect(provenanceRegistry.connect(reporter).reportSuspiciousSignature(signatureHash, "Suspicious activity detected"))
                .to.emit(provenanceRegistry, "Challenge")
                .withArgs(signatureHash, reporter.address, "Suspicious activity detected");
        });

        it("Should revert if reporting an unregistered asset", async function () {
            await expect(provenanceRegistry.connect(reporter).reportSuspiciousSignature(signatureHash2, "Suspicious"))
                .to.be.revertedWithCustomError(provenanceRegistry, "AssetNotRegistered");
        });
    });

    describe("Asset Protection Status", function () {
        beforeEach(async function () {
            await provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash);
        });

        it("Should allow the owner of the asset to update protection status", async function () {
            await expect(provenanceRegistry.connect(verifiedCreator).setAssetProtection(signatureHash, false))
                .to.emit(provenanceRegistry, "AssetProtectionStatusChanged")
                .withArgs(signatureHash, false);

            const asset = await provenanceRegistry.assets(signatureHash);
            expect(asset.isProtected).to.be.false;
        });

        it("Should allow the contract owner to update protection status", async function () {
            await expect(provenanceRegistry.connect(owner).setAssetProtection(signatureHash, false))
                .to.emit(provenanceRegistry, "AssetProtectionStatusChanged")
                .withArgs(signatureHash, false);
        });

        it("Should revert if a non-owner tries to update protection status", async function () {
            await expect(provenanceRegistry.connect(unverifiedCreator).setAssetProtection(signatureHash, false))
                .to.be.revertedWithCustomError(provenanceRegistry, "NotAssetOwner");
        });

        it("Should revert if the asset is not registered", async function () {
            await expect(provenanceRegistry.connect(owner).setAssetProtection(signatureHash2, false))
                .to.be.revertedWithCustomError(provenanceRegistry, "AssetNotRegistered");
        });
    });

    describe("Pausable", function () {
        it("Should pause and unpause the contract", async function () {
            await provenanceRegistry.connect(owner).pause();
            await expect(provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash))
                .to.be.revertedWithCustomError(provenanceRegistry, "EnforcedPause");

            await provenanceRegistry.connect(owner).unpause();
            await expect(provenanceRegistry.connect(verifiedCreator).registerAsset(signatureHash))
                .to.emit(provenanceRegistry, "AssetRegistered");
        });
    });
});
