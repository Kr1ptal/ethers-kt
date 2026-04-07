// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract Errors {
    error NoArgsError();
    error SimpleArgsError(uint256 status, string msg, bool done);
    error ComplexArgsError(uint256[][][] status, string[3] msg);
    error StructArgsError(uint256 status, Details[] details);

    struct Details {
        bool success;
        bytes data;
    }
}
