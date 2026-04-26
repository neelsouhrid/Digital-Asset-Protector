// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/Pausable.sol";

// Interface for IdentityRegistry
interface IIdentityRegistry {
    function isVerifiedCreator(address account) external view returns (bool);
}

/**
 * @title ProvenanceRegistry
 * @notice Core registry where asset signatures are anchored to the blockchain.
 *         Only verified creators can register assets.
 */
contract ProvenanceRegistry is Ownable, ReentrancyGuard, Pausable {
    
    struct Asset {
        bytes32 signatureHash;
        address owner;
        uint256 timestamp;
        bool isProtected;
    }

    IIdentityRegistry public identityRegistry;

    // Maps signature hash to Asset
    mapping(bytes32 => Asset) public assets;

    event AssetRegistered(bytes32 indexed signatureHash, address indexed owner, uint256 timestamp);
    event Challenge(bytes32 indexed signatureHash, address indexed reporter, string reason);
    event AssetProtectionStatusChanged(bytes32 indexed signatureHash, bool isProtected);
    event AssetTransferred(bytes32 indexed signatureHash, address indexed oldOwner, address indexed newOwner);

    error NotVerifiedCreator();
    error AssetAlreadyRegistered();
    error AssetNotRegistered();
    error NotAssetOwner();
    error InvalidRegistryAddress();

    /**
     * @param _identityRegistry The address of the IdentityRegistry contract
     * @param initialOwner The owner of the ProvenanceRegistry
     */
    constructor(address _identityRegistry, address initialOwner) Ownable(initialOwner) {
        if (_identityRegistry == address(0)) {
            revert InvalidRegistryAddress();
        }
        identityRegistry = IIdentityRegistry(_identityRegistry);
    }

    modifier onlyVerifiedCreator() {
        if (!identityRegistry.isVerifiedCreator(msg.sender)) {
            revert NotVerifiedCreator();
        }
        _;
    }

    /**
     * @notice Registers a new asset with a signature hash
     * @param _signatureHash The unique hash of the asset's signature
     */
    function registerAsset(bytes32 _signatureHash) external whenNotPaused nonReentrant onlyVerifiedCreator {
        if (assets[_signatureHash].timestamp != 0) {
            revert AssetAlreadyRegistered();
        }

        assets[_signatureHash] = Asset({
            signatureHash: _signatureHash,
            owner: msg.sender,
            timestamp: block.timestamp,
            isProtected: true
        });

        emit AssetRegistered(_signatureHash, msg.sender, block.timestamp);
    }

    /**
     * @notice Reports a suspicious signature
     * @dev Could be called by the Android Core Service
     * @param _signatureHash The signature hash being challenged
     * @param reason The reason for the challenge
     */
    function reportSuspiciousSignature(bytes32 _signatureHash, string calldata reason) external whenNotPaused {
        if (assets[_signatureHash].timestamp == 0) {
            revert AssetNotRegistered();
        }
        emit Challenge(_signatureHash, msg.sender, reason);
    }

    /**
     * @notice Sets the protection status of an asset
     * @param _signatureHash The signature hash of the asset
     * @param _isProtected The new protection status
     */
    function setAssetProtection(bytes32 _signatureHash, bool _isProtected) external {
        if (assets[_signatureHash].timestamp == 0) {
            revert AssetNotRegistered();
        }
        if (msg.sender != assets[_signatureHash].owner && msg.sender != owner()) {
            revert NotAssetOwner();
        }
        
        assets[_signatureHash].isProtected = _isProtected;
        emit AssetProtectionStatusChanged(_signatureHash, _isProtected);
    }

    /**
     * @notice Transfers ownership of a registered asset to a new owner
     * @param _signatureHash The signature hash of the asset
     * @param _newOwner The address of the new owner
     */
    function transferAsset(bytes32 _signatureHash, address _newOwner) external whenNotPaused {
        if (assets[_signatureHash].timestamp == 0) {
            revert AssetNotRegistered();
        }
        if (msg.sender != assets[_signatureHash].owner) {
            revert NotAssetOwner();
        }
        if (_newOwner == address(0)) {
            revert InvalidRegistryAddress();
        }

        address oldOwner = assets[_signatureHash].owner;
        assets[_signatureHash].owner = _newOwner;

        emit AssetTransferred(_signatureHash, oldOwner, _newOwner);
    }

    /**
     * @notice Pauses the registry
     */
    function pause() external onlyOwner {
        _pause();
    }

    /**
     * @notice Unpauses the registry
     */
    function unpause() external onlyOwner {
        _unpause();
    }

    /**
     * @notice Updates the Identity Registry address
     * @param _identityRegistry The new address of the IdentityRegistry
     */
    function updateIdentityRegistry(address _identityRegistry) external onlyOwner {
        if (_identityRegistry == address(0)) {
            revert InvalidRegistryAddress();
        }
        identityRegistry = IIdentityRegistry(_identityRegistry);
    }
}
