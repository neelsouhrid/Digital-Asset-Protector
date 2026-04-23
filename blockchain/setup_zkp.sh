#!/bin/bash
set -e

echo "====================================="
echo "   ZKP Setup: Signature Match ZK     "
echo "====================================="

# 1. Install dependencies
echo "[1/6] Installing Circomlib & SnarkJS..."
npm install circomlib snarkjs

# Check for circom
if ! command -v circom &> /dev/null; then
    echo "⚠️ circom could not be found in PATH."
    echo "Circom does not provide pre-built binaries for macOS Apple Silicon."
    echo "Please install it by running the following commands in your terminal:"
    echo "  curl --proto '=https' --tlsv1.2 https://sh.rustup.rs -sSf | sh"
    echo "  git clone https://github.com/iden3/circom.git"
    echo "  cd circom && cargo build --release && cargo install --path circom"
    echo "Then re-run this script."
    exit 1
fi

cd circuits

# 2. Compile the Circuit
echo "[2/6] Compiling SignatureMatch circuit..."
circom SignatureMatch.circom --r1cs --wasm --sym

# 3. Powers of Tau (Trusted Setup Phase 1)
echo "[3/6] Running Powers of Tau Phase 1..."
npx snarkjs powersoftau new bn128 12 pot12_0000.ptau -v
npx snarkjs powersoftau contribute pot12_0000.ptau pot12_0001.ptau --name="First contribution" -v -e="entropy_for_digital_asset_protector"

# 4. Phase 2 Setup
echo "[4/6] Running Phase 2 Setup..."
npx snarkjs powersoftau prepare phase2 pot12_0001.ptau pot12_final.ptau -v

# 5. Generate ZKey (Proving Key)
echo "[5/6] Generating zkey (Prover Key)..."
npx snarkjs groth16 setup SignatureMatch.r1cs pot12_final.ptau circuit_0000.zkey
npx snarkjs zkey contribute circuit_0000.zkey circuit_final.zkey --name="Second contribution" -v -e="more_entropy_for_digital_asset_protector"

# 6. Export Keys & Smart Contract
echo "[6/6] Exporting Verification Key & Verifier.sol..."
npx snarkjs zkey export verificationkey circuit_final.zkey verification_key.json
npx snarkjs zkey export solidityverifier circuit_final.zkey ../contracts/Verifier.sol

echo "====================================="
echo "✅ ZKP Setup Complete!"
echo "Generated the following assets:"
echo " 📜 Smart Contract: contracts/Verifier.sol"
echo " 🔑 Verification Key: circuits/verification_key.json"
echo " 🔐 Prover Key: circuits/circuit_final.zkey"
echo " ⚙️  WASM Prover: circuits/SignatureMatch_js/SignatureMatch.wasm"
echo "====================================="
