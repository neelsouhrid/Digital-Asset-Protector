pragma circom 2.0.0;

include "../node_modules/circomlib/circuits/poseidon.circom";

/**
 * SignatureMatch ZK-SNARK Circuit
 * Verifies that a leaked photo signature matches a registered signature hash,
 * without revealing the original raw leaked signature.
 */
template SignatureMatch() {
    // Private input from the user's phone (the raw signature data)
    signal input leakedSignature;
    
    // Public input from the blockchain (the registered hash of the signature)
    signal input registeredSignatureHash;
    
    // Instantiate Poseidon hash function with 1 input
    component poseidon = Poseidon(1);
    poseidon.inputs[0] <== leakedSignature;
    
    // Constraint: The computed hash must equal the registered hash
    registeredSignatureHash === poseidon.out;
}

// The main component, exposing registeredSignatureHash as public
component main {public [registeredSignatureHash]} = SignatureMatch();
