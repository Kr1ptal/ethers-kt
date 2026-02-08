// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

interface ExtendedResolver {
    /**
    * Returns the address associated with an ENS node.
    * @param node The ENS node to query.
    * @return The associated address.
    * https://etherscan.io/address/0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63#code#F19#L15
    */
    function addr(bytes32 node) external view returns (address payable);


    /**
     * Returns the name associated with an ENS node, for reverse records.
     * Defined in EIP181.
     * @param node The ENS node to query.
     * @return The associated name.
     * https://etherscan.io/address/0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63#code#F25#L7
     */
    function name(bytes32 node) external view returns (string memory);

    /**
     * Returns the text data associated with an ENS node and key.
     * @param node The ENS node to query.
     * @param key The text data key to query.
     * @return The associated text data.
     * https://etherscan.io/address/0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63#code#F27#L12
     */
    function text(
        bytes32 node,
        string calldata key
    ) external view returns (string memory);

    /**
     * Resolves a name, as specified by ENSIP 10.
     * @param name The DNS-encoded name to resolve.
     * @param data The ABI encoded data for the underlying resolution function (Eg, addr(bytes32), text(bytes32,string), etc).
     * @return The return data, ABI encoded identically to the underlying function.
     * https://docs.ens.domains/ens-improvement-proposals/ensip-10-wildcard-resolution
     */
    function resolve(bytes calldata name, bytes calldata data) external view returns (bytes memory);

    /**
     * @dev Returns true if this contract implements the interface defined by
     * `interfaceId`. See the corresponding
     * https://eips.ethereum.org/EIPS/eip-165#how-interfaces-are-identified[EIP section]
     * to learn more about how these ids are created.
     *
     * This function call must use less than 30 000 gas.
     * https://etherscan.io/address/0x231b0Ee14048e9dCcD1d247744d114a4EB5E8E63#code#F6#L24
     */
    function supportsInterface(bytes4 interfaceId) external view returns (bool);

    error OffchainLookup(address sender, string[] urls, bytes callData, bytes4 callbackFunction, bytes extraData);
}

