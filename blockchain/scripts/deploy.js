// scripts/deploy.js
// Deploy IdentityRegistry (Soulbound DID System) to Polygon Amoy Testnet
//
// Usage:
//   npx hardhat run scripts/deploy.js --network amoy
//   npx hardhat run scripts/deploy.js --network localhost

const { ethers, network, run } = require("hardhat");
const fs   = require("fs");
const path = require("path");

const DEPLOYMENT_LOG_PATH = path.join(__dirname, "../deployments");

const log = (msg) => console.log(`[${new Date().toISOString()}] ${msg}`);
const sep = ()     => console.log("─".repeat(60));

async function main() {
  sep();
  log("🚀 Deploying IdentityRegistry — Soulbound DID System");
  sep();

  // ── Network & signer info ─────────────────────────────────────────────────
  const networkName = network.name;
  const { chainId } = await ethers.provider.getNetwork();
  const [deployer] = await ethers.getSigners();
  const deployerAddress = await deployer.getAddress();
  const balance = await ethers.provider.getBalance(deployerAddress);

  log(`Network  : ${networkName} (chainId ${chainId})`);
  log(`Deployer : ${deployerAddress}`);
  log(`Balance  : ${ethers.formatEther(balance)} MATIC`);

  if (balance === 0n) {
    throw new Error(
      "Deployer has zero balance. Fund via https://faucet.polygon.technology/"
    );
  }

  sep();

  // ── Deploy ────────────────────────────────────────────────────────────────
  log("📦 Deploying IdentityRegistry...");
  const Factory  = await ethers.getContractFactory("IdentityRegistry");
  const contract = await Factory.deploy(deployerAddress);
  await contract.waitForDeployment();

  const contractAddress = await contract.getAddress();
  const deployTx = contract.deploymentTransaction();
  log(`✅ Deployed at : ${contractAddress}`);
  log(`   Tx Hash     : ${deployTx.hash}`);

  // Wait for confirmations on live networks
  if (networkName !== "hardhat" && networkName !== "localhost") {
    log("Waiting for 5 block confirmations...");
    await deployTx.wait(5);
    log("✅ Confirmed.");
  }

  sep();

  // ── Smoke test: mint the deployer's Creator Identity ─────────────────────
  log("🔬 Smoke test: minting deployer Creator Identity...");
  const sampleDID = `did:dap:${chainId}:${deployerAddress.toLowerCase()}`;
  const mintTx = await contract.mintIdentity(
    deployerAddress,
    sampleDID,
    "ipfs://QmPlaceholder_ReplaceWithRealIPFSHash"
  );
  await mintTx.wait(1);

  const identity = await contract.getIdentity(deployerAddress);
  log(`   Token ID  : ${identity.tokenId}`);
  log(`   DID       : ${identity.did}`);
  const isLocked = await contract.locked(identity.tokenId);
  log(`   Soulbound : ${isLocked ? "LOCKED ✅" : "NOT LOCKED ❌"}`);

  // Also set them as a verified creator so they can register assets
  const verifyTx = await contract.setVerifiedCreator(deployerAddress, true);
  await verifyTx.wait(1);
  log(`   Verified Creator: ✅`);

  sep();

  // ── Deploy ProvenanceRegistry ─────────────────────────────────────────────
  log("📦 Deploying ProvenanceRegistry...");
  const ProvFactory  = await ethers.getContractFactory("ProvenanceRegistry");
  const provContract = await ProvFactory.deploy(contractAddress, deployerAddress);
  await provContract.waitForDeployment();

  const provAddress = await provContract.getAddress();
  const provDeployTx = provContract.deploymentTransaction();
  log(`✅ ProvenanceRegistry Deployed at : ${provAddress}`);
  log(`   Tx Hash     : ${provDeployTx.hash}`);

  if (networkName !== "hardhat" && networkName !== "localhost") {
    log("Waiting for 5 block confirmations...");
    await provDeployTx.wait(5);
    log("✅ Confirmed.");
  }

  sep();

  // ── Polygonscan verification ──────────────────────────────────────────────
  const shouldVerify =
    process.env.POLYGONSCAN_API_KEY &&
    networkName !== "hardhat" &&
    networkName !== "localhost";

  if (shouldVerify) {
    log("🔍 Verifying on Polygonscan...");
    try {
      await run("verify:verify", {
        address: contractAddress,
        constructorArguments: [deployerAddress],
      });
      log("✅ Contract verified!");
    } catch (err) {
      log(
        err.message.includes("Already Verified")
          ? "ℹ️  Already verified."
          : `⚠️  Verification failed: ${err.message}`
      );
    }
  } else {
    log("ℹ️  Polygonscan verification skipped (set POLYGONSCAN_API_KEY to enable).");
  }

  sep();

  // ── Save deployment record ────────────────────────────────────────────────
  if (!fs.existsSync(DEPLOYMENT_LOG_PATH)) {
    fs.mkdirSync(DEPLOYMENT_LOG_PATH, { recursive: true });
  }

  const record = {
    contractName:   "IdentityRegistry",
    network:        networkName,
    chainId:        chainId.toString(),
    identityRegistryAddress: contractAddress,
    provenanceRegistryAddress: provAddress,
    deployerAddress,
    txHashIdentity: deployTx.hash,
    txHashProvenance: provDeployTx.hash,
    deployedAt:     new Date().toISOString(),
  };

  const logFile = path.join(
    DEPLOYMENT_LOG_PATH,
    `${networkName}-Contracts-${Date.now()}.json`
  );
  fs.writeFileSync(logFile, JSON.stringify(record, null, 2));
  log(`📄 Record saved → ${logFile}`);

  sep();
  log("🎉 Deployment complete!");
  log(`   IdentityRegistry Address   : ${contractAddress}`);
  log(`   ProvenanceRegistry Address : ${provAddress}`);
  if (networkName === "amoy") {
    log(`   Faucet   : https://faucet.polygon.technology/`);
  }
  sep();
}

main()
  .then(() => process.exit(0))
  .catch((err) => { console.error("❌", err); process.exit(1); });
