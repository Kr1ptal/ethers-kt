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

    function overloaded(uint256 status, string memory msg) external {}

    function overloaded(uint256 status, string memory msg, bool done) external {}

    // reserved java keywords
    function class(uint256 status, string memory msg) external {}

    function package(uint256 status, string memory msg) external {}


    function payableArgs(uint256 status, string memory msg) external payable {}

    function viewArgs(uint256 status, string memory msg) external view {}

    struct Details {
        bool success;
        bytes data;
    }
}
