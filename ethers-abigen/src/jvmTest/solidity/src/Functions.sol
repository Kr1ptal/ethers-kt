// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract Functions {
    function noArgs() external {}

    function noArgsReturns() external returns (uint256) {
        return 42;
    }

    function noArgsMultiReturns() external returns (uint256 status, bool done) {
        return (42, true);
    }

    function simpleArgs(uint256 status, string memory msg) external {}

    function complexArgs(uint256 status, Details[3][] memory details) external returns (uint256) {
        return status;
    }

    function complexArgs2(uint256 status, Details[3][] memory differentName) external returns (uint256) {
        return status;
    }

    // overloaded functions

    function overloaded(uint256 status, string memory msg) external {}

    function overloaded(uint256 status, string memory msg, bool done) external {}

    function get_dy(
        int128 i,
        int128 j,
        uint256 dx
    ) external view returns (uint256) {
        return dx;
    }

    function get_dy(
        uint256 i,
        uint256 j,
        uint256 dx
    ) external view returns (uint256) {
        return dx;
    }

    // reserved java keywords

    function class(uint256 status, string memory msg) external {}

    function package(uint256 status, string memory msg) external {}

    /////////////////////////

    function payableArgs(uint256 status, string memory msg) external payable {}

    function viewArgs(uint256 status, string memory msg) external view {}

    // complex constant names

    function getE2ModeCategoryData() external {}

    function JsonABI() external {}

    function repayWithATokens() external {}

    //////////////////////////

    struct Details {
        bool success;
        bytes data;
    }
}
