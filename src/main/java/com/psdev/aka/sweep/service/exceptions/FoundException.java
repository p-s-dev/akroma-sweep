package com.psdev.aka.sweep.service.exceptions;

import org.web3j.protocol.core.methods.response.EthBlock;

public class FoundException extends RuntimeException{
    EthBlock.Block block;
    public FoundException(EthBlock.Block b) {
        super();
        this.block = b;
    }

    public EthBlock.Block getBlock() {
        return block;
    }


}
