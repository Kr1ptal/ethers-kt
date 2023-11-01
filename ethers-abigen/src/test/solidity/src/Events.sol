// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract Events {
    event NoArgsEvent();
    event NoIndexedArgsEvent(uint256 status, string msg);
    event OnlyIndexedArgsEvent(uint256 indexed status, bool indexed done);
    event ComplexIndexedArgsEvent(uint256 indexed status, string indexed msg);
    event StructIndexedArgsEvent(uint256 indexed status, Details indexed details);
    event AnonymousEvent(uint256 indexed status, string indexed msg) anonymous;

    // overloaded events
    event IndexedAndDataArgsEvent(uint256 indexed status, Details details);
    event IndexedAndDataArgsEvent(uint256 indexed status, uint16 errorCode, Details details);

    struct Details {
        bool success;
        bytes data;
    }
}
