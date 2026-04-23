// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title IdentityRegistry
 * @author Digital Asset Protector
 * @notice Soulbound Token (SBT) contract implementing a Decentralized Identity (DID)
 *         system for asset creators. Each token is non-transferable and permanently
 *         binds a wallet address to a unique DID string, establishing a verifiable
 *         on-chain "Creator Identity."
 *
 * @dev Based on ERC-721 with full transfer restriction (EIP-5192 Soulbound).
 *      Once minted, tokens cannot be transferred — only burned by the holder or
 *      revoked by the contract owner (registry authority).
 *
 *      Compatible with OpenZeppelin Contracts v5.x
 *
 *      DID Format: "did:dap:<chainId>:<walletAddress>"
 *      Example:    "did:dap:80002:0xabc...123"
 */
contract IdentityRegistry is ERC721URIStorage, Ownable {

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────

    /// @dev Token ID counter — starts at 1 (0 = "no identity" sentinel)
    uint256 private _nextTokenId;

    /// @notice Maps token ID → DID string
    mapping(uint256 => string) public tokenDID;

    /// @notice Maps wallet address → token ID (0 = no identity minted)
    mapping(address => uint256) public walletToTokenId;

    /// @notice Maps wallet address → verified creator status
    /// @dev Verified creators can register assets without additional checks
    mapping(address => bool) public isVerifiedCreator;

    /// @notice Maps DID string → whether it has been registered (prevents duplicates)
    mapping(string => bool) private _didRegistered;

    /// @notice Maps wallet address → DID string for quick reverse lookup
    mapping(address => string) public walletToDID;

    // ─────────────────────────────────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────────────────────────────────

    /// @notice Emitted when a new Creator Identity SBT is minted
    event IdentityMinted(
        address indexed wallet,
        uint256 indexed tokenId,
        string did
    );

    /// @notice Emitted when a Creator Identity SBT is revoked/burned
    event IdentityRevoked(
        address indexed wallet,
        uint256 indexed tokenId,
        string did
    );

    /// @notice Emitted when a creator's verified status changes
    event CreatorVerificationUpdated(
        address indexed wallet,
        bool indexed verified
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Custom Errors
    // ─────────────────────────────────────────────────────────────────────────

    error SoulboundToken__NonTransferable();
    error IdentityRegistry__AlreadyHasIdentity(address wallet, uint256 tokenId);
    error IdentityRegistry__DIDAlreadyRegistered(string did);
    error IdentityRegistry__NoIdentityFound(address wallet);
    error IdentityRegistry__NotTokenOwner(address caller, uint256 tokenId);
    error IdentityRegistry__InvalidDID();
    error IdentityRegistry__ZeroAddress();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param initialOwner The contract administrator (registry authority)
     */
    constructor(address initialOwner)
        ERC721("Digital Asset Protector Identity", "DAPI")
        Ownable(initialOwner)
    {
        _nextTokenId = 1; // token IDs start at 1
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core Identity Functions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Mint a Soulbound Creator Identity token to a wallet address.
     * @dev Only the contract owner (registry authority) can mint identities.
     *      Each wallet may hold at most one identity token.
     *      The DID must be globally unique across the registry.
     *
     * @param to          The wallet address receiving the Creator Identity
     * @param did         The decentralized identifier string
     *                    Recommended format: "did:dap:<chainId>:<walletAddress>"
     * @param metadataURI IPFS/HTTPS URI pointing to the identity metadata JSON
     */
    function mintIdentity(
        address to,
        string calldata did,
        string calldata metadataURI
    ) external onlyOwner {
        if (to == address(0))          revert IdentityRegistry__ZeroAddress();
        if (bytes(did).length == 0)    revert IdentityRegistry__InvalidDID();
        if (walletToTokenId[to] != 0)  revert IdentityRegistry__AlreadyHasIdentity(to, walletToTokenId[to]);
        if (_didRegistered[did])       revert IdentityRegistry__DIDAlreadyRegistered(did);

        uint256 tokenId = _nextTokenId++;

        // Store identity mappings
        tokenDID[tokenId]      = did;
        walletToTokenId[to]    = tokenId;
        walletToDID[to]        = did;
        _didRegistered[did]    = true;

        // Mint the SBT and attach metadata
        _safeMint(to, tokenId);
        _setTokenURI(tokenId, metadataURI);

        emit IdentityMinted(to, tokenId, did);
    }

    /**
     * @notice Burn / revoke a Creator Identity SBT.
     * @dev Callable by the token owner (self-sovereign) or the registry owner
     *      (authority revocation). Clears all associated identity mappings.
     *
     * @param tokenId The token ID to revoke
     */
    function revokeIdentity(uint256 tokenId) external {
        address tokenOwner = ownerOf(tokenId);

        if (msg.sender != tokenOwner && msg.sender != owner()) {
            revert IdentityRegistry__NotTokenOwner(msg.sender, tokenId);
        }

        string memory did = tokenDID[tokenId];

        // Clear all mappings before burning
        delete walletToTokenId[tokenOwner];
        delete walletToDID[tokenOwner];
        delete tokenDID[tokenId];
        delete _didRegistered[did];

        // Also revoke verification if present
        if (isVerifiedCreator[tokenOwner]) {
            isVerifiedCreator[tokenOwner] = false;
            emit CreatorVerificationUpdated(tokenOwner, false);
        }

        _burn(tokenId);

        emit IdentityRevoked(tokenOwner, tokenId, did);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Verification Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Grant or revoke verified creator status for a wallet.
     * @dev Only callable by the registry owner.
     *      The wallet must already hold a Creator Identity SBT.
     *
     * @param wallet   The creator's wallet address
     * @param verified True to grant, false to revoke
     */
    function setVerifiedCreator(address wallet, bool verified) external onlyOwner {
        if (wallet == address(0))         revert IdentityRegistry__ZeroAddress();
        if (walletToTokenId[wallet] == 0) revert IdentityRegistry__NoIdentityFound(wallet);

        isVerifiedCreator[wallet] = verified;
        emit CreatorVerificationUpdated(wallet, verified);
    }

    /**
     * @notice Batch-update verified status for multiple creators.
     * @param wallets  Array of wallet addresses
     * @param verified True to grant, false to revoke (applied to all)
     */
    function batchSetVerifiedCreators(
        address[] calldata wallets,
        bool verified
    ) external onlyOwner {
        for (uint256 i = 0; i < wallets.length; i++) {
            if (wallets[i] == address(0))          revert IdentityRegistry__ZeroAddress();
            if (walletToTokenId[wallets[i]] == 0)  revert IdentityRegistry__NoIdentityFound(wallets[i]);
            isVerifiedCreator[wallets[i]] = verified;
            emit CreatorVerificationUpdated(wallets[i], verified);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View / Query Functions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice Check if a wallet has a valid Creator Identity SBT.
     * @param wallet The wallet address to query
     * @return True if the wallet holds an identity token
     */
    function hasIdentity(address wallet) external view returns (bool) {
        return walletToTokenId[wallet] != 0;
    }

    /**
     * @notice Retrieve the full identity record for a wallet.
     * @param wallet The wallet address to query
     * @return tokenId   The SBT token ID
     * @return did       The DID string
     * @return verified  Whether the wallet is a verified creator
     * @return metaURI   The IPFS/HTTPS metadata URI
     */
    function getIdentity(address wallet)
        external
        view
        returns (
            uint256 tokenId,
            string memory did,
            bool verified,
            string memory metaURI
        )
    {
        tokenId = walletToTokenId[wallet];
        if (tokenId == 0) revert IdentityRegistry__NoIdentityFound(wallet);

        did      = tokenDID[tokenId];
        verified = isVerifiedCreator[wallet];
        metaURI  = tokenURI(tokenId);
    }

    /**
     * @notice Resolve a DID string to a wallet address.
     * @dev Linear scan — use off-chain indexing for high-frequency lookups.
     * @param did The DID string to resolve
     * @return wallet The owner's wallet address (address(0) if not found)
     */
    function resolveDID(string calldata did) external view returns (address wallet) {
        uint256 total = _nextTokenId;
        for (uint256 i = 1; i < total; i++) {
            if (
                _ownerOf(i) != address(0) &&
                keccak256(bytes(tokenDID[i])) == keccak256(bytes(did))
            ) {
                return ownerOf(i);
            }
        }
        return address(0);
    }

    /**
     * @notice Returns the total number of identities ever minted
     *         (includes revoked ones; use events to track active count).
     */
    function totalIdentities() external view returns (uint256) {
        return _nextTokenId - 1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EIP-5192 — Minimal Soulbound NFT Interface
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @notice All tokens in this contract are permanently locked (soulbound).
     * @dev Implements EIP-5192 locked() method.
     */
    function locked(uint256 /*tokenId*/) external pure returns (bool) {
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Soulbound Enforcement — Block All Transfers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @dev Block approve — approvals are meaningless for soulbound tokens.
     */
    function approve(address, uint256) public virtual override(ERC721, IERC721) {
        revert SoulboundToken__NonTransferable();
    }

    /**
     * @dev Block setApprovalForAll — same reason as approve.
     */
    function setApprovalForAll(address, bool) public virtual override(ERC721, IERC721) {
        revert SoulboundToken__NonTransferable();
    }

    /**
     * @dev Override _update to block all transfers.
     *      In OZ v5, _update() is the unified hook for mint/transfer/burn.
     */
    function _update(address to, uint256 tokenId, address auth) internal virtual override returns (address) {
        address from = _ownerOf(tokenId);
        
        // Allow mint (from = 0) and burn (to = 0)
        if (from != address(0) && to != address(0)) {
            revert SoulboundToken__NonTransferable();
        }

        return super._update(to, tokenId, auth);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERC-165 supportsInterface
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @dev Declares support for ERC-721, ERC-721Metadata, and EIP-5192.
     *      EIP-5192 interface ID: 0xb45a3c0e
     */
    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(ERC721URIStorage)
        returns (bool)
    {
        return
            interfaceId == 0xb45a3c0e || // EIP-5192 Soulbound
            super.supportsInterface(interfaceId);
    }
}
