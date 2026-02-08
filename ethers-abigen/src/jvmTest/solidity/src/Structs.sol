// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract Structs {
    struct Simple {
        bool success;
        bytes data;
    }

    struct Complex {
        uint256[][][] status;
        string[3] msg;
    }

    struct Nested {
        string desc;
        Simple simple;
        Complex complex;
    }

    // reserved java keywords
    struct class {
        string desc;
    }

    struct package {
        string desc;
    }

    function withStructs(Simple memory simple, Complex memory complex, Nested memory nested, class memory cls, package memory pkg) external {}
}
