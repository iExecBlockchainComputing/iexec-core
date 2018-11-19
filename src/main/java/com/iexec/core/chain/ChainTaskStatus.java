package com.iexec.core.chain;

import java.math.BigInteger;

public enum ChainTaskStatus {
    UNSET,     // Work order not yet initialized (invalid address)
    ACTIVE,    // Marketed → constributions are open
    REVEALING, // Starting consensus reveal
    COMPLETED, // Concensus achieved
    FAILLED;    // Failed consensus

    public static ChainTaskStatus getValue(int i) {
        return ChainTaskStatus.values()[i];
    }

    public static ChainTaskStatus getValue(BigInteger i) {
        return ChainTaskStatus.values()[i.intValue()];
    }

}
