// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.13;

contract ReceiveAndFallback {
    receive() external payable {}

    fallback() external payable {}
}

contract NonpayableFallback {
    fallback() external {}
}
