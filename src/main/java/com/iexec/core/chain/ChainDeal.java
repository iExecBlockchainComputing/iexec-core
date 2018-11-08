package com.iexec.core.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ChainDeal {

    // deal_pt1
    String dappPointer;
    String dappOwner;
    BigInteger dappPrice;
    String dataPointer;
    String dataOwner;
    BigInteger dataPrice;
    String poolPointer;
    String poolOwner;
    BigInteger poolPrice;

    // deal_pt2
    BigInteger trust;
    BigInteger tag;
    String requester;
    String beneficiary;
    String callback;
    String params;

    // config
    BigInteger category;
    BigInteger startTime;
    BigInteger botFirst;
    BigInteger botSize;
    BigInteger workerStake;
    BigInteger schedulerRewardRatio;

}
