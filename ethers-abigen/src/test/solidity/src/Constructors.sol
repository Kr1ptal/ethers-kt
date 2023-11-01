// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract ConstructorNoExplicit {

}

contract ConstructorExplicitEmpty {
    constructor() {}
}

contract ConstructorArgumentsPayable {
    constructor(uint256 value, string memory description) payable {}
}

contract ConstructorComplexArguments {
    constructor(uint256 number, Details memory details) {}

    struct Details {
        bool success;
        bytes data;
    }
}
